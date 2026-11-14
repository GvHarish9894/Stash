package com.techgv.stash.core.testing

import com.techgv.stash.core.domain.AutoArchivePolicy
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.CommandDecider
import com.techgv.stash.core.domain.Decision
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.IdGenerator
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.StreakEngine
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.StreakDay
import kotlin.time.Instant

/**
 * A [CommandBus] that applies commands to [items] with the real [CommandDecider], like the data layer: it records the
 * streak day of a read and its celebration (decision D21), keeps [FakeStreakRepository.unreadZeroSince] (decision D13),
 * applies settings changes to [settings] and returns the real [DispatchResult]. It records every command in [dispatched]
 * and every operation in [operations]. [rejectNext] scripts a rejection (for example [Rejection.StorageFailure]) for the
 * next command, which then changes nothing.
 */
class FakeCommandBus(
    val items: FakeItemRepository = FakeItemRepository(),
    val settings: FakeSettingsRepository = FakeSettingsRepository(),
    val streak: FakeStreakRepository = FakeStreakRepository(items),
    ids: IdGenerator = SequenceIdGenerator(),
) : CommandBus {
    private val decider = CommandDecider(ids)
    private var scriptedRejection: Rejection? = null
    private val log = mutableListOf<Operation>()

    val dispatched = mutableListOf<Command>()
    val operations: List<Operation> get() = log

    fun rejectNext(rejection: Rejection) {
        scriptedRejection = rejection
    }

    override suspend fun dispatch(command: Command): DispatchResult {
        dispatched += command
        scriptedRejection?.let {
            scriptedRejection = null
            return DispatchResult.Rejected(it)
        }
        if (command is Command.ChangeSetting) {
            settings.preferences.value = command.transform(settings.preferences.value)
            return DispatchResult.Done(affected = 0)
        }
        val now = items.clock.now()
        val unreadBefore = unreadCount()
        val result = when (command) {
            is Command.ClearRead -> bulk(decider.clearRead(items.items.value, now))
            is Command.ApplyAutoArchive ->
                if (AutoArchivePolicy.cutoff(command.period, now) == null) {
                    DispatchResult.Rejected(Rejection.NothingToApply)
                } else {
                    bulk(decider.applyAutoArchive(items.items.value, command.period, now))
                }
            else -> {
                val decision = decideOne(command, now)
                if (decision is Decision.Applied) store(decision)
                val celebration = if (decision is Decision.Applied && command.isRead()) recordRead(now) else null
                if (decision is Decision.Applied && command is Command.SaveLink) retryFailedMetadata(decision, now)
                when (val result = DispatchResult.of(decision)) {
                    is DispatchResult.Done -> result.copy(celebration = celebration)
                    is DispatchResult.Rejected -> result
                }
            }
        }
        trackUnreadZero(unreadBefore, now)
        return result
    }

    private fun decideOne(command: Command, now: Instant): Decision {
        fun find(id: ItemId) = items.items.value.find { it.id == id }
        return when (command) {
            is Command.SaveLink ->
                decider.saveLink(items.items.value.find { it.normalizedUrl == command.capture.normalizedUrl }, command, now)
            is Command.ForItem -> decider.decide(find(command.id), command, now)
            is Command.OpenItem -> decider.open(find(command.id), settings.preferences.value.markReadOnOpen, now)
            is Command.Undo -> {
                val operation = log.find { it.opId == command.opId }
                decider.undo(operation, operation?.let { find(it.itemId) }, now)
            }
            is Command.ClearRead, is Command.ApplyAutoArchive, is Command.ChangeSetting -> error("not a single-item command")
        }
    }

    private fun Command.isRead() = this is Command.MarkRead || this is Command.OpenItem

    private fun bulk(decision: Decision): DispatchResult {
        if (decision is Decision.Applied) store(decision)
        return DispatchResult.ofBulk(listOf(decision))
    }

    private fun store(decision: Decision.Applied) {
        items.upsert(decision.items)
        log += decision.operations
    }

    private fun recordRead(now: Instant): Celebration? {
        val since = streak.unreadZeroSince.value?.let { minOf(it, now) }
        val outcome = StreakEngine.onRead(streak.days.value, since, now, items.zones.zone())
        outcome.newDay?.let { day -> streak.days.value = streak.days.value.filter { it.date != day.date } + day }
        return outcome.celebration
    }

    /** Sharing a link whose metadata failed is a retry, as in the data layer. */
    private fun retryFailedMetadata(decision: Decision.Applied, now: Instant) {
        val item = decision.items.single()
        if (item.fetchStatus != FetchStatus.Failed) return
        val retry = decider.decide(item, Command.RetryMetadata(item.id), now)
        if (retry is Decision.Applied) store(retry)
    }

    private fun unreadCount() = items.items.value.count { it.lifecycle == ItemLifecycle.Unread }

    private fun trackUnreadZero(unreadBefore: Int, now: Instant) {
        val unreadAfter = unreadCount()
        when {
            unreadBefore > 0 && unreadAfter == 0 -> streak.unreadZeroSince.value = now
            unreadBefore == 0 && unreadAfter > 0 -> {
                val since = streak.unreadZeroSince.value?.let { minOf(it, now) } ?: return
                val zone = items.zones.zone()
                val rest = StreakEngine.restDays(streak.days.value, since, StreakEngine.dayOf(now, zone), zone)
                streak.days.value += rest.map { StreakDay(it, firstReadAt = null) }
                streak.unreadZeroSince.value = null
            }
        }
    }
}
