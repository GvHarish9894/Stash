package com.techgv.stash.core.platform

import com.techgv.stash.core.model.ItemId
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * [BackgroundJob.FetchMetadata] runs at once in the app process through [fetchMetadata] (`shared` launches
 * `StashSync.fetchMetadata` in the application scope); iOS has no per-item background job, so a fetch that fails is
 * retried by the foreground catch-up and the app refresh task, and the data layer enforces the attempt limit.
 *
 * [BackgroundJob.DailyMaintenance] submits one `BGAppRefreshTask` ([REFRESH_TASK_ID], listed in the app's
 * `BGTaskSchedulerPermittedIdentifiers`) unless one is already pending (keep); its handler, registered at launch by
 * `shared`, runs `StashSync.onForeground()`. A refused request (no permission, simulator) is logged in debug builds
 * with its error code and otherwise dropped.
 */
class IosBackgroundJobs(private val fetchMetadata: (ItemId) -> Unit) : BackgroundJobs {
    override fun schedule(job: BackgroundJob) {
        when (job) {
            is BackgroundJob.FetchMetadata -> fetchMetadata(job.itemId)
            BackgroundJob.DailyMaintenance -> submitRefreshTask()
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun submitRefreshTask() {
        val scheduler = BGTaskScheduler.sharedScheduler
        scheduler.getPendingTaskRequestsWithCompletionHandler { pending ->
            if (pending.orEmpty().any { (it as BGTaskRequest).identifier == REFRESH_TASK_ID }) return@getPendingTaskRequestsWithCompletionHandler
            val request = BGAppRefreshTaskRequest(REFRESH_TASK_ID)
            // iOS picks the real time; this is only the earliest.
            request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(EARLIEST_SECONDS)
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                if (!scheduler.submitTaskRequest(request, error.ptr)) {
                    // Log only the reason (the simulator always refuses with BGTaskSchedulerErrorCodeUnavailable = 1).
                    debugLog("refresh task not submitted: ${error.value?.domain} ${error.value?.code}")
                }
            }
        }
    }

    companion object {
        const val REFRESH_TASK_ID = "com.techgv.stash.refresh"
        private const val EARLIEST_SECONDS = 60.0 * 60
    }
}
