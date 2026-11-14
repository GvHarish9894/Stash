package com.techgv.stash.di

import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.domain.ManualLink
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.network.createStashHttpClient
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.testing.FakeNotificationScheduler
import com.techgv.stash.platform.tempDirectory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.concurrent.AtomicInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The iOS `FetchMetadata` job: it fetches at once in the app process, through the real platform module and data layer. */
class InAppMetadataFetchTest {
    @Test
    fun aSavedLinkIsFetchedOnceAndQuickRepeatsDoNotFetchAgain() = runTest {
        val path = "/in-app-${Random.nextLong().toULong()}"
        val pageRequests = AtomicInt(0)
        val network = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath == path) pageRequests.incrementAndGet()
            network.await()
            respond(
                "<html><head><title>Fetched in the app</title></head><body>words</body></html>",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        val appJob = SupervisorJob()
        val app = koinApplication {
            modules(
                appModule,
                module {
                    single(APP_GROUP_DIRECTORY) { tempDirectory() }
                    single(WIDGET_RELOAD) { {} }
                    // The real scheduler needs an app bundle, which the test runner does not have.
                    single<NotificationScheduler> { FakeNotificationScheduler() }
                    single<HttpClient> { createStashHttpClient(engine) }
                    single(APPLICATION_SCOPE) { CoroutineScope(appJob + Dispatchers.Default) }
                },
            )
        }
        try {
            val koin = app.koin
            val capture = (LinkCapturePolicy.validateManual("https://example.org$path") as ManualLink.Valid).capture
            // Saving schedules FetchMetadata through the real IosBackgroundJobs.
            val saved = koin.get<CommandBus>().dispatch(Command.SaveLink(capture, null)) as DispatchResult.Done
            val id = checkNotNull(saved.itemId)
            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { while (pageRequests.value == 0) delay(10.milliseconds) }
                // While that fetch waits on the network, the same job twice more: the data layer's in-flight guard
                // answers retry-later without a second request.
                repeat(2) { koin.get<BackgroundJobs>().schedule(BackgroundJob.FetchMetadata(id)) }
                delay(300.milliseconds)
                network.complete(Unit)
                val items = koin.get<StashDatabase>().savedItemDao()
                withTimeout(10.seconds) { while (items.get(id.value)?.fetchStatus != FetchStatus.Fetched) delay(10.milliseconds) }
                assertEquals("Fetched in the app", items.get(id.value)?.title)
            }
            assertEquals(1, pageRequests.value)
        } finally {
            appJob.cancelAndJoin()
            app.koin.getOrNull<StashDatabase>()?.close()
            app.close()
        }
    }
}
