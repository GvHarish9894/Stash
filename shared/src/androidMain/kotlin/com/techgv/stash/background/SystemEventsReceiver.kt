package com.techgv.stash.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.data.StashSync
import com.techgv.stash.core.platform.AlarmNotificationScheduler
import com.techgv.stash.widget.WidgetSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Reboot and app update drop every alarm: re-arm the applied reminder plan straight away, then let the data layer
 * re-plan. A clock or time-zone change moves the local times reminders are planned for: re-plan. A language change
 * (the system's, or the app's own on Android 13+, which the OS also announces to the app) redraws the widget's text.
 *
 * Not direct-boot aware (no `LOCKED_BOOT_COMPLETED`): the plan, the database and the graph live in credential-protected
 * storage, which is readable only after the first unlock. Re-arming at `BOOT_COMPLETED` still posts a reminder whose
 * slot passed less than 30 minutes before the unlock; one due earlier while the phone was locked after a reboot is
 * skipped.
 */
class SystemEventsReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_LOCALE_CHANGED) {
            val result = goAsync()
            // Needs no graph: also safe in a process that has none (the library's device tests).
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    WidgetSnapshotStore.languageChanged(context)
                } finally {
                    result.finish()
                }
            }
            return
        }
        if (action !in HANDLED) return
        val result = goAsync()
        get<CoroutineScope>(APPLICATION_SCOPE).launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    get<AlarmNotificationScheduler>().rearm()
                }
                get<StashSync>().onTimeChanged()
            } catch (e: Exception) {
                // A failed re-plan must not crash the app from a system broadcast; the next foreground re-plans.
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
