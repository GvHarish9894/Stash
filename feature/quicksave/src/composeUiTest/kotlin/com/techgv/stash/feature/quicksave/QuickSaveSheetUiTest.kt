package com.techgv.stash.feature.quicksave

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Runs on the iOS simulator (and as an Android device test), in English. The main clock drives the auto-close delay.

@OptIn(ExperimentalTestApi::class)
class QuickSaveSheetUiTest {
    /** Shows the sheet for [outcome] with motion on and a manual clock; returns how often onDone ran. */
    private fun ComposeUiTest.show(outcome: QuickSaveOutcome, reduceMotion: Boolean = false, fontScale: Float = 1f): IntArray {
        val done = IntArray(1)
        mainClock.autoAdvance = false
        setContent {
            StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = reduceMotion, systemReduceMotion = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    // Above 100 % text, a phone's width.
                    Box(if (fontScale > 1f) Modifier.width(390.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        QuickSaveSheet(outcome) { done[0]++ }
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        return done
    }

    @Test
    fun aSaveClosesItselfAfterFourAndAHalfSeconds() = runComposeUiTest {
        val done = show(QuickSaveOutcome.Saved)
        mainClock.advanceTimeBy(400)
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Tucked into your shelf")).assertExists()
        onNodeWithText("Tucked into your shelf").assert(isHeading())
        mainClock.advanceTimeBy(3_900)
        assertEquals(0, done[0])
        mainClock.advanceTimeBy(300)
        assertEquals(1, done[0])
    }

    @Test
    fun aDuplicateSaysItMovedToTheTop() = runComposeUiTest {
        val done = show(QuickSaveOutcome.AlreadyOnShelf)
        mainClock.advanceTimeBy(2_000)
        onNodeWithText("Already on your shelf — moved to the top.").assertExists()
        mainClock.advanceTimeBy(2_300)
        assertEquals(0, done[0])
        mainClock.advanceTimeBy(300)
        assertEquals(1, done[0])
    }

    @Test
    fun nothingToSaveClosesAfterTwoSeconds() = runComposeUiTest {
        val done = show(QuickSaveOutcome.NothingToSave)
        onNodeWithText("Nothing to save here").assertExists()
        onNodeWithText("There was no link in what you shared.").assertExists()
        mainClock.advanceTimeBy(1_800)
        assertEquals(0, done[0])
        mainClock.advanceTimeBy(300)
        assertEquals(1, done[0])
    }

    @Test
    fun aFailureStaysUntilDone() = runComposeUiTest {
        val done = show(QuickSaveOutcome.NotSaved)
        onNodeWithText("Couldn’t save this link").assertExists()
        onNodeWithText("Tucked into your shelf").assertDoesNotExist()
        mainClock.advanceTimeBy(60_000)
        assertEquals(0, done[0])
        onNodeWithText("Done").performClick()
        assertEquals(1, done[0])
    }

    @Test
    fun theHandleCloses() = runComposeUiTest {
        val done = show(QuickSaveOutcome.Saved)
        mainClock.advanceTimeBy(400)
        onNodeWithContentDescription("Close").performClick()
        assertEquals(1, done[0])
    }

    /** Where Done sits on the first frame and once everything has settled. */
    private fun ComposeUiTest.doneTops(): Pair<Float, Float> {
        mainClock.advanceTimeByFrame()
        val first = onNodeWithText("Done").fetchSemanticsNode().boundsInRoot.top
        mainClock.advanceTimeBy(2_000)
        return first to onNodeWithText("Done").fetchSemanticsNode().boundsInRoot.top
    }

    @Test
    fun theSheetSlidesUpWithMotion() = runComposeUiTest {
        show(QuickSaveOutcome.Saved)
        val (first, settled) = doneTops()
        // The sheet's own height, well beyond the 8 dp the text rises.
        assertTrue(first > settled + 40f, "no slide: $first -> $settled")
    }

    @Test
    fun reduceMotionShowsTheSheetInPlace() = runComposeUiTest {
        show(QuickSaveOutcome.Saved, reduceMotion = true)
        val (first, settled) = doneTops()
        assertEquals(settled, first)
    }

    @Test
    fun doneFitsAt200PercentTextOnAPhone() = runComposeUiTest {
        show(QuickSaveOutcome.NotSaved, fontScale = 2f)
        mainClock.advanceTimeBy(1_000)
        val node = onNodeWithText("Done", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) } || layout.didOverflowHeight, "Done is clipped")
        val parent = node.parent!!.boundsInRoot
        assertTrue(node.boundsInRoot.top >= parent.top - 1f && node.boundsInRoot.bottom <= parent.bottom + 1f)
    }
}
