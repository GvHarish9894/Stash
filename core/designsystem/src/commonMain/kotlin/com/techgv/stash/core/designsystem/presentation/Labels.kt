package com.techgv.stash.core.designsystem.presentation

import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.AppLanguage
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.model.WidgetSize
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.accent_forest
import com.techgv.stash.core.resources.accent_ink_blue
import com.techgv.stash.core.resources.accent_leather
import com.techgv.stash.core.resources.accent_oxblood
import com.techgv.stash.core.resources.app_icon_ink
import com.techgv.stash.core.resources.app_icon_leather
import com.techgv.stash.core.resources.app_icon_night
import com.techgv.stash.core.resources.app_icon_parchment
import com.techgv.stash.core.resources.auto_archive_days
import com.techgv.stash.core.resources.auto_archive_off
import com.techgv.stash.core.resources.celebration_message_1
import com.techgv.stash.core.resources.celebration_message_2
import com.techgv.stash.core.resources.celebration_message_3
import com.techgv.stash.core.resources.celebration_message_4
import com.techgv.stash.core.resources.celebration_message_5
import com.techgv.stash.core.resources.celebration_milestone_100
import com.techgv.stash.core.resources.celebration_milestone_14
import com.techgv.stash.core.resources.celebration_milestone_30
import com.techgv.stash.core.resources.celebration_milestone_50
import com.techgv.stash.core.resources.celebration_milestone_7
import com.techgv.stash.core.resources.chip_all
import com.techgv.stash.core.resources.chip_archived
import com.techgv.stash.core.resources.chip_unread
import com.techgv.stash.core.resources.group_earlier
import com.techgv.stash.core.resources.group_last_week
import com.techgv.stash.core.resources.group_month_year
import com.techgv.stash.core.resources.group_this_month
import com.techgv.stash.core.resources.group_this_week
import com.techgv.stash.core.resources.group_today
import com.techgv.stash.core.resources.group_yesterday
import com.techgv.stash.core.resources.grouping_day
import com.techgv.stash.core.resources.grouping_month
import com.techgv.stash.core.resources.grouping_none
import com.techgv.stash.core.resources.grouping_week
import com.techgv.stash.core.resources.language_english
import com.techgv.stash.core.resources.language_hindi
import com.techgv.stash.core.resources.language_spanish
import com.techgv.stash.core.resources.language_system
import com.techgv.stash.core.resources.language_tamil
import com.techgv.stash.core.resources.layout_comfortable
import com.techgv.stash.core.resources.layout_compact
import com.techgv.stash.core.resources.layout_large_images
import com.techgv.stash.core.resources.menu_archive
import com.techgv.stash.core.resources.menu_delete
import com.techgv.stash.core.resources.menu_mark_read
import com.techgv.stash.core.resources.menu_mark_unread
import com.techgv.stash.core.resources.menu_remove
import com.techgv.stash.core.resources.menu_restore
import com.techgv.stash.core.resources.menu_share
import com.techgv.stash.core.resources.month_april
import com.techgv.stash.core.resources.month_august
import com.techgv.stash.core.resources.month_december
import com.techgv.stash.core.resources.month_february
import com.techgv.stash.core.resources.month_january
import com.techgv.stash.core.resources.month_july
import com.techgv.stash.core.resources.month_june
import com.techgv.stash.core.resources.month_march
import com.techgv.stash.core.resources.month_may
import com.techgv.stash.core.resources.month_november
import com.techgv.stash.core.resources.month_october
import com.techgv.stash.core.resources.month_september
import com.techgv.stash.core.resources.month_short_april
import com.techgv.stash.core.resources.month_short_august
import com.techgv.stash.core.resources.month_short_december
import com.techgv.stash.core.resources.month_short_february
import com.techgv.stash.core.resources.month_short_january
import com.techgv.stash.core.resources.month_short_july
import com.techgv.stash.core.resources.month_short_june
import com.techgv.stash.core.resources.month_short_march
import com.techgv.stash.core.resources.month_short_may
import com.techgv.stash.core.resources.month_short_november
import com.techgv.stash.core.resources.month_short_october
import com.techgv.stash.core.resources.month_short_september
import com.techgv.stash.core.resources.open_in_app
import com.techgv.stash.core.resources.open_in_browser
import com.techgv.stash.core.resources.reminder_time_evening
import com.techgv.stash.core.resources.reminder_time_morning
import com.techgv.stash.core.resources.sort_by_source
import com.techgv.stash.core.resources.sort_newest
import com.techgv.stash.core.resources.sort_oldest
import com.techgv.stash.core.resources.swipe_action_archive
import com.techgv.stash.core.resources.swipe_action_mark_read
import com.techgv.stash.core.resources.swipe_action_nothing
import com.techgv.stash.core.resources.swipe_action_remove
import com.techgv.stash.core.resources.swipe_action_share
import com.techgv.stash.core.resources.swipe_archive
import com.techgv.stash.core.resources.swipe_delete
import com.techgv.stash.core.resources.swipe_read
import com.techgv.stash.core.resources.swipe_remove
import com.techgv.stash.core.resources.swipe_restore
import com.techgv.stash.core.resources.swipe_share
import com.techgv.stash.core.resources.swipe_unread
import com.techgv.stash.core.resources.text_size_large
import com.techgv.stash.core.resources.text_size_medium
import com.techgv.stash.core.resources.text_size_small
import com.techgv.stash.core.resources.theme_auto
import com.techgv.stash.core.resources.theme_night
import com.techgv.stash.core.resources.theme_parchment
import com.techgv.stash.core.resources.tip_both
import com.techgv.stash.core.resources.tip_left
import com.techgv.stash.core.resources.tip_none
import com.techgv.stash.core.resources.tip_right
import com.techgv.stash.core.resources.tip_verb_archive
import com.techgv.stash.core.resources.tip_verb_mark_read
import com.techgv.stash.core.resources.tip_verb_remove
import com.techgv.stash.core.resources.tip_verb_share
import com.techgv.stash.core.resources.undo_archived
import com.techgv.stash.core.resources.undo_deleted
import com.techgv.stash.core.resources.undo_removed
import com.techgv.stash.core.resources.undo_restored
import com.techgv.stash.core.resources.weekday_friday
import com.techgv.stash.core.resources.widget_size_medium
import com.techgv.stash.core.resources.widget_size_small
import com.techgv.stash.core.resources.weekday_initial_friday
import com.techgv.stash.core.resources.weekday_initial_monday
import com.techgv.stash.core.resources.weekday_initial_saturday
import com.techgv.stash.core.resources.weekday_initial_sunday
import com.techgv.stash.core.resources.weekday_initial_thursday
import com.techgv.stash.core.resources.weekday_initial_tuesday
import com.techgv.stash.core.resources.weekday_initial_wednesday
import com.techgv.stash.core.resources.weekday_monday
import com.techgv.stash.core.resources.weekday_saturday
import com.techgv.stash.core.resources.weekday_sunday
import com.techgv.stash.core.resources.weekday_thursday
import com.techgv.stash.core.resources.weekday_tuesday
import com.techgv.stash.core.resources.weekday_wednesday
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.StringResource

// Pure mappings from domain and model values to string resources.

/** Header text; a [GroupLabel.Month] in a year other than [currentYear] carries the year. */
fun groupLabelText(label: GroupLabel, currentYear: Int): UiText = when (label) {
    GroupLabel.Today -> text(Res.string.group_today)
    GroupLabel.Yesterday -> text(Res.string.group_yesterday)
    is GroupLabel.Weekday -> text(weekdayName(label.day))
    GroupLabel.ThisWeek -> text(Res.string.group_this_week)
    GroupLabel.LastWeek -> text(Res.string.group_last_week)
    GroupLabel.Earlier -> text(Res.string.group_earlier)
    GroupLabel.ThisMonth -> text(Res.string.group_this_month)
    is GroupLabel.Month ->
        if (label.year == currentYear) text(monthName(label.month)) else text(Res.string.group_month_year, text(monthName(label.month)), label.year)
    // The source key doubles as its display name ("Medium", "example.com"), as the domain stores it.
    is GroupLabel.Source -> UiText.Plain(label.key)
}

fun weekdayName(day: DayOfWeek): StringResource = when (day) {
    DayOfWeek.MONDAY -> Res.string.weekday_monday
    DayOfWeek.TUESDAY -> Res.string.weekday_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.weekday_wednesday
    DayOfWeek.THURSDAY -> Res.string.weekday_thursday
    DayOfWeek.FRIDAY -> Res.string.weekday_friday
    DayOfWeek.SATURDAY -> Res.string.weekday_saturday
    DayOfWeek.SUNDAY -> Res.string.weekday_sunday
}

/** One letter for the streak week strip. */
fun weekdayInitial(day: DayOfWeek): StringResource = when (day) {
    DayOfWeek.MONDAY -> Res.string.weekday_initial_monday
    DayOfWeek.TUESDAY -> Res.string.weekday_initial_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.weekday_initial_wednesday
    DayOfWeek.THURSDAY -> Res.string.weekday_initial_thursday
    DayOfWeek.FRIDAY -> Res.string.weekday_initial_friday
    DayOfWeek.SATURDAY -> Res.string.weekday_initial_saturday
    DayOfWeek.SUNDAY -> Res.string.weekday_initial_sunday
}

fun monthName(month: Month): StringResource = when (month) {
    Month.JANUARY -> Res.string.month_january
    Month.FEBRUARY -> Res.string.month_february
    Month.MARCH -> Res.string.month_march
    Month.APRIL -> Res.string.month_april
    Month.MAY -> Res.string.month_may
    Month.JUNE -> Res.string.month_june
    Month.JULY -> Res.string.month_july
    Month.AUGUST -> Res.string.month_august
    Month.SEPTEMBER -> Res.string.month_september
    Month.OCTOBER -> Res.string.month_october
    Month.NOVEMBER -> Res.string.month_november
    Month.DECEMBER -> Res.string.month_december
}

fun shortMonthName(month: Month): UiText = text(
    when (month) {
        Month.JANUARY -> Res.string.month_short_january
        Month.FEBRUARY -> Res.string.month_short_february
        Month.MARCH -> Res.string.month_short_march
        Month.APRIL -> Res.string.month_short_april
        Month.MAY -> Res.string.month_short_may
        Month.JUNE -> Res.string.month_short_june
        Month.JULY -> Res.string.month_short_july
        Month.AUGUST -> Res.string.month_short_august
        Month.SEPTEMBER -> Res.string.month_short_september
        Month.OCTOBER -> Res.string.month_short_october
        Month.NOVEMBER -> Res.string.month_short_november
        Month.DECEMBER -> Res.string.month_short_december
    },
)

/** Chip label without the count; a source chip shows its key. */
fun shelfFilterLabel(filter: ShelfFilter): UiText = when (filter) {
    ShelfFilter.All -> text(Res.string.chip_all)
    ShelfFilter.Unread -> text(Res.string.chip_unread)
    is ShelfFilter.Source -> UiText.Plain(filter.key)
}

fun searchFilterLabel(filter: SearchFilter): UiText = when (filter) {
    SearchFilter.All -> text(Res.string.chip_all)
    SearchFilter.Archived -> text(Res.string.chip_archived)
    is SearchFilter.Source -> UiText.Plain(filter.key)
}

/** Label on the swipe action background. Null for [SwipeOutcome.RubberBand], which shows no background. */
fun swipeOutcomeLabel(outcome: SwipeOutcome): StringResource? = when (outcome) {
    SwipeOutcome.MarkRead -> Res.string.swipe_read
    SwipeOutcome.MarkUnread -> Res.string.swipe_unread
    SwipeOutcome.Archive -> Res.string.swipe_archive
    SwipeOutcome.Share -> Res.string.swipe_share
    SwipeOutcome.Remove -> Res.string.swipe_remove
    SwipeOutcome.Restore -> Res.string.swipe_restore
    SwipeOutcome.Delete -> Res.string.swipe_delete
    SwipeOutcome.RubberBand -> null
}

/** Name of a card action in menus and accessibility actions. Null for [SwipeOutcome.RubberBand]. */
fun cardActionLabel(outcome: SwipeOutcome): StringResource? = when (outcome) {
    SwipeOutcome.MarkRead -> Res.string.menu_mark_read
    SwipeOutcome.MarkUnread -> Res.string.menu_mark_unread
    SwipeOutcome.Archive -> Res.string.menu_archive
    SwipeOutcome.Share -> Res.string.menu_share
    SwipeOutcome.Remove -> Res.string.menu_remove
    SwipeOutcome.Restore -> Res.string.menu_restore
    SwipeOutcome.Delete -> Res.string.menu_delete
    SwipeOutcome.RubberBand -> null
}

/** Undo snackbar text after an undoable command; null for operations that offer no Undo. */
fun undoMessage(type: OperationType): StringResource? = when (type) {
    OperationType.Remove -> Res.string.undo_removed
    OperationType.Archive, OperationType.AutoArchive -> Res.string.undo_archived
    OperationType.Restore -> Res.string.undo_restored
    OperationType.Delete -> Res.string.undo_deleted
    else -> null
}

private val celebrationMessages = listOf(
    Res.string.celebration_message_1, Res.string.celebration_message_2, Res.string.celebration_message_3,
    Res.string.celebration_message_4, Res.string.celebration_message_5,
)

/** Milestone copy for 7, 14, 30, 50 and 100 days; otherwise one of five messages, picked by the new count. */
fun celebrationMessage(celebration: Celebration): StringResource = when (celebration.milestone) {
    7 -> Res.string.celebration_milestone_7
    14 -> Res.string.celebration_milestone_14
    30 -> Res.string.celebration_milestone_30
    50 -> Res.string.celebration_milestone_50
    100 -> Res.string.celebration_milestone_100
    else -> celebrationMessages[celebration.to.mod(celebrationMessages.size)]
}

private fun tipVerb(action: SwipeAction): StringResource? = when (action) {
    SwipeAction.MarkRead -> Res.string.tip_verb_mark_read
    SwipeAction.Archive -> Res.string.tip_verb_archive
    SwipeAction.Share -> Res.string.tip_verb_share
    SwipeAction.Remove -> Res.string.tip_verb_remove
    SwipeAction.Nothing -> null
}

/**
 * The gesture tip, built from the start / end swipe settings and worded in screen directions for [layoutDirection]: the
 * start edge is the left in LTR and the right in RTL.
 */
fun gestureTipText(start: SwipeAction, end: SwipeAction, layoutDirection: LayoutDirection): UiText {
    val ltr = layoutDirection == LayoutDirection.Ltr
    val left = tipVerb(if (ltr) start else end)
    val right = tipVerb(if (ltr) end else start)
    return when {
        left != null && right != null -> text(Res.string.tip_both, text(left), text(right))
        left != null -> text(Res.string.tip_left, text(left))
        right != null -> text(Res.string.tip_right, text(right))
        else -> text(Res.string.tip_none)
    }
}

val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.Auto -> Res.string.theme_auto
        ThemeMode.Parchment -> Res.string.theme_parchment
        ThemeMode.Night -> Res.string.theme_night
    }

val Accent.label: StringResource
    get() = when (this) {
        Accent.Leather -> Res.string.accent_leather
        Accent.Oxblood -> Res.string.accent_oxblood
        Accent.Forest -> Res.string.accent_forest
        Accent.InkBlue -> Res.string.accent_ink_blue
    }

val CardLayout.label: StringResource
    get() = when (this) {
        CardLayout.Comfortable -> Res.string.layout_comfortable
        CardLayout.Compact -> Res.string.layout_compact
        CardLayout.LargeImages -> Res.string.layout_large_images
    }

val TextSize.label: StringResource
    get() = when (this) {
        TextSize.Small -> Res.string.text_size_small
        TextSize.Medium -> Res.string.text_size_medium
        TextSize.Large -> Res.string.text_size_large
    }

val OpenLinksIn.label: StringResource
    get() = when (this) {
        OpenLinksIn.InApp -> Res.string.open_in_app
        OpenLinksIn.Browser -> Res.string.open_in_browser
    }

val ReminderTime.label: StringResource
    get() = when (this) {
        ReminderTime.Morning -> Res.string.reminder_time_morning
        ReminderTime.Evening -> Res.string.reminder_time_evening
    }

val ShelfSort.label: StringResource
    get() = when (this) {
        ShelfSort.Newest -> Res.string.sort_newest
        ShelfSort.Oldest -> Res.string.sort_oldest
        ShelfSort.BySource -> Res.string.sort_by_source
    }

val ShelfGrouping.label: StringResource
    get() = when (this) {
        ShelfGrouping.Day -> Res.string.grouping_day
        ShelfGrouping.Week -> Res.string.grouping_week
        ShelfGrouping.Month -> Res.string.grouping_month
        ShelfGrouping.None -> Res.string.grouping_none
    }

val AutoArchivePeriod.label: UiText
    get() = days?.let { plural(Res.plurals.auto_archive_days, it) } ?: text(Res.string.auto_archive_off)

val WidgetSize.label: StringResource
    get() = when (this) {
        WidgetSize.Small -> Res.string.widget_size_small
        WidgetSize.Medium -> Res.string.widget_size_medium
    }

/** Language names are written in their own language, so they are the same in every locale. */
val AppLanguage.label: StringResource
    get() = when (this) {
        AppLanguage.System -> Res.string.language_system
        AppLanguage.English -> Res.string.language_english
        AppLanguage.Spanish -> Res.string.language_spanish
        AppLanguage.Hindi -> Res.string.language_hindi
        AppLanguage.Tamil -> Res.string.language_tamil
    }

val AppIcon.label: StringResource
    get() = when (this) {
        AppIcon.Leather -> Res.string.app_icon_leather
        AppIcon.Night -> Res.string.app_icon_night
        AppIcon.Parchment -> Res.string.app_icon_parchment
        AppIcon.Ink -> Res.string.app_icon_ink
    }

val SwipeAction.label: StringResource
    get() = when (this) {
        SwipeAction.MarkRead -> Res.string.swipe_action_mark_read
        SwipeAction.Archive -> Res.string.swipe_action_archive
        SwipeAction.Share -> Res.string.swipe_action_share
        SwipeAction.Remove -> Res.string.swipe_action_remove
        SwipeAction.Nothing -> Res.string.swipe_action_nothing
    }
