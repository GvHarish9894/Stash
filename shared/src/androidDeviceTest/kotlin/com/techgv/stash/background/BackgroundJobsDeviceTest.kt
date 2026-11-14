package com.techgv.stash.background

import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.data.MetadataFetchOutcome
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.withStashKoin
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BackgroundJobsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val jobs = WorkManagerBackgroundJobs(context)

    @BeforeTest
    fun useTestWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    private fun unique(name: String): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    @Test
    fun fetchMetadataIsOneKeptJobPerItemThatWaitsForTheNetwork() {
        jobs.schedule(BackgroundJob.FetchMetadata(ItemId("a")))
        val first = unique(WorkManagerBackgroundJobs.fetchWorkName("a")).single()
        jobs.schedule(BackgroundJob.FetchMetadata(ItemId("a")))
        val again = unique(WorkManagerBackgroundJobs.fetchWorkName("a")).single()
        jobs.schedule(BackgroundJob.FetchMetadata(ItemId("b")))
        val other = unique(WorkManagerBackgroundJobs.fetchWorkName("b")).single()

        assertEquals(first.id, again.id, "scheduling again keeps the pending job")
        assertNotEquals(first.id, other.id)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        // The test driver never reports the constraint met, so the job is still waiting.
        assertEquals(WorkInfo.State.ENQUEUED, again.state)
    }

    @Test
    fun retriesOnlyOnRetryLaterAndStopsAfterTheAttemptLimit() {
        val retry = ListenableWorker.Result.retry()
        val done = ListenableWorker.Result.success()
        assertEquals(3, BackgroundJob.FetchMetadata.MAX_ATTEMPTS)
        assertEquals(listOf(retry, retry, done), (0..2).map { FetchMetadataWorker.resultFor(MetadataFetchOutcome.RetryLater, it) })
        listOf(MetadataFetchOutcome.Fetched, MetadataFetchOutcome.Failed, MetadataFetchOutcome.Skipped).forEach { outcome ->
            assertEquals(done, FetchMetadataWorker.resultFor(outcome, 0), outcome.name)
        }
    }

    @Test
    fun theWorkerFetchesThroughTheAppGraph() = runTest {
        withStashKoin {
            val missingItem = TestListenableWorkerBuilder<FetchMetadataWorker>(context)
                .setInputData(workDataOf(FetchMetadataWorker.KEY_ITEM_ID to "no-such-item"))
                .build()
            assertEquals(ListenableWorker.Result.success(), missingItem.doWork(), "skipped: nothing to fetch")
            assertEquals(ListenableWorker.Result.failure(), TestListenableWorkerBuilder<FetchMetadataWorker>(context).build().doWork())
        }
    }

    @Test
    fun dailyMaintenanceIsOneUniqueDailyJob() {
        jobs.schedule(BackgroundJob.DailyMaintenance)
        val first = unique(WorkManagerBackgroundJobs.DAILY_MAINTENANCE).single()
        jobs.schedule(BackgroundJob.DailyMaintenance)
        val again = unique(WorkManagerBackgroundJobs.DAILY_MAINTENANCE).single()

        assertEquals(first.id, again.id)
        assertEquals(TimeUnit.DAYS.toMillis(1), again.periodicityInfo?.repeatIntervalMillis)
    }
}
