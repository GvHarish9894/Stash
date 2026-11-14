package com.techgv.stash.core.data

import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.CommandDecider
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.ReaderRepository
import com.techgv.stash.core.domain.RecentSearchRepository
import com.techgv.stash.core.domain.ReminderRepository
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.domain.UuidV7Generator
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.network.networkModule
import com.techgv.stash.core.preferences.SettingsStore
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

/** Database and DataStore need a platform path (Android Context, iOS Application Support). */
internal expect val platformDataModule: Module

/**
 * The application's lifetime `CoroutineScope`, bound by the shared module: re-plans and widget updates run in it after
 * the command that requested them has returned. Tests bind a scope they cancel and join before closing the database.
 */
val APPLICATION_SCOPE: Qualifier = named("ApplicationScope")

/**
 * The data layer. Requires from the platform and shared modules: `ShareInbox`, `NotificationScheduler`,
 * `WidgetPublisher`, `BackgroundJobs`, `Connectivity`, `ReminderTextRenderer` and the [APPLICATION_SCOPE] scope.
 */
val dataModule: Module = module {
    includes(platformDataModule, networkModule)
    // The only place core:data reads the system clock and zone; everything else gets them injected.
    single<Clock> { Clock.System }
    single<ZoneProvider> { ZoneProvider { TimeZone.currentSystemDefault() } }
    single { CommandDecider(UuidV7Generator) }
    single { SettingsStore(get()) } bind SettingsRepository::class
    single { LocalTicks(get(), get()) }
    single<ReminderRepository> { StashReminderRepository(get(), get(), get(), get(), get(), get()) }
    single { SideEffects(get(APPLICATION_SCOPE), get(), get(), get(), get(), get(), get(), get()) }
    single { StashCommandBus(get(), get(), get(), get(), get(), get()) } bind CommandBus::class
    single<ItemRepository> { StashItemRepository(get(), get(), get(), get()) }
    single<StreakRepository> { StashStreakRepository(get(), get(), get(), get()) }
    single<ReaderRepository> { StashReaderRepository(get(), get(), get()) }
    single<RecentSearchRepository> { StashRecentSearchRepository(get(), get()) }
    single { MetadataUpdater(get(), get(), get(), get(), get()) }
    single { StashSync(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
