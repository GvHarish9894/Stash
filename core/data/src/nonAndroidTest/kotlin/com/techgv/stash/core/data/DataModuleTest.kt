package com.techgv.stash.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.domain.ManualLink
import com.techgv.stash.core.domain.ReaderRepository
import com.techgv.stash.core.domain.RecentSearchRepository
import com.techgv.stash.core.domain.ReminderRepository
import com.techgv.stash.core.domain.ReminderTextRenderer
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.network.createStashHttpClient
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.Connectivity
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.platform.ShareInbox
import com.techgv.stash.core.platform.WidgetPublisher
import com.techgv.stash.core.testing.FakeBackgroundJobs
import com.techgv.stash.core.testing.FakeConnectivity
import com.techgv.stash.core.testing.FakeNotificationScheduler
import com.techgv.stash.core.testing.FakeShareInbox
import com.techgv.stash.core.testing.FakeWidgetPublisher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock

class DataModuleTest {
    @Test
    fun everyBindingResolvesAndTheGraphSavesALink() = runTest {
        val appScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val app = koinApplication {
            modules(
                dataModule,
                // What the platform modules and the shared module provide in the app.
                module {
                    single<StashDatabase> { TestData.inMemoryDatabase(this@runTest) }
                    single<DataStore<Preferences>> {
                        PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
                            (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stash-koin-${Random.nextLong().toULong()}.preferences_pb").toString().toPath()
                        }
                    }
                    single<HttpClient> { createStashHttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }) }
                    single<ShareInbox> { FakeShareInbox() }
                    single<NotificationScheduler> { FakeNotificationScheduler() }
                    single<WidgetPublisher> { FakeWidgetPublisher() }
                    single<BackgroundJobs> { FakeBackgroundJobs() }
                    single<Connectivity> { FakeConnectivity() }
                    single<ReminderTextRenderer> { testRenderer }
                    single(APPLICATION_SCOPE) { appScope }
                },
            )
        }
        val koin = app.koin

        listOf(
            CommandBus::class, ItemRepository::class, SettingsRepository::class, StreakRepository::class,
            ReminderRepository::class, ReaderRepository::class, RecentSearchRepository::class, StashSync::class,
            ZoneProvider::class,
        ).forEach { koin.get<Any>(it) }
        assertEquals(Clock.System, koin.get<Clock>())

        val captured = LinkCapturePolicy.validateManual("example.com/koin")
        val save = koin.get<CommandBus>().dispatch(
            Command.SaveLink((captured as ManualLink.Valid).capture, null),
        )
        assertIs<DispatchResult.Done>(save)
        koin.get<StashSync>().onForeground()

        // The owner of the application scope stops it before the database closes.
        appScope.coroutineContext.job.cancelAndJoin()
        koin.get<StashDatabase>().close()
        app.close()
    }
}
