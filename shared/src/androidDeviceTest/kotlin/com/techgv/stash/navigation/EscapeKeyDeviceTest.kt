package com.techgv.stash.navigation

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.model.ItemId
import kotlin.test.Test

/**
 * A hardware keyboard's Esc closes the top layer as back does (handoff, Accessibility notes). A real key event through
 * the window, so Android only: on iOS, Compose itself turns an unconsumed Esc into back.
 */
@OptIn(ExperimentalTestApi::class)
class EscapeKeyDeviceTest {
    private val searchPill = hasText("Search your shelf")

    private fun escape() = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)

    @Test
    fun escClosesTheItemSheetTheReaderAndAScreen() {
        var id = ItemId("")
        journeyTest(seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Esc-closes")
            // Today's read already happened, so no celebration stands between the reader and the shelf.
            bus.dispatch(Command.OpenItem(id))
        }) {
            waitFor(cardMatcher(id))
            card(id).performSemanticsAction(SemanticsActions.OnLongClick)
            waitForText("Mark as unread")
            escape()
            waitGone(hasText("Mark as unread"))

            card(id).performClick()
            waitFor(labelled("Close and return to Stash"))
            escape()
            waitFor(searchPill)

            test.onNodeWithContentDescription("Settings").performClick()
            waitGone(searchPill)
            escape()
            waitFor(searchPill)
        }
    }
}
