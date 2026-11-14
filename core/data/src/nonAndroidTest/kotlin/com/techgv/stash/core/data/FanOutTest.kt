package com.techgv.stash.core.data

import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.domain.WidgetSnapshotPolicy
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.ScheduledNotification
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import com.techgv.stash.core.model.AppLanguage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** After a commit: reminders are re-planned, the widget republished and metadata scheduled, each step on its own. */
class FanOutTest {
    private val morning = FIXTURE_NOW - 2.hours // 08:00 UTC, before the 9 AM daily reminder

    private suspend fun scheduled(plan: List<PlannedNotification>) =
        plan.map { val text = testRenderer.render(it); ScheduledNotification(it.id, it.fireAt, text.title, text.body, it.itemId) }

    private fun expectedPlan(preferences: UserPreferences, streak: StreakState, unread: List<SavedItem>, data: TestData) =
        ReminderPlanner.plan(preferences, streak.count, streak.readToday, unread.size, ReminderPlanner.shortestUnread(unread), data.clock.now, TimeZone.UTC)

    private suspend fun TestData.assertPlanFollowsState() {
        settle()
        val unread = strings("SELECT id FROM saved_item WHERE deleted_at IS NULL AND archived_at IS NULL AND read_at IS NULL").map { item(it)!! }
        val expected = expectedPlan(settings.preferences.first(), streak.observeStreak().first(), unread, this)
        assertEquals(scheduled(expected), scheduler.scheduled)
        assertEquals(expected, database.plannedNotificationDao().all().map { it.toModel() }, "the stored plan is the applied plan")
    }

    @Test
    fun theSchedulerAlwaysGetsThePlanForTheCurrentState() = runTest {
        val data = TestData(this)
        data.clock.now = morning
        data.insert(savedItem("long", addedAt = morning - 3.days, readingMinutes = 12), savedItem("seen", ItemLifecycle.Read, addedAt = morning - 3.days))
        data.clock.now = morning - 1.days
        data.open("seen") // a streak of 1 from yesterday
        data.clock.now = morning

        data.save("https://example.com/new")
        data.assertPlanFollowsState()
        assertEquals(5, data.scheduler.scheduled.size, "before 9 AM, unread, streak alive: today's four and tomorrow's daily")

        data.bus.dispatch(Command.MarkRead(ItemId("long")))
        data.assertPlanFollowsState()
        assertTrue(data.scheduler.scheduled.none { it.fireAt < FIXTURE_NOW + 14.hours }, "a read cancels today's remaining nudges")

        data.open("seen")
        data.assertPlanFollowsState()

        data.bus.dispatch(Command.MarkUnread(ItemId("long")))
        data.assertPlanFollowsState()

        data.bus.dispatch(Command.ChangeSetting { it.copy(dailyReminder = false, streakSaver = false) })
        data.assertPlanFollowsState()
        assertEquals(emptyList(), data.scheduler.scheduled)
    }

    @Test
    fun nothingUnreadMeansAnEmptyPlan() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 1.days))
        data.bus.dispatch(Command.Archive(ItemId("a")))
        data.settle()

        assertEquals(emptyList(), data.scheduler.scheduled)
        assertEquals(1, data.scheduler.applyCount)
        assertEquals(emptyList(), data.database.plannedNotificationDao().all())
    }

    @Test
    fun theWidgetGetsThePolicySnapshotAfterEachCommand() = runTest {
        val data = TestData(this)
        val items = List(5) { savedItem("i$it", addedAt = FIXTURE_NOW - it.days) } + savedItem("read", ItemLifecycle.Read)
        data.insert(*items.toTypedArray())

        data.bus.dispatch(Command.Archive(ItemId("i0")))
        data.settle()

        val unread = items.drop(1).filter { it.lifecycle == ItemLifecycle.Unread }
        val expected = WidgetSnapshotPolicy.snapshot(unread, unread.size, data.streak.observeStreak().first())
        assertEquals(listOf(expected), data.widget.published)
        assertEquals(listOf("i1", "i2", "i3"), expected.items.map { it.id.value })
    }

    @Test
    fun aBurstOfCommandsCoalesces() = runTest {
        val data = TestData(this)
        data.insert(*List(30) { savedItem("i$it", addedAt = FIXTURE_NOW - it.days) }.toTypedArray())

        repeat(30) { data.bus.dispatch(Command.MarkRead(ItemId("i$it"))) }
        data.settle()

        assertEquals(1, data.scheduler.applyCount, "30 commands, one re-plan")
        assertEquals(emptyList(), data.scheduler.scheduled, "the last plan reflects the last command")
        assertTrue(data.widget.published.last().allCaughtUp)
    }

    @Test
    fun aFailingFanOutStepNeitherFailsTheCommandNorStopsTheOthers() = runTest {
        for (broken in listOf("scheduler", "widget", "jobs")) {
            val data = TestData(this)
            data.failing += broken

            val result = data.save("https://example.com/a")
            data.settle()

            assertIs<DispatchResult.Done>(result, broken)
            assertEquals(listOf("id-1"), data.itemIds(), "$broken: the save stays")
            if (broken != "scheduler") assertEquals(1, data.scheduler.applyCount, "$broken: reminders still planned")
            if (broken != "widget") assertEquals(1, data.widget.published.size, "$broken: widget still published")
            if (broken != "jobs") assertEquals(listOf<BackgroundJob>(BackgroundJob.FetchMetadata(ItemId("id-1"))), data.jobs.scheduled)

            data.failing.clear()
            data.bus.dispatch(Command.MarkRead(ItemId("id-1")))
            data.settle()
            assertEquals(emptyList<ScheduledNotification>(), data.scheduler.scheduled, "$broken: recovers on the next command")
            data.close()
        }
    }

    @Test
    fun aStrayCancellationOrErrorInAStepDoesNotStopLaterRefreshes() = runTest {
        for (stray in listOf<Throwable>(CancellationException("a platform timeout"), NotImplementedError("a platform bug"))) {
            val data = TestData(this)
            data.insert(savedItem("a", addedAt = FIXTURE_NOW - 1.days))
            var calls = 0
            data.beforeApply = { if (calls++ == 0) throw stray }

            data.bus.dispatch(Command.Archive(ItemId("a")))
            data.settle()
            assertEquals(0, data.scheduler.applyCount, "$stray: the first apply failed")

            data.bus.dispatch(Command.Restore(ItemId("a")))
            data.settle()
            assertEquals(1, data.scheduler.applyCount, "$stray: the worker is still running")
            data.close()
        }
    }

    @Test
    fun aSlowerOlderRefreshCannotOverwriteANewerPlan() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 1.days), savedItem("b", addedAt = FIXTURE_NOW - 1.days))
        val released = CompletableDeferred<Unit>()
        var first = true
        data.beforeApply = { if (first) { first = false; released.await() } }

        val older = launch { data.effects.refreshNow() } // reads "a" unread, then waits at apply
        runCurrent()
        data.markRead("a")
        val newer = launch { data.sync.onTimeChanged() } // reads "a" read
        runCurrent()
        released.complete(Unit)
        older.join()
        newer.join()

        // Read today with a streak of 1: tomorrow's daily reminder and saver; the stale plan held only the daily.
        assertEquals(4, data.scheduler.scheduled.size)
        assertEquals(data.scheduler.scheduled.map { it.id }, data.database.plannedNotificationDao().all().map { it.id })
    }

    @Test
    fun theWidgetAndThePlanRefreshAtLocalMidnight() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW - 1.days), savedItem("b", addedAt = FIXTURE_NOW - 1.days))
        data.markRead("a")
        data.settle()
        assertEquals(true, data.widget.published.last().readToday)
        val published = data.widget.published.size

        data.clock.now = FIXTURE_NOW + 14.hours - 1.minutes // 23:59
        data.clock.advanceBy(1.minutes)
        advanceTimeBy(14.hours)
        data.settle()

        assertEquals(published + 1, data.widget.published.size)
        assertEquals(false, data.widget.published.last().readToday, "a new day: not read yet")
    }

    @Test
    fun aLanguageChangeReplans() = runTest {
        val data = TestData(this)
        data.save("https://example.com/a")
        data.settle()
        val plans = data.scheduler.applyCount

        data.bus.dispatch(Command.ChangeSetting { it.copy(language = AppLanguage.Tamil) })
        data.settle()

        assertEquals(plans + 1, data.scheduler.applyCount)
    }
}
