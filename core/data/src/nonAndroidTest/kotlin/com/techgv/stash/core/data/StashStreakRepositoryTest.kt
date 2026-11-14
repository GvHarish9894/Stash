package com.techgv.stash.core.data

import androidx.room.useWriterConnection
import app.cash.turbine.test
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.DayMark
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.StreakDay
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Decision D21: MarkRead and OpenItem record the streak day in their own transaction and return the celebration. */
class StashStreakRepositoryTest {
    private fun celebration(result: DispatchResult) = (result as DispatchResult.Done).celebration

    private suspend fun TestData.streakDays(): List<StreakDay> = database.streakDayDao().between(LocalDate(2000, 1, 1), LocalDate(2100, 1, 1)).map { it.toModel() }

    @Test
    fun markReadCelebratesTheFirstReadOfTheDayOnceAndMidnightStartsANewDay() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 5.days), savedItem("b", addedAt = FIXTURE_NOW - 5.days))

        assertEquals(Celebration(from = 0, to = 1, milestone = null), celebration(data.markRead("a")))
        data.clock.advanceBy(1.hours)
        assertEquals(null, celebration(data.markRead("b")), "second read the same day")

        data.clock.now = FIXTURE_NOW + 14.hours // 00:00 on Nov 13
        assertEquals(Celebration(from = 1, to = 2, milestone = null), celebration(data.open("a")), "opening a read item counts")
    }

    @Test
    fun openingAnAlreadyReadItemRecordsTheDayAndChangesNothingElse() = runTest {
        val data = TestData(this)
        val read = savedItem("a", ItemLifecycle.Read, addedAt = FIXTURE_NOW - 5.days)
        data.insert(read)

        assertEquals(Celebration(0, 1, null), celebration(data.open("a")))

        assertEquals(read, data.item("a"))
        assertEquals(emptyList(), data.operations())
        assertEquals(listOf(StreakDay(LocalDate(2026, 11, 12), FIXTURE_NOW)), data.streakDays())
    }

    @Test
    fun openingWithMarkReadOnOpenOffRecordsTheDayAndLeavesTheItemUnread() = runTest {
        val data = TestData(this)
        val unread = savedItem("a", addedAt = FIXTURE_NOW - 5.days)
        data.insert(unread)
        data.bus.dispatch(Command.ChangeSetting { it.copy(markReadOnOpen = false) })

        assertEquals(Celebration(0, 1, null), celebration(data.open("a")))

        assertEquals(unread, data.item("a"))
        assertEquals(1, data.streakDays().size)

        data.bus.dispatch(Command.ChangeSetting { it.copy(markReadOnOpen = true) })
        data.open("a")
        assertEquals(ItemLifecycle.Read, data.item("a")!!.lifecycle, "with the setting on, opening marks read")
    }

    @Test
    fun openingADeletedOrMissingItemIsRejectedAndRecordsNothing() = runTest {
        val data = TestData(this)
        data.insert(savedItem("gone", ItemLifecycle.Deleted, addedAt = FIXTURE_NOW - 5.days))

        assertEquals(DispatchResult.Rejected(Rejection.InvalidTransition(ItemLifecycle.Deleted, OperationType.MarkRead)), data.open("gone"))
        assertEquals(DispatchResult.Rejected(Rejection.NotFound), data.open("missing"))
        assertEquals(emptyList(), data.streakDays())
    }

    @Test
    fun theItemChangeAndTheStreakDayCommitOrRollBackTogether() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 5.days))
        // The streak row is written after the item and its operation; failing it must undo them too.
        data.database.useWriterConnection { connection ->
            connection.usePrepared("CREATE TEMP TRIGGER no_streak BEFORE INSERT ON streak_day BEGIN SELECT RAISE(ABORT, 'disk full'); END") { it.step() }
        }

        assertEquals(DispatchResult.Rejected(Rejection.StorageFailure), data.markRead("a"))

        assertEquals(ItemLifecycle.Unread, data.item("a")!!.lifecycle)
        assertEquals(emptyList(), data.operations())
        assertEquals(emptyList(), data.streakDays())
    }

    @Test
    fun anEmptyShelfDayWhileTheAppIsClosedKeepsTheStreak() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 5.days))
        data.markRead("a") // the shelf is empty from Nov 12, 10:00

        data.clock.advanceBy(2.days) // nothing happens on Nov 13; Nov 14, 10:00
        val saved = (data.save("https://example.com/new") as DispatchResult.Done).itemId!!.value

        assertEquals(Celebration(from = 1, to = 2, milestone = null), celebration(data.open(saved)))
    }

    @Test
    fun aReadOnAStillEmptyShelfCountsTheEmptyDaysBeforeIt() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 5.days), savedItem("old", ItemLifecycle.Archived, addedAt = FIXTURE_NOW - 9.days))
        data.clock.now = FIXTURE_NOW - 2.days
        data.markRead("a") // Nov 10: read, and nothing is unread from then on
        data.clock.now = FIXTURE_NOW // Nov 12, the shelf is still empty: Nov 11 was a rest day

        assertEquals(1, data.streak.observeStreak().first().count)
        assertEquals(Celebration(from = 1, to = 2, milestone = null), celebration(data.open("old")))
    }

    @Test
    fun aDayWithSomethingUnreadAndNoReadBreaksIt() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 5.days), savedItem("b", addedAt = FIXTURE_NOW - 5.days))
        data.markRead("a")

        data.clock.advanceBy(2.days)

        assertEquals(Celebration(from = 0, to = 1, milestone = null), celebration(data.markRead("b")))
    }

    private fun TestScope.passTime(data: TestData, duration: Duration) {
        data.clock.advanceBy(duration)
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun observeStreakReEmitsAtMidnightAnd2130() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 5.days), savedItem("seen", ItemLifecycle.Read, addedAt = FIXTURE_NOW - 5.days))
        data.clock.now = FIXTURE_NOW - 1.days
        data.open("seen") // read yesterday, Nov 11
        data.clock.now = FIXTURE_NOW + 11.hours + 29.minutes // Nov 12, 21:29, not read today

        data.streak.observeStreak().test {
            val before = awaitItem()
            assertEquals(StreakStatus.Unlit, before.status)
            assertEquals(1, before.count)

            passTime(data, 1.minutes)
            assertEquals(StreakStatus.AtRisk, awaitItem().status, "21:30 with something unread")

            passTime(data, 2.hours + 30.minutes)
            val nextDay = awaitItem()
            assertEquals(0, nextDay.count, "Nov 12 ended with something unread and no read")
            assertEquals(DayMark.NotRead, nextDay.week[5].mark)
            assertEquals(LocalDate(2026, 11, 13), nextDay.week.last().date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theTicksFollowTheUsersZoneNotUtc() = runTest {
        val newYork = TimeZone.of("America/New_York")
        val data = TestData(this, zone = newYork)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 5.days), savedItem("seen", ItemLifecycle.Read, addedAt = FIXTURE_NOW - 5.days))
        data.clock.now = LocalDateTime(2026, 11, 11, 12, 0).toInstant(newYork)
        data.open("seen") // read on Nov 11 in New York
        data.clock.now = LocalDateTime(2026, 11, 12, 21, 29).toInstant(newYork) // 02:29 UTC on Nov 13

        data.streak.observeStreak().test {
            val before = awaitItem()
            assertEquals(LocalDate(2026, 11, 12), before.week.last().date)
            assertEquals(StreakStatus.Unlit, before.status)

            passTime(data, 1.minutes)
            assertEquals(StreakStatus.AtRisk, awaitItem().status, "21:30 in New York")

            passTime(data, 2.hours + 30.minutes)
            assertEquals(LocalDate(2026, 11, 13), awaitItem().week.last().date, "midnight in New York")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeStreakFollowsReadsAndAZoneChange() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 5.days))

        data.streak.observeStreak().test {
            assertEquals(0, awaitItem().count)
            data.markRead("a")
            runCurrent()
            var lit = awaitItem()
            while (lit.status != StreakStatus.Lit) lit = awaitItem()
            assertEquals(1, lit.count)

            data.zone = TimeZone.of("Pacific/Kiritimati") // UTC+14: it is already 00:00 on Nov 13 there
            data.sync.onTimeChanged()
            runCurrent()
            val travelled = awaitItem()
            assertEquals(false, travelled.readToday)
            assertEquals(LocalDate(2026, 11, 13), travelled.week.last().date)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
