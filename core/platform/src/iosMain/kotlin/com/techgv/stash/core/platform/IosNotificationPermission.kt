package com.techgv.stash.core.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * `UNUserNotificationCenter` authorization; iOS shows its prompt only while the status is still undetermined.
 * [openSettings] opens the app's notification settings page.
 */
class IosNotificationPermission : NotificationPermission {
    // Lazy: the center needs an app bundle, which a test runner does not have.
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }

    override suspend fun isGranted(): Boolean = suspendCancellableCoroutine { done ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            done.resume(
                settings?.authorizationStatus in
                    setOf(UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral),
            )
        }
    }

    override suspend fun request(): Boolean = suspendCancellableCoroutine { done ->
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            done.resume(granted)
        }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString)
            ?: NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
