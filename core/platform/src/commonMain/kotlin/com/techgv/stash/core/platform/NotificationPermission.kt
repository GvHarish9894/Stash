package com.techgv.stash.core.platform

/**
 * The OS permission to show reminders.
 *
 * [request] shows the system prompt when the OS still allows one and returns whether notifications are permitted
 * afterwards; it never throws. Ask only when the user turns a reminder on, never at launch. The OS stops prompting
 * after a denial (iOS after one, Android 13+ after two), so when [request] returns false the UI offers [openSettings].
 * The suspend functions are safe to call from any thread.
 */
interface NotificationPermission {
    suspend fun isGranted(): Boolean
    suspend fun request(): Boolean

    /**
     * Opens the system notification settings for this app (Android `ACTION_APP_NOTIFICATION_SETTINGS`, iOS
     * `openNotificationSettingsURLString`). Main thread; never throws.
     */
    fun openSettings()
}
