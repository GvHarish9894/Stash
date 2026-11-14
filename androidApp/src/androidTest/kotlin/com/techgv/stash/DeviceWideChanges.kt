package com.techgv.stash

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * Skips the calling test on a physical device: it changes device-wide state (a global or secure setting, airplane mode,
 * accessibility services, the clipboard, a system dialog) that a personal phone must keep. It runs on an emulator, or
 * anywhere with `-e allowDeviceWideChanges true`. `-e forcePhysicalDevice true` makes an emulator count as a phone, to
 * check this gate. From Gradle: `-Pandroid.testInstrumentationRunnerArguments.forcePhysicalDevice=true`.
 */
fun assumeDeviceWideChangesAllowed() {
    val arguments = InstrumentationRegistry.getArguments()
    val emulator = arguments.getString("forcePhysicalDevice") != "true" && isEmulator()
    assumeTrue(
        "changes device-wide state: runs only on an emulator or with -e allowDeviceWideChanges true",
        emulator || arguments.getString("allowDeviceWideChanges") == "true",
    )
}

// The emulator's virtual board, or its kernel flag (ro.boot.qemu from Android 12, ro.kernel.qemu before).
private fun isEmulator(): Boolean =
    Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || listOf("ro.boot.qemu", "ro.kernel.qemu").any { getprop(it) == "1" }

private fun getprop(name: String): String = try {
    ProcessBuilder("getprop", name).start().inputStream.bufferedReader().use { it.readText().trim() }
} catch (e: Exception) {
    ""
}
