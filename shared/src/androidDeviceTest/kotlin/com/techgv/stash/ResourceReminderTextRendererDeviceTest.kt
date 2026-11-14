package com.techgv.stash

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ResourceReminderTextRendererDeviceTest {
    @Test
    fun rendersEveryLevelInEnglish() = runTest { assertEnglishReminderTexts() }
}
