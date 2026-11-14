package com.techgv.stash

import android.content.res.Resources
import android.os.SystemClock
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.platform.AppLocale
import org.koin.core.context.GlobalContext
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T3.0 open question: which language do reminder texts use on Android? `ResourceReminderTextRenderer` reads Compose
 * resources outside composition, whose Android environment takes the language from `Locale.getDefault()`
 * (components-resources 1.12.2, `ResourceEnvironment.android.kt`). This checks that the app process's default locale
 * follows the per-app language, so reminders are rendered in the in-app choice on Android 13+.
 */
class ReminderLanguageTest {
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun theProcessDefaultLocaleFollowsThePerAppLanguage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appLocale = GlobalContext.get().get<AppLocale>()
        // Read before the change: on some phones (realme) the system resources follow the app language while it is set.
        val system = Resources.getSystem().configuration.locales[0].language
        try {
            instrumentation.runOnMainSync { appLocale.set("hi") }
            assertEquals("hi", waitForDefaultLanguage("hi"))
        } finally {
            instrumentation.runOnMainSync { appLocale.set(null) }
        }
        assertEquals(system, waitForDefaultLanguage(system))
    }

    private fun waitForDefaultLanguage(language: String): String {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (Locale.getDefault().language != language && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
        return Locale.getDefault().language
    }
}
