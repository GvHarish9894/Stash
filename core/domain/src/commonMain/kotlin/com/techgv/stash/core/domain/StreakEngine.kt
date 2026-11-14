package com.techgv.stash.core.domain

import com.techgv.stash.core.model.StreakDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class StreakStatus { Lit, Unlit, AtRisk }

enum class DayMark { Read, Rest, NotRead }

data class WeekDay(val date: LocalDate, val isToday: Boolean, val mark: DayMark)

/** [week] is the 7-day strip, oldest first, ending today. */
data class StreakState(val count: Int, val readToday: Boolean, val status: StreakStatus, val week: List<WeekDay>)

/** Shown once per day, on the first read. [milestone] is set when [to] is 7, 14, 30, 50 or 100. */
data class Celebration(val from: Int, val to: Int, val milestone: Int?)

/** [newDay] is the row to upsert, or null when today already counts. */
data class ReadOutcome(val newDay: StreakDay?, val celebration: Celebration?)

/**
 * Streak rules. A day counts on the first open or mark-read, dated in the user's zone at that moment; nothing undoes it.
 * A day without a read is safe (a rest day) when nothing was unread at its end, which the engine derives from
 * `unreadZeroSince`: when the unread count last became zero, or null while something is unread (decision D13).
 * Today is never a rest day and never breaks the streak until it is over.
 *
 * Rows dated more than one day after today are ignored, as coming from a clock that was set forward and then corrected.
 * Known limit: at one instant local dates can differ by up to two days (UTC+14 against UTC−11 or UTC−12), so a read
 * recorded in Kiritimati and viewed in American Samoa or Niue can look two days ahead; it is ignored until the local date
 * catches up to one day before it.
 */
object StreakEngine {
    val MILESTONES = setOf(7, 14, 30, 50, 100)
    val AT_RISK_FROM = LocalTime(21, 30)

    fun dayOf(at: Instant, zone: TimeZone): LocalDate = at.toLocalDateTime(zone).date

    /**
     * Rest days not yet stored: dates before [today] with no row whose end (next local midnight) is at or after
     * [unreadZeroSince]. Nothing has been unread since then, so each of those days ended with an empty shelf. The data
     * layer writes these as rest rows in the transaction that takes the unread count from 0 to more, then clears
     * `unreadZeroSince`; until then the engine derives them on every read.
     */
    fun restDays(days: List<StreakDay>, unreadZeroSince: Instant?, today: LocalDate, zone: TimeZone): List<LocalDate> {
        unreadZeroSince ?: return emptyList()
        val stored = days.mapTo(HashSet()) { it.date }
        var date = dayOf(unreadZeroSince, zone).minus(1, DateTimeUnit.DAY)
        val result = ArrayList<LocalDate>()
        while (date < today) {
            if (date !in stored && date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) >= unreadZeroSince) result += date
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    /** Records an open or mark-read at [at]. Celebrates only the first read of the day. */
    fun onRead(days: List<StreakDay>, unreadZeroSince: Instant?, at: Instant, zone: TimeZone): ReadOutcome {
        val today = dayOf(at, zone)
        if (readOn(days, today)) return ReadOutcome(newDay = null, celebration = null)
        val newDay = StreakDay(today, at)
        val from = count(days, unreadZeroSince, today, zone)
        val to = count(days.filter { it.date != today } + newDay, unreadZeroSince, today, zone)
        return ReadOutcome(newDay, Celebration(from, to, to.takeIf { it in MILESTONES }))
    }

    /** At risk: after 21:30, not read today, something unread and a streak to lose. */
    fun state(days: List<StreakDay>, unreadZeroSince: Instant?, unreadCount: Int, now: Instant, zone: TimeZone): StreakState {
        val local = now.toLocalDateTime(zone)
        val today = local.date
        val readToday = readOn(days, today)
        val kept = keptDays(days, unreadZeroSince, today, zone)
        val count = countKept(kept, today)
        val status = when {
            readToday -> StreakStatus.Lit
            count > 0 && unreadCount > 0 && local.time >= AT_RISK_FROM -> StreakStatus.AtRisk
            else -> StreakStatus.Unlit
        }
        val week = (6 downTo 0).map { back ->
            val date = today.minus(back, DateTimeUnit.DAY)
            val day = kept[date]
            val mark = when {
                back == 0 && readToday -> DayMark.Read
                day == null -> DayMark.NotRead
                day.firstReadAt == null -> DayMark.Rest
                else -> DayMark.Read
            }
            WeekDay(date, isToday = back == 0, mark = mark)
        }
        return StreakState(count, readToday, status, week)
    }

    /** Read days in the unbroken chain of kept days (read, stored rest or derived rest) ending at the latest one. */
    fun count(days: List<StreakDay>, unreadZeroSince: Instant?, today: LocalDate, zone: TimeZone): Int =
        countKept(keptDays(days, unreadZeroSince, today, zone), today)

    /** The chain is alive while its latest day is yesterday or later; a day after today can exist after travelling west. */
    private fun countKept(kept: Map<LocalDate, StreakDay>, today: LocalDate): Int {
        var date = kept.keys.maxOrNull() ?: return 0
        if (date < today.minus(1, DateTimeUnit.DAY)) return 0
        var count = 0
        while (true) {
            val day = kept[date] ?: return count
            if (day.firstReadAt != null) count++
            date = date.minus(1, DateTimeUnit.DAY)
        }
    }

    private fun keptDays(days: List<StreakDay>, unreadZeroSince: Instant?, today: LocalDate, zone: TimeZone): Map<LocalDate, StreakDay> {
        val kept = valid(days, today).associateByTo(HashMap()) { it.date }
        for (date in restDays(days, unreadZeroSince, today, zone)) kept.getOrPut(date) { StreakDay(date, null) }
        return kept
    }

    private fun valid(days: List<StreakDay>, today: LocalDate): List<StreakDay> {
        val latest = today.plus(1, DateTimeUnit.DAY)
        return days.filter { it.date <= latest }
    }

    private fun readOn(days: List<StreakDay>, today: LocalDate) =
        valid(days, today).any { it.firstReadAt != null && it.date >= today }
}
