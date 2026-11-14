package com.techgv.stash.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/** The handoff's motion table for the screen behind another one. */
class ScreenTransitionsTest {
    @Test
    fun behindTheReaderTheScreenScalesDimsMovesUpAndRoundsItsCorners() {
        assertEquals(Recede(0.93f, 0.8f, 0f, lift = 6f, corner = 22f), recede(Layer.Reader))
    }

    @Test
    fun behindSearchHomeScalesAndDimsTo97Percent() {
        assertEquals(Recede(0.97f, 0.97f, 0f), recede(Layer.Search))
    }
}
