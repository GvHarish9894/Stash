package com.techgv.stash

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.data.StashSync
import com.techgv.stash.core.platform.AliasAppIconSwitcher
import com.techgv.stash.core.platform.AndroidAppLocale
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.ForegroundActivity
import com.techgv.stash.core.platform.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

/**
 * Everything the process needs before any component runs; call from `Application.onCreate`. Applies the app language
 * to the process, starts the graph, then WorkManager (its startup initializer is removed from the manifest, so no
 * worker runs before the graph exists), tracks the foreground activity, creates the reminder channel, schedules daily
 * maintenance, and runs the foreground catch-up off the main thread once per process start and on every return to the
 * foreground. A pending app-icon change is applied when the user leaves the app.
 */
fun startStash(application: Application) {
    AndroidAppLocale.applyToProcess(application)
    startStashKoin { androidContext(application) }
    WorkManager.initialize(application, Configuration.Builder().build())
    ForegroundActivity.install(application)
    ReminderReceiver.createChannel(application)
    val koin = GlobalContext.get()
    val scope = koin.get<CoroutineScope>(APPLICATION_SCOPE)
    // Off the main thread, and the catch-up's graph only when it runs: a cold share's save waits for neither.
    scope.launch { koin.get<BackgroundJobs>().schedule(BackgroundJob.DailyMaintenance) }
    val icons = koin.get<AliasAppIconSwitcher>()
    fun catchUp() {
        ProcessCatchUp.ran = true
        Log.d("Stash", "Foreground catch-up")
        scope.launch {
            try {
                koin.get<StashSync>().onForeground()
            } catch (e: Exception) {
                // Every step is idempotent; whatever failed runs again on the next foreground.
            }
        }
    }
    val process = ProcessLifecycleOwner.get().lifecycle
    process.addObserver(
        object : DefaultLifecycleObserver {
            // A share is not the app coming to the foreground: its save must not compete with a full catch-up.
            override fun onStart(owner: LifecycleOwner) {
                if (ShareReceiverActivity.open == 0) catchUp()
            }

            // Stopped behind a screen the app opened itself (a Custom Tab, the share sheet) is not leaving: the swap
            // would close that screen; it waits for the next real leave or background start.
            override fun onStop(owner: LifecycleOwner) {
                if (!ForegroundActivity.openedOtherApp) icons.applyPending()
            }
        },
    )
    // Only timings, for the cold-share budget.
    Log.d("Stash", "Startup: ${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()} ms after process start")
    // A process started for an activity reaches onStart within moments and catches up there. Only a background start
    // (worker, receiver) is still not started after the wait, and catches up here unless a worker already did: once per
    // process start either way. A swap still pending from a process that ended in the foreground is applied here too.
    // shortcut: a UI start whose first activity takes longer than the wait catches up twice (harmless, idempotent);
    // switch to ApplicationStartInfo (API 35+) if that ever matters.
    Handler(Looper.getMainLooper()).postDelayed(
        {
            if (!process.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                icons.applyPending()
                if (!ProcessCatchUp.ran) catchUp()
            }
        },
        BACKGROUND_START_WAIT_MS,
    )
}

/** Whether this process ran the catch-up yet: from an activity start, the daily worker or the background-start wait. */
internal object ProcessCatchUp {
    @Volatile
    var ran = false
}

private const val BACKGROUND_START_WAIT_MS = 2_000L
