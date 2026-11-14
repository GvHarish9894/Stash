package com.techgv.stash.core.designsystem.presentation

import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.time_date
import com.techgv.stash.core.resources.time_date_year
import com.techgv.stash.core.resources.time_days_long
import com.techgv.stash.core.resources.time_days_short
import com.techgv.stash.core.resources.time_hours_long
import com.techgv.stash.core.resources.time_hours_short
import com.techgv.stash.core.resources.time_just_now
import com.techgv.stash.core.resources.time_minutes_long
import com.techgv.stash.core.resources.time_minutes_short
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How long ago something happened, bucketed for display. */
sealed interface RelativeTime {
    /** Under a minute ago, or in the future (clock skew). */
    data object JustNow : RelativeTime
    data class Minutes(val count: Int) : RelativeTime
    data class Hours(val count: Int) : RelativeTime

    /** 1 to 6 days ago. */
    data class Days(val count: Int) : RelativeTime

    /** A week or more ago; [showYear] when the year differs from now's. */
    data class OnDate(val date: LocalDate, val showYear: Boolean) : RelativeTime
}

/** Elapsed time decides the bucket; [zone] only dates the 7-days-plus case. */
fun relativeTime(at: Instant, now: Instant, zone: TimeZone): RelativeTime {
    val elapsed = now - at
    return when {
        elapsed < 1.minutes -> RelativeTime.JustNow
        elapsed < 1.hours -> RelativeTime.Minutes(elapsed.inWholeMinutes.toInt())
        elapsed < 1.days -> RelativeTime.Hours(elapsed.inWholeHours.toInt())
        elapsed < 7.days -> RelativeTime.Days(elapsed.inWholeDays.toInt())
        else -> {
            val date = at.toLocalDateTime(zone).date
            RelativeTime.OnDate(date, showYear = date.year != now.toLocalDateTime(zone).date.year)
        }
    }
}

/** Card form: "just now", "5m ago", "2h ago", "3d ago", "Nov 2", "Nov 2, 2025". */
fun RelativeTime.short(): UiText = when (this) {
    RelativeTime.JustNow -> text(Res.string.time_just_now)
    is RelativeTime.Minutes -> text(Res.string.time_minutes_short, count)
    is RelativeTime.Hours -> text(Res.string.time_hours_short, count)
    is RelativeTime.Days -> text(Res.string.time_days_short, count)
    is RelativeTime.OnDate -> dateText()
}

/** Spoken form for accessibility labels: "2 hours ago". */
fun RelativeTime.long(): UiText = when (this) {
    RelativeTime.JustNow -> text(Res.string.time_just_now)
    is RelativeTime.Minutes -> plural(Res.plurals.time_minutes_long, count)
    is RelativeTime.Hours -> plural(Res.plurals.time_hours_long, count)
    is RelativeTime.Days -> plural(Res.plurals.time_days_long, count)
    is RelativeTime.OnDate -> dateText()
}

private fun RelativeTime.OnDate.dateText(): UiText {
    val month = shortMonthName(date.month)
    return if (showYear) text(Res.string.time_date_year, month, date.day, date.year) else text(Res.string.time_date, month, date.day)
}
