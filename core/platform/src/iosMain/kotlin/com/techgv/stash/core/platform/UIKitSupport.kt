package com.techgv.stash.core.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** The view controller on top of the active scene's key window, or null when the app has nothing on screen. */
internal fun topViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: return null
    val windows = scene.windows.filterIsInstance<UIWindow>()
    var top = (windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull())?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/** [argb] is `0xAARRGGBB`. */
internal fun uiColor(argb: Int): UIColor = UIColor.colorWithRed(
    red = ((argb shr 16) and 0xFF) / 255.0,
    green = ((argb shr 8) and 0xFF) / 255.0,
    blue = (argb and 0xFF) / 255.0,
    alpha = ((argb ushr 24) and 0xFF) / 255.0,
)

/** [text] as an http(s) URL with a host, or null. */
internal fun httpUrl(text: String): NSURL? =
    NSURL.URLWithString(text)?.takeIf { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrEmpty() }
