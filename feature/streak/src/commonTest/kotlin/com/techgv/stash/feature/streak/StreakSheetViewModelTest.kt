package com.techgv.stash.feature.streak

import app.cash.turbine.test
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.DayMark
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.StreakDay
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.FakeCommandBus
import com.techgv.stash.core.testing.FakeItemRepository
import com.techgv.stash.core.testing.FakeSettingsRepository
import com.techgv.stash.core.testing.TestClock
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StreakSheetViewModelTest {
    private val scheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Time is FIXTURE_NOW (10:00 UTC); yesterday was read, so the streak is 1. */
    private class Sheet(scope: TestScope, items: List<SavedItem>, preferences: UserPreferences = UserPreferences()) {
        val clock = TestClock()
        val repo = FakeItemRepository(items, clock)
        val settings = FakeSettingsRepository(preferences)
        val bus = FakeCommandBus(repo, settings)
        val vm = StreakSheetViewModel(bus.streak, settings, repo, bus)

        init {
            bus.streak.days.value = listOf(StreakDay(LocalDate(2026, 11, 11), FIXTURE_NOW - 24.hours))
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    private val shelf = listOf(savedItem("long", readingMinutes = 20), savedItem("short", readingMinutes = 3))

    @Test
    fun stateFollowsTheStreakTheShelfAndTheReminderSettings() = runTest(scheduler) {
        val sheet = Sheet(this, shelf)
        val state = sheet.vm.state.value
        assertEquals(1, state.streak?.count)
        assertEquals(StreakStatus.Unlit, state.streak?.status)
        assertTrue(state.hasUnread)
        assertEquals(ReminderTime.Morning, state.reminderTime)
        assertTrue(state.streakSaver)

        sheet.settings.preferences.value = UserPreferences(dailyReminder = false, streakSaver = false)
        sheet.repo.items.value = listOf(savedItem("a", lifecycle = ItemLifecycle.Read))
        runCurrent()
        assertNull(sheet.vm.state.value.reminderTime)
        assertFalse(sheet.vm.state.value.streakSaver)
        assertFalse(sheet.vm.state.value.hasUnread)
    }

    @Test
    fun theStreakIsAtRiskFrom2130AndTheDayTurnsAtMidnight() = runTest(scheduler) {
        val sheet = Sheet(this, shelf)
        sheet.clock.now = Instant.parse("2026-11-12T21:30:00Z")
        runCurrent()
        assertEquals(StreakStatus.AtRisk, sheet.vm.state.value.streak?.status)

        sheet.vm.onIntent(StreakSheetIntent.ReadShort)
        runCurrent()
        assertEquals(StreakStatus.Lit, sheet.vm.state.value.streak?.status)
        assertEquals(DayMark.Read, sheet.vm.state.value.streak?.week?.last()?.mark)

        sheet.clock.now = Instant.parse("2026-11-13T00:00:00Z")
        runCurrent()
        val next = sheet.vm.state.value.streak
        assertEquals(2, next?.count)
        assertEquals(false, next?.readToday)
        assertEquals(StreakStatus.Unlit, next?.status)
    }

    @Test
    fun readSomethingShortOpensTheBrowserWhenLinksOpenThereAndToastsTheResult() = runTest(scheduler) {
        val sheet = Sheet(this, shelf, UserPreferences(openLinksIn = OpenLinksIn.Browser))
        sheet.vm.effects.test {
            sheet.vm.onIntent(StreakSheetIntent.ReadShort)
            assertEquals(StreakSheetEffect.OpenInBrowser(shelf[1].url), awaitItem())
            assertEquals(StreakSheetEffect.Celebrate(Celebration(from = 1, to = 2, milestone = null)), awaitItem())
        }
        assertEquals(listOf<Command>(Command.OpenItem(ItemId("short"))), sheet.bus.dispatched)

        sheet.vm.onIntent(StreakSheetIntent.BrowserOpened(opened = true))
        runCurrent()
        assertEquals(StreakToast.OpenedInBrowser, sheet.vm.state.value.toast)
        advanceTimeBy(StreakSheetViewModel.TOAST_MILLIS + 1)
        assertNull(sheet.vm.state.value.toast)

        sheet.vm.onIntent(StreakSheetIntent.BrowserOpened(opened = false))
        runCurrent()
        assertEquals(StreakToast.BrowserFailed, sheet.vm.state.value.toast)
    }

    @Test
    fun readSomethingShortOpensTheShortestUnreadAndCelebratesOncePerDay() = runTest(scheduler) {
        val sheet = Sheet(this, shelf)
        sheet.vm.effects.test {
            sheet.vm.onIntent(StreakSheetIntent.ReadShort)
            assertEquals(StreakSheetEffect.OpenReader(ItemId("short"), markedRead = true), awaitItem())
            assertEquals(StreakSheetEffect.Celebrate(Celebration(from = 1, to = 2, milestone = null)), awaitItem())
            sheet.vm.onIntent(StreakSheetIntent.ReadShort)
            assertEquals(StreakSheetEffect.OpenReader(ItemId("long"), markedRead = true), awaitItem())
            runCurrent()
            expectNoEvents()
        }
        assertEquals(listOf<Command>(Command.OpenItem(ItemId("short")), Command.OpenItem(ItemId("long"))), sheet.bus.dispatched)
    }

    @Test
    fun aFailedReadStillOpensThePage() = runTest(scheduler) {
        val sheet = Sheet(this, shelf)
        sheet.bus.rejectNext(Rejection.StorageFailure)
        sheet.vm.effects.test {
            sheet.vm.onIntent(StreakSheetIntent.ReadShort)
            assertEquals(StreakSheetEffect.OpenReader(ItemId("short"), markedRead = false), awaitItem())
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun nothingUnreadOpensNothing() = runTest(scheduler) {
        val sheet = Sheet(this, listOf(savedItem("a", lifecycle = ItemLifecycle.Read)))
        sheet.vm.effects.test {
            sheet.vm.onIntent(StreakSheetIntent.ReadShort)
            runCurrent()
            expectNoEvents()
        }
        assertEquals(emptyList<Command>(), sheet.bus.dispatched)
    }

    @Test
    fun remindersOpensSettings() = runTest(scheduler) {
        val sheet = Sheet(this, shelf)
        sheet.vm.effects.test {
            sheet.vm.onIntent(StreakSheetIntent.OpenReminders)
            assertEquals(StreakSheetEffect.OpenReminders, awaitItem())
        }
    }
}
