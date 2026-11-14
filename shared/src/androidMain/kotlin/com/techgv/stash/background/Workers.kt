package com.techgv.stash.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.techgv.stash.ProcessCatchUp
import com.techgv.stash.core.data.MetadataFetchOutcome
import com.techgv.stash.core.data.StashSync
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.BackgroundJob
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** `BackgroundJob.FetchMetadata`: fetches one item, retried with backoff only while the data layer says "retry later". */
class FetchMetadataWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val sync: StashSync by inject()

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        return resultFor(sync.fetchMetadata(ItemId(id)), runAttemptCount)
    }

    companion object {
        const val KEY_ITEM_ID = "itemId"

        /**
         * Retry only on [MetadataFetchOutcome.RetryLater], and at most [BackgroundJob.FetchMetadata.MAX_ATTEMPTS] runs in
         * all ([attempt] counts from 0). An item still pending after that is fetched by the next foreground catch-up.
         */
        fun resultFor(outcome: MetadataFetchOutcome, attempt: Int): ListenableWorker.Result =
            if (outcome == MetadataFetchOutcome.RetryLater && attempt + 1 < BackgroundJob.FetchMetadata.MAX_ATTEMPTS) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.success()
            }
    }
}

/** `BackgroundJob.DailyMaintenance`: the same catch-up the app runs on every foreground. */
class DailyMaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val sync: StashSync by inject()

    override suspend fun doWork(): Result {
        // The process-start catch-up then skips: this run is that catch-up.
        ProcessCatchUp.ran = true
        sync.onForeground()
        return Result.success()
    }
}
