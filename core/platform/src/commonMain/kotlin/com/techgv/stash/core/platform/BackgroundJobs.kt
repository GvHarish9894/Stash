package com.techgv.stash.core.platform

import com.techgv.stash.core.model.ItemId

sealed interface BackgroundJob {
    /**
     * Fetches metadata for one item (Android: one WorkManager job per item, network only, retried with backoff while
     * the data layer answers "retry later"). The data layer counts failed attempts in the database and fails the item
     * after [MAX_ATTEMPTS], whichever platform ran them.
     */
    data class FetchMetadata(val itemId: ItemId) : BackgroundJob {
        companion object {
            const val MAX_ATTEMPTS = 3
        }
    }

    /** Daily maintenance: auto-archive and other housekeeping. */
    data object DailyMaintenance : BackgroundJob
}

/**
 * Asks the OS to run work later (WorkManager on Android, BGTaskScheduler on iOS).
 *
 * Scheduling a job that is already scheduled KEEPS the existing one (WorkManager `ExistingWorkPolicy.KEEP`): a running
 * fetch is not cancelled and its backoff is not reset. [schedule] never throws.
 *
 * iOS has no per-item background jobs: it runs [BackgroundJob.FetchMetadata] at once in the app process, without retry;
 * later attempts come from `StashSync.onForeground()` and the app refresh task, and the data layer enforces the attempt
 * limit.
 *
 * Correctness never depends on a job running: the app also does each job's work on foreground, and every job is
 * idempotent.
 */
interface BackgroundJobs {
    fun schedule(job: BackgroundJob)
}
