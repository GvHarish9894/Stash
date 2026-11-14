package com.techgv.stash.core.platform

import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIUserInterfaceStyle

/** `SFSafariViewController` presented over whatever is on top; bar in the theme's primary, controls in on-primary. */
class IosBrowserLauncher : BrowserLauncher {
    override fun open(url: String, toolbarColorArgb: Int, darkTheme: Boolean): Boolean {
        // SFSafariViewController raises on anything but http(s).
        val page = httpUrl(url) ?: return false
        val presenter = topViewController() ?: return false
        val safari = SFSafariViewController(page)
        safari.preferredBarTintColor = uiColor(toolbarColorArgb)
        safari.preferredControlTintColor = uiColor(if (darkTheme) ON_PRIMARY_DARK else ON_PRIMARY_LIGHT)
        safari.overrideUserInterfaceStyle =
            if (darkTheme) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
        presenter.presentViewController(safari, animated = true, completion = null)
        return true
    }

    private companion object {
        // Handoff token color-on-primary.
        const val ON_PRIMARY_LIGHT = 0xFFFBF6EA.toInt()
        const val ON_PRIMARY_DARK = 0xFF1E1812.toInt()
    }
}
