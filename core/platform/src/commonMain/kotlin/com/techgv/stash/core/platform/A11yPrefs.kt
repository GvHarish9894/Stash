package com.techgv.stash.core.platform

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** Accessibility settings the OS owns. */
interface A11yPrefs {
    /**
     * True while the OS asks for less motion: Android "Remove animations" (animator duration scale 0), iOS
     * `UIAccessibility.isReduceMotionEnabled`. Updates when the user changes it. The UI combines it with the in-app
     * Reduce motion switch: either one turns animation off.
     */
    val reduceMotion: StateFlow<Boolean>

    /**
     * How long a message with text and an action (the Undo snackbar) stays up, given the app's [default].
     * [Duration.INFINITE] while a screen reader runs (TalkBack, VoiceOver): the message stays until dismissed.
     * Otherwise Android returns `AccessibilityManager.getRecommendedTimeoutMillis(default, FLAG_CONTENT_TEXT or
     * FLAG_CONTENT_CONTROLS)` and iOS returns [default]. Never shorter than [default]. Ask each time a message is shown.
     */
    fun recommendedTimeout(default: Duration): Duration
}
