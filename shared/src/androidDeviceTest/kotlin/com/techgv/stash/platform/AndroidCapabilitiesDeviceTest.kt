package com.techgv.stash.platform

import android.app.LocaleManager
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.platform.AndroidA11yPrefs
import com.techgv.stash.core.platform.AndroidAppLocale
import com.techgv.stash.core.platform.AndroidConnectivity
import com.techgv.stash.assumeDeviceWideChangesAllowed
import com.techgv.stash.core.platform.AndroidNotificationPermission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AndroidCapabilitiesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun shell(command: String) {
        // Reading to the end waits for the command to finish.
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return true
            SystemClock.sleep(200)
        }
        return condition()
    }

    @Test
    fun connectivityAnswersInAndOutOfAirplaneMode() {
        assumeDeviceWideChangesAllowed() // airplane mode
        val connectivity = AndroidConnectivity(context)
        assertTrue(waitFor(30_000) { connectivity.isOnline() }, "online before the test")
        try {
            shell("cmd connectivity airplane-mode enable")
            assertTrue(waitFor(15_000) { !connectivity.isOnline() }, "offline in airplane mode")
        } finally {
            shell("cmd connectivity airplane-mode disable")
        }
        assertTrue(waitFor(60_000) { connectivity.isOnline() }, "online again")
    }

    @Test
    fun notificationPermissionWithoutAForegroundActivityAnswersWithoutPrompting() = runTest {
        val permission = AndroidNotificationPermission(context)
        assertEquals(permission.isGranted(), permission.request())
    }

    @Test
    fun reduceMotionFollowsRemoveAnimationsAndTheTimeoutIsNeverShorter() = runBlocking { // real time: waits for the OS
        assumeDeviceWideChangesAllowed() // the global animator_duration_scale setting
        val prefs = AndroidA11yPrefs(context)
        assertTrue(prefs.recommendedTimeout(10.seconds) >= 10.seconds)
        try {
            shell("settings put global animator_duration_scale 0")
            withTimeout(10.seconds) { prefs.reduceMotion.first { it } }
        } finally {
            shell("settings put global animator_duration_scale 1")
        }
        withTimeout(10.seconds) { prefs.reduceMotion.first { !it } }
        Unit
    }

    @Test
    fun theTimeoutIsInfiniteWhileAScreenReaderExploresByTouch() {
        assumeDeviceWideChangesAllowed() // turns TalkBack on, replacing the enabled accessibility services meanwhile
        val talkBack = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        // The default UiAutomation connection switches every other accessibility service off; this one keeps TalkBack.
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String) =
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
        // Put back exactly what was there, so even an allowed run on a phone keeps its own accessibility services.
        val services = shell("settings get secure enabled_accessibility_services")
        val enabled = shell("settings get secure accessibility_enabled")
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        val prefs = AndroidA11yPrefs(context)
        try {
            shell("settings put secure enabled_accessibility_services $talkBack")
            shell("settings put secure accessibility_enabled 1")
            // Skipped, not failed, on an image without TalkBack.
            assumeTrue("TalkBack explores by touch", waitFor(15_000) { accessibility.isTouchExplorationEnabled })
            assertEquals(Duration.INFINITE, prefs.recommendedTimeout(10.seconds))
        } finally {
            shell(if (services == "null") "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $services")
            shell("settings put secure accessibility_enabled ${if (enabled == "null") 0 else enabled}")
        }
        if (talkBack in services) return
        assertTrue(waitFor(15_000) { !accessibility.isTouchExplorationEnabled }, "TalkBack off again")
        assertEquals(10.seconds, prefs.recommendedTimeout(10.seconds))
    }

    // The OS's own record (Android 13+), which is what `cmd locale get-app-locales` prints.
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun appLocaleSetsReadsAndClearsTheAppLanguage() {
        val locale = AndroidAppLocale(context)
        val os = context.getSystemService(LocaleManager::class.java)
        try {
            instrumentation.runOnMainSync { locale.set("hi") }
            assertEquals("hi", os.applicationLocales.toLanguageTags())
            assertEquals("hi", locale.current)
        } finally {
            instrumentation.runOnMainSync { locale.set(null) }
        }
        assertTrue(os.applicationLocales.isEmpty)
        assertEquals(null, locale.current)
    }
}
