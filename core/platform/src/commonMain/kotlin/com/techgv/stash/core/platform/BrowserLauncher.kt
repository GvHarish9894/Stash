package com.techgv.stash.core.platform

/**
 * Opens a page in the platform's in-app browser surface: Custom Tabs on Android (falling back to any browser),
 * `SFSafariViewController` on iOS. Used when "Open links in" is Browser (decision D9); the in-app reader is a screen.
 * Call on the main thread.
 */
interface BrowserLauncher {
    /**
     * Opens [url] with the toolbar in [toolbarColorArgb] (`0xAARRGGBB`, the theme's primary colour) and the light or
     * dark appearance from [darkTheme]. Returns false, and never throws, for a URL that is not http(s) or when nothing on
     * the device can show [url];
     * the caller then tells the user.
     */
    fun open(url: String, toolbarColorArgb: Int, darkTheme: Boolean): Boolean
}
