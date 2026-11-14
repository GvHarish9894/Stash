package com.techgv.stash.core.data

import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.StreakDay
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Decision D13: `unreadZeroSince` follows every command that changes the unread count, in the same transaction. */
class UnreadZeroSinceTest {
    private val earlier = FIXTURE_NOW - 10.days

    private suspend fun TestData.unreadZeroSince() = database.appStateDao().unreadZeroSince()

    private suspend fun TestData.streakDays() = database.streakDayDao().all()

    @Test
    fun aFreshInstallStartsWithNull() = runTest {
        assertNull(TestData(this).unreadZeroSince())
    }

    @Test
    fun theLastUnreadLeavingTheShelfSetsItToNow() = runTest {
        val commands = listOf(Command::MarkRead, Command::Remove, Command::Archive)
        for (command in commands) {
            val data = TestData(this)
            data.insert(savedItem("a", addedAt = earlier), savedItem("b", addedAt = earlier))
            data.bus.dispatch(command(ItemId("a")))
            assertNull(data.unreadZeroSince(), "one unread is left")

            data.bus.dispatch(command(ItemId("b")))

            assertEquals(FIXTURE_NOW, data.unreadZeroSince(), "after the last unread")
            data.close()
        }
    }

    @Test
    fun aSaveOrMarkUnreadOnAnEmptyShelfStoresTheRestDaysAndClearsIt() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = earlier))
        data.clock.now = FIXTURE_NOW - 3.days // Nov 9, 10:00
        data.bus.dispatch(Command.MarkRead(ItemId("a")))
        assertEquals(FIXTURE_NOW - 3.days, data.unreadZeroSince())

        data.clock.now = FIXTURE_NOW // Nov 12: Nov 9, 10 and 11 ended with an empty shelf
        data.bus.dispatch(Command.MarkUnread(ItemId("a")))

        assertNull(data.unreadZeroSince())
        assertEquals(
            listOf(StreakDay(LocalDate(2026, 11, 9), FIXTURE_NOW - 3.days), StreakDay(LocalDate(2026, 11, 10), null), StreakDay(LocalDate(2026, 11, 11), null)),
            data.streakDays(),
            "the read day from MarkRead, then rest rows for the past days, never for today",
        )
    }

    @Test
    fun aRestRowNeverReplacesAReadDay() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = earlier))
        data.clock.now = FIXTURE_NOW - 2.days
        data.bus.dispatch(Command.MarkRead(ItemId("a"))) // records Nov 10 as a read day
        data.clock.now = FIXTURE_NOW

        data.save("https://example.com/new")

        assertEquals(
            listOf(StreakDay(LocalDate(2026, 11, 10), FIXTURE_NOW - 2.days), StreakDay(LocalDate(2026, 11, 11), null)),
            data.streakDays(),
        )
    }

    @Test
    fun reshareAndUndoMoveItBothWays() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", ItemLifecycle.Archived, addedAt = earlier, url = "https://example.com/a"))
        data.insert(savedItem("b", addedAt = earlier))
        val archiveB = (data.bus.dispatch(Command.Archive(ItemId("b"))) as DispatchResult.Done).undoOpId!!
        assertEquals(FIXTURE_NOW, data.unreadZeroSince())

        data.clock.now = FIXTURE_NOW + 1.hours
        data.bus.dispatch(Command.Undo(archiveB))
        assertNull(data.unreadZeroSince(), "undo brought an unread item back")

        data.bus.dispatch(Command.Archive(ItemId("b")))
        assertEquals(FIXTURE_NOW + 1.hours, data.unreadZeroSince())
        data.save("https://example.com/a")
        assertNull(data.unreadZeroSince(), "a re-share is unread again")
        assertEquals(emptyList(), data.streakDays(), "same day: no rest row")
    }

    @Test
    fun aFutureValueIsCappedAtNow() = runTest {
        val data = TestData(this)
        data.database.appStateDao().setUnreadZeroSince(FIXTURE_NOW + 5.days) // a clock that ran ahead
        data.clock.now = FIXTURE_NOW + 2.days

        data.save("https://example.com/a")

        // Capped at now (Nov 14), the shelf has been empty since today: nothing before it is a rest day.
        assertEquals(emptyList(), data.database.streakDayDao().between(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1)).map { it.toModel() })
        assertNull(data.unreadZeroSince())
    }
}
