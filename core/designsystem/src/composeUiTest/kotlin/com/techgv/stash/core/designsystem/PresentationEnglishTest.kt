package com.techgv.stash.core.designsystem

import com.techgv.stash.core.designsystem.presentation.NOW
import com.techgv.stash.core.designsystem.presentation.RelativeTime
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.designsystem.presentation.cardAccessibilityLabel
import com.techgv.stash.core.designsystem.presentation.gestureTipText
import com.techgv.stash.core.designsystem.presentation.groupLabelText
import com.techgv.stash.core.designsystem.presentation.label
import com.techgv.stash.core.designsystem.presentation.load
import com.techgv.stash.core.designsystem.presentation.long
import com.techgv.stash.core.designsystem.presentation.plural
import com.techgv.stash.core.designsystem.presentation.savedItem
import com.techgv.stash.core.designsystem.presentation.short
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.common_item_count
import com.techgv.stash.core.resources.common_list_separator
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** The mapped text in English, as the iOS simulator and an English Android device resolve it. */
class PresentationEnglishTest {
    @Test
    fun cardTextAndLabel() = runTest {
        val card = savedItem().toCardUi(NOW, TimeZone.UTC)
        assertEquals("2h ago", card.time!!.load())
        assertEquals("7 min read", card.length!!.load())
        assertEquals(
            "Unread. Designing for slow reading in a fast feed. medium.com, 2 hours ago, 7 min read.",
            cardAccessibilityLabel(card, text(Res.string.common_list_separator)).load(),
        )
    }

    @Test
    fun pluralsPickTheirForm() = runTest {
        assertEquals(listOf("1 minute ago", "5 minutes ago"), listOf(1, 5).map { RelativeTime.Minutes(it).long().load() })
        assertEquals(listOf("1 hour ago", "2 hours ago"), listOf(1, 2).map { RelativeTime.Hours(it).long().load() })
        assertEquals(listOf("1 day ago", "6 days ago"), listOf(1, 6).map { RelativeTime.Days(it).long().load() })
        assertEquals(listOf("1 item", "3 items"), listOf(1, 3).map { plural(Res.plurals.common_item_count, it).load() })
        assertEquals(listOf("Off", "7 days", "30 days", "90 days"), AutoArchivePeriod.entries.map { it.label.load() })
    }

    @Test
    fun datesAndHeaders() = runTest {
        assertEquals("just now", RelativeTime.JustNow.short().load())
        assertEquals("Nov 2", RelativeTime.OnDate(LocalDate(2026, 11, 2), false).short().load())
        assertEquals("Nov 2, 2025", RelativeTime.OnDate(LocalDate(2025, 11, 2), true).short().load())
        assertEquals("March", groupLabelText(GroupLabel.Month(2026, Month.MARCH), 2026).load())
        assertEquals("March 2025", groupLabelText(GroupLabel.Month(2025, Month.MARCH), 2026).load())
        assertEquals("Today", groupLabelText(GroupLabel.Today, 2026).load())
        assertEquals("Medium", UiText.Plain("Medium").load())
    }

    @Test
    fun gestureTip() = runTest {
        assertEquals("Swipe left to remove, right to mark read. Long-press for more.", gestureTipText(SwipeAction.Remove, SwipeAction.MarkRead, LayoutDirection.Ltr).load())
        assertEquals("Long-press for more.", gestureTipText(SwipeAction.Nothing, SwipeAction.Nothing, LayoutDirection.Ltr).load())
    }
}
