package com.techgv.stash

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.AlarmNotificationScheduler
import com.techgv.stash.core.platform.AliasAppIconSwitcher
import com.techgv.stash.core.platform.AppIconSwitcher
import com.techgv.stash.core.platform.AppLocale
import com.techgv.stash.core.platform.Clipboard
import com.techgv.stash.core.platform.ScheduledNotification
import com.techgv.stash.core.platform.WidgetPinner
import com.techgv.stash.core.model.WidgetSize
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Drives the installed app's real capabilities for the end-to-end checks, whose results are read with adb afterwards
 * (`dumpsys notification`, `dumpsys package`, `cmd locale get-app-locales`, logcat, screenshots). Run one method at a time:
 * `am instrument -w -e class 'com.techgv.stash.EndToEndDriverTest#<method>' [-e itemId <id>] [-e icon Night]
 * [-e language hi] com.techgv.stash.test/androidx.test.runner.AndroidJUnitRunner`. Without arguments every method
 * leaves the app as it was (Leather icon, phone language, a reminder that is cancelled again, the clipboard only peeked).
 * Excluded from `connectedDebugAndroidTest` ([EndToEndDriver], see `androidApp/build.gradle.kts`): it changes the real
 * reminder plan and leaves the widget pin dialog up.
 */
@EndToEndDriver
class EndToEndDriverTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val koin = GlobalContext.get()

    @Test
    fun armsAReminderOneMinuteAhead() = runBlocking {
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // Let the launch catch-up apply the app's own plan first, so it does not replace this one.
        SystemClock.sleep(3_000)
        val scheduler = koin.get<AlarmNotificationScheduler>()
        val itemId = arguments.getString("itemId")
        val reminder = ScheduledNotification(
            "e2e", Clock.System.now() + 1.minutes, "Your shelf is waiting", "End-to-end reminder", ItemId(itemId ?: "e2e-item"),
        )
        scheduler.apply(scheduler.applied() + reminder)
        assertNotNull(scheduler.pendingAlarm("e2e"))
        if (itemId == null) scheduler.apply(scheduler.applied() - reminder)
    }

    @Test
    fun switchesTheLauncherIcon() = runBlocking {
        assertTrue(koin.get<AppIconSwitcher>().set(AppIcon.valueOf(arguments.getString("icon", AppIcon.Leather.name))))
        // No app activity is visible while the driver runs: swap now, as going to the background would.
        koin.get<AliasAppIconSwitcher>().applyPending()
    }

    @Test
    fun setsTheAppLanguage() {
        instrumentation.runOnMainSync { koin.get<AppLocale>().set(arguments.getString("language")) }
    }

    /** With the app focused (Android 10+ lets only the focused app see the clipboard), peeks, or reads with `-e read true`. */
    @Test
    fun looksAtTheClipboard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val clipboard = koin.get<Clipboard>()
            val result = runBlocking { if (arguments.getString("read") == "true") clipboard.readUrl() else clipboard.peekLink() }
            // What kind of answer, never the clip itself.
            println("Clipboard: ${result?.let { it::class.simpleName } ?: "nothing"}")
            // Keeps the app in front while the end-to-end check takes a screenshot (the paste toast lasts ~2 s).
            SystemClock.sleep(4_000)
        }
    }

    /** Asks the launcher to pin the widget (`-e size Small|Medium`) from the app's activity; the dialog stays up. */
    @Test
    fun requestsPinningTheWidget() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var shown = false
            val size = WidgetSize.valueOf(arguments.getString("size", WidgetSize.Medium.name))
            scenario.onActivity { shown = koin.get<WidgetPinner>().requestPin(size) }
            println("Pin request shown: $shown")
            assertTrue(shown)
            SystemClock.sleep(4_000)
        }
    }
}

/** Marks the end-to-end drivers, which run only when asked for by class with `am instrument`. */
annotation class EndToEndDriver
