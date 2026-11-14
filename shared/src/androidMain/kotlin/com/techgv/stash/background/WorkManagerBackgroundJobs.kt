package com.techgv.stash.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.BackgroundJobs
import java.util.concurrent.TimeUnit

/** WorkManager: one unique job per item for metadata, one unique daily job for maintenance; both KEEP a pending one. */
class WorkManagerBackgroundJobs(private val context: Context) : BackgroundJobs {
    override fun schedule(job: BackgroundJob) {
        try {
            val work = WorkManager.getInstance(context)
            when (job) {
                is BackgroundJob.FetchMetadata -> work.enqueueUniqueWork(
                    fetchWorkName(job.itemId.value),
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<FetchMetadataWorker>()
                        .setInputData(workDataOf(FetchMetadataWorker.KEY_ITEM_ID to job.itemId.value))
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                        .build(),
                )
                BackgroundJob.DailyMaintenance -> work.enqueueUniquePeriodicWork(
                    DAILY_MAINTENANCE,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(1, TimeUnit.DAYS).build(),
                )
            }
        } catch (e: RuntimeException) {
            // Never throws: catch-up on the next foreground does the job's work anyway.
        }
    }

    companion object {
        const val DAILY_MAINTENANCE = "daily-maintenance"
        const val BACKOFF_SECONDS = 30L

        fun fetchWorkName(itemId: String) = "fetch-metadata-$itemId"
    }
}
