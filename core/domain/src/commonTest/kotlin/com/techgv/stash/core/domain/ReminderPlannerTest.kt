package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.ReminderCopy
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.ReminderLevel.Daily
import com.techgv.stash.core.model.ReminderLevel.Evening
import com.techgv.stash.core.model.ReminderLevel.LastCall
import com.techgv.stash.core.model.ReminderLevel.Late
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReminderPlannerTest {
    private val zone = NEW_YORK
    private val short = item("short", title = "A short read", readingMinutes = 4)
    private val morning = UserPreferences(reminderTime = ReminderTime.Morning)
    private val evening = UserPreferences(reminderTime = ReminderTime.Evening)

    private fun plan(
        prefs: UserPreferences,
        now: Instant,
        streak: Int = 5,
        readToday: Boolean = false,
        unread: Int = 3,
        zone: TimeZone = this.zone,
    ) = ReminderPlanner.plan(prefs, streak, readToday, unread, short, now, zone)

    private fun List<PlannedNotification>.slots() = map { it.fireAt to it.level }

    private fun slots(vararg slots: Pair<String, ReminderLevel>, zone: TimeZone = this.zone) =
        slots.map { (time, level) -> local(time, zone) to level }

    @Test
    fun morningDailyWithStreakSaver() {
        assertEquals(
            slots(
                "2026-11-12T09:00" to Daily, "2026-11-12T19:00" to Evening, "2026-11-12T21:30" to Late,
                "2026-11-12T23:00" to LastCall, "2026-11-13T09:00" to Daily,
            ),
            plan(morning, local("2026-11-12T08:00", zone)).slots(),
        )
    }

    @Test
    fun eveningDailySharesItsSlotWithTheSaver() {
        assertEquals(
            slots("2026-11-12T19:00" to Evening, "2026-11-12T21:30" to Late, "2026-11-12T23:00" to LastCall, "2026-11-13T19:00" to Daily),
            plan(evening, local("2026-11-12T08:00", zone)).slots(),
        )
        assertEquals(
            slots("2026-11-12T19:00" to Daily, "2026-11-13T19:00" to Daily),
            plan(evening.copy(streakSaver = false), local("2026-11-12T08:00", zone)).slots(),
        )
    }

    @Test
    fun saverAloneIsAtMostThreeAndNeedsALiveStreak() {
        val saverOnly = plan(morning.copy(dailyReminder = false), local("2026-11-12T08:00", zone))
        assertEquals(slots("2026-11-12T19:00" to Evening, "2026-11-12T21:30" to Late, "2026-11-12T23:00" to LastCall), saverOnly.slots())
        assertEquals(emptyList(), plan(morning.copy(dailyReminder = false), local("2026-11-12T08:00", zone), streak = 0))
        assertEquals(
            slots("2026-11-12T09:00" to Daily, "2026-11-13T09:00" to Daily),
            plan(morning, local("2026-11-12T08:00", zone), streak = 0).slots(),
        )
    }

    @Test
    fun aReadTodayCancelsTheRestOfTodayAndPlansTomorrow() {
        val afterRead = plan(morning, local("2026-11-12T08:00", zone), readToday = true)
        assertEquals(true, afterRead.none { it.id.startsWith("2026-11-12") }, "nothing left today")
        assertEquals(
            slots("2026-11-13T09:00" to Daily, "2026-11-13T19:00" to Evening, "2026-11-13T21:30" to Late, "2026-11-13T23:00" to LastCall),
            afterRead.slots(),
        )
        assertEquals(
            slots("2026-11-13T19:00" to Evening, "2026-11-13T21:30" to Late, "2026-11-13T23:00" to LastCall),
            plan(morning.copy(dailyReminder = false), local("2026-11-12T08:00", zone), streak = 5, readToday = true).slots(),
        )
        assertEquals(
            slots("2026-11-13T19:00" to Evening, "2026-11-13T21:30" to Late, "2026-11-13T23:00" to LastCall),
            plan(evening, local("2026-11-12T08:00", zone), readToday = true).slots(),
            "a 7 PM daily merges into tomorrow's saver",
        )
        assertEquals(emptyList(), plan(morning.copy(dailyReminder = false, streakSaver = false), local("2026-11-12T08:00", zone), readToday = true))
    }

    @Test
    fun atMostFourPendingAfterARead() {
        for (daily in listOf(true, false)) for (saver in listOf(true, false)) for (time in ReminderTime.entries) {
            val prefs = UserPreferences(dailyReminder = daily, streakSaver = saver, reminderTime = time)
            val planned = plan(prefs, local("2026-11-12T08:00", zone), readToday = true)
            assertEquals(true, planned.size <= 4, "$prefs")
            assertEquals(true, planned.all { it.id.startsWith("2026-11-13") }, "$prefs")
        }
    }

    @Test
    fun nothingUnreadMeansNoPlan() {
        assertEquals(emptyList(), plan(morning, local("2026-11-12T08:00", zone), unread = 0))
        assertEquals(emptyList(), ReminderPlanner.plan(morning, 5, false, 3, null, local("2026-11-12T08:00", zone), zone))
    }

    @Test
    fun planningLateInTheEveningSkipsSlotsPastTheirGrace() {
        assertEquals(slots("2026-11-12T23:00" to LastCall, "2026-11-13T09:00" to Daily), plan(morning, local("2026-11-12T22:01", zone)).slots())
        assertEquals(slots("2026-11-12T23:00" to LastCall, "2026-11-13T09:00" to Daily), plan(morning, local("2026-11-12T23:30", zone)).slots())
        assertEquals(slots("2026-11-13T09:00" to Daily), plan(morning, local("2026-11-12T23:31", zone)).slots())
    }

    @Test
    fun aSlotStaysForThirtyMinutesAfterItsTimeOnlyWhileItStillApplies() {
        val nine = local("2026-11-12T09:00", zone)
        for (after in listOf(16.seconds, 20.seconds, 29.minutes)) {
            assertEquals(nine to Daily, plan(morning, nine + after).slots().first(), "$after after the slot, nothing read")
        }
        assertEquals(5, plan(morning, nine + 20.seconds).size, "still at most 5 pending")
        assertTrue(plan(morning, nine + 20.seconds, readToday = true).none { it.fireAt == nine }, "a read removes it")
        assertTrue(plan(morning.copy(dailyReminder = false), nine + 20.seconds).none { it.fireAt == nine }, "switching it off removes it")
        assertEquals(emptyList(), plan(morning, nine + 20.seconds, unread = 0), "nothing unread")
        assertTrue(plan(morning, nine + 31.minutes).none { it.fireAt == nine }, "31 minutes after")

        val seven = local("2026-11-12T19:00", zone)
        assertEquals(seven to Evening, plan(morning, seven + 20.seconds).slots().first())
        assertTrue(plan(morning, seven + 20.seconds, streak = 0).none { it.fireAt == seven }, "a saver needs a live streak")
        assertTrue(plan(morning.copy(streakSaver = false), seven + 20.seconds).none { it.fireAt == seven }, "saver switched off")
    }

    @Test
    fun springForwardInNewYork() {
        // 8 March 2026: clocks jump from 02:00 EST to 03:00 EDT.
        assertEquals(
            listOf(
                "2026-03-08T13:00:00Z" to Daily, "2026-03-08T23:00:00Z" to Evening, "2026-03-09T01:30:00Z" to Late,
                "2026-03-09T03:00:00Z" to LastCall, "2026-03-09T13:00:00Z" to Daily,
            ).map { Instant.parse(it.first) to it.second },
            plan(morning, Instant.parse("2026-03-08T06:00:00Z")).slots(),
        )
        // Planned the evening before, tomorrow's 9 AM is already in EDT.
        assertEquals(
            listOf("2026-03-08T04:00:00Z" to LastCall, "2026-03-08T13:00:00Z" to Daily).map { Instant.parse(it.first) to it.second },
            plan(morning, local("2026-03-07T22:10", zone)).slots(),
        )
    }

    @Test
    fun fallBackInNewYork() {
        // 1 November 2026: clocks fall back from 02:00 EDT to 01:00 EST.
        assertEquals(
            listOf("2026-11-01T03:00:00Z" to LastCall, "2026-11-01T14:00:00Z" to Daily).map { Instant.parse(it.first) to it.second },
            plan(morning, local("2026-10-31T22:10", zone)).slots(),
        )
        assertEquals(
            listOf(
                "2026-11-01T14:00:00Z" to Daily, "2026-11-02T00:00:00Z" to Evening, "2026-11-02T02:30:00Z" to Late,
                "2026-11-02T04:00:00Z" to LastCall, "2026-11-02T14:00:00Z" to Daily,
            ).map { Instant.parse(it.first) to it.second },
            plan(morning, Instant.parse("2026-11-01T06:00:00Z")).slots(),
        )
    }

    @Test
    fun planSizeAndOrderAcrossSettingsTimesAndDstDays() {
        var largest = 0
        var largestAfterRead = 0
        for (day in listOf("2026-11-12", "2026-03-08", "2026-11-01")) {
            val dayStart = LocalDate.parse(day).atStartOfDayIn(zone)
            val nextDay = LocalDate.parse(day).plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            var now = dayStart
            while (now < nextDay) {
                for (daily in listOf(true, false)) for (saver in listOf(true, false)) for (time in ReminderTime.entries) {
                    for (readToday in listOf(false, true)) for (streak in listOf(0, 5)) {
                        val prefs = UserPreferences(dailyReminder = daily, streakSaver = saver, reminderTime = time)
                        val planned = plan(prefs, now, streak = streak, readToday = readToday)
                        val name = "$day $now $prefs read=$readToday streak=$streak"
                        assertTrue(planned.size <= 5, name)
                        if (readToday) assertTrue(planned.size <= 4, name)
                        assertTrue(planned.all { now - it.fireAt <= ReminderPlanner.DELIVERY_GRACE }, name)
                        assertEquals(planned.size, planned.map { it.id }.toSet().size, name)
                        assertEquals(planned.sortedBy { it.fireAt }, planned, name)
                        largest = maxOf(largest, planned.size)
                        if (readToday) largestAfterRead = maxOf(largestAfterRead, planned.size)
                    }
                }
                now += 30.minutes
            }
        }
        assertEquals(5, largest, "the D15 limit is reached")
        assertEquals(4, largestAfterRead)
    }

    @Test
    fun aZoneChangeMovesEverySlotToTheNewWallClock() {
        val now = T0 // 05:00 in New York, 15:30 in Kolkata
        assertEquals(local("2026-11-12T09:00", NEW_YORK), plan(morning, now, zone = NEW_YORK).first().fireAt)
        assertEquals(
            slots(
                "2026-11-12T19:00" to Evening, "2026-11-12T21:30" to Late, "2026-11-12T23:00" to LastCall,
                "2026-11-13T09:00" to Daily, zone = KOLKATA,
            ),
            plan(morning, now, zone = KOLKATA).slots(),
        )
    }

    @Test
    fun copyParametersAndStableIds() {
        val planned = plan(morning, local("2026-11-12T08:00", zone))
        assertEquals(
            listOf("2026-11-12-Daily", "2026-11-12-Evening", "2026-11-12-Late", "2026-11-12-LastCall", "2026-11-13-Daily"),
            planned.map { it.id },
        )
        assertEquals(ReminderCopy(streak = 5, unreadCount = 3, itemTitle = "A short read", minutes = 4), planned.first().copy)
        assertEquals(ItemId("short"), planned.first().itemId)
        // Tomorrow's reminder only fires if today went unread, so its streak has restarted.
        assertEquals(0, planned.last().copy.streak)
        assertEquals(setOf(5), plan(morning, local("2026-11-12T08:00", zone), readToday = true).map { it.copy.streak }.toSet())
    }

    @Test
    fun titleIsCappedAtFortyCharactersPlusEllipsis() {
        val forty = "x".repeat(40)
        assertEquals(forty, ReminderPlanner.capTitle(forty))
        assertEquals("$forty…", ReminderPlanner.capTitle(forty + "y"))
        assertEquals("a".repeat(39) + "…", ReminderPlanner.capTitle("a".repeat(39) + "😀b"))
        val long = item("long", title = "Designing for slow reading in a fast feed, a longer essay", readingMinutes = 2)
        val planned = ReminderPlanner.plan(morning, 5, false, 1, long, local("2026-11-12T08:00", zone), zone)
        assertEquals("Designing for slow reading in a fast fee…", planned.first().copy.itemTitle)
    }

    @Test
    fun aTitleThatIsOnlyALinkBecomesTheSiteName() {
        val link = item("i1", siteName = "example.com")
        val longPath = "https://example.com/a-very-long-path/that-goes-on-and-on-and-on"
        assertEquals("example.com", ReminderPlanner.copyTitle(link.copy(title = link.url)))
        assertEquals("example.com", ReminderPlanner.copyTitle(link.copy(title = link.normalizedUrl)))
        assertEquals("example.com", ReminderPlanner.copyTitle(link.copy(title = " $longPath ")))
        assertEquals("A short read", ReminderPlanner.copyTitle(link.copy(title = "A short read")))
        assertEquals("Read https://example.com/x later", ReminderPlanner.copyTitle(link.copy(title = "Read https://example.com/x later")))
        assertEquals("ftp://example.com/x", ReminderPlanner.copyTitle(link.copy(title = "ftp://example.com/x")))
        val planned = ReminderPlanner.plan(morning, 5, false, 1, link.copy(title = longPath), local("2026-11-12T08:00", zone), zone)
        assertEquals(setOf("example.com"), planned.map { it.copy.itemTitle }.toSet())
    }

    private fun nudge(time: String, prefs: UserPreferences = morning, streak: Int = 5, readToday: Boolean = false, unread: Int = 2) =
        ReminderPlanner.nudgeLevel(prefs, streak, readToday, unread, local("2026-11-12T$time", zone), zone)

    @Test
    fun nudgeLevelFollowsTheSaverSchedule() {
        val cases = listOf(
            "00:30" to Daily, "08:00" to Daily, "18:59" to Daily, "19:00" to Evening, "21:29" to Evening,
            "21:30" to Late, "22:59" to Late, "23:00" to LastCall, "23:59" to LastCall,
        )
        for ((time, level) in cases) assertEquals(level, nudge(time), time)
        assertEquals(null, nudge("23:00", readToday = true))
        assertEquals(null, nudge("23:00", unread = 0))
    }

    @Test
    fun nudgeLevelWithoutTheSaverStaysAtTheDailyLevel() {
        val saverOff = morning.copy(streakSaver = false)
        assertEquals(Daily, nudge("23:30", prefs = saverOff))
        assertEquals(Daily, nudge("23:30", streak = 0))
        assertEquals(Daily, nudge("18:59", prefs = evening.copy(streakSaver = false)))
        assertEquals(Evening, nudge("19:00", prefs = evening.copy(streakSaver = false)))
        assertEquals(Evening, nudge("23:30", prefs = evening, streak = 0))
        assertEquals(Daily, nudge("23:30", prefs = morning.copy(dailyReminder = false, streakSaver = false)))
    }

    private fun preview(
        level: ReminderLevel,
        prefs: UserPreferences = morning,
        streak: Int = 5,
        readToday: Boolean = false,
        unread: Int = 3,
        shortest: SavedItem? = short,
    ) = ReminderPlanner.preview(level, prefs, streak, readToday, unread, shortest, local("2026-11-12T22:00", zone), zone)

    private fun previewedSlot(result: ReminderPreviewResult) =
        (result as ReminderPreviewResult.Planned).notification.let { it.fireAt to it.level }

    private fun none(reason: NoReminderReason) = ReminderPreviewResult.NoReminder(reason)

    @Test
    fun previewShowsTodaysSlotEvenWhenItsTimeHasPassed() {
        assertEquals(local("2026-11-12T09:00", zone) to Daily, previewedSlot(preview(Daily)))
        assertEquals(local("2026-11-12T21:30", zone) to Late, previewedSlot(preview(Late)))
        val planned = (preview(LastCall) as ReminderPreviewResult.Planned).notification
        assertEquals(ReminderCopy(streak = 5, unreadCount = 3, itemTitle = "A short read", minutes = 4), planned.copy)
        assertEquals(short.id, planned.itemId)
    }

    @Test
    fun previewOfASevenPmDailyReminder() {
        // The saver takes the 7 PM slot; with the saver off the daily reminder fills it.
        assertEquals(local("2026-11-12T19:00", zone) to Evening, previewedSlot(preview(Daily, evening)))
        val saverOff = evening.copy(streakSaver = false)
        assertEquals(local("2026-11-12T19:00", zone) to Daily, previewedSlot(preview(Evening, saverOff)))
        assertEquals(none(NoReminderReason.SaverOff), preview(Late, saverOff))
    }

    @Test
    fun previewSaysWhyThereWouldBeNoReminder() {
        assertEquals(none(NoReminderReason.ReminderOff), preview(Daily, morning.copy(dailyReminder = false)))
        assertEquals(none(NoReminderReason.SaverOff), preview(Evening, morning.copy(streakSaver = false)))
        assertEquals(none(NoReminderReason.ReadToday), preview(Daily, readToday = true))
        assertEquals(none(NoReminderReason.NothingUnread), preview(Late, unread = 0))
        assertEquals(none(NoReminderReason.NothingUnread), preview(Daily, shortest = null))
        assertEquals(none(NoReminderReason.NoStreak), preview(LastCall, streak = 0))
        // The settings come first, then the day.
        assertEquals(none(NoReminderReason.ReminderOff), preview(Daily, morning.copy(dailyReminder = false), readToday = true, unread = 0))
    }

    @Test
    fun shortestUnreadPrefersFewestMinutesThenNewest() {
        val items = listOf(
            item("unknown", readingMinutes = null),
            item("ten", readingMinutes = 10),
            item("threeOld", addedAt = T0 - 2.days, readingMinutes = 3),
            item("threeNew", addedAt = T0 - 1.days, readingMinutes = 3),
            item("oneRead", readAt = T0, readingMinutes = 1),
            item("oneArchived", archivedAt = T0, readingMinutes = 1),
            item("oneDeleted", deletedAt = T0, readingMinutes = 1),
        )
        assertEquals("threeNew", ReminderPlanner.shortestUnread(items)?.id?.value)
        assertEquals("unknown", ReminderPlanner.shortestUnread(listOf(items[0]))?.id?.value)
        assertEquals("ten", ReminderPlanner.shortestUnread(items.take(2))?.id?.value)
        assertEquals(null, ReminderPlanner.shortestUnread(items.drop(4)))
    }
}
