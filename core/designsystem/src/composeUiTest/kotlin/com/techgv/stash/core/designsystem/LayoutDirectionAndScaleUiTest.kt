package com.techgv.stash.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.component.AddLinkSheet
import com.techgv.stash.core.designsystem.component.CelebrationDialog
import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.designsystem.component.FilterChip
import com.techgv.stash.core.designsystem.component.GestureTip
import com.techgv.stash.core.designsystem.component.ItemActionSheet
import com.techgv.stash.core.designsystem.component.NudgeBanner
import com.techgv.stash.core.designsystem.component.ReaderTopBar
import com.techgv.stash.core.designsystem.component.SavedItemCard
import com.techgv.stash.core.designsystem.component.StreakChip
import com.techgv.stash.core.designsystem.component.StreakSheet
import com.techgv.stash.core.designsystem.component.StreakSheetStatus
import com.techgv.stash.core.designsystem.component.itemMenuActions
import com.techgv.stash.core.designsystem.presentation.NOW
import com.techgv.stash.core.designsystem.presentation.gestureTipText
import com.techgv.stash.core.designsystem.presentation.resolve
import com.techgv.stash.core.designsystem.presentation.savedItem
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.DayMark
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.domain.WeekDay
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

// Right-to-left swipes and their wording, 200 % text, and the round-2 accessibility fixes. iOS simulator and Android device.

@Composable
private fun Themed(
    direction: LayoutDirection = LayoutDirection.Ltr,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalLayoutDirection provides direction, LocalDensity provides Density(density.density, fontScale)) {
        StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false, content = content)
    }
}

private val card = savedItem().toCardUi(NOW, TimeZone.UTC)

private val week = (6 downTo 0).map { back ->
    WeekDay(LocalDate(2026, 11, 12 - back), isToday = back == 0, mark = if (back == 0) DayMark.NotRead else DayMark.Read)
}

@OptIn(ExperimentalTestApi::class)
class LayoutDirectionAndScaleUiTest {

    private fun swipeTest(
        start: SwipeOutcome,
        end: SwipeOutcome,
        fixedLeftRight: Boolean,
        block: ComposeUiTest.(events: MutableList<String>) -> Unit,
    ) = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            Themed(LayoutDirection.Rtl) {
                Box(Modifier.width(600.dp)) {
                    SavedItemCard(
                        card, CardLayout.Comfortable, start, end,
                        onOpen = {}, onAction = { events += "$it" }, onMore = {}, fixedLeftRight = fixedLeftRight,
                    )
                }
            }
        }
        block(events)
    }

    private fun ComposeUiTest.swipe(right: Boolean) {
        onNodeWithTag("item-a").performTouchInput {
            if (right) swipeRight(startX = left + 20f, endX = left + 20f + 160.dp.toPx(), durationMillis = 300)
            else swipeLeft(startX = this.right - 20f, endX = this.right - 20f - 160.dp.toPx(), durationMillis = 300)
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun shelfSwipesFollowReadingDirectionInRtl() = swipeTest(SwipeOutcome.Remove, SwipeOutcome.MarkRead, fixedLeftRight = false) { events ->
        // In RTL the start edge is on the right: a right swipe runs the start action.
        swipe(right = true)
        swipe(right = false)
        assertEquals(listOf("Remove", "MarkRead"), events)
    }

    @Test
    fun archiveSwipesStayRightRestoresLeftDeletesInRtl() = swipeTest(SwipeOutcome.Delete, SwipeOutcome.Restore, fixedLeftRight = true) { events ->
        swipe(right = true)
        swipe(right = false)
        assertEquals(listOf("Restore", "Delete"), events)
    }

    @Test
    fun gestureTipWordsTheRtlDirections() = runComposeUiTest {
        setContent {
            Themed(LayoutDirection.Rtl) {
                GestureTip(gestureTipText(SwipeAction.Remove, SwipeAction.MarkRead, LocalLayoutDirection.current).resolve(), {})
            }
        }
        // Start (remove) is the right swipe in RTL, end (mark read) the left one — matching the shelf card above.
        onNodeWithText("Swipe left to mark read, right to remove. Long-press for more.").assertExists()
    }

    /** No text that may wrap is cut off by a fixed height: no unlimited-line text overflows its height. */
    private fun ComposeUiTest.assertNoClippedText() {
        val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        val clipped = nodes.mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            // Fixed heights cut text vertically; ellipsized single-line titles (maxLines set) are intended.
            results.firstOrNull()?.takeIf { it.layoutInput.maxLines == Int.MAX_VALUE && it.didOverflowHeight }
                ?.let { "${it.layoutInput.text.text} ${it.size} max ${it.layoutInput.constraints.maxHeight}" }
        }
        assertEquals(emptyList(), clipped, "text clipped at 200 %")
    }

    private fun scaled(content: @Composable () -> Unit) = runComposeUiTest {
        setContent { Themed(fontScale = 2f) { Box(Modifier.size(390.dp, 844.dp)) { content() } } }
        waitForIdle()
        assertNoClippedText()
    }

    @Test
    fun streakSheetFitsAt200Percent() = scaled {
        StreakSheet(
            true, 12, StreakStatus.Unlit, StreakSheetStatus.NotYet, week, ReminderTime.Morning, streakSaver = true,
            onReminders = {}, onReadShort = {}, onDismiss = {},
        )
    }

    @Test
    fun addLinkSheetFitsAt200Percent() = scaled {
        AddLinkSheet(true, "", {}, ClipboardSuggestion.Visible("github.com/compose/samples"), "Paste or type a link first.", {}, {}, {})
    }

    @Test
    fun itemSheetFitsAt200Percent() = scaled { ItemActionSheet(card, itemMenuActions(card), {}, {}) }

    @Test
    fun readerTopBarFitsAt200Percent() = scaled {
        ReaderTopBar("Designing for slow reading in a fast feed", "medium.com", false, { 0.4f }, {}, {}, {})
    }

    @Test
    fun readerTopBarTargetsAreAtLeast48dp() = runComposeUiTest {
        setContent { Themed { ReaderTopBar("Title", "medium.com", false, { 0f }, {}, {}, {}) } }
        for (label in listOf("Close and return to Stash", "Reader mode")) {
            val node = onNodeWithContentDescription(label).fetchSemanticsNode()
            val size = with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() }
            assertTrue(size >= 48.dp, "$label touch height $size")
        }
    }

    @Test
    fun filterChipCanBeARadioButton() = runComposeUiTest {
        setContent { Themed { FilterChip("Archive", true, {}, role = Role.RadioButton) } }
        onNodeWithText("Archive").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun streakChipReadsItsCountOnce() = runComposeUiTest {
        setContent { Themed { StreakChip(12, StreakStatus.Unlit, {}) } }
        onNodeWithContentDescription("12-day reading streak, not read today").assertExists()
        onAllNodesWithText("12", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun nudgeAtStreakZeroDoesNotMentionAStreak() = runComposeUiTest {
        setContent { Themed { NudgeBanner(ReminderLevel.Daily, 0, "Room FTS", 6, {}) } }
        onNodeWithText("Your shelf is waiting").assertExists()
        onNodeWithText("“Room FTS” takes 6 min.").assertExists()
        onAllNodesWithText("0-day", substring = true).assertCountEquals(0)
    }

    @Test
    fun celebrationWeekDaysAreDescribed() = runComposeUiTest {
        setContent { Themed { CelebrationDialog(true, 12, 13, "Today’s read is in the books.", week, {}) } }
        onNodeWithContentDescription("Thursday, today, not read yet").assertExists()
        onNodeWithContentDescription("Monday, read").assertExists()
    }

    @Test
    fun archivedCardStateIsNotLostByRtl() = runComposeUiTest {
        val archived = savedItem(age = 20.days, readAt = NOW - 15.days, archivedAt = NOW - 10.days).toCardUi(NOW, TimeZone.UTC)
        setContent {
            Themed(LayoutDirection.Rtl) {
                SavedItemCard(archived, CardLayout.Comfortable, SwipeOutcome.Delete, SwipeOutcome.Restore, {}, {}, {}, fixedLeftRight = true)
            }
        }
        val actions = onNodeWithTag("item-a").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Restore to shelf", "Share", "Delete forever", "More options"), actions)
    }
}
