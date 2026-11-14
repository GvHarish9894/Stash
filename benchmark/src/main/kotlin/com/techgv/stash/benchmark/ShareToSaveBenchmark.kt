package com.techgv.stash.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A share from another app into a stopped Stash. `timeToInitialDisplayMs` is the share activity's first (still empty)
 * frame; `sheetFirstFrameMs` is the quick-save sheet's first frame, after the save.
 */
@RunWith(AndroidJUnit4::class)
class ShareToSaveBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun coldShare() {
        try {
            rule.measureRepeated(
                packageName = PACKAGE,
                metrics = listOf(StartupTimingMetric(), SheetFirstFrameMetric()),
                compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
                startupMode = StartupMode.COLD,
                iterations = ITERATIONS,
                setupBlock = {
                    pressHome()
                    cancelBackgroundWork()
                },
            ) {
                startActivityAndWait(
                    Intent(Intent.ACTION_SEND)
                        .setComponent(ComponentName(PACKAGE, "$PACKAGE.ShareReceiverActivity"))
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, newSharedUrl()),
                )
                Thread.sleep(SHEET_DRAW_MS)
            }
        } finally {
            wipeSeeded()
        }
    }
}

/**
 * From the system starting the launch to the end of the first main-thread frame after the sheet's Compose view is
 * created: the share activity sets its content only once the save is done, so that frame is the sheet's first.
 */
@OptIn(ExperimentalMetricApi::class)
private class SheetFirstFrameMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val app = captureInfo.targetPackageName
        val mainThreadSlices = "slice s JOIN thread_track tt ON s.track_id = tt.id JOIN thread t USING (utid) " +
            "JOIN process p USING (upid) WHERE p.name = '$app' AND t.tid = p.pid"
        val row = traceSession.query(
            """
            SELECT
              (SELECT ts FROM slice WHERE name = 'launching: $app' ORDER BY ts LIMIT 1) AS launched,
              (SELECT s.ts + s.dur FROM $mainThreadSlices AND s.name LIKE 'Choreographer#doFrame%'
                 AND s.ts > (SELECT s.ts FROM $mainThreadSlices AND s.name = 'Compose:initializeView' ORDER BY s.ts LIMIT 1)
                 ORDER BY s.ts LIMIT 1) AS drawn
            """.trimIndent(),
        ).single()
        val launched = row.nullableLong("launched") ?: return emptyList()
        val drawn = row.nullableLong("drawn") ?: return emptyList()
        return listOf(Metric.Measurement("sheetFirstFrameMs", (drawn - launched) / 1_000_000.0))
    }
}
