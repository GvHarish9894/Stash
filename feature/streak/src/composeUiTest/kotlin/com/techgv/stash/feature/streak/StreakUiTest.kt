package com.techgv.stash.feature.streak

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.DayMark
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.domain.WeekDay
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Runs on the iOS simulator (and as an Android device test), in English.

/** Thursday 12 Nov 2026 is today; Saturday was a rest day, Monday was missed. */
private val week = (6 downTo 0).map { back ->
    val mark = when (back) {
        5 -> DayMark.Rest
        3 -> DayMark.NotRead
        0 -> DayMark.NotRead
        else -> DayMark.Read
    }
    WeekDay(LocalDate(2026, 11, 12).minus(back, DateTimeUnit.DAY), isToday = back == 0, mark = mark)
}

private val notYet = StreakSheetState(StreakState(3, false, StreakStatus.Unlit, week), hasUnread = true, ReminderTime.Morning, streakSaver = true)

@OptIn(ExperimentalTestApi::class)
class StreakUiTest {
    private fun ComposeUiTest.themed(fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) = setContent {
        StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
        }
    }

    private fun ComposeUiTest.sheet(state: StreakSheetState, fontScale: Float = 1f, notificationsPermitted: Boolean = true): MutableList<String> {
        val events = mutableListOf<String>()
        themed(fontScale) { StreakSheetScreen(true, state, { events += it.toString() }, { events += "dismiss" }, notificationsPermitted) }
        return events
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

    @Test
    fun sheetShowsTodayTheWeekAndTheRule() = runComposeUiTest {
        sheet(notYet)
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "3-day reading streak")).assertExists()
        onNodeWithText("3-day reading streak").assert(isHeading())
        onNodeWithText("Read one thing before midnight to make it 4.").assertExists()
        onNodeWithContentDescription("Thursday, today, not read yet").assertExists()
        onNodeWithContentDescription("Saturday, rest day").assertExists()
        onNodeWithContentDescription("Monday, not read").assertExists()
        onNodeWithContentDescription("Wednesday, read").assertExists()
        onNodeWithText("Open or mark one item as read each day to keep it going. Daily reminder at 9 AM, streak saver on.", substring = true)
            .assertExists()
    }

    @Test
    fun withoutNotificationPermissionTheRuleSaysRemindersAreOff() = runComposeUiTest {
        sheet(notYet, notificationsPermitted = false)
        onNodeWithText("Open or mark one item as read each day to keep it going. Reminders are off.").assertExists()
    }

    @Test
    fun buttonsAskForRemindersAndSomethingShort() = runComposeUiTest {
        val events = sheet(notYet)
        onNodeWithText("Reminders").performClick()
        onNodeWithText("Read something short").performClick()
        onNodeWithContentDescription("Close streak").performClick()
        assertEquals(listOf("OpenReminders", "ReadShort", "dismiss"), events)
    }

    @Test
    fun doneTodayAndNothingUnreadOfferClose() {
        runComposeUiTest {
            val events = sheet(notYet.copy(streak = notYet.streak!!.copy(readToday = true, status = StreakStatus.Lit)))
            onNodeWithText("Today’s read is done. See you tomorrow.").assertExists()
            onNodeWithText("Close").performClick()
            assertEquals(listOf("dismiss"), events)
        }
        runComposeUiTest {
            sheet(notYet.copy(hasUnread = false, reminderTime = null))
            onNodeWithText("Nothing unread, so your streak is safe today and no reminders will come.").assertExists()
            onNodeWithText("Reminders are off.", substring = true).assertExists()
            onNodeWithText("Read something short").assertDoesNotExist()
        }
    }

    // Only on the simulator's wide window: at a phone's 390 dp width the sheet is taller than the window, does not
    // scroll, and squeezes these buttons until their text is cut off (design-system BottomSheet, review finding M2).
    @Test
    fun sheetButtonsFitAt200PercentTextOnAWideWindow() = runComposeUiTest {
        sheet(notYet, fontScale = 2f)
        assertFits("Reminders")
        assertFits("Read something short")
    }

    @Test
    fun celebrationShowsTheMilestoneAndCloses() = runComposeUiTest {
        var closed = 0
        val celebration = mutableStateOf<Celebration?>(Celebration(6, 7, 7))
        themed { CelebrationScreen(celebration.value, week, { closed++ }) }
        val announcement = "Streak extended to 7 days. A full week of reading. That is a habit taking root."
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, announcement)).assertExists()
        onNodeWithText("Keep reading").performClick()
        assertEquals(1, closed)
        celebration.value = null
        waitForIdle()
        onNodeWithText("Keep reading").assertDoesNotExist()
    }

    @Test
    fun aRegularDayGetsARegularMessage() = runComposeUiTest {
        themed { CelebrationScreen(Celebration(1, 2, null), week) {} }
        onNodeWithText("Small reads, every day. Your future self is taking notes.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noCelebrationShowsNothing() = runComposeUiTest {
        themed { CelebrationScreen(null, week) {} }
        onNodeWithText("Keep reading").assertDoesNotExist()
    }
}
