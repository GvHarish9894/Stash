package com.techgv.stash.core.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * `POST_NOTIFICATIONS` on Android 13+; below that there is no runtime permission, so notifications are permitted while
 * the user has not turned them off for the app (which only system Settings can undo, so [request] cannot prompt there).
 * The prompt runs on [ForegroundActivity]; without one [request] returns false.
 */
class AndroidNotificationPermission(private val context: Context) : NotificationPermission {
    override suspend fun isGranted(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override suspend fun request(): Boolean {
        if (isGranted()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            withContext(Dispatchers.Main) { prompt() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** The app's notification settings (API 26+), or its app-info page below that. */
    override fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        val activity = ForegroundActivity.current
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            (activity ?: context).startActivity(intent)
            ForegroundActivity.startedOtherApp()
        } catch (e: RuntimeException) {
            // Never throws; a device without the settings screen leaves nothing to do.
        }
    }

    private suspend fun prompt(): Boolean {
        val activity = ForegroundActivity.current ?: return false
        return suspendCancellableCoroutine { continuation ->
            lateinit var launcher: ActivityResultLauncher<String>
            // The activity can be destroyed while the prompt is up (rotation, back); answer with the state then.
            val onDestroy = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    launcher.unregister()
                    if (continuation.isActive) continuation.resume(NotificationManagerCompat.from(context).areNotificationsEnabled())
                }
            }
            launcher = activity.activityResultRegistry.register(KEY, ActivityResultContracts.RequestPermission()) { granted ->
                activity.lifecycle.removeObserver(onDestroy)
                launcher.unregister()
                if (continuation.isActive) continuation.resume(granted)
            }
            activity.lifecycle.addObserver(onDestroy)
            continuation.invokeOnCancellation {
                activity.runOnUiThread {
                    activity.lifecycle.removeObserver(onDestroy)
                    launcher.unregister()
                }
            }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            ForegroundActivity.startedOtherApp()
        }
    }

    private companion object {
        const val KEY = "stash.notificationPermission"
    }
}
