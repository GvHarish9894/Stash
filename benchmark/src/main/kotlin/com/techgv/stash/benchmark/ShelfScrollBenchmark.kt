package com.techgv.stash.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while flinging the shelf with 40 and with 50,000 seeded items (plus the app's own items). The seeded
 * items stay afterwards; wipe them with `am broadcast -a com.techgv.stash.WIPE -p com.techgv.stash`.
 */
@RunWith(AndroidJUnit4::class)
class ShelfScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scroll40() = scroll(40)

    @Test
    fun scroll50000() = scroll(50_000)

    private fun scroll(items: Int) {
        seed(items)
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = ITERATIONS,
            setupBlock = {
                startActivityAndWait()
                // Best effort: a missed lookup must not fail the run (see scrollShelf); the flings measure either way.
                device.wait(Until.hasObject(ITEM), SHELF_WAIT_MS)
            },
        ) {
            scrollShelf()
        }
    }
}

private const val SHELF_WAIT_MS = 5_000L
