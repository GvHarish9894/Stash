package com.techgv.stash.feature.search

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.toast_link_copied
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Runs on the iOS simulator and as an Android device test, in English.

private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))

private fun result(id: String, title: String, lifecycle: ItemLifecycle = ItemLifecycle.Unread, titleHighlights: List<IntRange> = emptyList()) =
    SearchResultUi(
        savedItem(id, lifecycle, title = title, siteName = "medium.com").toCardUi(FIXTURE_NOW, TimeZone.UTC),
        titleHighlights, emptyList(),
    )

private fun results(total: Int, archived: Int?, filter: SearchFilter = SearchFilter.All) = SearchResults(
    "compose", total,
    listOf(SearchChipUi(SearchFilter.All, total), SearchChipUi(SearchFilter.Source("medium.com"), 2)) +
        listOfNotNull(archived?.let { SearchChipUi(SearchFilter.Archived, it) }),
    filter, archived,
)

private fun hasLiveRegion(mode: LiveRegionMode) = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)

@OptIn(ExperimentalTestApi::class)
class SearchScreenUiTest {
    private class Host {
        val intents = mutableListOf<SearchIntent>()
        val back = DirectNavigationEventInput()
    }

    private fun screenTest(
        state: SearchState,
        query: String = "compose",
        shelf: List<SearchResultUi> = emptyList(),
        archive: List<SearchResultUi> = emptyList(),
        fontScale: Float = 1f,
        undoTimeout: () -> Duration = { 10.seconds },
        // Off for the undo countdown, which keeps asking for frames, so waitForIdle would run it out.
        autoAdvance: Boolean = true,
        height: Dp? = null,
        reduceMotion: Boolean = true,
        block: ComposeUiTest.(Host) -> Unit,
    ) = runComposeUiTest {
        val host = Host()
        mainClock.autoAdvance = autoAdvance
        setContent {
            Box(if (height == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(height)) {
                Screen(state, query, shelf, archive, fontScale, undoTimeout, host, reduceMotion)
            }
        }
        if (autoAdvance) waitForIdle() else mainClock.advanceTimeByFrame()
        block(host)
    }

    @Composable
    private fun Screen(
        state: SearchState, query: String, shelf: List<SearchResultUi>, archive: List<SearchResultUi>, fontScale: Float,
        undoTimeout: () -> Duration, host: Host, reduceMotion: Boolean,
    ) {
        val density = LocalDensity.current
        val owner = rememberNavigationEventDispatcherOwner(parent = null)
        remember(owner) { owner.navigationEventDispatcher.addInput(host.back) }
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = reduceMotion, systemReduceMotion = false) {
                val shelfItems = remember(shelf) { flowOf(PagingData.from(shelf, complete)) }.collectAsLazyPagingItems()
                val archiveItems = remember(archive) { flowOf(PagingData.from(archive, complete)) }.collectAsLazyPagingItems()
                SearchScreen(state, query, shelfItems, archiveItems, WidthClass.Compact, undoTimeout, { host.intents += it })
            }
        }
    }

    private fun ComposeUiTest.top(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    // ---- Milestone 4 wiring (T4.4) ----------------------------------------------------------------------------

    @Test
    fun aShortWindowWithTheKeyboardUpStillReachesTheResults() = screenTest(
        // Phone landscape with the keyboard up leaves about this much: the field row, the chips and the summary alone
        // are taller.
        SearchState(results = results(2, archived = null)), shelf = listOf(result("a", "Compose a"), result("b", "Compose b")), height = 150.dp,
    ) {
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag("item-b"))
        onNodeWithTag("item-b").assertIsDisplayed()
    }

    @Test
    fun theItemSheetHidesTheScreenUnderneath() = screenTest(SearchState(menu = result("a", "Compose a").card), shelf = listOf(result("a", "Compose a"))) {
        onNodeWithContentDescription("Back to shelf").assertDoesNotExist()
        onNodeWithTag("item-a").assertDoesNotExist()
    }

    @Test
    fun theResultUndoBroughtBackFliesIn() = screenTest(
        SearchState(results = results(1, archived = null), flyBackItemId = "a"), shelf = listOf(result("a", "Compose a")),
        autoAdvance = false, reduceMotion = false,
    ) {
        val x = { onNodeWithTag("item-a").fetchSemanticsNode().positionInRoot.x / density.density }
        assertTrue(x() < -40f, "starts toward the start edge: ${x()}")
        mainClock.advanceTimeBy(700)
        assertEquals(20f, x(), 1f)
    }

    @Test
    fun beforeTypingShowsRecentsAndHelpAndFocusesTheField() = screenTest(SearchState(recents = listOf("compose", "room")), query = "") { host ->
        onNodeWithContentDescription("Search saved items").assertIsFocused()
        onNodeWithText("RECENT SEARCHES").assert(isHeading())
        onNodeWithText("Search looks through titles", substring = true).assertIsDisplayed()
        onNodeWithText("room").performClick()
        assertEquals(listOf<SearchIntent>(SearchIntent.QueryChanged("room")), host.intents)
    }

    @Test
    fun theKeyboardSearchKeyLeavesTheField() = screenTest(SearchState(results = results(1, archived = null)), shelf = listOf(result("a", "Compose a"))) {
        val field = onNodeWithContentDescription("Search saved items")
        field.assertIsFocused()
        field.performImeAction()
        field.assertIsNotFocused()
    }

    @Test
    fun noRecentsShowsOnlyTheHelpLine() = screenTest(SearchState(), query = "") {
        onNodeWithText("RECENT SEARCHES").assertDoesNotExist()
        onNodeWithText("Search looks through titles", substring = true).assertIsDisplayed()
    }

    @Test
    fun chipsAndSummary() = screenTest(
        SearchState(results = results(3, archived = 1)),
        shelf = listOf(result("a", "Compose a"), result("b", "Compose b")),
        archive = listOf(result("c", "Compose c", ItemLifecycle.Archived)),
    ) { host ->
        onNodeWithText("All · 3").assertIsSelected()
        onNodeWithText("Archived · 1").assertIsNotSelected()
        onNodeWithText("3 results for “compose” · 1 archived").assert(hasLiveRegion(LiveRegionMode.Polite))
        onNodeWithText("medium.com · 2").performClick()
        assertEquals(listOf<SearchIntent>(SearchIntent.SelectFilter(SearchFilter.Source("medium.com"))), host.intents)
    }

    @Test
    fun summaryForEachFilter() {
        screenTest(SearchState(results = results(3, archived = 1, SearchFilter.Archived)), archive = listOf(result("c", "Compose c", ItemLifecycle.Archived))) {
            onNodeWithText("1 result for “compose” in your archive").assertIsDisplayed()
            onNodeWithText("Archived · 1").assertIsSelected()
        }
        screenTest(SearchState(results = results(3, archived = 1, SearchFilter.Source("medium.com"))), shelf = listOf(result("a", "Compose a"))) {
            onNodeWithText("2 results for “compose” in medium.com").assertIsDisplayed()
        }
        screenTest(SearchState(results = results(1, archived = null)), shelf = listOf(result("a", "Compose a"))) {
            onNodeWithText("1 result for “compose”").assertIsDisplayed()
        }
    }

    @Test
    fun shelfHitsComeBeforeTheArchiveSection() = screenTest(
        SearchState(results = results(3, archived = 1)),
        shelf = listOf(result("a", "Compose a"), result("b", "Compose b")),
        archive = listOf(result("c", "Compose c", ItemLifecycle.Archived)),
    ) {
        onNodeWithText("IN YOUR ARCHIVE").assert(isHeading())
        val header = onNodeWithText("IN YOUR ARCHIVE").fetchSemanticsNode().boundsInRoot.top
        assertTrue(top("item-a") < top("item-b"))
        assertTrue(top("item-b") < header)
        assertTrue(header < top("item-c"))
    }

    @Test
    fun onlyArchivedHitsAreFoundInTheArchive() = screenTest(
        SearchState(results = results(2, archived = null)),
        archive = listOf(result("c", "Compose c", ItemLifecycle.Archived), result("d", "Compose d", ItemLifecycle.Archived)),
    ) {
        onNodeWithText("FOUND IN YOUR ARCHIVE").assertIsDisplayed()
        onNodeWithText("2 results for “compose” · 2 archived").assertIsDisplayed()
    }

    @Test
    fun noMatches() = screenTest(SearchState(results = results(0, archived = null))) {
        onNodeWithText("No matches on your shelf").assertIsDisplayed()
        onNodeWithText("Try a shorter word", substring = true).assertIsDisplayed()
        onNodeWithText("All · 0").assertDoesNotExist()
    }

    @Test
    fun resultsOpenAndOfferActionsWithoutGestures() = screenTest(
        SearchState(results = results(1, archived = null)),
        shelf = listOf(result("a", "Compose a")),
    ) { host ->
        onNodeWithTag("item-a").performClick()
        val actions = onNodeWithTag("item-a").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Mark as read", "Archive", "Share", "Remove from shelf", "More options"), actions.map { it.label })
        runOnIdle { actions[3].action() }
        runOnIdle { actions[4].action() }
        onNodeWithTag("item-a").performTouchInput { longClick(durationMillis = 600) }
        waitForIdle()
        val id = ItemId("a")
        assertEquals(
            listOf(SearchIntent.Open(id), SearchIntent.CardAction(id, SwipeOutcome.Remove), SearchIntent.ShowMenu(id), SearchIntent.ShowMenu(id)),
            host.intents,
        )
    }

    @Test
    fun archivedHitsOfferTheArchivedActions() = screenTest(
        SearchState(results = results(1, archived = null)),
        archive = listOf(result("c", "Compose c", ItemLifecycle.Archived)),
    ) {
        val actions = onNodeWithTag("item-c").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Restore to shelf", "Share", "Delete forever", "More options"), actions.map { it.label })
    }

    @Test
    fun theItemSheetOpensWithItsTitleAndActs() = screenTest(
        SearchState(results = results(1, archived = null), menu = result("a", "Compose a").card),
        shelf = listOf(result("a", "Compose a")),
    ) { host ->
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Item options")).assertExists()
        onNodeWithText("Copy link").performClick()
        onNodeWithContentDescription("Close options").performClick()
        assertEquals(listOf(SearchIntent.MenuAction(ItemMenuAction.CopyLink), SearchIntent.DismissMenu), host.intents)
    }

    @Test
    fun backButtonAndSystemBackLeave() = screenTest(SearchState()) { host ->
        onNodeWithContentDescription("Back to shelf").performClick()
        runOnIdle { host.back.backCompleted() }
        assertEquals(listOf<SearchIntent>(SearchIntent.Back, SearchIntent.Back), host.intents)
    }

    @Test
    fun systemBackClosesTheSheetFirst() = screenTest(SearchState(menu = result("a", "Compose a").card)) { host ->
        runOnIdle { host.back.backCompleted() }
        assertEquals(listOf<SearchIntent>(SearchIntent.DismissMenu), host.intents)
    }

    @Test
    fun undoBarUndoesAndTimesOut() = screenTest(
        SearchState(undo = PendingUndo("op-1", OperationType.Archive, ItemId("a"))),
        undoTimeout = { 10.seconds },
        autoAdvance = false,
    ) { host ->
        onNodeWithText("Moved to Archive").assertIsDisplayed()
        onNodeWithText("Undo").performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(listOf<SearchIntent>(SearchIntent.Undo), host.intents)
        mainClock.advanceTimeBy(9_000)
        assertEquals(listOf<SearchIntent>(SearchIntent.Undo), host.intents)
        mainClock.advanceTimeBy(1_500)
        assertEquals(listOf(SearchIntent.Undo, SearchIntent.UndoClosed("op-1")), host.intents)
    }

    @Test
    fun theUndoTimeoutIsAskedWhenTheBarAppears() {
        var asked = 0
        screenTest(SearchState(undo = PendingUndo("op-1", OperationType.Remove, ItemId("a"))), undoTimeout = { asked++; Duration.INFINITE }) {
            onNodeWithText("Removed from your shelf").assertIsDisplayed()
            mainClock.advanceTimeBy(30_000)
            waitForIdle()
            onNodeWithText("Removed from your shelf").assertIsDisplayed()
        }
        assertEquals(1, asked)
    }

    @Test
    fun toastsAreShown() = screenTest(SearchState(toast = text(Res.string.toast_link_copied))) {
        onNodeWithText("Link copied").assertIsDisplayed()
    }

    @Test
    fun primaryActionsStayVisibleAt200Percent() = screenTest(
        SearchState(results = results(3, archived = 1)),
        shelf = listOf(result("a", "Compose a with a long title that wraps over several lines at large text sizes")),
        archive = listOf(result("c", "Compose c", ItemLifecycle.Archived)),
        fontScale = 2f,
    ) {
        onNodeWithContentDescription("Back to shelf").assertIsDisplayed()
        onNodeWithContentDescription("Search saved items").assertIsDisplayed()
        onNodeWithContentDescription("Clear search").assertIsDisplayed()
        onNodeWithText("All · 3").assertIsDisplayed()
        onNodeWithTag("item-a").assertIsDisplayed()
        // The full title wraps instead of being cut.
        val layout = mutableListOf<TextLayoutResult>()
        onNodeWithText("Compose a with a long title", substring = true, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        assertTrue(layout.single().lineCount > 1 && !layout.single().hasVisualOverflow)
    }
}
