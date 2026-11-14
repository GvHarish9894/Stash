package com.techgv.stash.di

import com.techgv.stash.AppInfo
import com.techgv.stash.background.WorkManagerBackgroundJobs
import com.techgv.stash.core.platform.A11yPrefs
import com.techgv.stash.core.platform.AlarmNotificationScheduler
import com.techgv.stash.core.platform.AliasAppIconSwitcher
import com.techgv.stash.core.platform.AndroidA11yPrefs
import com.techgv.stash.core.platform.AndroidAppLocale
import com.techgv.stash.core.platform.AndroidClipboard
import com.techgv.stash.core.platform.AndroidConnectivity
import com.techgv.stash.core.platform.AndroidNotificationPermission
import com.techgv.stash.core.platform.AndroidShareInbox
import com.techgv.stash.core.platform.AndroidShareSheet
import com.techgv.stash.core.platform.AppIconSwitcher
import com.techgv.stash.core.platform.AppLocale
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.BrowserLauncher
import com.techgv.stash.core.platform.Clipboard
import com.techgv.stash.core.platform.Connectivity
import com.techgv.stash.core.platform.CustomTabsBrowserLauncher
import com.techgv.stash.core.platform.NotificationPermission
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.platform.ShareInbox
import com.techgv.stash.core.platform.ShareSheet
import com.techgv.stash.core.platform.WidgetPinner
import com.techgv.stash.core.platform.WidgetPublisher
import com.techgv.stash.widget.GlanceWidgetPinner
import com.techgv.stash.widget.GlanceWidgetPublisher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<ShareInbox> { AndroidShareInbox }
    // Also bound as itself: the boot receiver re-arms the applied plan.
    single { AlarmNotificationScheduler(androidContext()) } bind NotificationScheduler::class
    single<WidgetPublisher> { GlanceWidgetPublisher(androidContext()) }
    single<BackgroundJobs> { WorkManagerBackgroundJobs(androidContext()) }
    single<Connectivity> { AndroidConnectivity(androidContext()) }
    single<BrowserLauncher> { CustomTabsBrowserLauncher(androidContext()) }
    single<ShareSheet> { AndroidShareSheet(androidContext()) }
    single<Clipboard> { AndroidClipboard(androidContext()) }
    single<WidgetPinner> { GlanceWidgetPinner(androidContext()) }
    // Also bound as itself: startup applies a pending icon change when the app goes to the background.
    single { AliasAppIconSwitcher(androidContext()) } bind AppIconSwitcher::class
    single<AppLocale> { AndroidAppLocale(androidContext()) }
    single<A11yPrefs> { AndroidA11yPrefs(androidContext()) }
    single<NotificationPermission> { AndroidNotificationPermission(androidContext()) }
    single { AppInfo(androidContext().packageManager.getPackageInfo(androidContext().packageName, 0).versionName.orEmpty()) }
}
