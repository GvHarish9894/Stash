package com.techgv.stash.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Every duration (ms) and easing of the handoff's motion table. Use [motionSpec] (or `Stash.reduceMotion`) so Reduce
 * motion turns each spec into `snap()`.
 */
object StashMotion {
    /** Entering motion. */
    val EaseOutEmphasized: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** Exits; about 30 % faster than the matching entrance. */
    val EaseIn: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** CSS `ease`. */
    val Ease: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** CSS `ease-in-out`. */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** Reader load bar. */
    val LoadBar: Easing = CubicBezierEasing(0.3f, 0.7f, 0.4f, 1f)

    /** Unread dot pop-in. */
    val Overshoot: Easing = CubicBezierEasing(0.3f, 1.35f, 0.5f, 1f)

    const val SEARCH_OPEN = 340
    const val SEARCH_CLOSE = 220
    const val READER_OPEN = 360
    const val READER_CLOSE = 240
    const val PANEL_IN = 340
    const val PANEL_OUT = 240
    const val SHEET_IN = 360
    const val SHEET_OUT = 240
    const val SCRIM_IN = 250
    const val LIST_RISE = 460
    const val LIST_STAGGER = 55
    const val LIST_STAGGER_MAX_ITEMS = 8
    const val NEW_ITEM = 700
    const val SWIPE_OUT = 230
    /** Row collapse after a swipe-out; also how long a flown-off card waits before it slides back if it is still shown. */
    const val ROW_COLLAPSE = 260
    const val STRIKE = 450
    const val DOT_CRACK = 400
    const val CHECK_POP = 380
    const val QUICK_SAVE_SHEET = 360
    const val QUICK_SAVE_TUCK_DELAY = 250
    const val QUICK_SAVE_TUCK = 800
    const val QUICK_SAVE_COVER_DELAY = 850
    const val QUICK_SAVE_COVER = 450
    const val QUICK_SAVE_RIBBON_DELAY = 1200
    const val QUICK_SAVE_RIBBON = 350
    const val QUICK_SAVE_TEXT_DELAY = 1250
    const val QUICK_SAVE_TEXT = 350
    const val CELEBRATION_IGNITE_DELAY = 350
    const val CELEBRATION_IGNITE = 700
    const val CELEBRATION_GLOW_DELAY = 450
    const val CELEBRATION_GLOW = 900
    const val CELEBRATION_ROLL_DELAY = 1000
    const val CELEBRATION_ROLL = 500
    const val CELEBRATION_TEXT_DELAY = 1250
    const val CELEBRATION_TEXT = 350
    const val CELEBRATION_DOT_DELAY = 1400
    const val CELEBRATION_DOT = 450
    const val CELEBRATION_SPARK = 3000
    const val FLAME_FLICKER_CYCLE = 1600
    const val FLAME_FLICKER_CYCLES = 3
    const val NUDGE_PULSE_CYCLE = 1800
    const val NUDGE_PULSE_CYCLES = 3
    const val SELECT = 200
    const val NOTIFICATION_IN = 420
    const val NOTIFICATION_OUT = 260
    const val READER_LOAD = 1200
    const val TOAST = 300

    /** Title ink and thumbnail opacity when an item becomes read or unread. */
    const val READ_STYLE = 300

    /** One sweep of the paper shimmer over pending content; it sweeps [SHIMMER_SWEEPS] times (~4 s, no loop over ~5 s). */
    const val SHIMMER = 1400
    const val SHIMMER_SWEEPS = 3

    /** Chip press: scale to 0.94. */
    const val CHIP_PRESS = 180
    const val CHIP_PRESS_SCALE = 0.94f

    /** The empty-state illustration breathes once: up to 1.03 and back. */
    const val EMPTY_BREATH = 1600

    /** Card layout or text size change: thumbnail size, padding, radius and font size animate (ease). */
    const val LAYOUT_CHANGE = 250

    /** List item fade in / out and placement (`stashAnimateItem`). */
    const val LIST_ITEM = 260

    /** Card spring-back after a release below the threshold (about 380 ms). */
    fun <T> swipeBack(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 400f)

    /** The switch thumb. */
    fun <T> switchThumb(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 400f)

    /** Press feedback: a short springy shrink. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.45f, stiffness = 1500f)

    fun <T> enter(durationMillis: Int, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, EaseOutEmphasized)

    fun <T> exit(durationMillis: Int): FiniteAnimationSpec<T> = tween(durationMillis, easing = EaseIn)
}

/** [spec], or an instant change when Reduce motion is on. */
@Composable
@ReadOnlyComposable
fun <T> motionSpec(spec: AnimationSpec<T>): AnimationSpec<T> = if (LocalReduceMotion.current) snap() else spec
