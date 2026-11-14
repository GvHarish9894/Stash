package com.techgv.stash.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSUserDomainMask
import kotlin.experimental.ExperimentalNativeApi

/** The App Group the app, the Share Extension and the widget share (entitlements of all three targets). */
const val APP_GROUP_ID = "group.com.techgv.stash"

/** The shared container's path, or null when the process has no App Group entitlement. */
fun appGroupDirectory(): String? =
    NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)?.path

/**
 * The App Group container, or, when the app is signed without the App Group (mis-provisioned), the app's own
 * Application Support directory: the app keeps working, but the extensions cannot see the inbox or the widget file.
 * Logged once per call, without any content; call it once (a Koin single).
 */
@OptIn(ExperimentalForeignApi::class)
fun sharedDirectory(): String = appGroupDirectory() ?: run {
    NSLog("Stash: no App Group container; the Share Extension and the widget cannot reach the app")
    val support = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    checkNotNull(support?.path) { "No Application Support directory" }
}

/** `NSLog` in debug binaries only. The message is the format string, `%` escaped: Kotlin values as NSLog varargs crash. */
@OptIn(ExperimentalNativeApi::class)
fun debugLog(message: String) {
    if (Platform.isDebugBinary) NSLog("Stash: " + message.replace("%", "%%"))
}
