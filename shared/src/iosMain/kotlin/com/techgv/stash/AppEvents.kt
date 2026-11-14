package com.techgv.stash

import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.data.StashSync
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.IosBackgroundJobs
import com.techgv.stash.core.platform.debugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.BackgroundTasks.BGTaskScheduler

// What the Swift app calls when the OS tells it something. Each returns at once; the work runs in the app scope.

/** Launch and every foreground, and the Share Extension's "inbox changed" Darwin notification. */
fun onForeground() {
    debugLog("foreground catch-up requested")
    launchSync { onForeground() }
}

/** Significant time change (midnight, manual clock change) and time-zone change. */
fun onTimeChanged() {
    launchSync { onTimeChanged() }
}

private fun launchSync(block: suspend StashSync.() -> Unit): Job {
    val koin = KoinPlatform.getKoin()
    return koin.get<CoroutineScope>(APPLICATION_SCOPE).launch { koin.get<StashSync>().block() }
}

/** The app refresh task runs the foreground catch-up, stops when iOS expires it, and asks for the next one first. */
internal fun registerRefreshTask() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(IosBackgroundJobs.REFRESH_TASK_ID, usingQueue = null) { task ->
        debugLog("refresh task started")
        KoinPlatform.getKoin().get<BackgroundJobs>().schedule(BackgroundJob.DailyMaintenance)
        val run = launchSync { onForeground() }
        task?.expirationHandler = { run.cancel() }
        run.invokeOnCompletion { error ->
            debugLog("refresh task finished, success=${error == null}")
            task?.setTaskCompletedWithSuccess(error == null)
        }
    }
}
