package com.techgv.stash.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern

// The user journeys the baseline profile and the benchmarks share. Selectors are the Compose test tags (exposed as
// resource ids by MainActivity) and visible English text, so run on a device in English.

const val PACKAGE = "com.techgv.stash"

// Generous: emulators on a loaded host draw a frame in seconds.
private const val TIMEOUT_MS = 30_000L
/** A shelf card (its test tag, `item-<id>`). */
val ITEM: BySelector = By.res(Pattern.compile("item-.*"))

/**
 * Replaces the seeded items with [count] new ones through the benchmark build's seed receiver (see `SeedReceiver`) and
 * returns its report. The app's own items are kept. `--include-stopped-packages`: Macrobenchmark force-stops the app.
 */
fun seed(count: Int): String = shell("am broadcast -a $PACKAGE.SEED -p $PACKAGE --include-stopped-packages --ei count $count")
    .also { check("seeded $count" in it) { "seeding failed: $it" } }

/** Removes every seeded item, and the links [shareLink] saved. */
fun wipeSeeded(): String = shell("am broadcast -a $PACKAGE.WIPE -p $PACKAGE --include-stopped-packages")

val uiDevice: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

private fun shell(command: String): String = uiDevice.executeShellCommand(command)

/**
 * Cancels the app's scheduled jobs (WorkManager's), so JobScheduler does not start the app in the background between a
 * cold-start iteration's kill and its launch. The app schedules them again when it next starts.
 */
fun cancelBackgroundWork() {
    shell("cmd jobscheduler cancel $PACKAGE")
}

/** Waits until the shelf shows its first card. */
fun MacrobenchmarkScope.waitForShelf() {
    device.require(Until.hasObject(ITEM), "the shelf showed no item")
}

/** Waits for [condition]; on a timeout, saves a screenshot to the test app's files directory and fails. */
private fun UiDevice.require(condition: SearchCondition<Boolean>, failure: String) {
    if (wait(condition, TIMEOUT_MS)) return
    val files = InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null)
    takeScreenshot(File(files, "${System.currentTimeMillis()}-failure.png"))
    error(failure)
}

/**
 * Flings the shelf down three times and back up three times. By screen position, not through the list's node: on a
 * loaded emulator UiAutomator at times sees none of the app's nodes for many seconds. A sixth of the width from the
 * left is inside the shelf on a phone and inside the list pane on a tablet, and clear of the back-gesture edge.
 */
fun MacrobenchmarkScope.scrollShelf() {
    repeat(3) { flingShelf(up = false) }
    repeat(3) { flingShelf(up = true) }
}

private fun MacrobenchmarkScope.flingShelf(up: Boolean) {
    val x = device.displayWidth / 6
    val low = device.displayHeight * 3 / 4
    val high = device.displayHeight / 4
    // Few steps make a fast swipe, which the list turns into a fling.
    if (up) device.swipe(x, high, x, low, FLING_STEPS) else device.swipe(x, low, x, high, FLING_STEPS)
    device.waitForIdle()
}

private const val FLING_STEPS = 5

/** Opens the first card in the reader and closes it again. */
fun MacrobenchmarkScope.openAndCloseReader() {
    device.findObject(ITEM).click()
    val close = By.desc("Close and return to Stash")
    device.require(Until.hasObject(close), "the reader did not open")
    device.findObject(close).click()
    device.waitForIdle()
}

/** Home → Search → types a query → back to the shelf. */
fun MacrobenchmarkScope.searchShelf() {
    val pill = By.text("Search your shelf")
    // The pill scrolls with the shelf; back to the top until it shows.
    repeat(10) { if (!device.hasObject(pill)) flingShelf(up = true) }
    device.findObject(pill).click()
    val field = By.clazz("android.widget.EditText")
    device.require(Until.hasObject(field), "search did not open")
    device.findObject(field).text = "design"
    device.waitForIdle()
    device.pressBack() // the keyboard
    device.pressBack() // search
    waitForShelf()
}

/**
 * Shares a new link to Stash like another app would and gives the quick-save sheet time to draw. The link is on a
 * seeded host, so [wipeSeeded] removes it again. The sheet is not looked up: UiAutomator gets no accessibility root
 * for its window on the tablet emulator (API 35).
 */
fun UiDevice.shareLink() {
    executeShellCommand(
        "am start -W -a android.intent.action.SEND -t text/plain -n $PACKAGE/.ShareReceiverActivity " +
            "--es android.intent.extra.TEXT ${newSharedUrl()}",
    )
    Thread.sleep(SHEET_DRAW_MS)
}

fun newSharedUrl() = "https://share.seed.stash.invalid/${System.nanoTime()}"

/** The save finishes and the sheet draws well within this after the share activity's first frame. */
const val SHEET_DRAW_MS = 2_000L
