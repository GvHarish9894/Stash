package com.techgv.stash.di

import com.techgv.stash.AppInfo
import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.data.StashSync
import com.techgv.stash.core.platform.A11yPrefs
import com.techgv.stash.core.platform.AppGroupShareInbox
import com.techgv.stash.core.platform.AppGroupWidgetPublisher
import com.techgv.stash.core.platform.AppIconSwitcher
import com.techgv.stash.core.platform.AppLocale
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.BrowserLauncher
import com.techgv.stash.core.platform.Clipboard
import com.techgv.stash.core.platform.Connectivity
import com.techgv.stash.core.platform.IosA11yPrefs
import com.techgv.stash.core.platform.IosAppIconSwitcher
import com.techgv.stash.core.platform.IosAppLocale
import com.techgv.stash.core.platform.IosBackgroundJobs
import com.techgv.stash.core.platform.IosBrowserLauncher
import com.techgv.stash.core.platform.IosClipboard
import com.techgv.stash.core.platform.IosConnectivity
import com.techgv.stash.core.platform.IosNotificationPermission
import com.techgv.stash.core.platform.IosNotificationScheduler
import com.techgv.stash.core.platform.IosShareSheet
import com.techgv.stash.core.platform.IosWidgetPinner
import com.techgv.stash.core.platform.NotificationPermission
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.platform.ShareInbox
import com.techgv.stash.core.platform.ShareSheet
import com.techgv.stash.core.platform.WidgetPinner
import com.techgv.stash.core.platform.WidgetPublisher
import com.techgv.stash.core.platform.debugLog
import com.techgv.stash.core.platform.sharedDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSBundle

/**
 * The App Group container's path, or the app's Application Support directory when the app was signed without the
 * App Group ([sharedDirectory]). The Kotlin test runner binds a temporary directory here.
 */
val APP_GROUP_DIRECTORY = named("AppGroupDirectory")

/**
 * `WidgetCenter.reloadAllTimelines`, which has no Objective-C API: the Swift app hands it to `initKoin`; tests bind
 * their own.
 */
val WIDGET_RELOAD = named("WidgetReload")

actual val platformModule: Module = module {
    single(APP_GROUP_DIRECTORY) { sharedDirectory() }
    single<ShareInbox> { AppGroupShareInbox(get<String>(APP_GROUP_DIRECTORY) + "/inbox") }
    single<WidgetPublisher> { AppGroupWidgetPublisher(get<String>(APP_GROUP_DIRECTORY) + "/widget.json", get(WIDGET_RELOAD)) }
    single<NotificationScheduler> { IosNotificationScheduler() }
    single<NotificationPermission> { IosNotificationPermission() }
    single<BackgroundJobs> {
        val koin = getKoin()
        IosBackgroundJobs { id ->
            // Resolved per call, not injected: StashSync itself needs BackgroundJobs.
            koin.get<CoroutineScope>(APPLICATION_SCOPE).launch {
                // An uncaught exception would end the process (Kotlin/Native); the catch-up retries the item.
                try {
                    koin.get<StashSync>().fetchMetadata(id)
                } catch (e: Exception) {
                    debugLog("metadata fetch failed: ${e::class.simpleName}")
                }
            }
        }
    }
    single<Connectivity> { IosConnectivity() }
    single<BrowserLauncher> { IosBrowserLauncher() }
    single<ShareSheet> { IosShareSheet() }
    single<Clipboard> { IosClipboard() }
    single<WidgetPinner> { IosWidgetPinner() }
    single<AppIconSwitcher> { IosAppIconSwitcher() }
    single<AppLocale> { IosAppLocale() }
    single<A11yPrefs> { IosA11yPrefs() }
    single { AppInfo(NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "") }
}
