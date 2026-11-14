package com.techgv.stash.core.data

import com.techgv.stash.core.database.ArticleContentEntity
import com.techgv.stash.core.database.toEntity
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.OperationPayload
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SharedPayload
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException

class StashSyncTest {
    private val page = TestData.html("<p>Body</p>", head = "<title>Fetched title</title>")

    private fun shared(text: String?, subject: String? = null) = SharedPayload(text, subject, sourceApp = "com.example.app", at = FIXTURE_NOW)

    @Test
    fun theInboxBecomesOneItemPerLinkAndASecondCallChangesNothing() = runTest {
        val data = TestData(this, network = page)
        data.inbox.share(
            shared("Nothing to save here"),
            shared("Read this https://example.com/post?utm_source=share"),
            shared("https://example.com/post", subject = "Same link again"),
        )

        data.sync.onForeground()
        data.settle()

        assertEquals(listOf("id-1"), data.itemIds())
        val item = data.item("id-1")!!
        assertEquals("com.example.app", item.sourceApp)
        assertEquals(FetchStatus.Fetched, item.fetchStatus, "pending metadata is fetched during catch-up")
        assertEquals("Fetched title", item.title)
        val operations = data.operations()

        data.sync.onForeground()
        data.settle()

        assertEquals(listOf("id-1"), data.itemIds())
        assertEquals(item, data.item("id-1"))
        assertEquals(operations, data.operations())
    }

    @Test
    fun aRepeatedDeliveryAfterACrashIsStillOneItem() = runTest {
        val data = TestData(this)
        val payload = shared("https://example.com/post")
        data.inbox.share(payload)
        data.sync.onForeground()

        data.inbox.share(payload) // the inbox handed it over, then the app died before it was removed
        data.sync.onForeground()

        assertEquals(listOf("id-1"), data.itemIds())
        assertEquals(listOf(OperationType.Save, OperationType.Reshare), data.operations().map { it.type })
    }

    @Test
    fun anEntryWhoseSaveWasNotRemovedIsRedeliveredAsOneItem() = runTest {
        val data = TestData(this)
        data.inbox.share(shared("https://example.com/post"))
        data.inbox.dieAfterNextSave() // saved, then killed before the inbox removed the entry

        data.sync.onForeground()
        assertEquals(1, data.inbox.waiting.size)
        data.sync.onForeground()

        assertEquals(listOf("id-1"), data.itemIds())
        assertEquals(emptyList(), data.inbox.waiting)
    }

    @Test
    fun aSaveTheDatabaseRejectedStaysInTheInboxUntilItIsStored() = runTest {
        val data = TestData(this)
        // The first save would take id-1 and id-2; an operation id-2 already exists, so it fails with StorageFailure.
        data.database.operationDao().insert(listOf(Operation("id-2", ItemId("x"), OperationType.MarkRead, OperationPayload.Metadata(FetchStatus.Failed, FetchStatus.Pending), FIXTURE_NOW).toEntity()))
        val payload = shared("https://example.com/post")
        data.inbox.share(payload, shared("https://example.com/second"))

        data.sync.onForeground()
        assertEquals(emptyList(), data.itemIds())
        assertEquals(2, data.inbox.waiting.size, "the failed entry and the ones after it are kept")

        data.sync.onForeground()
        assertEquals(2, data.itemIds().size)
        assertEquals(emptyList(), data.inbox.waiting)
    }

    @Test
    fun anEntryAppendedDuringADrainIsDelivered() = runTest {
        val data = TestData(this)
        data.inbox.share(shared("https://example.com/first"))
        data.inbox.shareDuringNextSave(shared("https://example.com/appended"))

        data.sync.onForeground()

        assertEquals(2, data.itemIds().size)
        assertEquals(emptyList(), data.inbox.waiting)
    }

    @Test
    fun aPayloadWithoutALinkIsConsumedNotRetried() = runTest {
        val data = TestData(this)
        data.inbox.share(shared("Nothing to save here"))

        data.sync.onForeground()

        assertEquals(emptyList(), data.inbox.waiting)
        assertEquals(emptyList(), data.itemIds())
    }

    @Test
    fun catchUpAppliesAutoArchivePrunesTheLogAndRefreshesEverything() = runTest {
        val data = TestData(this)
        data.insert(savedItem("old", addedAt = FIXTURE_NOW - 60.days, readAt = FIXTURE_NOW - 31.days))
        val stale = Operation("stale", ItemId("old"), OperationType.MarkRead, OperationPayload.Metadata(FetchStatus.Failed, FetchStatus.Pending), FIXTURE_NOW - 30.days - 1.milliseconds)
        data.database.operationDao().insert(listOf(stale.toEntity()))

        data.sync.onForeground()

        assertEquals(ItemLifecycle.Archived, data.item("old")!!.lifecycle, "default auto-archive: 30 days")
        assertEquals(listOf(OperationType.AutoArchive), data.operations().map { it.type }, "the 30-day-old row is pruned")
        assertEquals(1, data.scheduler.applyCount)
        assertEquals(1, data.widget.published.size)
        assertEquals(listOf<BackgroundJob>(BackgroundJob.DailyMaintenance), data.jobs.scheduled)
    }

    @Test
    fun autoArchiveFollowsTheSetting() = runTest {
        val data = TestData(this)
        data.insert(savedItem("old", addedAt = FIXTURE_NOW - 60.days, readAt = FIXTURE_NOW - 31.days))
        data.bus.dispatch(Command.ChangeSetting { it.copy(autoArchive = AutoArchivePeriod.Days90) })

        data.sync.onForeground()

        assertEquals(ItemLifecycle.Read, data.item("old")!!.lifecycle)
    }

    @Test
    fun eachStepsFailureLeavesTheOthersRunning() = runTest {
        for (broken in listOf("inbox", "scheduler", "widget", "jobs")) {
            val data = TestData(this)
            data.failing += broken
            data.inbox.share(shared("https://example.com/new"))
            data.insert(savedItem("old", addedAt = FIXTURE_NOW - 60.days, readAt = FIXTURE_NOW - 31.days))

            data.sync.onForeground()

            assertEquals(ItemLifecycle.Archived, data.item("old")!!.lifecycle, broken)
            if (broken != "inbox") assertTrue("id-1" in data.itemIds(), broken)
            if (broken != "scheduler") assertTrue(data.scheduler.applyCount >= 1, broken)
            if (broken != "widget") assertTrue(data.widget.published.isNotEmpty(), broken)
            if (broken != "jobs") assertTrue(BackgroundJob.DailyMaintenance in data.jobs.scheduled, broken)
            data.close()
        }
    }

    @Test
    fun pullToRefreshFetchesFailedMetadataAgain() = runTest {
        val data = TestData(this, network = page)
        data.insert(savedItem("failed", addedAt = FIXTURE_NOW - 1.days, fetchStatus = FetchStatus.Failed, title = "https://example.com/failed"))

        data.sync.refetchFailedMetadata()

        assertEquals(FetchStatus.Fetched, data.item("failed")!!.fetchStatus)
        assertEquals("Fetched title", data.item("failed")!!.title)
        assertEquals(emptyList(), data.jobs.scheduled, "fetched inline, not also by a job")
    }

    @Test
    fun linksFromTheInboxAreFetchedInlineNotAlsoByAJob() = runTest {
        val data = TestData(this, network = page)
        data.inbox.share(shared("https://example.com/post"))

        data.sync.onForeground()

        assertEquals(FetchStatus.Fetched, data.item("id-1")!!.fetchStatus)
        assertEquals(listOf<BackgroundJob>(BackgroundJob.DailyMaintenance), data.jobs.scheduled)
    }

    @Test
    fun theLocalStepsFinishFirstAndASlowNetworkDoesNotHoldTheNextCall() = runTest {
        val data = TestData(this)
        val networkReleased = CompletableDeferred<Unit>()
        data.network = { networkReleased.await(); page(it) }
        data.insert(savedItem("old", addedAt = FIXTURE_NOW - 60.days, readAt = FIXTURE_NOW - 31.days))
        data.inbox.share(shared("https://example.com/post"))

        val first = launch { data.sync.onForeground() }
        runCurrent()

        // The fetch of the new link is still waiting on the network; everything local is done.
        assertEquals(ItemLifecycle.Archived, data.item("old")!!.lifecycle)
        assertEquals(1, data.scheduler.applyCount)
        assertEquals(1, data.widget.published.size)
        assertEquals(listOf<BackgroundJob>(BackgroundJob.DailyMaintenance), data.jobs.scheduled)
        assertEquals(FetchStatus.Pending, data.item("id-1")!!.fetchStatus)

        val second = launch { data.sync.onForeground() }
        runCurrent()
        assertEquals(true, second.isCompleted, "the second call's local steps did not wait for the first call's fetch")

        networkReleased.complete(Unit)
        first.join()
        assertEquals(FetchStatus.Fetched, data.item("id-1")!!.fetchStatus)
    }

    @Test
    fun catchUpTrimsTheArticleCache() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a"), savedItem("b"))
        listOf("a" to FIXTURE_NOW - 1.days, "b" to FIXTURE_NOW).forEach { (id, opened) ->
            data.database.articleContentDao().upsert(ArticleContentEntity(id, "x".repeat(6_000), null, 6_000, opened, 1, 6_000, opened))
        }

        data.sync.onForeground()

        assertEquals(listOf("b"), data.strings("SELECT item_id FROM article_content"), "over the 10,000-byte budget: the least recently opened goes")
    }

    @Test
    fun twoCatchUpsAtOnceDoNotDeliverOneSharedLinkTwice() = runTest {
        val data = TestData(this)
        data.inbox.share(shared("https://example.com/post"))

        listOf(launch { data.sync.onForeground() }, launch { data.sync.onForeground() }).forEach { it.join() }

        assertEquals(listOf(OperationType.Save), data.operations().map { it.type })
    }

    @Test
    fun deadLinksDoNotStarveOlderPendingItems() = runTest {
        val data = TestData(this)
        data.network = { request -> if (request.url.host == "dead.example") throw IOException("no such host") else page(request) }
        val older = List(10) { savedItem("old$it", addedAt = FIXTURE_NOW - 10.days, fetchStatus = FetchStatus.Pending) }
        val dead = List(50) { savedItem("dead" + "$it".padStart(2, '0'), addedAt = FIXTURE_NOW - 1.days, siteName = "dead.example", fetchStatus = FetchStatus.Pending) }
        data.insert(*(older + dead).toTypedArray())

        data.sync.onForeground() // the 50 newest: every dead link, one attempt each
        assertEquals(FetchStatus.Pending, data.item("old0")!!.fetchStatus)
        data.sync.onForeground() // never-attempted items go first

        older.forEach { assertEquals(FetchStatus.Fetched, data.item(it.id.value)!!.fetchStatus, it.id.value) }
        repeat(2) { data.sync.onForeground() }
        assertEquals(FetchStatus.Failed, data.item("dead00")!!.fetchStatus, "dead links fail on their third attempt")
    }

    @Test
    fun twoOverlappingCatchUpsAttemptEachPendingItemOnce() = runTest {
        for (heldOpen in listOf(false, true)) {
            val data = TestData(this)
            val pending = List(8) { savedItem("p$it", addedAt = FIXTURE_NOW - it.days, fetchStatus = FetchStatus.Pending) }
            data.insert(*pending.toTypedArray())
            val released = CompletableDeferred<Unit>()
            data.network = { if (heldOpen) released.await(); respondError(HttpStatusCode.ServiceUnavailable) }

            // App start and the first foreground, as the platforms call it.
            val calls = listOf(launch { data.sync.onForeground() }, launch { data.sync.onForeground() })
            runCurrent()
            released.complete(Unit)
            calls.forEach { it.join() }

            val attempts = pending.map { data.database.metadataAttemptDao().failedAttempts(it.id.value) }
            assertEquals(List(8) { 1 }, attempts, "held open: $heldOpen")
            data.close()
        }
    }

    @Test
    fun aLinkSavedByACatchUpWhileAnotherIsFetchingIsFetchedWithoutAnotherCall() = runTest {
        val data = TestData(this)
        val released = CompletableDeferred<Unit>()
        data.network = { released.await(); page(it) }
        data.insert(savedItem("p", addedAt = FIXTURE_NOW - 1.days, fetchStatus = FetchStatus.Pending))
        val first = launch { data.sync.onForeground() }
        runCurrent() // fetching "p", held by the network

        data.inbox.share(shared("https://example.com/new")) // e.g. the iOS Share Extension's notification
        val second = launch { data.sync.onForeground() }
        runCurrent()
        assertEquals(true, second.isCompleted, "the second call's local steps did not wait for the first call's fetch")
        assertEquals(FetchStatus.Pending, data.item("id-1")!!.fetchStatus)

        released.complete(Unit)
        first.join()

        assertEquals(FetchStatus.Fetched, data.item("id-1")!!.fetchStatus, "the running catch-up fetched it in another pass")
        assertEquals(FetchStatus.Fetched, data.item("p")!!.fetchStatus)
        assertEquals(2, data.requests.size)
        assertEquals(listOf<BackgroundJob>(BackgroundJob.DailyMaintenance, BackgroundJob.DailyMaintenance), data.jobs.scheduled)
    }

    @Test
    fun aJobThatFindsACatchUpFetchingItsItemRetriesLater() = runTest {
        val data = TestData(this)
        data.insert(savedItem("p", addedAt = FIXTURE_NOW - 1.days, fetchStatus = FetchStatus.Pending))
        val released = CompletableDeferred<Unit>()
        data.network = { released.await(); respondError(HttpStatusCode.ServiceUnavailable) }
        val catchUp = launch { data.sync.onForeground() }
        runCurrent()

        assertEquals(MetadataFetchOutcome.RetryLater, data.sync.fetchMetadata(ItemId("p")), "kept, not dropped")
        released.complete(Unit)
        catchUp.join()
        assertEquals(1, data.database.metadataAttemptDao().failedAttempts("p"))

        // The platform runs the job again after its backoff: the item's next attempt.
        assertEquals(MetadataFetchOutcome.RetryLater, data.sync.fetchMetadata(ItemId("p")))
        assertEquals(2, data.database.metadataAttemptDao().failedAttempts("p"))
    }

    @Test
    fun anErrorFromAPlatformStepInTheRefreshDoesNotStopTheCatchUp() = runTest {
        val data = TestData(this, network = page)
        data.insert(savedItem("p", addedAt = FIXTURE_NOW - 1.days, fetchStatus = FetchStatus.Pending))
        data.beforeApply = { throw NotImplementedError("a platform bug") }

        data.sync.onForeground()

        assertEquals(listOf<BackgroundJob>(BackgroundJob.DailyMaintenance), data.jobs.scheduled)
        assertEquals(FetchStatus.Fetched, data.item("p")!!.fetchStatus)
    }
}
