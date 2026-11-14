package com.techgv.stash.core.data

import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.LinkCapture
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.ShareInbox
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Entry points for the platform shells. Every one is idempotent and safe to call again at any time; each step's failure
 * is isolated from the others, and whatever did not happen happens on the next call.
 */
class StashSync internal constructor(
    private val inbox: ShareInbox,
    private val bus: StashCommandBus,
    private val metadata: MetadataUpdater,
    private val database: StashDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val effects: SideEffects,
    private val jobs: BackgroundJobs,
    private val ticks: LocalTicks,
    private val maxCacheBytes: Long = ARTICLE_CACHE_MAX_BYTES,
) {
    private val running = Mutex()
    private val fetching = Mutex()

    // Set by every catch-up; the one holding [fetching] runs another pass while it is set.
    @Volatile
    private var fetchRequested = false

    /**
     * Catches up on everything background work may have missed. Call on launch, on every foreground, and from the
     * `DailyMaintenance` job (and the iOS app refresh task). First the local steps, one call at a time: save shared links
     * waiting in the inbox, apply auto-archive, prune the operation log, trim the article cache, re-plan reminders,
     * republish the widget, schedule the next `DailyMaintenance`. Then fetch metadata for pending items, least recently
     * attempted first. One catch-up fetches at a time: a call that finds another one fetching returns after its local
     * steps, and the fetching one reads the pending list again when its pass ends, skipping the items it already
     * attempted. So a link the later call saved is still fetched, and no item is attempted twice by one catch-up.
     */
    suspend fun onForeground() {
        running.withLock {
            ticks.poke()
            isolated { saveSharedLinks() }
            isolated {
                val period = settings.preferences.first().autoArchive
                if (period.days != null) bus.dispatch(Command.ApplyAutoArchive(period))
            }
            isolated { database.operationDao().prune(clock.now()) }
            isolated { database.articleContentDao().evictBeyond(maxCacheBytes) }
            try {
                effects.refreshNow()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (e: Throwable) {
                // Like the worker: an Error from a platform step ends the refresh, not the catch-up.
            }
            isolated { jobs.schedule(BackgroundJob.DailyMaintenance) }
        }
        fetchPending()
    }

    private suspend fun fetchPending() {
        fetchRequested = true
        val attempted = HashSet<String>()
        // Checked again after unlocking: a call that failed tryLock just before the unlock left its request to us.
        while (fetchRequested && fetching.tryLock()) {
            try {
                while (fetchRequested) {
                    fetchRequested = false
                    isolated { fetchAll(database.metadataAttemptDao().pendingIds(CATCH_UP_FETCHES).filter(attempted::add)) }
                }
            } finally {
                fetching.unlock()
            }
        }
    }

    /** The `FetchMetadata(itemId)` job: retry with backoff when this returns [MetadataFetchOutcome.RetryLater]. */
    suspend fun fetchMetadata(id: ItemId): MetadataFetchOutcome = metadata.fetch(id)

    /** Pull to refresh: every failed item goes back to pending with a fresh set of attempts and is fetched now. */
    suspend fun refetchFailedMetadata() {
        val failed = database.savedItemDao().idsWithFetchStatus(FetchStatus.Failed, REFETCH_LIMIT)
        failed.forEach { bus.dispatch(Command.RetryMetadata(ItemId(it)), fetchLater = false) }
        fetchAll(failed)
    }

    /**
     * Call on boot, on a time-zone change and on a manual clock change: re-plans reminders for the new local times and
     * recomputes "today" for the lists and the streak.
     */
    suspend fun onTimeChanged() {
        ticks.poke()
        effects.refreshNow()
    }

    /**
     * A payload without a link, a save and a re-share are all handed over (removed from the inbox); only a save the
     * database could not store throws, so the inbox keeps that entry for the next drain. A redelivered payload, or two
     * shares of one link, re-share the same item: one row, moved to the top. Metadata is fetched by [onForeground]'s
     * last step, not by a job as well.
     */
    private suspend fun saveSharedLinks() = inbox.drain { payload ->
        val capture = LinkCapturePolicy.capture(payload) as? LinkCapture.Captured ?: return@drain
        val result = bus.dispatch(Command.SaveLink(capture, payload.sourceApp), fetchLater = false)
        if (result == DispatchResult.Rejected(Rejection.StorageFailure)) throw IllegalStateException("not stored; kept in the inbox")
    }

    private suspend fun fetchAll(ids: List<String>) = coroutineScope {
        val permits = Semaphore(METADATA_CONCURRENCY)
        ids.forEach { id -> launch { permits.withPermit { isolated { metadata.fetch(ItemId(id)) } } } }
    }

    private companion object {
        const val CATCH_UP_FETCHES = 50
        const val REFETCH_LIMIT = 200
        const val METADATA_CONCURRENCY = 4
    }
}
