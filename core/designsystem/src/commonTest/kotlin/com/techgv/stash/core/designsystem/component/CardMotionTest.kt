package com.techgv.stash.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardMotionTest {

    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.001f)

    @Test
    fun settleGoesFrom095Over102To1() {
        near(0.95f, settleScale(0f))
        near(1.02f, settleScale(0.6f))
        near(1f, settleScale(1f))
        // Ease-out per segment: past the linear midpoint half-way through the first segment.
        assertTrue(settleScale(0.3f) > (0.95f + 1.02f) / 2)
    }

    @Test
    fun flyBackStartsAtMinus80OvershootsBy6AndRests() {
        near(-80f, flyBackX(0f)); near(-5f, flyBackTurn(0f)); near(0f, flyBackAlpha(0f))
        near(6f, flyBackX(0.6f)); near(1f, flyBackTurn(0.6f)); near(1f, flyBackAlpha(0.6f))
        near(0f, flyBackX(1f)); near(0f, flyBackTurn(1f)); near(1f, flyBackAlpha(1f))
    }

    @Test
    fun tearPullsOffThenLeavesThenCollapses() {
        val near24 = 24f
        val width = 300f
        near(0f, tearX(0f, near24, width)); near(0f, tearTurn(0f)); near(1f, tearAlpha(0f)); near(1f, tearHeight(0f))
        near(-24f, tearX(0.35f, near24, width)); near(-3f, tearTurn(0.35f)); near(0.9f, tearAlpha(0.35f))
        near(-360f, tearX(0.75f, near24, width)); near(-9f, tearTurn(0.75f)); near(0f, tearAlpha(0.75f)); near(1f, tearHeight(0.75f))
        near(-360f, tearX(1f, near24, width)); near(0f, tearHeight(1f))
        // Ease-in per segment: the collapse is still less than half done half-way through it.
        assertTrue(tearHeight(0.875f) > 0.5f)
    }
}
