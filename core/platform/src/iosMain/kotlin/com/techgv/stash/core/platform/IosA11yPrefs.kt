package com.techgv.stash.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import kotlin.time.Duration

/** `UIAccessibility`: Reduce Motion, followed through its change notification; VoiceOver for the undo timeout. */
class IosA11yPrefs : A11yPrefs {
    private val state = MutableStateFlow(UIAccessibilityIsReduceMotionEnabled())
    override val reduceMotion: StateFlow<Boolean> = state.asStateFlow()

    init {
        // Lives as long as the process (a Koin single), so the observer is never removed.
        NSNotificationCenter.defaultCenter.addObserverForName(UIAccessibilityReduceMotionStatusDidChangeNotification, null, null) {
            state.value = UIAccessibilityIsReduceMotionEnabled()
        }
    }

    override fun recommendedTimeout(default: Duration): Duration =
        if (UIAccessibilityIsVoiceOverRunning()) Duration.INFINITE else default
}
