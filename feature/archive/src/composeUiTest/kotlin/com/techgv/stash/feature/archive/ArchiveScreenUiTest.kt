package com.techgv.stash.feature.archive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

// Runs on the iOS simulator and as an Android device test, in English.

private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))

private fun card(id: String, title: String) = ArchiveRowUi.Card(
    savedItem(id, ItemLifecycle.Archived, title = title, addedAt = FIXTURE_NOW - 5.days, archivedAt = FIXTURE_NOW - 2.days)
        .toCardUi(FIXTURE_NOW, TimeZone.UTC),
    startAction = SwipeOutcome.Delete, endAction = SwipeOutcome.Restore, titleHighlights = emptyList(),
)

private val twoGroups = listOf(ArchiveRowUi.Header(GroupLabel.ThisMonth), card("a", "Room guide"), ArchiveRowUi.Header(GroupLabel.Earlier), card("b", "Typography"))

@OptIn(ExperimentalTestApi::class)
class ArchiveScreenUiTest {
    private class Host {
        val intents = mutableListOf<ArchiveIntent>()
        val back = DirectNavigationEventInput()
    }

    private fun screenTest(
        state: ArchiveState = ArchiveState(count = 2),
        rows: List<ArchiveRowUi> = twoGroups,
        query: String = "",
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        autoAdvance: Boolean = true,
        widthClass: WidthClass = WidthClass.Compact,
        block: ComposeUiTest.(Host) -> Unit,
    ) = runComposeUiTest {
        val host = Host()
        mainClock.autoAdvance = autoAdvance
        setContent { Screen(state, rows, query, direction, fontScale, host, widthClass) }
        if (autoAdvance) waitForIdle() else mainClock.advanceTimeByFrame()
        block(host)
    }

    @Composable
    private fun Screen(
        state: ArchiveState,
        rows: List<ArchiveRowUi>,
        query: String,
        direction: LayoutDirection,
        fontScale: Float,
        host: Host,
        widthClass: WidthClass,
    ) {
        val density = LocalDensity.current
        val owner = rememberNavigationEventDispatcherOwner(parent = null)
        remember(owner) { owner.navigationEventDispatcher.addInput(host.back) }
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            LocalLayoutDirection provides direction,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false) {
                val items = remember(rows) { flowOf(PagingData.from(rows, complete)) }.collectAsLazyPagingItems()
                Box(Modifier.width(if (widthClass == WidthClass.Compact) 400.dp else 700.dp)) {
                    ArchiveScreen(state, query, items, widthClass, { 10.seconds }, { host.intents += it })
                }
            }
        }
    }

    // ---- Milestone 4 wiring (T4.4) ----------------------------------------------------------------------------

    @Test
    fun theTearingCardReportsItIsTorn() = screenTest(ArchiveState(count = 2, tearingItemId = "a")) { host ->
        // Reduce motion: the tear is instant and reports straight away.
        assertEquals(listOf<ArchiveIntent>(ArchiveIntent.Torn(ItemId("a"))), host.intents)
    }

    @Test
    fun theItemSheetHidesTheScreenUnderneath() = screenTest(ArchiveState(count = 2, menu = (twoGroups[1] as ArchiveRowUi.Card).card)) {
        onNodeWithContentDescription("Back to shelf").assertDoesNotExist()
        onNodeWithTag("item-a").assertDoesNotExist()
    }

    @Test
    fun mediumWidthShowsLargeImageCardsInTwoColumns() {
        val oneGroup = listOf(ArchiveRowUi.Header(GroupLabel.ThisMonth), card("a", "Room guide"), card("b", "Typography"))
        screenTest(ArchiveState(count = 2, layout = CardLayout.LargeImages), oneGroup, widthClass = WidthClass.Medium) {
            val a = onNodeWithTag("item-a").fetchSemanticsNode().boundsInRoot
            val b = onNodeWithTag("item-b").fetchSemanticsNode().boundsInRoot
            assertTrue(abs(a.top - b.top) < 1f && b.left > a.right, "not side by side: $a, $b")
        }
        screenTest(ArchiveState(count = 2, layout = CardLayout.LargeImages), oneGroup) {
            val a = onNodeWithTag("item-a").fetchSemanticsNode().boundsInRoot
            val b = onNodeWithTag("item-b").fetchSemanticsNode().boundsInRoot
            assertTrue(b.top >= a.bottom, "not stacked at compact width: $a, $b")
        }
    }

    @Test
    fun headerCountAndFocusOnBack() = screenTest { host ->
        onNodeWithText("Archive").assert(isHeading())
        onNodeWithText("2 items").assertIsDisplayed()
        onNodeWithContentDescription("Back to shelf").assertIsFocused()
        onNodeWithContentDescription("Back to shelf").performClick()
        assertEquals(listOf<ArchiveIntent>(ArchiveIntent.Back), host.intents)
    }

    @Test
    fun ruleLineFollowsTheAutoArchiveSetting() {
        screenTest {
            onNodeWithText("Read items move here after 30 days. Swipe right to restore, left to delete.").assertIsDisplayed()
        }
        screenTest(ArchiveState(count = 2, autoArchive = AutoArchivePeriod.Off)) {
            onNodeWithText("Auto-archive is off. Swipe right to restore an item, left to delete it.").assertIsDisplayed()
        }
    }

    @Test
    fun groupsAreHeadings() = screenTest {
        onNodeWithText("THIS MONTH").assert(isHeading())
        onNodeWithText("EARLIER").assert(isHeading())
    }

    @Test
    fun swipeRightRestoresAndLeftDeletes() = screenTest { host ->
        onNodeWithTag("item-a").performTouchInput { swipeRight(startX = 20f, endX = 20f + 160.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        onNodeWithTag("item-b").performTouchInput { swipeLeft(startX = right - 20f, endX = right - 20f - 160.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        assertEquals(
            listOf<ArchiveIntent>(ArchiveIntent.CardAction(ItemId("a"), SwipeOutcome.Restore), ArchiveIntent.CardAction(ItemId("b"), SwipeOutcome.Delete)),
            host.intents,
        )
    }

    @Test
    fun rightToLeftStillRestoresRightAndDeletesLeft() = screenTest(direction = LayoutDirection.Rtl) { host ->
        onNodeWithText("Read items move here after 30 days. Swipe right to restore, left to delete.").assertIsDisplayed()
        onNodeWithTag("item-a").performTouchInput { swipeRight(startX = 20f, endX = 20f + 160.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        onNodeWithTag("item-b").performTouchInput { swipeLeft(startX = right - 20f, endX = right - 20f - 160.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        assertEquals(
            listOf<ArchiveIntent>(ArchiveIntent.CardAction(ItemId("a"), SwipeOutcome.Restore), ArchiveIntent.CardAction(ItemId("b"), SwipeOutcome.Delete)),
            host.intents,
        )
    }

    @Test
    fun everyCardActionHasAnAccessibleRoute() = screenTest { host ->
        onNodeWithTag("item-a").performClick()
        val actions = onNodeWithTag("item-a").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Restore to shelf", "Share", "Delete forever", "More options"), actions.map { it.label })
        actions.forEach { action -> runOnIdle { action.action() } }
        onNodeWithTag("item-a").performTouchInput { longClick(durationMillis = 600) }
        waitForIdle()
        val id = ItemId("a")
        assertEquals(
            listOf(
                ArchiveIntent.Open(id),
                ArchiveIntent.CardAction(id, SwipeOutcome.Restore),
                ArchiveIntent.CardAction(id, SwipeOutcome.Share),
                ArchiveIntent.CardAction(id, SwipeOutcome.Delete),
                ArchiveIntent.ShowMenu(id),
                ArchiveIntent.ShowMenu(id),
            ),
            host.intents,
        )
    }

    @Test
    fun theSheetOffersTheArchivedActions() = screenTest(ArchiveState(count = 2, menu = card("a", "Room guide").card)) { host ->
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Item options")).assertExists()
        for (label in listOf("Restore to shelf", "Open", "Share", "Copy link", "Delete forever")) onNodeWithText(label).assertIsDisplayed()
        onNodeWithText("Delete forever").performClick()
        runOnIdle { host.back.backCompleted() }
        assertEquals(listOf(ArchiveIntent.MenuAction(ItemMenuAction.Delete), ArchiveIntent.DismissMenu), host.intents)
    }

    @Test
    fun systemBackIsLeftToTheNavigationHostWithoutASheet() = screenTest { host ->
        runOnIdle { host.back.backCompleted() }
        assertEquals(emptyList<ArchiveIntent>(), host.intents)
    }

    @Test
    fun emptyArchiveExplainsTheRule() {
        screenTest(ArchiveState(count = 0), rows = emptyList()) {
            onNodeWithText("Nothing archived yet").assertIsDisplayed()
            onNodeWithText("Read items move here 30 days after you read them.").assertIsDisplayed()
        }
        screenTest(ArchiveState(count = 0, autoArchive = AutoArchivePeriod.Off), rows = emptyList()) {
            onNodeWithText("Nothing archived yet").assertIsDisplayed()
            onNodeWithText("Auto-archive is off. Long-press any item and choose Archive.").assertIsDisplayed()
        }
    }

    @Test
    fun aSearchWithNoMatchSaysSo() = screenTest(ArchiveState(count = 2), rows = emptyList(), query = "zzz") {
        onNodeWithText("Nothing matches").assertIsDisplayed()
        onNodeWithText("Try another word, or search by site name.").assertIsDisplayed()
    }

    @Test
    fun searchFieldAndClear() = screenTest(query = "room") { host ->
        onNodeWithContentDescription("Clear archive search").performClick()
        assertEquals(listOf<ArchiveIntent>(ArchiveIntent.QueryChanged("")), host.intents)
    }

    @Test
    fun theKeyboardSearchKeyLeavesTheField() = screenTest(query = "room") {
        val field = onNodeWithContentDescription("Search the archive")
        field.performSemanticsAction(SemanticsActions.RequestFocus)
        field.assertIsFocused()
        field.performImeAction()
        field.assertIsNotFocused()
    }

    @Test
    fun undoBarsForRestoreAndDelete() {
        screenTest(ArchiveState(count = 1, undo = PendingUndo("op-1", OperationType.Restore, ItemId("recent"))), autoAdvance = false) { host ->
            onNodeWithText("Restored to your shelf").assertIsDisplayed()
            onNodeWithText("Undo").performClick()
            mainClock.advanceTimeByFrame()
            assertEquals(listOf<ArchiveIntent>(ArchiveIntent.Undo), host.intents)
        }
        screenTest(ArchiveState(count = 1, undo = PendingUndo("op-2", OperationType.Delete, ItemId("recent"))), autoAdvance = false) { host ->
            onNodeWithText("Deleted from Archive").assertIsDisplayed()
            mainClock.advanceTimeBy(10_500)
            assertEquals(listOf<ArchiveIntent>(ArchiveIntent.UndoClosed("op-2")), host.intents)
        }
    }

    @Test
    fun primaryActionsStayVisibleAt200Percent() = screenTest(fontScale = 2f) {
        onNodeWithContentDescription("Back to shelf").assertIsDisplayed()
        onNodeWithText("Archive").assertIsDisplayed()
        onNodeWithContentDescription("Search the archive").assertIsDisplayed()
        onNodeWithTag("item-a").assertIsDisplayed()
    }
}
