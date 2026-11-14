package com.techgv.stash.core.testing

import com.techgv.stash.core.model.SharedPayload
import com.techgv.stash.core.model.WidgetSnapshot
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.Connectivity
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.platform.ScheduledNotification
import com.techgv.stash.core.platform.ShareInbox
import com.techgv.stash.core.platform.WidgetPublisher

/**
 * Queue payloads with [share]; [drain] follows the [ShareInbox] contract: entries go to `save` in order and each is
 * removed only after `save` returns. [dieAfterNextSave] and [shareDuringNextSave] script the awkward moments.
 */
class FakeShareInbox : ShareInbox {
    private val queued = ArrayDeque<SharedPayload>()
    private var dieAfterSave = false
    private var appendDuringSave = emptyList<SharedPayload>()
    var drainCount = 0
        private set

    /** What is still waiting. */
    val waiting: List<SharedPayload> get() = queued.toList()

    fun share(vararg payloads: SharedPayload) {
        queued += payloads
    }

    /**
     * The next drain stops right after `save` returns for an entry, before removing it, as if the app were killed:
     * the entry is delivered again by the following drain.
     */
    fun dieAfterNextSave() {
        dieAfterSave = true
    }

    /** Appends [payloads] while the next `save` runs, as the iOS Share Extension may during a drain. */
    fun shareDuringNextSave(vararg payloads: SharedPayload) {
        appendDuringSave = payloads.toList()
    }

    override suspend fun drain(save: suspend (SharedPayload) -> Unit) {
        drainCount++
        while (queued.isNotEmpty()) {
            val next = queued.first()
            share(*appendDuringSave.toTypedArray()).also { appendDuringSave = emptyList() }
            save(next)
            if (dieAfterSave) {
                dieAfterSave = false
                throw IllegalStateException("process died before the entry was removed")
            }
            queued.removeFirst()
        }
    }
}

class FakeNotificationScheduler : NotificationScheduler {
    /** What is scheduled now: the last applied plan, empty before the first [apply]. */
    var scheduled: List<ScheduledNotification> = emptyList()
        private set
    var applyCount = 0
        private set

    override suspend fun apply(plan: List<ScheduledNotification>) {
        applyCount++
        scheduled = plan
    }
}

class FakeWidgetPublisher : WidgetPublisher {
    val published = mutableListOf<WidgetSnapshot>()

    override suspend fun publish(snapshot: WidgetSnapshot) {
        published += snapshot
    }
}

class FakeBackgroundJobs : BackgroundJobs {
    /** Every call, in order, including repeats; use `.toSet()` for what the OS would hold. */
    val scheduled = mutableListOf<BackgroundJob>()

    override fun schedule(job: BackgroundJob) {
        scheduled += job
    }
}

/** Set [online] to switch the device's connection. */
class FakeConnectivity(var online: Boolean = true) : Connectivity {
    override fun isOnline(): Boolean = online
}
