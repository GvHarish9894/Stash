package com.techgv.stash.feature.shelf

import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.requestFocus
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.testing.asPagingSourceFactory
import androidx.paging.compose.collectAsLazyPagingItems
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.ChipCounts
import com.techgv.stash.core.domain.DayMark
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.SourceCount
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.domain.WeekDay
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

// Runs on the iOS simulator (and as an Android device test), in English. Drives ShelfScreen directly with a state the
// test changes and records the intents.

private val itemA = savedItem("a")
private val itemB = savedItem("b", lifecycle = ItemLifecycle.Read, addedAt = FIXTURE_NOW - 2.hours)

private val week = (6 downTo 0).map { back ->
    WeekDay(LocalDate(2026, 11, 12).minus(back, DateTimeUnit.DAY), isToday = back == 0, mark = if (back == 0) DayMark.NotRead else DayMark.Read)
}

private val baseState = ShelfState(
    now = FIXTURE_NOW,
    zone = TimeZone.UTC,
    chips = ChipCounts(all = 3, unread = 2, sources = listOf(SourceCount("example.com", 2))),
    streak = StreakState(3, readToday = false, status = StreakStatus.Unlit, week = week),
    archiveCount = 4,
    firstLoad = false,
)

private val baseRows = listOf(
    ShelfListRow.Header(GroupLabel.Today),
    ShelfListRow.Card(itemA, SwipeOutcome.Remove, SwipeOutcome.MarkRead),
    ShelfListRow.Card(itemB, SwipeOutcome.Remove, SwipeOutcome.MarkUnread),
)

private var inputModes: InputModeManager? = null

private fun hasPaneTitle(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

@OptIn(ExperimentalTestApi::class)
class ShelfScreenUiTest {

    private class Harness(initial: ShelfState) {
        val state = mutableStateOf(initial)
        val intents = mutableListOf<ShelfIntent>()
        var selected by mutableStateOf<ItemId?>(null)
        var settle by mutableStateOf<ItemId?>(null)
    }

    private fun shelfTest(
        state: ShelfState = baseState,
        rows: List<ShelfListRow> = baseRows,
        widthClass: WidthClass = WidthClass.Compact,
        undoTimeout: Duration = 10.seconds,
        /** Font scale; above 1 the screen is also laid out at a phone's 390 dp width. */
        fontScale: Float = 1f,
        manualClock: Boolean = false,
        reduceMotion: Boolean = true,
        block: ComposeUiTest.(Harness) -> Unit,
    ) = runComposeUiTest {
        val harness = Harness(state)
        if (manualClock) mainClock.autoAdvance = false
        setContent {
            inputModes = LocalInputModeManager.current
            StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = reduceMotion, systemReduceMotion = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    // A real Pager, so the list reports loading and loaded like the repository's.
                    val paged = remember { Pager(PagingConfig(pageSize = 30), pagingSourceFactory = rows.asPagingSourceFactory()).flow }
                        .collectAsLazyPagingItems()
                    Box(if (fontScale > 1f) Modifier.width(390.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        ShelfScreen(
                            harness.state.value, paged, widthClass, { undoTimeout }, {}, { harness.intents += it },
                            selectedItemId = harness.selected, settleItemId = harness.settle,
                        )
                    }
                }
            }
        }
        if (manualClock) mainClock.advanceTimeByFrame()
        block(harness)
    }

    private fun ComposeUiTest.update(harness: Harness, change: ShelfState.() -> ShelfState) {
        harness.state.value = harness.state.value.change()
        waitForIdle()
    }

    private fun ComposeUiTest.customAction(tag: String, label: String) {
        val action = onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        runOnIdle { action.action() }
    }

    /** The text is neither ellipsized nor cut off by its height, and lies inside the control that holds it. */
    private fun ComposeUiTest.assertFits(text: String) {
        val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) }, "\"$text\" is ellipsized")
        assertFalse(layout.didOverflowHeight, "\"$text\" is cut off at the bottom")
        val bounds = node.boundsInRoot
        val parent = node.parent!!.boundsInRoot
        assertTrue(bounds.top >= parent.top - 1f && bounds.bottom <= parent.bottom + 1f, "\"$text\" sticks out: $bounds in $parent")
    }

    // ---- header -----------------------------------------------------------------------------------------------

    @Test
    fun headerIsLabelledAndNavigates() = shelfTest { h ->
        onNodeWithText("Stash").assert(isHeading())
        onNodeWithText("2 unread").assertExists()
        onNodeWithContentDescription("3-day reading streak, not read today").performClick()
        onNodeWithContentDescription("Archive").performClick()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Search your shelf").performClick()
        onNodeWithText("Add link").performClick()
        assertEquals(
            listOf(ShelfIntent.OpenStreak, ShelfIntent.OpenArchive, ShelfIntent.OpenSettings, ShelfIntent.OpenSearch, ShelfIntent.OpenAddLink),
            h.intents,
        )
    }

    // ---- chips ------------------------------------------------------------------------------------------------

    @Test
    fun chipsAreSelectableTabsWithCountsAndMoreOpensTheRest() = shelfTest(
        state = baseState.copy(chips = ChipCounts(30, 2, listOf(SourceCount("example.com", 2)), more = listOf(SourceCount("x.com", 2)))),
    ) { h ->
        onNodeWithText("All · 30").assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        onNodeWithText("Unread · 2").assertIsNotSelected().performClick()
        onNodeWithText("example.com · 2").performClick()
        onNodeWithText("More…").performClick()
        assertEquals(
            listOf(
                ShelfIntent.SelectFilter(ShelfFilter.Unread),
                ShelfIntent.SelectFilter(ShelfFilter.Source("example.com")),
                ShelfIntent.OpenMoreSources,
            ),
            h.intents,
        )
        update(h) { copy(moreSourcesOpen = true) }
        onNode(hasPaneTitle("More sources")).assertExists()
        onNodeWithText("x.com · 2").performClick()
        assertEquals(ShelfIntent.SelectFilter(ShelfFilter.Source("x.com")), h.intents.last())
        onNodeWithContentDescription("Close sources").performClick()
        assertEquals(ShelfIntent.CloseMoreSources, h.intents.last())
    }

    @Test
    fun aSourceInMoreMarksTheMoreChipSelected() = shelfTest(
        state = baseState.copy(
            chips = ChipCounts(30, 2, listOf(SourceCount("example.com", 2)), more = listOf(SourceCount("x.com", 2))),
            filter = ShelfFilter.Source("x.com"),
        ),
    ) {
        onNodeWithText("More…").assertIsSelected()
        onNodeWithText("All · 30").assertIsNotSelected()
    }

    // ---- cards ------------------------------------------------------------------------------------------------

    @Test
    fun cardsOpenActAndOpenTheMenuWithoutGestures() = shelfTest { h ->
        onNodeWithText("TODAY").assert(isHeading())
        val label = onNodeWithTag("item-a").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals("Unread. Title a. example.com, just now, 5 min read.", label)
        onNodeWithTag("item-a").performSemanticsAction(SemanticsActions.OnClick)
        customAction("item-a", "Archive")
        customAction("item-b", "Mark as unread")
        customAction("item-a", "More options")
        assertEquals(
            listOf(
                ShelfIntent.Open(ItemId("a")),
                ShelfIntent.Act(ItemId("a"), SwipeOutcome.Archive),
                ShelfIntent.Act(ItemId("b"), SwipeOutcome.MarkUnread),
                ShelfIntent.OpenMenu(ItemId("a")),
            ),
            h.intents,
        )
    }

    @Test
    fun swipeAndLongPressReachTheViewModel() = shelfTest { h ->
        onNodeWithTag("item-a").performTouchInput { swipeRight(startX = 20f, endX = 20f + 140.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        onNodeWithTag("item-b").performTouchInput { longClick(durationMillis = 600) }
        waitForIdle()
        assertEquals(listOf(ShelfIntent.Act(ItemId("a"), SwipeOutcome.MarkRead), ShelfIntent.OpenMenu(ItemId("b"))), h.intents)
    }

    @Test
    fun menuSheetTakesOverTheAccessibilityTree() = shelfTest(state = baseState.copy(menuItem = itemA)) { h ->
        onNode(hasPaneTitle("Item options")).assertExists()
        onNodeWithTag("item-a").assertDoesNotExist()
        onNodeWithText("Copy link").performClick()
        onNodeWithContentDescription("Close options").performClick()
        assertEquals(listOf(ShelfIntent.MenuAction(ItemMenuAction.CopyLink), ShelfIntent.CloseMenu), h.intents)
        update(h) { copy(menuItem = null) }
        onNodeWithTag("item-a").assertExists()
    }

    // ---- Milestone 4 wiring: card motion, selection, focus return --------------------------------------------

    private fun ComposeUiTest.left(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.x / density.density

    @Test
    fun theTearingCardReportsItIsTorn() = shelfTest(state = baseState.copy(tearingItemId = ItemId("a"))) { h ->
        // Reduce motion: the tear is instant and reports straight away.
        assertEquals(listOf<ShelfIntent>(ShelfIntent.Torn(ItemId("a"))), h.intents)
    }

    @Test
    fun theCardUndoBroughtBackFliesInAndTheClosedReadersCardSettles() = shelfTest(manualClock = true, reduceMotion = false) { h ->
        val rest = left("item-b")
        update(h) { copy(flyBackItemId = ItemId("a")) }
        mainClock.advanceTimeByFrame()
        assertTrue(left("item-a") < rest - 60f, "flies back from the start: ${left("item-a")} vs $rest")
        mainClock.advanceTimeBy(700)
        assertEquals(rest, left("item-a"), 0.5f)
        h.settle = ItemId("b")
        mainClock.advanceTimeByFrame()
        assertTrue(left("item-b") > rest + 4f, "settles from 0.95: ${left("item-b")} vs $rest")
        mainClock.advanceTimeBy(700)
        assertEquals(rest, left("item-b"), 0.5f)
    }

    @Test
    fun theItemOpenInThePaneShowsSelected() = shelfTest(widthClass = WidthClass.Expanded) { h ->
        h.selected = ItemId("a")
        onNodeWithTag("item-a").assertIsSelected()
        onNodeWithTag("item-b").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun aSourceThatLeavesShrinksOutBeforeTheNextChipMoves() = shelfTest(
        state = baseState.copy(chips = ChipCounts(4, 2, listOf(SourceCount("example.com", 2), SourceCount("other.org", 2)))),
        manualClock = true, reduceMotion = false,
    ) { h ->
        val start = onNodeWithText("other.org · 2").fetchSemanticsNode().boundsInRoot.left / density.density
        val end = onNodeWithText("example.com · 2").fetchSemanticsNode().boundsInRoot.left / density.density
        update(h) { copy(chips = ChipCounts(2, 2, listOf(SourceCount("other.org", 2)))) }
        mainClock.advanceTimeBy(64)
        val early = onNodeWithText("other.org · 2").fetchSemanticsNode().boundsInRoot.left / density.density
        // The shrink eases in (slow start), so the neighbour has hardly moved yet; a plain removal would jump or ease out.
        assertTrue(early > start - (start - end) * 0.08f, "$start → $early (end $end)")
        mainClock.advanceTimeBy(600)
        assertEquals(end, onNodeWithText("other.org · 2").fetchSemanticsNode().boundsInRoot.left / density.density, 0.5f)
    }

    @Test
    fun closingASheetReturnsFocusToItsOpener() = shelfTest { h ->
        runOnIdle { inputModes?.requestInputMode(InputMode.Keyboard) }
        val archive = onNodeWithContentDescription("Archive")
        archive.requestFocus()
        archive.assertIsFocused()
        update(h) { copy(menuItem = itemA) }
        archive.assertDoesNotExist()
        update(h) { copy(menuItem = null) }
        waitForIdle()
        archive.assertIsFocused()
    }

    // ---- undo -------------------------------------------------------------------------------------------------

    @Test
    fun undoBarUndoesAndTimesOutAfterTheGivenTimeout() = shelfTest(
        state = baseState.copy(undo = UndoOffer("op-1", OperationType.Remove, ItemId("a"))), manualClock = true,
    ) { h ->
        onNode(
            SemanticsMatcher("polite live region") { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite }
                and hasAnyDescendant(hasText("Removed from your shelf")),
        ).assertExists()
        onNodeWithText("Undo").performClick()
        mainClock.advanceTimeBy(9_000)
        assertEquals(listOf<ShelfIntent>(ShelfIntent.Undo), h.intents)
        mainClock.advanceTimeBy(1_500)
        assertEquals(ShelfIntent.DismissUndo("op-1"), h.intents.last())
    }

    @Test
    fun undoBarPausesWhileHeldAndANewerOfferRestartsIt() = shelfTest(
        state = baseState.copy(undo = UndoOffer("op-1", OperationType.Remove, ItemId("a"))), manualClock = true,
    ) { h ->
        mainClock.advanceTimeBy(5_000)
        onNodeWithText("Removed from your shelf").performTouchInput { down(center) }
        mainClock.advanceTimeBy(20_000)
        assertEquals(emptyList(), h.intents, "held: no timeout")
        onNodeWithText("Removed from your shelf").performTouchInput { up() }

        h.state.value = h.state.value.copy(undo = UndoOffer("op-2", OperationType.Archive, ItemId("a")))
        mainClock.advanceTimeBy(9_000)
        onNodeWithText("Moved to Archive").assertExists()
        assertEquals(emptyList(), h.intents, "the newer offer has its own 10 s")
        mainClock.advanceTimeBy(1_500)
        assertEquals(listOf<ShelfIntent>(ShelfIntent.DismissUndo("op-2")), h.intents)
    }

    @Test
    fun undoBarStaysUntilDismissedUnderAScreenReader() = shelfTest(
        state = baseState.copy(undo = UndoOffer("op-1", OperationType.Archive, ItemId("a"))), undoTimeout = Duration.INFINITE, manualClock = true,
    ) { h ->
        mainClock.advanceTimeBy(120_000)
        assertEquals(emptyList(), h.intents)
        onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(listOf<ShelfIntent>(ShelfIntent.DismissUndo("op-1")), h.intents)
    }

    // ---- empty, nudge, tip, archive link, toast --------------------------------------------------------------

    @Test
    fun emptyShelfAndEmptyFilterSayDifferentThings() {
        shelfTest(state = baseState.copy(chips = ChipCounts(0, 0, emptyList()), archiveCount = 0), rows = emptyList()) {
            onNodeWithText("Your shelf is empty").assertExists()
            onNodeWithText("Swipe left to remove, right to mark read. Long-press for more.").assertDoesNotExist()
        }
        shelfTest(rows = emptyList()) {
            onNodeWithText("Nothing matches").assertExists()
            onNodeWithText("Your shelf is empty").assertDoesNotExist()
        }
    }

    @Test
    fun nudgeTipAndArchiveLink() = shelfTest(state = baseState.copy(nudge = ShelfNudge(ReminderLevel.Daily, 3, ItemId("a"), "Title a", 5))) { h ->
        onNodeWithText("Keep your 3-day streak going").assertExists()
        onNodeWithText("Read now").performClick()
        onNodeWithText("Swipe left to remove, right to mark read. Long-press for more.").assertExists()
        onNodeWithContentDescription("Dismiss tip").performClick()
        onNodeWithText("Archive · 4 items").performClick()
        assertEquals(listOf(ShelfIntent.ReadNudge, ShelfIntent.DismissGestureTip, ShelfIntent.OpenArchive), h.intents)
        update(h) { copy(preferences = UserPreferences(gestureTipDismissed = true), nudge = null) }
        onNodeWithContentDescription("Dismiss tip").assertDoesNotExist()
        onNodeWithText("Read now").assertDoesNotExist()
    }

    @Test
    fun toastsAreShown() = shelfTest(state = baseState.copy(toast = ShelfToast.AlreadyOnShelf)) { h ->
        onNodeWithText("Already on your shelf — moved to the top").assertExists()
        update(h) { copy(toast = ShelfToast.OpenedInBrowser) }
        onNodeWithText("Opened in your browser").assertExists()
    }

    // ---- add link ---------------------------------------------------------------------------------------------

    @Test
    fun addLinkSheetSuggestsTheClipboardAndReportsErrors() = shelfTest(
        state = baseState.copy(addLink = AddLinkState("not a link", ClipboardSuggestion.Visible("https://x.example/y"), AddLinkError.NotALink)),
    ) { h ->
        onNode(hasPaneTitle("Add a link")).assertExists()
        onNodeWithText("https://x.example/y", useUnmergedTree = true).assertExists()
        val message = "That doesn’t look like a link. Try one that starts with https://"
        onNodeWithContentDescription("URL").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, message))
        onNodeWithText("Use").performClick()
        onNodeWithContentDescription("URL").performTextReplacement("example.org")
        onNodeWithText("Save to shelf").performClick()
        onNodeWithText("Cancel").performClick()
        assertEquals(
            listOf(ShelfIntent.UseClipboard, ShelfIntent.AddLinkUrlChanged("example.org"), ShelfIntent.SaveLink, ShelfIntent.CloseAddLink),
            h.intents,
        )
        update(h) { copy(addLink = AddLinkState(clipboard = ClipboardSuggestion.Hidden, error = AddLinkError.Empty)) }
        onNodeWithText("There is a link on your clipboard", useUnmergedTree = true).assertExists()
        onNodeWithText("Paste or type a link first.").assertExists()
    }

    // ---- width classes and large text -------------------------------------------------------------------------

    @Test
    fun mediumWidthShowsLargeImageCardsInTwoColumns() = shelfTest(
        state = baseState.copy(preferences = UserPreferences(cardLayout = CardLayout.LargeImages)), widthClass = WidthClass.Medium,
    ) {
        val a = onNodeWithTag("item-a").fetchSemanticsNode().boundsInRoot
        val b = onNodeWithTag("item-b").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(a.top - b.top) < 1f && b.left > a.right, "not side by side: $a, $b")
    }

    @Test
    fun compactWidthKeepsOneColumn() = shelfTest(state = baseState.copy(preferences = UserPreferences(cardLayout = CardLayout.LargeImages))) {
        val a = onNodeWithTag("item-a").fetchSemanticsNode().boundsInRoot
        val b = onNodeWithTag("item-b").fetchSemanticsNode().boundsInRoot
        assertTrue(b.top >= a.bottom, "not stacked: $a, $b")
    }

    @Test
    fun primaryActionsFitAt200PercentTextOnAPhone() = shelfTest(
        state = baseState.copy(undo = UndoOffer("op-1", OperationType.Remove, ItemId("a")), nudge = ShelfNudge(ReminderLevel.Daily, 3, ItemId("a"), "Title a", 5)),
        fontScale = 2f,
    ) { h ->
        assertFits("Add link")
        assertFits("Undo")
        assertFits("Search your shelf")
        assertFits("All · 3")
        assertFits("Read now")
        update(h) { copy(undo = null, addLink = AddLinkState()) }
        assertFits("Save to shelf")
        assertFits("Cancel")
        update(h) { copy(addLink = AddLinkState(clipboard = ClipboardSuggestion.Visible("https://x.example/y"))) }
        assertFits("Use")
    }
}
