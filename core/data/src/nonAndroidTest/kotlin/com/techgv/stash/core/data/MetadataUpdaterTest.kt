package com.techgv.stash.core.data

import androidx.room.Room
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.buildStashDatabase
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.network.MetadataFetcher
import com.techgv.stash.core.network.createStashHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.io.IOException
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath

class MetadataUpdaterTest {
    private val page = TestData.html(
        body = "<p>Body</p>",
        head = """<meta property="og:title" content="The real title"><meta property="og:description" content="What it is about">
            <meta property="og:image" content="https://example.com/cover.png"><link rel="icon" href="/icon.png">""",
    )
    private val id = ItemId("id-1")

    @Test
    fun successAppliesThePageAndClearsPending() = runTest {
        val data = TestData(this, network = page)
        data.save("https://example.com/post")

        assertEquals(MetadataFetchOutcome.Fetched, data.metadata.fetch(id))

        val item = data.item("id-1")!!
        assertEquals("The real title", item.title)
        assertEquals("What it is about", item.description)
        assertEquals("https://example.com/cover.png", item.imageUrl)
        assertEquals("https://example.com/icon.png", item.faviconUrl)
        assertEquals(FetchStatus.Fetched, item.fetchStatus)
        assertEquals("https://example.com/post", item.url)
        assertEquals(MetadataFetchOutcome.Skipped, data.metadata.fetch(id), "already fetched")
    }

    @Test
    fun siteFieldsComeFromTheUrlAfterRedirects() = runTest {
        val data = TestData(this)
        data.network = { request ->
            if (request.url.host == "short.example") {
                respond("", HttpStatusCode.MovedPermanently, headersOf("Location", "https://www.github.com/owner/repo"))
            } else {
                page(request)
            }
        }
        data.save("https://short.example/abc")

        data.metadata.fetch(id)

        val item = data.item("id-1")!!
        assertEquals("github.com", item.domain)
        assertEquals("github.com", item.siteName)
        assertEquals("GitHub", item.sourceKey)
        assertEquals(ContentType.Repository, item.contentType)
        assertEquals("https://short.example/abc", item.url, "the saved URL does not change (D17, D18)")
    }

    @Test
    fun aPageWithoutATitleKeepsTheProvisionalOne() = runTest {
        val data = TestData(this, network = TestData.html("<p>No title here</p>"))
        data.save("https://example.com/post", text = "What the sharer called it")

        data.metadata.fetch(id)

        assertEquals("What the sharer called it", data.item("id-1")!!.title)
        assertEquals(FetchStatus.Fetched, data.item("id-1")!!.fetchStatus)
    }

    @Test
    fun aFailureRetryingCannotFixFailsAtOnce() = runTest {
        val data = TestData(this, network = TestData.status(HttpStatusCode.NotFound))
        data.save("https://example.com/gone")

        assertEquals(MetadataFetchOutcome.Failed, data.metadata.fetch(id))
        assertEquals(FetchStatus.Failed, data.item("id-1")!!.fetchStatus)
        assertEquals("https://example.com/gone", data.item("id-1")!!.title, "the URL stays as the title")
    }

    @Test
    fun retryableFailuresStayPendingUntilTheThirdAcrossProcessDeaths() = runTest {
        val data = TestData(this, network = TestData.status(HttpStatusCode.ServiceUnavailable))
        data.save("https://example.com/flaky")
        // A fresh updater per attempt, as after process death: only the database remembers the attempts.
        fun freshUpdater() = MetadataUpdater(data.database, MetadataFetcher(createStashHttpClient(MockEngine { data.network(this, it) })), data.clock, data.effects, data.connectivity)

        assertEquals(MetadataFetchOutcome.RetryLater, freshUpdater().fetch(id))
        assertEquals(MetadataFetchOutcome.RetryLater, freshUpdater().fetch(id))
        assertEquals(FetchStatus.Pending, data.item("id-1")!!.fetchStatus)
        assertEquals(MetadataFetchOutcome.Failed, freshUpdater().fetch(id))
        assertEquals(FetchStatus.Failed, data.item("id-1")!!.fetchStatus)

        // Retry (or pull to refresh) starts a fresh set of three attempts.
        assertIs<DispatchResult.Done>(data.bus.dispatch(Command.RetryMetadata(id)))
        assertEquals(MetadataFetchOutcome.RetryLater, freshUpdater().fetch(id))
        assertEquals(1, data.database.metadataAttemptDao().failedAttempts("id-1"))
    }

    @Test
    fun aDeadDomainWhileOnlineFailsOnTheThirdAttempt() = runTest {
        val data = TestData(this) // the network throws an I/O error, reported as Offline, but the device is online
        data.save("https://no-such-host.example/post")

        assertEquals(MetadataFetchOutcome.RetryLater, data.metadata.fetch(id))
        assertEquals(MetadataFetchOutcome.RetryLater, data.metadata.fetch(id))
        assertEquals(FetchStatus.Pending, data.item("id-1")!!.fetchStatus)
        assertEquals(MetadataFetchOutcome.Failed, data.metadata.fetch(id))

        assertEquals(FetchStatus.Failed, data.item("id-1")!!.fetchStatus)
        assertEquals(emptyList(), data.strings("SELECT item_id FROM metadata_attempt"), "resolved: no attempt row left")
    }

    @Test
    fun anOfflineDeviceNeverUsesUpAttemptsAndResolvesOnceOnline() = runTest {
        val data = TestData(this)
        data.connectivity.online = false
        assertIs<DispatchResult.Done>(data.save("https://example.com/later"), "saving never waits on the network")
        assertEquals(ItemLifecycle.Unread, data.item("id-1")!!.lifecycle)

        repeat(10) { assertEquals(MetadataFetchOutcome.RetryLater, data.metadata.fetch(id)) }
        assertEquals(FetchStatus.Pending, data.item("id-1")!!.fetchStatus)
        assertEquals(0, data.database.metadataAttemptDao().failedAttempts("id-1"))

        data.connectivity.online = true
        data.network = page
        assertEquals(MetadataFetchOutcome.Fetched, data.metadata.fetch(id))
        assertEquals("The real title", data.item("id-1")!!.title)
    }

    @Test
    fun anOfflineFailureCountsOnlyWhenTheDeviceWasOnlineBeforeAndAfterTheRequest() = runTest {
        for (onlineAtRequest in listOf(true, false)) {
            val data = TestData(this)
            data.save("https://example.com/post")
            // The connection flips while the request is out.
            data.network = { data.connectivity.online = !onlineAtRequest; throw IOException("connection lost") }

            repeat(3) {
                data.connectivity.online = onlineAtRequest
                assertEquals(MetadataFetchOutcome.RetryLater, data.metadata.fetch(id), "online at request: $onlineAtRequest")
            }

            assertEquals(0, data.database.metadataAttemptDao().failedAttempts("id-1"), "online at request: $onlineAtRequest")
            data.close()
        }
    }

    @Test
    fun aServerErrorCountsEvenWhenTheDeviceSaysOffline() = runTest {
        val data = TestData(this, network = TestData.status(HttpStatusCode.ServiceUnavailable))
        data.save("https://example.com/flaky")
        data.connectivity.online = false

        val outcomes = List(3) { data.metadata.fetch(id) }

        assertEquals(listOf(MetadataFetchOutcome.RetryLater, MetadataFetchOutcome.RetryLater, MetadataFetchOutcome.Failed), outcomes)
    }

    @Test
    fun anUncountedAttemptStillMovesTheItemBehindTheOthers() = runTest {
        // Intended: an attempt made offline records when it ran, so catch-up tries the other pending items first.
        val data = TestData(this)
        data.insert(
            savedItem("older", addedAt = FIXTURE_NOW - 2.days, fetchStatus = FetchStatus.Pending),
            savedItem("newer", addedAt = FIXTURE_NOW - 1.days, fetchStatus = FetchStatus.Pending),
        )
        val queue = data.database.metadataAttemptDao()
        assertEquals(listOf("newer", "older"), queue.pendingIds(10))
        data.connectivity.online = false

        assertEquals(MetadataFetchOutcome.RetryLater, data.metadata.fetch(ItemId("newer")))

        assertEquals(0, queue.failedAttempts("newer"))
        assertEquals(listOf("older", "newer"), queue.pendingIds(10))
    }

    @Test
    fun theContentTypeAndMinutesThePageGivesAreApplied() = runTest {
        val data = TestData(
            this,
            network = TestData.html(
                "<p>Clip</p>",
                head = """<meta property="og:title" content="A clip"><meta property="og:type" content="video.other">
                    <meta property="og:video:duration" content="600">""",
            ),
        )
        data.save("https://example.com/watch")

        data.metadata.fetch(id)

        val item = data.item("id-1")!!
        assertEquals(ContentType.Video, item.contentType)
        assertEquals(10, item.readingMinutes)
    }

    @Test
    fun fetchedMetadataReachesTheWidgetAndTheReminders() = runTest {
        val data = TestData(this, network = page)
        data.save("https://example.com/post")
        data.settle()
        val plans = data.scheduler.applyCount

        data.metadata.fetch(id)
        data.settle()

        assertEquals(plans + 1, data.scheduler.applyCount)
        assertEquals("The real title", data.widget.published.last().items.single().title)
        assertEquals(true, data.scheduler.scheduled.all { "The real title" in it.body })
    }

    @Test
    fun aFailureIsWrittenOntoTheRowAsItIsWhenTheFetchEnds() = runTest {
        val data = TestData(this)
        data.save("https://example.com/gone")
        data.network = { _ ->
            data.bus.dispatch(Command.Archive(id)) // the user archives it while the page loads
            respondError(HttpStatusCode.NotFound)
        }

        assertEquals(MetadataFetchOutcome.Failed, data.metadata.fetch(id))

        val item = data.item("id-1")!!
        assertEquals(ItemLifecycle.Archived, item.lifecycle, "the archive is kept")
        assertEquals(FetchStatus.Failed, item.fetchStatus)
    }

    @Test
    fun twoFetchesOfOneItemAtOnceCountOneAttempt() = runTest {
        val data = TestData(this)
        data.save("https://example.com/flaky")
        val bothStarted = CompletableDeferred<Unit>()
        data.network = { bothStarted.await(); respondError(HttpStatusCode.ServiceUnavailable) }

        val first = async { data.metadata.fetch(id) }
        testScheduler.runCurrent()
        val second = data.metadata.fetch(id)
        bothStarted.complete(Unit)

        assertEquals(MetadataFetchOutcome.RetryLater, second, "the second caller retries later, it is not dropped")
        assertEquals(MetadataFetchOutcome.RetryLater, first.await())
        assertEquals(1, data.database.metadataAttemptDao().failedAttempts("id-1"))
    }

    @Test
    fun theAttemptCountSurvivesReopeningTheDatabaseFile() = runTest {
        val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stash-attempts-${Random.nextLong().toULong()}.db").toString()
        fun open() = Room.databaseBuilder<StashDatabase>(path).buildStashDatabase(StandardTestDispatcher(testScheduler))
        val before = TestData(this, open(), network = TestData.status(HttpStatusCode.ServiceUnavailable))
        before.save("https://example.com/flaky")
        assertEquals(MetadataFetchOutcome.RetryLater, before.metadata.fetch(id))
        before.close()

        val after = TestData(this, open(), network = TestData.status(HttpStatusCode.ServiceUnavailable))
        assertEquals(1, after.database.metadataAttemptDao().failedAttempts("id-1"))
        assertEquals(MetadataFetchOutcome.RetryLater, after.metadata.fetch(id))
        assertEquals(MetadataFetchOutcome.Failed, after.metadata.fetch(id))
        after.close()
        listOf("", "-wal", "-shm").forEach { FileSystem.SYSTEM.delete((path + it).toPath()) }
    }

    @Test
    fun metadataForAnItemDeletedMeanwhileChangesNothing() = runTest {
        val data = TestData(this)
        data.save("https://example.com/post")
        data.network = { request ->
            data.bus.dispatch(Command.Remove(id)) // the user removes it while the page loads
            page(request)
        }
        val removed = data.item("id-1")

        assertEquals(MetadataFetchOutcome.Skipped, data.metadata.fetch(id))

        assertEquals(removed!!.copy(deletedAt = data.clock.now, updatedAt = data.clock.now), data.item("id-1"))
    }

    @Test
    fun metadataForAnItemArchivedMeanwhileKeepsItArchived() = runTest {
        val data = TestData(this)
        data.save("https://example.com/post")
        data.network = { request ->
            data.bus.dispatch(Command.Archive(id))
            page(request)
        }

        assertEquals(MetadataFetchOutcome.Fetched, data.metadata.fetch(id))

        val item = data.item("id-1")!!
        assertEquals(ItemLifecycle.Archived, item.lifecycle)
        assertEquals("The real title", item.title)
    }
}
