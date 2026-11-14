package com.techgv.stash.core.domain

import com.techgv.stash.core.model.StreakDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class StreakEngineTest {
    private fun read(date: String) = StreakDay(LocalDate.parse(date), Instant.parse("${date}T12:00:00Z"))
    private fun rest(date: String) = StreakDay(LocalDate.parse(date), null)

    /** Applies a read to [days] the way the repository would: upsert the new day. */
    private fun List<StreakDay>.after(outcome: ReadOutcome) =
        outcome.newDay?.let { new -> filter { it.date != new.date } + new } ?: this

    @Test
    fun midnightRolloverInEachZone() {
        val cases = listOf(
            Triple(KOLKATA, "2026-11-12T18:29:59Z", "2026-11-12"),
            Triple(KOLKATA, "2026-11-12T18:30:00Z", "2026-11-13"),
            Triple(LOS_ANGELES, "2026-11-13T07:59:59Z", "2026-11-12"),
            Triple(LOS_ANGELES, "2026-11-13T08:00:00Z", "2026-11-13"),
            Triple(KIRITIMATI, "2026-11-12T09:59:59Z", "2026-11-12"),
            Triple(KIRITIMATI, "2026-11-12T10:00:00Z", "2026-11-13"),
        )
        for ((zone, at, date) in cases) assertEquals(LocalDate.parse(date), StreakEngine.dayOf(Instant.parse(at), zone), "$zone $at")
    }

    @Test
    fun readsEitherSideOfMidnightCountAsTwoDays() {
        for (zone in listOf(KOLKATA, LOS_ANGELES, KIRITIMATI)) {
            val first = StreakEngine.onRead(emptyList(), null, local("2026-11-12T23:59:59", zone), zone)
            assertEquals(Celebration(0, 1, null), first.celebration, "$zone")
            val days = emptyList<StreakDay>().after(first)
            val second = StreakEngine.onRead(days, null, local("2026-11-13T00:00:00", zone), zone)
            assertEquals(Celebration(1, 2, null), second.celebration, "$zone")
            assertEquals(LocalDate.parse("2026-11-13"), second.newDay?.date)
        }
    }

    @Test
    fun celebrationOncePerDay() {
        val zone = KOLKATA
        val first = StreakEngine.onRead(emptyList(), null, local("2026-11-12T08:00", zone), zone)
        assertNotNull(first.celebration)
        val days = emptyList<StreakDay>().after(first)
        assertEquals(ReadOutcome(null, null), StreakEngine.onRead(days, null, local("2026-11-12T20:00", zone), zone))
        assertEquals(local("2026-11-12T08:00", zone), days.single().firstReadAt)
    }

    @Test
    fun missedDayBreaksTheStreak() {
        val today = LocalDate.parse("2026-11-12")
        assertEquals(0, StreakEngine.count(listOf(read("2026-11-09"), read("2026-11-10")), null, today, KOLKATA))
        assertEquals(3, StreakEngine.count(listOf(read("2026-11-09"), read("2026-11-10"), read("2026-11-11")), null, today, KOLKATA))
        assertEquals(2, StreakEngine.count(listOf(read("2026-11-05"), read("2026-11-10"), read("2026-11-11")), null, today, KOLKATA))
        assertEquals(4, StreakEngine.count(listOf(read("2026-11-09"), read("2026-11-10"), read("2026-11-11"), read("2026-11-12")), null, today, KOLKATA))
        assertEquals(0, StreakEngine.count(emptyList(), null, today, KOLKATA))
    }

    @Test
    fun emptyShelfDayIsSafeAndAddsNothing() {
        val days = listOf(read("2026-11-09"), read("2026-11-10"), rest("2026-11-11"))
        val now = local("2026-11-12T10:00", KOLKATA)
        val state = StreakEngine.state(days, null, unreadCount = 3, now, KOLKATA)
        assertEquals(2, state.count)
        assertEquals(false, state.readToday)
        assertEquals(DayMark.Rest, state.week[5].mark)
        val outcome = StreakEngine.onRead(days, null, now, KOLKATA)
        assertEquals(Celebration(2, 3, null), outcome.celebration)
        // Today with nothing unread is never at risk.
        assertEquals(StreakStatus.Unlit, StreakEngine.state(days, null, unreadCount = 0, local("2026-11-12T23:00", KOLKATA), KOLKATA).status)
    }

    @Test
    fun aRestDayRecordedTodayIsUpgradedByAFirstRead() {
        val days = listOf(read("2026-11-11"), rest("2026-11-12"))
        val outcome = StreakEngine.onRead(days, null, local("2026-11-12T18:00", KOLKATA), KOLKATA)
        assertEquals(Celebration(1, 2, null), outcome.celebration)
        assertEquals(2, StreakEngine.count(days.after(outcome), null, LocalDate.parse("2026-11-12"), KOLKATA))
    }

    @Test
    fun unmarkingDoesNotUndoTheDay() {
        val zone = KOLKATA
        val days = emptyList<StreakDay>().after(StreakEngine.onRead(emptyList(), null, local("2026-11-12T08:00", zone), zone))
        // The item read this morning is marked unread again, so it is back in the unread count; the day still counts.
        val state = StreakEngine.state(days, null, unreadCount = 1, local("2026-11-12T22:00", zone), zone)
        assertEquals(StreakStatus.Lit, state.status)
        assertEquals(1, state.count)
        assertEquals(true, state.readToday)
    }

    @Test
    fun travellingAcrossZones() {
        // Read late evening in Kolkata, then land in Los Angeles where it is still the same date.
        val kolkataRead = StreakEngine.onRead(emptyList(), null, local("2026-11-12T22:00", KOLKATA), KOLKATA)
        val westSameDate = StreakEngine.state(emptyList<StreakDay>().after(kolkataRead), null, 1, Instant.parse("2026-11-12T20:00:00Z"), LOS_ANGELES)
        assertEquals(true, westSameDate.readToday)

        // Read on 13 November in Kiritimati, then land in Los Angeles where it is 12 November: the future day counts.
        val kiritimatiRead = StreakEngine.onRead(listOf(read("2026-11-12")), null, local("2026-11-13T08:00", KIRITIMATI), KIRITIMATI)
        val days = listOf(read("2026-11-12")).after(kiritimatiRead)
        val west = StreakEngine.state(days, null, 1, Instant.parse("2026-11-12T18:30:00Z"), LOS_ANGELES)
        assertEquals(LocalDate.parse("2026-11-12"), StreakEngine.dayOf(Instant.parse("2026-11-12T18:30:00Z"), LOS_ANGELES))
        assertEquals(true, west.readToday)
        assertEquals(2, west.count)
        assertEquals(ReadOutcome(null, null), StreakEngine.onRead(days, null, Instant.parse("2026-11-12T18:30:00Z"), LOS_ANGELES))

        // Read on the evening of 12 November in Los Angeles, then land in Kiritimati where it is already 13 November.
        val laRead = StreakEngine.onRead(emptyList(), null, local("2026-11-12T20:00", LOS_ANGELES), LOS_ANGELES)
        val east = StreakEngine.state(emptyList<StreakDay>().after(laRead), null, 1, Instant.parse("2026-11-13T04:30:00Z"), KIRITIMATI)
        assertEquals(false, east.readToday)
        assertEquals(1, east.count, "yesterday keeps the streak alive")
    }

    @Test
    fun daylightSavingDays() {
        // 1 November 2026 has 25 hours in New York: 00:30 EDT and 23:30 EST are the same day.
        val first = StreakEngine.onRead(emptyList(), null, Instant.parse("2026-11-01T04:30:00Z"), NEW_YORK)
        assertEquals(LocalDate.parse("2026-11-01"), first.newDay?.date)
        val sameDay = StreakEngine.onRead(emptyList<StreakDay>().after(first), null, Instant.parse("2026-11-02T04:30:00Z"), NEW_YORK)
        assertEquals(ReadOutcome(null, null), sameDay)

        // 8 March 2026 has 23 hours: 23:30 EST on the 7th and 23:30 EDT on the 8th are consecutive days.
        val march7 = StreakEngine.onRead(emptyList(), null, Instant.parse("2026-03-08T04:30:00Z"), NEW_YORK)
        assertEquals(LocalDate.parse("2026-03-07"), march7.newDay?.date)
        val march8 = StreakEngine.onRead(emptyList<StreakDay>().after(march7), null, Instant.parse("2026-03-09T03:30:00Z"), NEW_YORK)
        assertEquals(LocalDate.parse("2026-03-08"), march8.newDay?.date)
        assertEquals(Celebration(1, 2, null), march8.celebration)
    }

    @Test
    fun eachMilestone() {
        val today = LocalDate.parse("2026-11-12")
        val now = local("2026-11-12T09:00", TimeZone.UTC)
        val expected = mapOf(6 to null, 7 to 7, 8 to null, 13 to null, 14 to 14, 30 to 30, 50 to 50, 99 to null, 100 to 100, 101 to null)
        for ((target, milestone) in expected) {
            val chain = (1 until target).map { back ->
                StreakDay(today.minus(back, DateTimeUnit.DAY), Instant.parse("2026-01-01T00:00:00Z"))
            }
            assertEquals(Celebration(target - 1, target, milestone), StreakEngine.onRead(chain, null, now, TimeZone.UTC).celebration, "$target")
        }
    }

    @Test
    fun statusLitUnlitAndAtRisk() {
        val zone = NEW_YORK
        val yesterday = listOf(read("2026-11-11"))
        val cases = listOf(
            Triple("2026-11-12T21:29", 2, StreakStatus.Unlit),
            Triple("2026-11-12T21:30", 2, StreakStatus.AtRisk),
            Triple("2026-11-12T23:59", 2, StreakStatus.AtRisk),
            Triple("2026-11-12T22:00", 0, StreakStatus.Unlit),
            Triple("2026-11-12T08:00", 2, StreakStatus.Unlit),
        )
        for ((time, unread, status) in cases) {
            assertEquals(status, StreakEngine.state(yesterday, null, unread, local(time, zone), zone).status, "$time unread=$unread")
        }
        val lit = StreakEngine.state(yesterday + read("2026-11-12"), null, 2, local("2026-11-12T22:00", zone), zone)
        assertEquals(StreakStatus.Lit, lit.status)
        assertEquals(2, lit.count)
    }

    @Test
    fun weekStripIsTheLastSevenDaysEndingToday() {
        val days = listOf(read("2026-11-05"), read("2026-11-06"), rest("2026-11-08"), read("2026-11-11"), read("2026-11-12"))
        val week = StreakEngine.state(days, null, 1, local("2026-11-12T10:00", KOLKATA), KOLKATA).week
        assertEquals((6 downTo 0).map { LocalDate.parse("2026-11-12").minus(it, DateTimeUnit.DAY) }, week.map { it.date })
        assertEquals(
            listOf(DayMark.Read, DayMark.NotRead, DayMark.Rest, DayMark.NotRead, DayMark.NotRead, DayMark.Read, DayMark.Read),
            week.map { it.mark },
        )
        assertEquals(listOf(false, false, false, false, false, false, true), week.map { it.isToday })
    }

    @Test
    fun anEmptyShelfAtTheEndOfADayMakesItARestDayWithoutARow() {
        val days = listOf(read("2026-11-10"))
        val zone = KOLKATA
        val emptySince = local("2026-11-10T20:00", zone)
        val today = LocalDate.parse("2026-11-12")
        assertEquals(listOf(LocalDate.parse("2026-11-11")), StreakEngine.restDays(days, emptySince, today, zone))
        assertEquals(1, StreakEngine.count(days, emptySince, today, zone))
        assertEquals(0, StreakEngine.count(days, null, today, zone), "without unreadZeroSince the missed day breaks it")
        val state = StreakEngine.state(days, emptySince, 0, local("2026-11-12T10:00", zone), zone)
        assertEquals(DayMark.Rest, state.week[5].mark)
        assertEquals(DayMark.NotRead, state.week[6].mark, "today is never a rest day")
        val outcome = StreakEngine.onRead(days, emptySince, local("2026-11-12T12:00", zone), zone)
        assertEquals(Celebration(1, 2, null), outcome.celebration)
        // Today's read row must not hide the derived rest day before it.
        assertEquals(2, StreakEngine.count(days.after(outcome), emptySince, today, zone))
    }

    @Test
    fun restDaysAreRowlessDaysThatEndedWithAnEmptyShelf() {
        val zone = KOLKATA
        val today = LocalDate.parse("2026-11-12")
        // Empty since exactly midnight starting the 11th: the 10th ended with nothing unread too.
        assertEquals(
            listOf(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-11")),
            StreakEngine.restDays(listOf(read("2026-11-08")), local("2026-11-11T00:00", zone), today, zone),
        )
        // Days that already have a row are not derived again.
        assertEquals(emptyList(), StreakEngine.restDays(listOf(read("2026-11-10"), rest("2026-11-11")), local("2026-11-10T20:00", zone), today, zone))
        // A long-empty shelf with no rows at all.
        assertEquals(3, StreakEngine.restDays(emptyList(), local("2026-11-09T09:00", zone), today, zone).size)
        assertEquals(emptyList(), StreakEngine.restDays(listOf(read("2026-11-10")), null, today, zone))
    }

    @Test
    fun linksSavedLaterTodayMeanTodayIsNotSafe() {
        val zone = KOLKATA
        val days = listOf(read("2026-11-11"))
        // Empty at 08:00, five links saved at noon: the data layer materialises rest days, which are none, then clears it.
        val emptyAtEight = local("2026-11-12T08:00", zone)
        assertEquals(emptyList(), StreakEngine.restDays(days, emptyAtEight, LocalDate.parse("2026-11-12"), zone))
        val evening = StreakEngine.state(days, null, 5, local("2026-11-12T21:30", zone), zone)
        assertEquals(StreakStatus.AtRisk, evening.status)
        assertEquals(DayMark.NotRead, evening.week.last().mark)
        assertEquals(1, evening.count)
    }

    @Test
    fun aFarFutureRowFromAWrongClockIsIgnored() {
        val zone = KOLKATA
        val days = listOf(read("2027-05-01"))
        val now = local("2026-11-12T10:00", zone)
        val state = StreakEngine.state(days, null, 2, now, zone)
        assertEquals(false, state.readToday)
        assertEquals(0, state.count)
        val outcome = StreakEngine.onRead(days, null, now, zone)
        assertEquals(LocalDate.parse("2026-11-12"), outcome.newDay?.date)
        assertEquals(Celebration(0, 1, null), outcome.celebration)
        // One day ahead is still honoured (travelling west); two days ahead is not.
        assertEquals(true, StreakEngine.state(listOf(read("2026-11-13")), null, 2, now, zone).readToday)
        val twoAhead = listOf(read("2026-11-14"))
        assertEquals(false, StreakEngine.state(twoAhead, null, 2, now, zone).readToday)
        assertEquals(0, StreakEngine.count(twoAhead, null, LocalDate.parse("2026-11-12"), zone))
        assertEquals(LocalDate.parse("2026-11-12"), StreakEngine.onRead(twoAhead, null, now, zone).newDay?.date)
    }

    @Test
    fun weekStripShowsTodayReadWhenTheReadIsDatedTomorrow() {
        val week = StreakEngine.state(listOf(read("2026-11-13")), null, 1, Instant.parse("2026-11-12T18:30:00Z"), LOS_ANGELES).week
        assertEquals(LocalDate.parse("2026-11-12"), week.last().date)
        assertEquals(DayMark.Read, week.last().mark)
    }

    @Test
    fun yearBoundary() {
        val zone = KOLKATA
        assertEquals(LocalDate.parse("2026-12-31"), StreakEngine.dayOf(local("2026-12-31T23:59:59", zone), zone))
        val dec31 = StreakEngine.onRead(emptyList(), null, local("2026-12-31T23:59", zone), zone)
        val jan1 = StreakEngine.onRead(emptyList<StreakDay>().after(dec31), null, local("2027-01-01T00:01", zone), zone)
        assertEquals(LocalDate.parse("2027-01-01"), jan1.newDay?.date)
        assertEquals(Celebration(1, 2, null), jan1.celebration)
        val week = StreakEngine.state(listOf(read("2026-12-31"), read("2027-01-01")), null, 1, local("2027-01-02T09:00", zone), zone).week
        assertEquals(listOf(DayMark.Read, DayMark.Read, DayMark.NotRead), week.takeLast(3).map { it.mark })
    }

    @Test
    fun nothingIsAtRiskWithoutAStreak() {
        val state = StreakEngine.state(emptyList(), null, 3, local("2026-11-12T22:00", NEW_YORK), NEW_YORK)
        assertEquals(StreakStatus.Unlit, state.status)
    }

    @Test
    fun noDaysMeansNoStreak() {
        val state = StreakEngine.state(emptyList(), null, 0, T0, KOLKATA)
        assertEquals(0, state.count)
        assertEquals(StreakStatus.Unlit, state.status)
        assertNull(state.week.firstOrNull { it.mark != DayMark.NotRead })
    }
}
