package com.techgv.stash.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/**
 * A Custom Tab in the theme's colours. Its intent is a plain `ACTION_VIEW`, so a browser without Custom Tabs support
 * opens the page as a normal browser view. [open] returns false for a URL that is not http(s) or that nothing can view.
 */
class CustomTabsBrowserLauncher(private val context: Context) : BrowserLauncher {
    override fun open(url: String, toolbarColorArgb: Int, darkTheme: Boolean): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val tab = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColorArgb).build())
            .setColorScheme(if (darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .build()
        val activity = ForegroundActivity.current
        if (activity == null) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            tab.launchUrl(activity ?: context, uri)
            ForegroundActivity.startedOtherApp()
            true
        } catch (e: RuntimeException) {
            // Nothing can view it (ActivityNotFoundException) or the OS refused the start (SecurityException, ...).
            false
        }
    }
}
