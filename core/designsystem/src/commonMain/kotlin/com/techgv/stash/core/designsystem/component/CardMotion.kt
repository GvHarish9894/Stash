package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.util.lerp
import com.techgv.stash.core.designsystem.theme.StashMotion

/** Card motions of the handoff's table that `StashMotion` does not cover: page tear, settle and the undo fly-back. */
object CardMotion {
    /** Remove from the item menu: the page tear, then the row collapses. */
    const val TEAR = 420

    /** The card whose reader just closed settles: 0.95 → 1.02 → 1. */
    const val SETTLE = 500

    /** Undo: the card flies back from −80 dp, overshoots 6 dp and settles. */
    const val FLY_BACK = 500

    /** The tear's cubic-bezier(0.5, 0, 0.75, 0). */
    val TearEasing: Easing = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)
}

/**
 * The value at linear time [t] (0..1) on CSS-style keyframes: [values] at [times], each segment eased by [easing] (as a
 * CSS `animation-timing-function` eases each keyframe interval).
 */
internal fun keyframe(t: Float, times: FloatArray, values: FloatArray, easing: Easing): Float {
    for (i in 1 until times.size) {
        if (t <= times[i]) {
            val f = ((t - times[i - 1]) / (times[i] - times[i - 1])).coerceIn(0f, 1f)
            return lerp(values[i - 1], values[i], easing.transform(f))
        }
    }
    return values.last()
}

private val THIRDS = floatArrayOf(0f, 0.6f, 1f)
private val SETTLE_SCALE = floatArrayOf(0.95f, 1.02f, 1f)
private val FLY_BACK_X = floatArrayOf(-80f, 6f, 0f)
private val FLY_BACK_TURN = floatArrayOf(-5f, 1f, 0f)
private val FLY_BACK_ALPHA = floatArrayOf(0f, 1f, 1f)
private val TEAR_TIMES = floatArrayOf(0f, 0.35f, 0.75f, 1f)
private val TEAR_NEAR = floatArrayOf(0f, 1f, 1f, 1f)
private val TEAR_FAR = floatArrayOf(0f, 0f, 1f, 1f)
private val TEAR_TURN = floatArrayOf(0f, -3f, -9f, -9f)
private val TEAR_ALPHA = floatArrayOf(1f, 0.9f, 0f, 0f)
private val TEAR_HEIGHT = floatArrayOf(1f, 1f, 1f, 0f)

/** Settle scale at linear time [t]. */
internal fun settleScale(t: Float) = keyframe(t, THIRDS, SETTLE_SCALE, StashMotion.EaseOutEmphasized)

/** Undo fly-back at linear time [t]: x offset (dp, toward the end edge), rotation (degrees) and alpha. */
internal fun flyBackX(t: Float) = keyframe(t, THIRDS, FLY_BACK_X, StashMotion.EaseOutEmphasized)
internal fun flyBackTurn(t: Float) = keyframe(t, THIRDS, FLY_BACK_TURN, StashMotion.EaseOutEmphasized)
internal fun flyBackAlpha(t: Float) = keyframe(t, THIRDS, FLY_BACK_ALPHA, StashMotion.EaseOutEmphasized)

/**
 * Page tear at linear time [t]: −24 dp and −3° by 35 %, off screen (−120 % of the width) at −9° and faded out by 75 %,
 * then the height collapses. [tearX] returns the x offset in px for a card [widthPx] wide and [nearPx] = 24 dp.
 */
internal fun tearX(t: Float, nearPx: Float, widthPx: Float): Float =
    -nearPx * keyframe(t, TEAR_TIMES, TEAR_NEAR, CardMotion.TearEasing) +
        (nearPx - 1.2f * widthPx) * keyframe(t, TEAR_TIMES, TEAR_FAR, CardMotion.TearEasing)
internal fun tearTurn(t: Float) = keyframe(t, TEAR_TIMES, TEAR_TURN, CardMotion.TearEasing)
internal fun tearAlpha(t: Float) = keyframe(t, TEAR_TIMES, TEAR_ALPHA, CardMotion.TearEasing)
internal fun tearHeight(t: Float) = keyframe(t, TEAR_TIMES, TEAR_HEIGHT, CardMotion.TearEasing)
