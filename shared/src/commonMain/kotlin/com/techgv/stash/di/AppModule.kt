package com.techgv.stash.di

import com.techgv.stash.ResourceReminderTextRenderer
import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.data.dataModule
import com.techgv.stash.core.domain.ReminderTextRenderer
import com.techgv.stash.feature.archive.archiveFeatureModule
import com.techgv.stash.feature.reader.readerFeatureModule
import com.techgv.stash.feature.search.searchFeatureModule
import com.techgv.stash.feature.settings.settingsFeatureModule
import com.techgv.stash.feature.shelf.shelfFeatureModule
import com.techgv.stash.feature.streak.streakFeatureModule
import com.techgv.stash.feature.widgetpreview.widgetPreviewFeatureModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The whole app graph: the data layer, the feature ViewModels, what `shared` provides, and the platform's capabilities
 * and [com.techgv.stash.AppInfo] ([platformModule]).
 * Started once per process by `initKoin` on each platform.
 *
 * [APPLICATION_SCOPE] lives as long as the process. A test that starts this graph adds, after it, a module binding
 * `single(APPLICATION_SCOPE) { scope }` with a scope it cancels and joins before closing the database.
 */
val appModule: Module = module {
    includes(
        dataModule, platformModule, shelfFeatureModule, searchFeatureModule, archiveFeatureModule, readerFeatureModule,
        settingsFeatureModule, streakFeatureModule, widgetPreviewFeatureModule,
    )
    single(APPLICATION_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<ReminderTextRenderer> { ResourceReminderTextRenderer }
}
