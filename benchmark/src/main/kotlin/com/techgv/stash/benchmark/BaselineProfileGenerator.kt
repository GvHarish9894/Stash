package com.techgv.stash.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile: `ANDROID_SERIAL=<emulator> ./gradlew :androidApp:generateBaselineProfile`
 * (docs/PERFORMANCE.md). The result is committed in `androidApp/src/main/generated/baselineProfiles`.
 *
 * The plugin runs this against its `nonMinifiedRelease` variant, which has no seed receiver: it shares links instead
 * (on the seeded host, so the benchmark build's wipe removes them), so even a fresh install has a shelf to scroll.
 * Generate on an emulator, not on a phone with real data.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // The app asks once, over the shelf, the first time it holds an unread item (D27); answer it up front.
        uiDevice.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
        repeat(SHARED_LINKS) { uiDevice.shareLink() }
        // Five runs, not up to fifteen until the profile is stable: on a loaded emulator UiAutomator sometimes stops
        // seeing the app's views, and each extra run is another chance of that.
        rule.collect(packageName = PACKAGE, maxIterations = 5, includeInStartupProfile = true) {
            device.shareLink()
            pressHome()
            startActivityAndWait()
            waitForShelf()
            scrollShelf()
            openAndCloseReader()
            searchShelf()
        }
    }

    private companion object {
        // Enough cards to scroll on a tablet.
        const val SHARED_LINKS = 12
    }
}
