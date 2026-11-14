package com.techgv.stash.core.data

import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.domain.ReminderRepository
import com.techgv.stash.core.domain.WidgetSnapshotPolicy
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.WidgetPublisher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * What happens after a write commits: re-plan reminders, republish the widget, schedule metadata fetches. Runs outside
 * every transaction, so nothing here can roll a write back, and each step is isolated from the others. The worker and
 * the midnight republish live in [scope], the application's scope; cancel it before closing the database.
 */
internal class SideEffects(
    scope: CoroutineScope,
    private val reminders: ReminderRepository,
    private val widget: WidgetPublisher,
    private val jobs: BackgroundJobs,
    private val database: StashDatabase,
    private val clock: Clock,
    private val zones: ZoneProvider,
    ticks: LocalTicks,
) {
    // Conflated, and each run waits a moment first: a burst of commands (swipes, a drained inbox) re-plans once or
    // twice instead of once per command.
    private val refreshes = Channel<Unit>(Channel.CONFLATED)

    // One refresh at a time: a plan read before a change must not be applied after the plan read after it.
    private val refreshing = Mutex()

    init {
        // Nothing should escape the loops below; if something does, it must not take the application scope with it.
        val keepAlive = CoroutineExceptionHandler { _, _ -> }
        scope.launch(keepAlive) {
            for (request in refreshes) {
                delay(COALESCE_WINDOW)
                refreshes.tryReceive()
                try {
                    refreshNow()
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                } catch (e: Throwable) {
                    // An Error from a step ends this refresh, not the worker: the next request runs normally.
                }
            }
        }
        // A new local day changes "read today" on the widget and today's reminder slots.
        scope.launch(keepAlive) { ticks.at(MIDNIGHT).drop(1).collect { requestRefresh() } }
    }

    /** Re-plans reminders and republishes the widget soon, without making the caller wait. */
    fun requestRefresh() {
        refreshes.trySend(Unit)
    }

    suspend fun refreshNow() = refreshing.withLock {
        isolated { reminders.replan() }
        isolated { publishWidget() }
    }

    fun fetchMetadataLater(id: ItemId) {
        try {
            jobs.schedule(BackgroundJob.FetchMetadata(id))
        } catch (e: Exception) {
            // The contract says schedule never throws; if it does, catch-up fetches the pending item anyway.
        }
    }

    companion object {
        val COALESCE_WINDOW = 300.milliseconds
    }

    private suspend fun publishWidget() {
        val now = clock.now()
        val snapshot = database.readTransaction {
            val streak = database.streakNow(now, zones.zone())
            val next = database.savedItemDao().newestUnread(WidgetSnapshotPolicy.MAX_ITEMS).map { it.toModel() }
            WidgetSnapshotPolicy.snapshot(next, streak.unreadCount, streak.state)
        }
        widget.publish(snapshot)
    }
}
