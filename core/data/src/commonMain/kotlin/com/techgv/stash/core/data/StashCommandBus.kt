package com.techgv.stash.core.data

import androidx.sqlite.SQLiteException
import com.techgv.stash.core.database.NormalizedUrlTakenException
import com.techgv.stash.core.database.SavedItemEntity
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.toEntity
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.database.writeTransaction
import com.techgv.stash.core.domain.AutoArchivePolicy
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.CommandDecider
import com.techgv.stash.core.domain.Decision
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.StreakEngine
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.preferences.SettingsStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import okio.IOException
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The single funnel for every write. An item command reads the current state, decides with [CommandDecider] and writes
 * the items, their operations, the streak day of a read (decision D21) and `unreadZeroSince` in ONE immediate
 * transaction; SQLite has one writer, so two commands never decide on the same stale state. Side effects run after the
 * commit and cannot roll it back. Only storage errors become [Rejection.StorageFailure]; a bug still throws.
 */
internal class StashCommandBus(
    private val database: StashDatabase,
    private val settings: SettingsStore,
    private val decider: CommandDecider,
    private val clock: Clock,
    private val zones: ZoneProvider,
    private val effects: SideEffects,
) : CommandBus {
    private val items = database.savedItemDao()
    private val operations = database.operationDao()

    private class Outcome(val result: DispatchResult, val needsMetadata: List<ItemId>)

    override suspend fun dispatch(command: Command): DispatchResult = dispatch(command, fetchLater = true)

    /**
     * [fetchLater] false when the caller fetches the new item's metadata itself (catch-up), so one item is never both
     * scheduled and fetched inline.
     */
    suspend fun dispatch(command: Command, fetchLater: Boolean): DispatchResult {
        if (command is Command.ChangeSetting) return changeSetting(command)
        // Settings live outside the database, so they are read before the transaction.
        val markReadOnOpen = if (command is Command.OpenItem) {
            try {
                settings.preferences.first().markReadOnOpen
            } catch (e: IOException) {
                return storageFailure
            }
        } else {
            false
        }
        // Once started, the write and its follow-up run to the end even if the caller is cancelled: a committed
        // save must still get its metadata job and its re-plan.
        return withContext(NonCancellable) {
            val outcome = try {
                write(command, markReadOnOpen)
            } catch (e: NormalizedUrlTakenException) {
                // Another writer stored the same link between our read and write: deciding again makes it a re-share.
                if (command is Command.SaveLink) storageSafe { write(command, markReadOnOpen) } else Outcome(storageFailure, emptyList())
            } catch (e: SQLiteException) {
                Outcome(storageFailure, emptyList())
            }
            if (outcome.result is DispatchResult.Done) {
                if (fetchLater) outcome.needsMetadata.forEach(effects::fetchMetadataLater)
                effects.requestRefresh()
            }
            outcome.result
        }
    }

    private val storageFailure = DispatchResult.Rejected(Rejection.StorageFailure)

    private suspend fun storageSafe(block: suspend () -> Outcome): Outcome = try {
        block()
    } catch (e: SQLiteException) {
        Outcome(storageFailure, emptyList())
    }

    private suspend fun write(command: Command, markReadOnOpen: Boolean): Outcome = database.writeTransaction {
        val now = clock.now()
        val zone = zones.zone()
        val unreadBefore = items.unreadCount()
        val result = when (command) {
            is Command.ClearRead -> bulk { after -> items.clearReadCandidates(after, CHUNK).let { decider.clearRead(it.models(), now) to it } }
            is Command.ApplyAutoArchive -> {
                val cutoff = AutoArchivePolicy.cutoff(command.period, now)
                // Every foreground applies it and usually nothing is due: the indexed count answers that without the
                // candidate walk, which reads the whole table in id order.
                if (cutoff == null || items.countAutoArchiveDue(cutoff) == 0) {
                    DispatchResult.Rejected(Rejection.NothingToApply)
                } else {
                    bulk { after ->
                        items.autoArchiveCandidates(cutoff, after, CHUNK).let { decider.applyAutoArchive(it.models(), command.period, now) to it }
                    }
                }
            }
            else -> {
                val decision = decideOne(command, now, markReadOnOpen)
                if (decision is Decision.Applied) store(decision)
                val celebration = if (decision is Decision.Applied && command.isRead()) recordRead(now, zone) else null
                if (decision is Decision.Applied && command is Command.SaveLink) retryFailedMetadata(decision.items.single(), now)
                when (val result = DispatchResult.of(decision)) {
                    is DispatchResult.Done -> result.copy(celebration = celebration)
                    is DispatchResult.Rejected -> result
                }
            }
        }
        if (command is Command.RetryMetadata && result is DispatchResult.Done) database.metadataAttemptDao().reset(command.id.value)
        database.trackUnreadZero(unreadBefore, now, zone)
        Outcome(result, needsMetadata(command, result))
    }

    private fun Command.isRead() = this is Command.MarkRead || this is Command.OpenItem

    private suspend fun decideOne(command: Command, now: Instant, markReadOnOpen: Boolean): Decision = when (command) {
        is Command.SaveLink -> decider.saveLink(items.getByNormalizedUrl(command.capture.normalizedUrl)?.toModel(), command, now)
        is Command.ForItem -> decider.decide(items.get(command.id.value)?.toModel(), command, now)
        is Command.OpenItem -> decider.open(items.get(command.id.value)?.toModel(), markReadOnOpen, now)
        is Command.Undo -> {
            val operation = operations.get(command.opId)?.toModel()
            decider.undo(operation, operation?.let { items.get(it.itemId.value) }?.toModel(), now)
        }
        is Command.ClearRead, is Command.ApplyAutoArchive, is Command.ChangeSetting -> error("not a single-item command")
    }

    /**
     * A read counts for the streak (decision D21), even when it changed no item. Runs before `unreadZeroSince` is
     * updated, so the engine sees the empty-shelf days as they were when the user read.
     */
    private suspend fun recordRead(now: Instant, zone: TimeZone): Celebration? {
        val since = database.appStateDao().unreadZeroSince().cappedAt(now)
        val outcome = StreakEngine.onRead(database.streakDayDao().all(), since, now, zone)
        outcome.newDay?.let { database.streakDayDao().upsert(listOf(it.toEntity())) }
        return outcome.celebration
    }

    /** Sharing a link whose metadata failed again is a natural retry: back to pending with a fresh set of attempts. */
    private suspend fun retryFailedMetadata(item: SavedItem, now: Instant) {
        if (item.fetchStatus != FetchStatus.Failed) return
        val retry = decider.decide(item, Command.RetryMetadata(item.id), now)
        if (retry is Decision.Applied) store(retry)
        database.metadataAttemptDao().reset(item.id.value)
    }

    private suspend fun store(decision: Decision.Applied) {
        items.upsert(decision.items.map { it.toEntity() })
        operations.insert(decision.operations.map { it.toEntity() })
    }

    /**
     * Decides and stores keyset chunks of candidates until none are left. The result follows [DispatchResult.ofBulk]
     * without keeping every chunk's items alive: only rejections are kept, plus the count of changed items.
     */
    private suspend fun bulk(nextChunk: suspend (afterId: String) -> Pair<Decision, List<SavedItemEntity>>): DispatchResult {
        var affected = 0
        val rejections = ArrayList<Decision.Rejected>()
        var after = ""
        while (true) {
            val (decision, chunk) = nextChunk(after)
            if (chunk.isEmpty()) break
            when (decision) {
                is Decision.Applied -> {
                    store(decision)
                    affected += decision.items.size
                }
                is Decision.Rejected -> rejections += decision
            }
            after = chunk.last().id
        }
        return if (affected > 0) DispatchResult.Done(affected) else DispatchResult.ofBulk(rejections)
    }

    private fun List<SavedItemEntity>.models(): List<SavedItem> = map { it.toModel() }

    private suspend fun needsMetadata(command: Command, result: DispatchResult): List<ItemId> {
        val id = (result as? DispatchResult.Done)?.itemId ?: return emptyList()
        if (command !is Command.SaveLink && command !is Command.RetryMetadata) return emptyList()
        return listOfNotNull(id.takeIf { items.get(it.value)?.fetchStatus == FetchStatus.Pending })
    }

    // Like an item command: once started, the write and its re-plan run to the end even if the caller is cancelled.
    private suspend fun changeSetting(command: Command.ChangeSetting): DispatchResult = withContext(NonCancellable) {
        var before: UserPreferences? = null
        val after = try {
            settings.update { current -> command.transform(current).also { before = current } }
        } catch (e: IOException) {
            return@withContext storageFailure
        }
        if (before?.let { it.reminderSettings() != after.reminderSettings() } == true) effects.requestRefresh()
        DispatchResult.Done(affected = 0)
    }

    // The settings that change the reminder plan or its rendered text.
    private fun UserPreferences.reminderSettings() = listOf(dailyReminder, reminderTime, streakSaver, language)

    private companion object {
        const val CHUNK = 500
    }
}
