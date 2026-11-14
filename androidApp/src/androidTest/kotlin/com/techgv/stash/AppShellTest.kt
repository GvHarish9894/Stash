package com.techgv.stash

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.platform.AliasAppIconSwitcher
import com.techgv.stash.core.platform.AppIconSwitcher
import com.techgv.stash.core.platform.BrowserLauncher
import com.techgv.stash.core.platform.Clipboard
import com.techgv.stash.core.platform.NotificationPermission
import android.os.ParcelFileDescriptor
import com.techgv.stash.core.platform.ClipboardLink
import com.techgv.stash.core.platform.WidgetPinner
import com.techgv.stash.core.model.WidgetSize
import com.techgv.stash.navigation.DeepLinks
import com.techgv.stash.widget.openAppIntent
import android.content.Intent
import android.net.Uri
import com.techgv.stash.core.platform.ForegroundActivity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Runs in the app's own process: `StashApplication` started the real graph, and the real manifest applies. */
class AppShellTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val koin = GlobalContext.get()

    private val icons = koin.get<AliasAppIconSwitcher>()

    private fun enabledAliases() = AppIcon.entries.filter {
        context.packageManager.getComponentEnabledSetting(icons.alias(it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            // The manifest default: Leather is declared enabled, the others disabled.
            (it == AppIcon.Leather && context.packageManager.getComponentEnabledSetting(icons.alias(it)) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    /** Back to the Leather icon; call with no app activity visible. */
    private fun restoreLeather() = runBlocking {
        icons.set(AppIcon.Leather)
        icons.applyPending()
    }

    @Test
    fun exactlyOneLauncherAliasIsEnabledAfterEachIconChange() = runBlocking {
        val switcher = koin.get<AppIconSwitcher>()
        try {
            (AppIcon.entries + AppIcon.entries.reversed()).forEach { icon ->
                assertTrue(switcher.set(icon), icon.name)
                icons.applyPending()
                assertEquals(listOf(icon), enabledAliases())
            }
        } finally {
            restoreLeather()
        }
    }

    @Test
    fun changingTheIconKeepsTheAppOpenAndSwapsTheLauncherEntryOnceTheAppIsInTheBackground() = runBlocking {
        restoreLeather()
        // Exactly as the launcher starts the app: through the enabled alias, which becomes the task's root.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(icons.alias(AppIcon.Leather))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(launcher)
            val activity = assertNotNull(waitFor { ForegroundActivity.current as? MainActivity })

            assertTrue(koin.get<AppIconSwitcher>().set(AppIcon.Night))
            SystemClock.sleep(2_000)
            assertFalse(activity.isFinishing || activity.isDestroyed, "the app stays open after the icon change")
            assertSame(activity, ForegroundActivity.current)
            assertEquals(listOf(AppIcon.Leather), enabledAliases(), "no swap while the app is in front")

            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            assertEquals(listOf(AppIcon.Night), waitFor { enabledAliases().takeIf { it == listOf(AppIcon.Night) } })
        } finally {
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            SystemClock.sleep(1_500)
            restoreLeather()
        }
    }

    @Test
    fun noIconSwapWhileTheAppShowsACustomTabItOpenedOnlyOnceTheUserLeaves() = runBlocking {
        restoreLeather()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(icons.alias(AppIcon.Leather))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(launcher)
            val activity = assertNotNull(waitFor { ForegroundActivity.current as? MainActivity })
            assertTrue(koin.get<AppIconSwitcher>().set(AppIcon.Night))

            var opened = false
            instrumentation.runOnMainSync { opened = koin.get<BrowserLauncher>().open("https://example.com/", 0xFF7A4E2D.toInt(), darkTheme = false) }
            assertTrue(opened)
            assertNotNull(waitFor { activity.takeIf { !it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) } }, "the tab covers the app")
            SystemClock.sleep(2_000)
            assertEquals(listOf(AppIcon.Leather), enabledAliases(), "no swap behind the app's own Custom Tab")
            assertFalse(activity.isFinishing || activity.isDestroyed, "the tab's task is still open")

            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            assertSame(activity, waitFor { ForegroundActivity.current as? MainActivity }, "Back from the tab returns to the app")
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            assertEquals(listOf(AppIcon.Night), waitFor { enabledAliases().takeIf { it == listOf(AppIcon.Night) } }, "swapped once the user left")
        } finally {
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            SystemClock.sleep(1_500)
            restoreLeather()
        }
    }

    @Test
    fun theWidgetHeaderStillOpensTheAppAfterAnIconChange() = runBlocking {
        // Glance bakes the widget's intent into the widget when it draws, before the icon changes.
        val header = openAppIntent(context)
        try {
            icons.set(AppIcon.Night)
            icons.applyPending()
            assertEquals(listOf(AppIcon.Night), enabledAliases())
            context.startActivity(header)
            val activity = assertNotNull(waitFor { ForegroundActivity.current as? MainActivity }, "the header opened the app")
            instrumentation.runOnMainSync { activity.finish() }
            SystemClock.sleep(1_500)
        } finally {
            restoreLeather()
        }
    }

    @Test
    fun theForegroundActivityIsTrackedWhileResumedAndReleasedAfter() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        assertSame(activity, ForegroundActivity.current)
        scenario.close()
        assertNull(ForegroundActivity.current, "a destroyed activity must not stay reachable")
    }

    @Test
    fun theBrowserOpensAWebPageAndRefusesWhatNothingCanOpen() {
        // From the app's own activity, so the Custom Tab joins its task and Back returns to it.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val browser = koin.get<BrowserLauncher>()
            var opened = false
            var refused = true
            scenario.onActivity {
                opened = browser.open("https://example.com/", 0xFF7A4E2D.toInt(), darkTheme = false)
                refused = browser.open("stash-nothing-handles-this://x", 0xFF7A4E2D.toInt(), darkTheme = true)
            }
            assertTrue(opened)
            assertFalse(refused)
            SystemClock.sleep(2_000)
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    private fun <T : Any> waitFor(read: () -> T?): T? {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < end) read()?.let { return it }.also { SystemClock.sleep(100) }
        return read()
    }

    @Test
    fun aDeepLinkOpensOnceAndNotAgainWhenTheActivityIsRecreated() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("stash://reader/recreate-test")).setClass(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(link).use { scenario ->
            assertEquals("recreate-test", DeepLinks.pending.value)
            DeepLinks.consumed("recreate-test")
            scenario.recreate()
            assertNull(DeepLinks.pending.value, "a rotation must not reopen the reader")
            // A link arriving while the activity is on top goes through onNewIntent (singleTop).
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("stash://reader/second")).setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            assertEquals("second", waitFor { DeepLinks.pending.value })
            DeepLinks.consumed("second")
        }
    }

    @Test
    fun aDeepLinkIsNotOpenedAgainWhenItsTaskIsReopenedFromRecents() {
        assertNull(DeepLinks.pending.value, "precondition")
        // Recents restarts a task whose activity is gone with the intent that first started it, flagged as from history.
        val fromRecents = Intent(Intent.ACTION_VIEW, Uri.parse("stash://reader/recents-test")).setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        ActivityScenario.launch<MainActivity>(fromRecents).use {
            assertNull(DeepLinks.pending.value, "the reader must not reopen")
        }
    }

    @Test
    fun notificationSettingsOpenForThisApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { koin.get<NotificationPermission>().openSettings() }
            fun settingsOnTop() = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities"),
            ).use { String(it.readBytes()) }.lineSequence().any { "topResumedActivity" in it && "settings" in it }
            assertTrue(waitFor { settingsOnTop().takeIf { it } } != null, "the system settings screen is on top")
            // It sits in the app's task: left open, the next launch of that task would show it instead of the app. A
            // Back during its opening animation can be lost, so press again while it is still on top.
            repeat(3) {
                if (settingsOnTop()) {
                    instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    waitFor { (!settingsOnTop()).takeIf { it } }
                }
            }
            assertFalse(settingsOnTop(), "Back closed the settings screen")
        }
    }

    @Test
    fun widgetPinningIsSupportedAndAPinRequestReachesTheLauncher() {
        assumeDeviceWideChangesAllowed() // the launcher's "add to home screen" dialog, which one stray tap accepts
        val pinner = koin.get<WidgetPinner>()
        assertTrue(pinner.canPin)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var shown = false
            scenario.onActivity { shown = pinner.requestPin(WidgetSize.Medium) }
            assertTrue(shown)
            SystemClock.sleep(1_500)
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    @Test
    fun clipboardReturnsOnlyAWholeHttpLink() = runBlocking {
        assumeDeviceWideChangesAllowed() // overwrites the clipboard
        // Android 10+ lets only the focused app read the clipboard.
        ActivityScenario.launch(MainActivity::class.java).use {
            val clipboard = koin.get<Clipboard>()
            fun copied(text: String): String? {
                instrumentation.runOnMainSync { clipboard.copy(text) }
                return runBlocking { clipboard.readUrl() }
            }
            assertEquals("https://example.com/a?b=1", copied("  https://example.com/a?b=1 \n"))
            // Android 12+ classifies the clip in the background; peeking reads only that verdict.
            assertEquals(ClipboardLink.Hidden, waitFor { runBlocking { clipboard.peekLink() } })
            assertNull(copied("just words"))
            SystemClock.sleep(2_000)
            assertNull(clipboard.peekLink(), "plain words are not offered")
            assertNull(copied("Look https://example.com/a"), "a link inside text is not offered")
            assertNull(copied("example.com/a"), "no scheme")
            assertNull(copied("ftp://example.com/a"))
        }
    }
}
