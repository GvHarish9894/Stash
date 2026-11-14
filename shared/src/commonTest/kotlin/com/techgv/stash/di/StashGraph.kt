package com.techgv.stash.di

import com.techgv.stash.AppInfo
import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.data.StashSync
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.ReaderRepository
import com.techgv.stash.core.domain.RecentSearchRepository
import com.techgv.stash.core.domain.ReminderRepository
import com.techgv.stash.core.domain.ReminderTextRenderer
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.platform.A11yPrefs
import com.techgv.stash.core.platform.NotificationPermission
import com.techgv.stash.core.platform.AppIconSwitcher
import com.techgv.stash.core.platform.AppLocale
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.BrowserLauncher
import com.techgv.stash.core.platform.Clipboard
import com.techgv.stash.core.platform.Connectivity
import com.techgv.stash.core.platform.WidgetPinner
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.platform.ShareInbox
import com.techgv.stash.core.platform.ShareSheet
import com.techgv.stash.core.platform.WidgetPublisher
import com.techgv.stash.core.testing.FakeA11yPrefs
import com.techgv.stash.core.testing.FakeNotificationPermission
import com.techgv.stash.core.testing.FakeAppIconSwitcher
import com.techgv.stash.core.testing.FakeAppLocale
import com.techgv.stash.core.testing.FakeBackgroundJobs
import com.techgv.stash.core.testing.FakeBrowserLauncher
import com.techgv.stash.core.testing.FakeClipboard
import com.techgv.stash.core.testing.FakeConnectivity
import com.techgv.stash.core.testing.FakeWidgetPinner
import com.techgv.stash.core.testing.FakeNotificationScheduler
import com.techgv.stash.core.testing.FakeShareInbox
import com.techgv.stash.core.testing.FakeShareSheet
import com.techgv.stash.core.testing.FakeWidgetPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Clock

/** The thirteen capabilities, as `core:testing` fakes. */
val fakeCapabilitiesModule = module {
    single<ShareInbox> { FakeShareInbox() }
    single<NotificationScheduler> { FakeNotificationScheduler() }
    single<WidgetPublisher> { FakeWidgetPublisher() }
    single<BackgroundJobs> { FakeBackgroundJobs() }
    single<Connectivity> { FakeConnectivity() }
    single<BrowserLauncher> { FakeBrowserLauncher() }
    single<ShareSheet> { FakeShareSheet() }
    single<Clipboard> { FakeClipboard() }
    single<WidgetPinner> { FakeWidgetPinner() }
    single<AppIconSwitcher> { FakeAppIconSwitcher() }
    single<AppLocale> { FakeAppLocale() }
    single<A11yPrefs> { FakeA11yPrefs() }
    single<NotificationPermission> { FakeNotificationPermission() }
    single { AppInfo("test") }
}

/**
 * Starts [appModule] (with the real storage of the platform it runs on) plus [extra] modules, resolves every binding
 * the data layer, the renderer and the platform module expose, then shuts down in order: app scope, database, Koin.
 * Throws on the first binding that is missing.
 */
suspend fun assertStashGraphResolves(setUp: KoinApplication.() -> Unit = {}, vararg extra: org.koin.core.module.Module) {
    val appJob = SupervisorJob()
    val app = koinApplication {
        setUp()
        modules(appModule, *extra, module { single(APPLICATION_SCOPE) { CoroutineScope(appJob + Dispatchers.Default) } })
    }
    try {
        val koin = app.koin
        listOf(
            CommandBus::class, ItemRepository::class, SettingsRepository::class, StreakRepository::class,
            ReminderRepository::class, ReaderRepository::class, RecentSearchRepository::class, StashSync::class,
            ZoneProvider::class, Clock::class, ReminderTextRenderer::class,
            ShareInbox::class, NotificationScheduler::class, WidgetPublisher::class, BackgroundJobs::class,
            Connectivity::class, BrowserLauncher::class, ShareSheet::class, Clipboard::class, WidgetPinner::class,
            AppIconSwitcher::class, AppLocale::class, A11yPrefs::class, NotificationPermission::class, AppInfo::class,
        ).forEach { koin.get<Any>(it) }
        koin.get<CoroutineScope>(APPLICATION_SCOPE)
    } finally {
        appJob.cancelAndJoin()
        app.koin.getOrNull<StashDatabase>()?.close()
        app.close()
    }
}
