package com.techgv.stash.core.designsystem

import com.techgv.stash.core.designsystem.component.stashAnimateItem
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.component.CardEntrance
import com.techgv.stash.core.designsystem.component.FilterChip
import com.techgv.stash.core.designsystem.component.SavedItemCard
import com.techgv.stash.core.designsystem.component.rememberWithLeaving
import com.techgv.stash.core.designsystem.component.shrinkOut
import com.techgv.stash.core.designsystem.presentation.NOW
import com.techgv.stash.core.designsystem.presentation.savedItem
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Card, list and chip motion on the test clock: start, mid and end values, and instant under Reduce motion.
// iOS simulator and Android device.

@Composable
private fun Themed(reduceMotion: Boolean, content: @Composable () -> Unit) =
    StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = reduceMotion, systemReduceMotion = false, content = content)

private val card = savedItem().toCardUi(NOW, TimeZone.UTC)

@OptIn(ExperimentalTestApi::class)
class MotionUiTest {

    private var entrance by mutableStateOf(CardEntrance.None)
    private var tearing by mutableStateOf(false)
    private var layout by mutableStateOf(CardLayout.Comfortable)
    private var torn = 0

    /** A 300 dp card at x = 0 inside a "row" box, on a manual clock. */
    private fun cardTest(reduceMotion: Boolean, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            Themed(reduceMotion) {
                Box(Modifier.testTag("row")) {
                    SavedItemCard(
                        card, layout, SwipeOutcome.Remove, SwipeOutcome.MarkRead, {}, {}, {},
                        Modifier.width(300.dp), entrance = entrance, tearing = tearing, onTorn = { torn++ },
                    )
                }
            }
        }
        mainClock.advanceTimeByFrame()
        block()
    }

    /** The card's left edge in dp (its transform included). */
    private fun ComposeUiTest.cardX(): Float = onNodeWithTag("item-a").fetchSemanticsNode().positionInRoot.x / density.density

    private fun ComposeUiTest.height(tag: String): Float = onNodeWithTag(tag).fetchSemanticsNode().size.height / density.density

    private fun ComposeUiTest.frames(millis: Long) = mainClock.advanceTimeBy(millis)

    @Test
    fun cardSettlesWhenTheReaderClosesOnIt() = cardTest(reduceMotion = false) {
        assertEquals(0f, cardX(), 0.5f)
        entrance = CardEntrance.Settle
        frames(16)
        // Scale 0.95 about the centre: the left edge sits 2.5 % of 300 dp in.
        assertEquals(7.5f, cardX(), 1f)
        frames(284)
        // Around 60 %: 1.02, the edge 3 dp out.
        assertTrue(cardX() < -2f, "overshoot, x = ${cardX()}")
        frames(300)
        assertEquals(0f, cardX(), 0.5f)
    }

    @Test
    fun undoFliesTheCardBackFromTheStart() = cardTest(reduceMotion = false) {
        entrance = CardEntrance.FlyBack
        frames(16)
        assertTrue(cardX() in -90f..-75f, "starts 80 dp toward the start, x = ${cardX()}")
        frames(284)
        assertTrue(cardX() > 3f, "overshoots 6 dp, x = ${cardX()}")
        frames(300)
        assertEquals(0f, cardX(), 0.5f)
    }

    @Test
    fun menuRemoveTearsTheCardOffThenCollapsesItsRow() = cardTest(reduceMotion = false) {
        val rest = height("row")
        tearing = true
        frames(16)
        assertTrue(cardX() > -3f, "starts in place, x = ${cardX()}")
        frames(250)
        assertTrue(cardX() < -30f, "pulled toward the start, x = ${cardX()}")
        assertEquals(rest, height("row"), 0.5f)
        frames(100)
        assertTrue(height("row") < rest, "the row collapses in the last quarter")
        assertEquals(0, torn)
        frames(150)
        assertEquals(0f, height("row"), 0.5f)
        assertEquals(1, torn)
    }

    @Test
    fun cardMotionIsInstantUnderReduceMotion() = cardTest(reduceMotion = true) {
        entrance = CardEntrance.Settle
        frames(16)
        assertEquals(0f, cardX(), 0.5f)
        entrance = CardEntrance.FlyBack
        frames(16)
        assertEquals(0f, cardX(), 0.5f)
        tearing = true
        frames(16)
        assertEquals(0f, height("row"), 0.5f)
        assertEquals(1, torn)
    }

    @Test
    fun switchingToLargeImagesAnimatesTheThumbnailAndText() = cardTest(reduceMotion = false) {
        val comfortable = height("item-a")
        layout = CardLayout.LargeImages
        frames(16)
        assertEquals(comfortable, height("item-a"), 2f)
        frames(110)
        val mid = height("item-a")
        frames(300)
        val large = height("item-a")
        assertTrue(large > comfortable + 100f, "large images are taller: $comfortable → $large")
        assertTrue(mid > comfortable + 20f && mid < large - 20f, "half-way: $comfortable < $mid < $large")
    }

    @Test
    fun layoutChangeIsInstantUnderReduceMotion() = cardTest(reduceMotion = true) {
        val comfortable = height("item-a")
        layout = CardLayout.LargeImages
        frames(16)
        val large = height("item-a")
        assertTrue(large > comfortable + 100f, "$comfortable → $large")
        frames(400)
        assertEquals(large, height("item-a"), 0.5f)
    }

    private fun chipTest(reduceMotion: Boolean, block: ComposeUiTest.(remove: () -> Unit) -> Unit) = runComposeUiTest {
        var sources by mutableStateOf(listOf("alpha", "beta", "gamma"))
        mainClock.autoAdvance = false
        setContent {
            Themed(reduceMotion) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rememberWithLeaving(sources) { it }.forEach { (source, leaving) ->
                        FilterChip(source, false, {}, Modifier.shrinkOut(leaving).testTag(source))
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        block { sources = listOf("alpha", "gamma") }
    }

    private fun ComposeUiTest.chipX(tag: String): Float = onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.x / density.density

    @Test
    fun removedChipShrinksOut() = chipTest(reduceMotion = false) { remove ->
        val start = chipX("gamma")
        val settled = chipX("beta")
        remove()
        frames(16)
        // The leaving chip is no longer announced but still holds its place.
        onNodeWithText("beta").assertDoesNotExist()
        assertTrue(chipX("gamma") > start - 10f, "starts in place: $start → ${chipX("gamma")}")
        frames(150)
        assertTrue(chipX("gamma") in settled + 4f..start - 4f, "shrinking: ${chipX("gamma")}")
        frames(300)
        assertEquals(settled, chipX("gamma"), 0.5f)
    }

    @Test
    fun removedChipLeavesAtOnceUnderReduceMotion() = chipTest(reduceMotion = true) { remove ->
        val settled = chipX("beta")
        remove()
        frames(16)
        assertEquals(settled, chipX("gamma"), 0.5f)
    }

    @Test
    fun rowsBelowARemovedRowCloseTheGapWithEase() = runComposeUiTest {
        var rows by mutableStateOf(listOf("a", "b"))
        mainClock.autoAdvance = false
        setContent {
            Themed(reduceMotion = false) {
                LazyColumn(Modifier.height(400.dp)) {
                    items(rows, key = { it }) { Box(Modifier.stashAnimateItem(this).height(100.dp).fillMaxWidth().testTag(it)) }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        fun y() = onNodeWithTag("b").fetchSemanticsNode().positionInRoot.y / density.density
        assertEquals(100f, y(), 0.5f)
        rows = listOf("b")
        frames(146)
        // Halfway through the 260 ms collapse: ease (0.25, 0.1, 0.25, 1) has covered about 80 % of the 100 dp, where
        // ease-out-emphasized would have covered over 90 %.
        val travelled = 100f - y()
        assertTrue(travelled in 65f..88f, "travelled $travelled dp")
        frames(300)
        assertEquals(0f, y(), 0.5f)
    }
}
