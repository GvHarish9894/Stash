package com.techgv.stash.feature.widgetpreview

import app.cash.turbine.test
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.StreakDay
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.model.WidgetSize
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.settings_save_failed
import com.techgv.stash.core.resources.toast_pin_requested
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetPreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Env(items: List<SavedItem>, prefs: UserPreferences) {
        val clock = TestClock()
        val repo = FakeItemRepository(items, clock)
        val bus = FakeCommandBus(repo, FakeSettingsRepository(prefs))
        val vm = WidgetPreviewViewModel(bus.settings, repo, bus.streak, bus, clock, repo.zones)
        val effects = mutableListOf<WidgetPreviewEffect>()
        val state get() = vm.state.value
    }

    private fun TestScope.env(items: List<SavedItem> = emptyList(), prefs: UserPreferences = UserPreferences(), collectEffects: Boolean = true): Env {
        val env = Env(items, prefs)
        backgroundScope.launch { env.vm.state.collect {} }
        if (collectEffects) backgroundScope.launch { env.vm.effects.collect { env.effects += it } }
        runCurrent()
        return env
    }

    private fun TestScope.send(env: Env, intent: WidgetPreviewIntent) {
        env.vm.onIntent(intent)
        runCurrent()
    }

    /** Unread items added [n] minutes apart, "u1" newest. */
    private fun unread(n: Int) = (1..n).map { savedItem("u$it", addedAt = FIXTURE_NOW - it.minutes, title = "Unread $it") }

    @Test
    fun noUnreadItemsIsAllCaughtUp() = runTest(dispatcher) {
        val env = env(listOf(savedItem("r", ItemLifecycle.Read)))
        with(env.state) {
            assertTrue(loaded)
            assertTrue(items.isEmpty())
            assertEquals(0, unreadCount)
        }
    }

    @Test
    fun oneUnreadItemIsTheOnlyRow() = runTest(dispatcher) {
        val env = env(unread(1) + savedItem("r", ItemLifecycle.Read))
        assertEquals(listOf("u1"), env.state.items.map { it.id })
        assertEquals("Unread 1", env.state.items.single().title)
        assertEquals(1, env.state.unreadCount)
    }

    @Test
    fun threeOrMoreUnreadShowTheNewestThreeAndTheFullCount() = runTest(dispatcher) {
        val env = env(unread(5))
        assertEquals(listOf("u1", "u2", "u3"), env.state.items.map { it.id })
        assertEquals(5, env.state.unreadCount)
    }

    @Test
    fun rowsFollowItemChanges() = runTest(dispatcher) {
        val env = env(unread(4))
        env.bus.dispatch(Command.MarkRead(ItemId("u1")))
        runCurrent()
        assertEquals(listOf("u2", "u3", "u4"), env.state.items.map { it.id })
        assertEquals(3, env.state.unreadCount)
    }

    @Test
    fun streakSizeAndIconComeFromTheStoredData() = runTest(dispatcher) {
        val env = env(unread(2), UserPreferences(widgetSize = WidgetSize.Small, appIcon = AppIcon.Ink))
        env.bus.streak.days.value = listOf(StreakDay(LocalDate(2026, 11, 11), FIXTURE_NOW - 24.hours))
        runCurrent()
        with(env.state) {
            assertEquals(WidgetSize.Small, size)
            assertEquals(AppIcon.Ink, appIcon)
            assertEquals(1, streak)
            assertEquals(StreakStatus.Unlit, streakStatus)
        }
        env.bus.dispatch(Command.MarkRead(ItemId("u2")))
        runCurrent()
        assertEquals(2, env.state.streak)
        assertEquals(StreakStatus.Lit, env.state.streakStatus)
    }

    @Test
    fun sizeIsStoredAndOnlyTheSize() = runTest(dispatcher) {
        val env = env()
        send(env, WidgetPreviewIntent.SetSize(WidgetSize.Small))
        assertEquals(UserPreferences(widgetSize = WidgetSize.Small), env.bus.settings.preferences.value)
        assertEquals(WidgetSize.Small, env.state.size)
    }

    @Test
    fun aRefusedSizeChangeSaysSo() = runTest(dispatcher) {
        val env = env()
        env.bus.rejectNext(Rejection.StorageFailure)
        send(env, WidgetPreviewIntent.SetSize(WidgetSize.Small))
        assertEquals(WidgetSize.Medium, env.state.size)
        assertEquals(text(Res.string.settings_save_failed), env.state.toast)
    }

    @Test
    fun whenPinningIsPossibleSharedPinsTheChosenSize() = runTest(dispatcher) {
        val env = env(prefs = UserPreferences(widgetSize = WidgetSize.Small), collectEffects = false)
        env.vm.effects.test {
            env.vm.onIntent(WidgetPreviewIntent.AddToHomeScreen(canPin = true))
            assertEquals(WidgetPreviewEffect.PinWidget(WidgetSize.Small), awaitItem())
        }
        send(env, WidgetPreviewIntent.PinResult(dialogShown = true))
        assertEquals(text(Res.string.toast_pin_requested), env.state.toast)
        assertFalse(env.state.showSteps)
        advanceTimeBy(WidgetPreviewViewModel.TOAST_DURATION + 1.milliseconds)
        runCurrent()
        assertNull(env.state.toast)
    }

    @Test
    fun whenPinningIsNotPossibleTheStepsShow() = runTest(dispatcher) {
        val env = env()
        send(env, WidgetPreviewIntent.AddToHomeScreen(canPin = false))
        assertTrue(env.effects.isEmpty())
        assertTrue(env.state.showSteps)
    }

    @Test
    fun whenTheSystemDialogDidNotShowTheStepsShow() = runTest(dispatcher) {
        val env = env()
        send(env, WidgetPreviewIntent.AddToHomeScreen(canPin = true))
        send(env, WidgetPreviewIntent.PinResult(dialogShown = false))
        assertTrue(env.state.showSteps)
        assertNull(env.state.toast)
    }

    @Test
    fun backReturnsToSettings() = runTest(dispatcher) {
        val env = env()
        send(env, WidgetPreviewIntent.Back)
        assertEquals(listOf<WidgetPreviewEffect>(WidgetPreviewEffect.Back), env.effects)
    }
}
