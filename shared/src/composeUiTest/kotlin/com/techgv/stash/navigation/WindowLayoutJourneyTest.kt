package com.techgv.stash.navigation

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.model.ItemId
import kotlin.test.Test

/** Window sizes through the whole app: phone landscape (decision D29) and the expanded list-detail layout. */
@OptIn(ExperimentalTestApi::class)
class WindowLayoutJourneyTest {
    private val searchPill = hasText("Search your shelf")
    private val keepReading = hasText("Keep reading")
    private val readNow = hasText("Read now")
    private val readerClose = labelled("Close and return to Stash")
    private val readerMode = labelled("Reader mode")
    private val emptyPane = hasText("Nothing open yet")

    /** The operator's phone, 423 × 941 dp, upright and turned sideways. */
    private val portrait = 423.dp to 941.dp
    private val landscape = 941.dp to 423.dp

    private fun Journey.rotateTo(size: Pair<Dp, Dp>) = rotate(size.first, size.second)

    private fun Journey.waitUntilOn(matcher: SemanticsMatcher) =
        test.waitUntil(timeoutMillis = 5_000) { runCatching { test.onNode(matcher).assertIsOn() }.isSuccess }

    @Test
    fun aPhoneTurnedSidewaysIsSinglePaneHidesTheNudgeAndKeepsTheOpenReaderAcrossRotations() {
        var id = ItemId("")
        val paragraph = "Paragraph 1 of Sideways"
        journeyTest(width = portrait.first, height = portrait.second, seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Sideways")
        }) {
            waitFor(cardMatcher(id))
            // Nothing read today and an unread item: the nudge shows upright...
            waitFor(readNow)

            // ...and hides sideways, where the window is single-pane although 941 dp is an expanded width.
            rotateTo(landscape)
            waitFor(cardMatcher(id))
            test.onNode(readNow).assertDoesNotExist()
            test.onNode(emptyPane).assertDoesNotExist()

            // Opening navigates to the full-screen reader.
            card(id).performClick()
            waitFor(readerClose)
            test.onNode(searchPill).assertDoesNotExist()
            waitForText(paragraph, substring = true)
            waitForText("Marked as read")
            waitGone(hasText("Marked as read"))
            // The open reader's mode now differs from the setting, as after an Aa tap.
            settings { it.copy(readerModeDefault = false) }

            // Both ways the reader stays open, in its mode, with no second "Marked as read".
            for (size in listOf(portrait, landscape)) {
                rotateTo(size)
                waitFor(readerClose)
                waitForText(paragraph, substring = true)
                waitUntilOn(readerMode)
                test.onNode(searchPill).assertDoesNotExist()
                test.onNodeWithText("Marked as read").assertDoesNotExist()
                test.onNode(keepReading).assertDoesNotExist()
            }

            // The read happened in that one reader: closing it celebrates, over the single-pane shelf.
            test.onNode(readerClose).performClick()
            waitFor(keepReading)
            // At 423 dp the celebration scrolls: a real tap on "Keep reading".
            test.onNode(keepReading).performScrollTo().assertIsDisplayed().performClick()
            waitFor(searchPill)
            test.onNode(emptyPane).assertDoesNotExist()
        }
    }

    @Test
    fun aLargeWindowIsTwoPaneAndTheShelfsSheetsCoverBothPanes() {
        var id = ItemId("")
        val paragraph = "Paragraph 1 of Under-the-sheet"
        journeyTest(width = 1280.dp, height = 800.dp, seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Under-the-sheet")
            // Today's read already happened, so no celebration covers the pane.
            bus.dispatch(Command.OpenItem(id))
        }) {
            waitFor(emptyPane)
            card(id).performClick()
            waitForText(paragraph, substring = true)
            test.onNode(searchPill).assertIsDisplayed()
            // The item open in the pane shows selected in the list.
            card(id).assertIsSelected()

            // The add-link sheet: the reader pane leaves the accessibility tree, and a tap over it lands on the scrim.
            test.onNodeWithText("Add link").performClick()
            waitForText("Save to shelf")
            test.onNodeWithText(paragraph, substring = true).assertDoesNotExist()
            test.onRoot().performTouchInput { click(Offset(width - 4f, height * 0.1f)) }
            waitGone(hasText("Save to shelf"))
            waitForText(paragraph, substring = true)

            // An item sheet: back closes the sheet, not the pane.
            card(id).performSemanticsAction(SemanticsActions.OnLongClick)
            waitForText("Mark as unread")
            test.onNodeWithText(paragraph, substring = true).assertDoesNotExist()
            pressBack()
            waitGone(hasText("Mark as unread"))
            waitForText(paragraph, substring = true)
            test.onNode(readerClose).assertIsDisplayed()
        }
    }
}
