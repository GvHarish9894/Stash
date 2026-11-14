package com.techgv.stash.feature.settings

import app.cash.turbine.test
import com.techgv.stash.core.designsystem.presentation.label
import com.techgv.stash.core.designsystem.presentation.plural
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.ReminderText
import com.techgv.stash.core.domain.ReminderTextRenderer
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.AppLanguage
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.model.StreakDay
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.settings_app_icon_failed
import com.techgv.stash.core.resources.settings_preview_no_streak
import com.techgv.stash.core.resources.settings_save_failed
import com.techgv.stash.core.resources.time_7_00_pm
import com.techgv.stash.core.resources.time_9_00_am
import com.techgv.stash.core.resources.time_9_30_pm
import com.techgv.stash.core.resources.toast_app_icon_changed
import com.techgv.stash.core.resources.toast_archive_later
import com.techgv.stash.core.resources.toast_auto_archive_after
import com.techgv.stash.core.resources.toast_auto_archive_off
import com.techgv.stash.core.resources.toast_cleared
import com.techgv.stash.core.resources.toast_moved_to_archive
import com.techgv.stash.core.resources.toast_no_reminder_empty
import com.techgv.stash.core.resources.toast_no_reminder_read
import com.techgv.stash.core.resources.toast_nothing_to_clear
import com.techgv.stash.core.resources.toast_reminder_off
import com.techgv.stash.core.resources.toast_saver_off
import com.techgv.stash.core.resources.toast_tip_back
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// FIXTURE_NOW is 2026-11-12 10:00 UTC; the fakes use UTC.
private val TODAY = LocalDate(2026, 11, 12)

/** Renders the planned reminder's level and parameters, so tests see what the planner chose. */
private val renderer = ReminderTextRenderer { n ->
    ReminderText("${n.level} ${n.copy.streak}", "${n.copy.itemTitle} ${n.copy.minutes} ${n.copy.unreadCount}")
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Env(items: List<SavedItem>, prefs: UserPreferences, now: Instant) {
        val clock = TestClock(now)
        val repo = FakeItemRepository(items, clock)
        val bus = FakeCommandBus(repo, FakeSettingsRepository(prefs))
        val vm = SettingsViewModel(bus.settings, repo, bus.streak, bus, renderer, clock, repo.zones)
        val effects = mutableListOf<SettingsEffect>()
        val prefs get() = bus.settings.preferences.value
        val state get() = vm.state.value

        /** A streak of [count] days read before today, today not read yet. */
        fun streakOf(count: Int) {
            bus.streak.days.value = (1..count).map { back ->
                StreakDay(TODAY.minus(back, DateTimeUnit.DAY), FIXTURE_NOW - back.days)
            }
        }
    }

    private fun TestScope.env(
        items: List<SavedItem> = emptyList(),
        prefs: UserPreferences = UserPreferences(),
        now: Instant = FIXTURE_NOW,
        collectEffects: Boolean = true,
    ): Env {
        val env = Env(items, prefs, now)
        backgroundScope.launch { env.vm.state.collect {} }
        if (collectEffects) backgroundScope.launch { env.vm.effects.collect { env.effects += it } }
        runCurrent()
        return env
    }

    private fun TestScope.send(env: Env, intent: SettingsIntent) {
        env.vm.onIntent(intent)
        runCurrent()
    }

    private fun read(id: String, readAgo: Duration = 1.hours) =
        savedItem(id, ItemLifecycle.Read, addedAt = FIXTURE_NOW - readAgo - 1.hours, readAt = FIXTURE_NOW - readAgo)

    // ---- each setting writes exactly its field -------------------------------------------------------------

    @Test
    fun eachSettingWritesExactlyItsField() = runTest(dispatcher) {
        val base = UserPreferences()
        val cases: List<Pair<SettingsIntent, UserPreferences>> = listOf(
            SettingsIntent.SetTheme(ThemeMode.Night) to base.copy(theme = ThemeMode.Night),
            SettingsIntent.SetAccent(Accent.Forest) to base.copy(accent = Accent.Forest),
            SettingsIntent.SetCardLayout(CardLayout.Compact) to base.copy(cardLayout = CardLayout.Compact),
            SettingsIntent.SetTextSize(TextSize.Large) to base.copy(textSize = TextSize.Large),
            SettingsIntent.SetReduceMotion(true) to base.copy(reduceMotion = true),
            SettingsIntent.SetMarkReadOnOpen(false) to base.copy(markReadOnOpen = false),
            SettingsIntent.SetReaderModeDefault(true) to base.copy(readerModeDefault = true),
            SettingsIntent.SetOpenLinksIn(OpenLinksIn.Browser) to base.copy(openLinksIn = OpenLinksIn.Browser),
            SettingsIntent.SetDailyReminder(false) to base.copy(dailyReminder = false),
            SettingsIntent.SetReminderTime(ReminderTime.Evening) to base.copy(reminderTime = ReminderTime.Evening),
            SettingsIntent.SetStreakSaver(false) to base.copy(streakSaver = false),
            SettingsIntent.SetSwipeStart(SwipeAction.Archive) to base.copy(swipeStartAction = SwipeAction.Archive),
            SettingsIntent.SetSwipeEnd(SwipeAction.Share) to base.copy(swipeEndAction = SwipeAction.Share),
            SettingsIntent.SetSort(ShelfSort.Oldest) to base.copy(sort = ShelfSort.Oldest),
            SettingsIntent.SetGroup(ShelfGrouping.Month) to base.copy(group = ShelfGrouping.Month),
            SettingsIntent.SetAutoArchive(AutoArchivePeriod.Days90) to base.copy(autoArchive = AutoArchivePeriod.Days90),
        )
        for ((intent, expected) in cases) {
            val env = env(prefs = base)
            send(env, intent)
            assertEquals(expected, env.prefs, "$intent")
            assertEquals(1, env.bus.dispatched.count { it is Command.ChangeSetting }, "$intent")
            assertEquals(expected, env.state.prefs, "$intent shows the stored value")
        }
    }

    @Test
    fun choosingAGroupingWhileSortingBySourceGoesBackToNewest() = runTest(dispatcher) {
        val env = env(prefs = UserPreferences(sort = ShelfSort.BySource))
        send(env, SettingsIntent.SetGroup(ShelfGrouping.Week))
        assertEquals(UserPreferences(sort = ShelfSort.Newest, group = ShelfGrouping.Week), env.prefs)
    }

    @Test
    fun showTipAgainClearsOnlyTheDismissedFlag() = runTest(dispatcher) {
        val env = env(prefs = UserPreferences(gestureTipDismissed = true))
        send(env, SettingsIntent.ShowTipAgain)
        assertEquals(UserPreferences(), env.prefs)
        assertEquals(text(Res.string.toast_tip_back), env.state.toast)
    }

    @Test
    fun aRefusedChangeKeepsTheStoredValueAndSaysSo() = runTest(dispatcher) {
        val env = env()
        env.bus.rejectNext(Rejection.StorageFailure)
        send(env, SettingsIntent.SetTheme(ThemeMode.Night))
        assertEquals(ThemeMode.Auto, env.state.prefs.theme)
        assertEquals(text(Res.string.settings_save_failed), env.state.toast)
    }

    @Test
    fun toastGoesAwayAfterItsTime() = runTest(dispatcher) {
        val env = env()
        send(env, SettingsIntent.ClearRead)
        assertEquals(text(Res.string.toast_nothing_to_clear), env.state.toast)
        advanceTimeBy(SettingsViewModel.TOAST_DURATION - 1.milliseconds)
        runCurrent()
        assertNotNull(env.state.toast)
        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertNull(env.state.toast)
    }

    // ---- state from the data layer --------------------------------------------------------------------------

    @Test
    fun stateShowsTheStoredSettingsCountsAndStreak() = runTest(dispatcher) {
        val items = listOf(
            savedItem("u1"), savedItem("u2"), read("r1"),
            savedItem("a1", ItemLifecycle.Archived, addedAt = FIXTURE_NOW - 10.days),
        )
        val env = env(items, UserPreferences(theme = ThemeMode.Parchment))
        assertFalse(SettingsState().loaded, "nothing is drawn before the data arrives")
        env.streakOf(3)
        runCurrent()
        with(env.state) {
            assertTrue(loaded)
            assertEquals(ThemeMode.Parchment, prefs.theme)
            assertEquals(2, unreadCount)
            assertEquals(1, readCount)
            assertEquals(1, archivedCount)
            assertEquals(3, streak)
            assertFalse(readToday)
        }
    }

    @Test
    fun todayEndsAtMidnight() = runTest(dispatcher) {
        val env = env(listOf(savedItem("u1"), savedItem("u2")))
        env.bus.dispatch(Command.MarkRead(ItemId("u1")))
        runCurrent()
        assertTrue(env.state.readToday)
        assertEquals(StreakStatus.Lit, env.state.streakStatus)
        env.clock.advanceBy(14.hours) // 00:00 the next day
        runCurrent()
        assertFalse(env.state.readToday)
        assertEquals(1, env.state.streak)
    }

    // ---- notification permission ----------------------------------------------------------------------------

    @Test
    fun turningAReminderOnAsksFirstAndAppliesWhenGranted() = runTest(dispatcher) {
        val env = env(prefs = UserPreferences(dailyReminder = false))
        send(env, SettingsIntent.SetDailyReminder(true))
        assertEquals(listOf<SettingsEffect>(SettingsEffect.RequestNotificationPermission), env.effects)
        assertFalse(env.prefs.dailyReminder, "nothing changes before the answer")
        assertEquals(ReminderSwitch.DailyReminder, env.state.pendingPermission)
        send(env, SettingsIntent.NotificationPermissionResult(granted = true))
        // The user answered, so the app's own one-time ask (D27) is done too.
        assertEquals(UserPreferences(dailyReminder = true, notificationPermissionAsked = true), env.prefs)
        assertNull(env.state.pendingPermission)
        assertFalse(env.state.notificationsDenied)
    }

    @Test
    fun streakSaverPermissionTurnsOnOnlyTheSaver() = runTest(dispatcher) {
        val env = env(prefs = UserPreferences(dailyReminder = false, streakSaver = false))
        send(env, SettingsIntent.SetStreakSaver(true))
        send(env, SettingsIntent.NotificationPermissionResult(granted = true))
        assertEquals(UserPreferences(dailyReminder = false, streakSaver = true, notificationPermissionAsked = true), env.prefs)
    }

    @Test
    fun aDeniedPermissionLeavesTheReminderOffAndOffersTheSettings() = runTest(dispatcher) {
        val env = env(prefs = UserPreferences(dailyReminder = false), collectEffects = false)
        env.vm.effects.test {
            env.vm.onIntent(SettingsIntent.SetDailyReminder(true))
            assertEquals(SettingsEffect.RequestNotificationPermission, awaitItem())
            env.vm.onIntent(SettingsIntent.NotificationPermissionResult(granted = false))
            runCurrent()
            assertFalse(env.prefs.dailyReminder)
            assertTrue(env.state.notificationsDenied)
            // Only the record that the user was asked (D27): never asked again automatically.
            assertTrue(env.prefs.notificationPermissionAsked)
            env.vm.onIntent(SettingsIntent.OpenNotificationSettings)
            assertEquals(SettingsEffect.OpenNotificationSettings, awaitItem())
        }
    }

    @Test
    fun aLaterGrantHidesTheSettingsOffer() = runTest(dispatcher) {
        val env = env(prefs = UserPreferences(dailyReminder = false, streakSaver = false))
        send(env, SettingsIntent.SetDailyReminder(true))
        send(env, SettingsIntent.NotificationPermissionResult(granted = false))
        send(env, SettingsIntent.SetStreakSaver(true))
        send(env, SettingsIntent.NotificationPermissionResult(granted = true))
        assertFalse(env.state.notificationsDenied)
        assertEquals(UserPreferences(dailyReminder = false, streakSaver = true, notificationPermissionAsked = true), env.prefs)
    }

    @Test
    fun theOffersFollowWhetherTheOsPermitsNotifications() = runTest(dispatcher) {
        val env = env()
        send(env, SettingsIntent.NotificationsPermitted(false))
        assertTrue(env.state.notificationsDenied)
        send(env, SettingsIntent.NotificationsPermitted(true))
        assertFalse(env.state.notificationsDenied)
    }

    @Test
    fun anAnswerWithNoRequestChangesNothing() = runTest(dispatcher) {
        val env = env(prefs = UserPreferences(dailyReminder = false))
        send(env, SettingsIntent.NotificationPermissionResult(granted = true))
        assertFalse(env.prefs.dailyReminder)
    }

    @Test
    fun turningARemindersOffNeedsNoPermission() = runTest(dispatcher) {
        val env = env()
        send(env, SettingsIntent.SetStreakSaver(false))
        assertTrue(env.effects.isEmpty())
        assertFalse(env.prefs.streakSaver)
    }

    // ---- auto-archive ----------------------------------------------------------------------------------------

    @Test
    fun aPeriodThatMovesNothingIsSavedWithoutAConfirm() = runTest(dispatcher) {
        val env = env(listOf(read("r1", readAgo = 3.days)))
        send(env, SettingsIntent.SetAutoArchive(AutoArchivePeriod.Days7))
        assertEquals(AutoArchivePeriod.Days7, env.prefs.autoArchive)
        assertNull(env.state.autoArchiveConfirm)
        assertEquals(plural(Res.plurals.toast_auto_archive_after, 7), env.state.toast)
        send(env, SettingsIntent.SetAutoArchive(AutoArchivePeriod.Off))
        assertEquals(AutoArchivePeriod.Off, env.prefs.autoArchive)
        assertEquals(text(Res.string.toast_auto_archive_off), env.state.toast)
        assertTrue(env.bus.dispatched.none { it is Command.ApplyAutoArchive })
    }

    @Test
    fun aPeriodThatWouldMoveItemsAsksAndMoveNowMovesThem() = runTest(dispatcher) {
        val env = env(listOf(read("r1", readAgo = 40.days), read("r2", readAgo = 35.days), read("r3", readAgo = 1.days), savedItem("u1")))
        send(env, SettingsIntent.SetAutoArchive(AutoArchivePeriod.Days30))
        assertEquals(AutoArchivePeriod.Days30, env.prefs.autoArchive, "saved before the answer")
        assertEquals(AutoArchiveConfirm(AutoArchivePeriod.Days30, 2), env.state.autoArchiveConfirm)
        assertTrue(env.state.hasTopLayer)
        send(env, SettingsIntent.MoveDueNow)
        assertEquals(Command.ApplyAutoArchive(AutoArchivePeriod.Days30), env.bus.dispatched.last())
        assertNull(env.state.autoArchiveConfirm)
        assertEquals(plural(Res.plurals.toast_moved_to_archive, 2), env.state.toast)
        assertEquals(2, env.state.archivedCount)
    }

    @Test
    fun notYetKeepsThePeriodAndMovesNothing() = runTest(dispatcher) {
        val env = env(listOf(read("r1", readAgo = 40.days)))
        send(env, SettingsIntent.SetAutoArchive(AutoArchivePeriod.Days7))
        assertEquals(1, env.state.autoArchiveConfirm?.count)
        send(env, SettingsIntent.MoveDueLater)
        assertNull(env.state.autoArchiveConfirm)
        assertEquals(AutoArchivePeriod.Days7, env.prefs.autoArchive)
        assertEquals(text(Res.string.toast_archive_later), env.state.toast)
        assertTrue(env.bus.dispatched.none { it is Command.ApplyAutoArchive })
        assertEquals(0, env.state.archivedCount)
    }

    @Test
    fun aRefusedMoveSaysSo() = runTest(dispatcher) {
        val env = env(listOf(read("r1", readAgo = 40.days)))
        send(env, SettingsIntent.SetAutoArchive(AutoArchivePeriod.Days30))
        env.bus.rejectNext(Rejection.StorageFailure)
        send(env, SettingsIntent.MoveDueNow)
        assertEquals(text(Res.string.settings_save_failed), env.state.toast)
        assertEquals(0, env.state.archivedCount)
    }

    // ---- clear read ------------------------------------------------------------------------------------------

    @Test
    fun clearReadWithNothingReadSaysSo() = runTest(dispatcher) {
        val env = env(listOf(savedItem("u1")))
        send(env, SettingsIntent.ClearRead)
        assertNull(env.state.clearReadConfirm)
        assertEquals(text(Res.string.toast_nothing_to_clear), env.state.toast)
        assertTrue(env.bus.dispatched.isEmpty())
    }

    @Test
    fun clearReadAsksThenClears() = runTest(dispatcher) {
        val env = env(listOf(read("r1"), read("r2"), savedItem("u1")))
        send(env, SettingsIntent.ClearRead)
        assertEquals(2, env.state.clearReadConfirm)
        assertTrue(env.bus.dispatched.isEmpty(), "nothing is cleared before the confirm")
        send(env, SettingsIntent.ConfirmClearRead)
        assertEquals(listOf<Command>(Command.ClearRead), env.bus.dispatched)
        assertNull(env.state.clearReadConfirm)
        assertEquals(plural(Res.plurals.toast_cleared, 2), env.state.toast)
        assertEquals(0, env.state.readCount)
    }

    @Test
    fun cancellingClearReadClearsNothing() = runTest(dispatcher) {
        val env = env(listOf(read("r1")))
        send(env, SettingsIntent.ClearRead)
        send(env, SettingsIntent.CancelClearRead)
        assertNull(env.state.clearReadConfirm)
        assertTrue(env.bus.dispatched.isEmpty())
        assertEquals(1, env.state.readCount)
    }

    @Test
    fun aRefusedClearSaysSo() = runTest(dispatcher) {
        val env = env(listOf(read("r1")))
        send(env, SettingsIntent.ClearRead)
        env.bus.rejectNext(Rejection.StorageFailure)
        send(env, SettingsIntent.ConfirmClearRead)
        assertEquals(text(Res.string.settings_save_failed), env.state.toast)
        assertEquals(1, env.state.readCount)
    }

    // ---- app icon and language -------------------------------------------------------------------------------

    @Test
    fun anAppIconIsStoredOnlyWhenSharedAppliedIt() = runTest(dispatcher) {
        val env = env()
        send(env, SettingsIntent.ChooseAppIcon(AppIcon.Leather))
        assertTrue(env.effects.isEmpty(), "the current icon needs no change")
        send(env, SettingsIntent.ChooseAppIcon(AppIcon.Night))
        assertEquals(listOf<SettingsEffect>(SettingsEffect.SetAppIcon(AppIcon.Night)), env.effects)
        assertEquals(AppIcon.Leather, env.prefs.appIcon, "not stored before the answer")
        send(env, SettingsIntent.AppIconResult(AppIcon.Night, applied = true))
        assertEquals(UserPreferences(appIcon = AppIcon.Night), env.prefs)
        assertEquals(text(Res.string.toast_app_icon_changed, text(AppIcon.Night.label)), env.state.toast)
    }

    @Test
    fun aRefusedAppIconChangeStoresNothing() = runTest(dispatcher) {
        val env = env()
        send(env, SettingsIntent.ChooseAppIcon(AppIcon.Ink))
        send(env, SettingsIntent.AppIconResult(AppIcon.Ink, applied = false))
        assertEquals(AppIcon.Leather, env.prefs.appIcon)
        assertTrue(env.bus.dispatched.isEmpty())
        assertEquals(text(Res.string.settings_app_icon_failed), env.state.toast)
    }

    @Test
    fun languageIsAppliedBySharedAndNeverStored() = runTest(dispatcher) {
        val env = env()
        send(env, SettingsIntent.ChooseLanguage(AppLanguage.Hindi))
        send(env, SettingsIntent.ChooseLanguage(AppLanguage.System))
        send(env, SettingsIntent.OpenLanguageSettings)
        assertEquals(
            listOf(SettingsEffect.SetLanguage("hi"), SettingsEffect.SetLanguage(null), SettingsEffect.OpenLanguageSettings),
            env.effects,
        )
        assertTrue(env.bus.dispatched.isEmpty())
    }

    @Test
    fun theSelectedLanguageComesFromTheTag() {
        assertEquals(AppLanguage.System, appLanguageOf(null))
        assertEquals(AppLanguage.English, appLanguageOf("en-US"))
        assertEquals(AppLanguage.Tamil, appLanguageOf("ta"))
        assertNull(appLanguageOf("fr"))
    }

    // ---- reminder preview ------------------------------------------------------------------------------------

    private val unreadItems = listOf(
        savedItem("long", title = "A long read", readingMinutes = 20),
        savedItem("short", title = "A short read", readingMinutes = 3),
    )

    @Test
    fun previewShowsTheDailyReminderAsPlannedAndHidesAfterItsTime() = runTest(dispatcher) {
        val env = env(unreadItems)
        env.streakOf(4)
        send(env, SettingsIntent.PreviewReminder(ReminderLevel.Daily, 12.seconds))
        val preview = assertNotNull(env.state.preview)
        assertEquals(ReminderPreview(ReminderLevel.Daily, text(Res.string.time_9_00_am), "Daily 4", "A short read 3 2", ItemId("short")), preview)
        assertEquals(ReminderLevel.Daily, env.state.previewSlot)
        advanceTimeBy(12.seconds - 1.milliseconds)
        runCurrent()
        assertNotNull(env.state.preview)
        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertNull(env.state.preview)
    }

    @Test
    fun anInfiniteTimeoutKeepsThePreviewUntilDismissed() = runTest(dispatcher) {
        val env = env(unreadItems)
        send(env, SettingsIntent.PreviewReminder(ReminderLevel.Daily, Duration.INFINITE))
        advanceTimeBy(10.days)
        runCurrent()
        assertNotNull(env.state.preview)
        send(env, SettingsIntent.DismissPreview)
        assertNull(env.state.preview)
    }

    @Test
    fun previewOfAStreakSaverSlotUsesItsTimeAndLevel() = runTest(dispatcher) {
        val env = env(unreadItems)
        env.streakOf(6)
        send(env, SettingsIntent.PreviewReminder(ReminderLevel.Late, 12.seconds))
        assertEquals(ReminderPreview(ReminderLevel.Late, text(Res.string.time_9_30_pm), "Late 6", "A short read 3 2", ItemId("short")), env.state.preview)
    }

    @Test
    fun aSevenPmDailyReminderFillsTheSevenPmSlotWithTheSaverOff() = runTest(dispatcher) {
        val env = env(unreadItems, UserPreferences(reminderTime = ReminderTime.Evening, streakSaver = false))
        send(env, SettingsIntent.PreviewReminder(ReminderLevel.Evening, 12.seconds))
        assertEquals("Daily 0", env.state.preview?.title)
        assertEquals(text(Res.string.time_7_00_pm), env.state.preview?.time)
    }

    @Test
    fun previewExplainsWhyThereWouldBeNoReminder() = runTest(dispatcher) {
        fun TestScope.toastFor(slot: ReminderLevel, prefs: UserPreferences = UserPreferences(), items: List<SavedItem> = unreadItems, setUp: Env.() -> Unit = {}): Any? {
            val env = env(items, prefs)
            env.setUp()
            runCurrent()
            send(env, SettingsIntent.PreviewReminder(slot, 12.seconds))
            assertNull(env.state.preview, "$slot $prefs")
            return env.state.toast
        }
        assertEquals(text(Res.string.toast_reminder_off), toastFor(ReminderLevel.Daily, UserPreferences(dailyReminder = false)))
        assertEquals(text(Res.string.toast_saver_off), toastFor(ReminderLevel.Late, UserPreferences(streakSaver = false)) { streakOf(3) })
        assertEquals(text(Res.string.toast_no_reminder_empty), toastFor(ReminderLevel.Daily, items = listOf(read("r1"))))
        assertEquals(text(Res.string.toast_no_reminder_read), toastFor(ReminderLevel.Daily) { bus.streak.scripted.value = StreakState(3, true, StreakStatus.Lit, emptyList()) })
        assertEquals(text(Res.string.settings_preview_no_streak), toastFor(ReminderLevel.LastCall))
    }

    @Test
    fun readNowOpensTheNamedItemAndHandsOverTheCelebration() = runTest(dispatcher) {
        val env = env(unreadItems)
        send(env, SettingsIntent.PreviewReminder(ReminderLevel.Daily, 12.seconds))
        send(env, SettingsIntent.ReadPreviewItem)
        assertNull(env.state.preview)
        assertEquals(Command.OpenItem(ItemId("short")), env.bus.dispatched.last())
        assertEquals(SettingsEffect.OpenReader(ItemId("short"), markedRead = true), env.effects[0])
        val celebrate = env.effects[1] as SettingsEffect.Celebrate
        assertEquals(1, celebrate.celebration.to)
    }

    @Test
    fun readNowWithBrowserLinksOpensTheUrl() = runTest(dispatcher) {
        val env = env(unreadItems, UserPreferences(openLinksIn = OpenLinksIn.Browser))
        send(env, SettingsIntent.PreviewReminder(ReminderLevel.Daily, 12.seconds))
        send(env, SettingsIntent.ReadPreviewItem)
        assertEquals(SettingsEffect.OpenExternally("https://example.com/short"), env.effects.first())
    }

    @Test
    fun aRefusedOpenOpensNothing() = runTest(dispatcher) {
        val env = env(unreadItems)
        send(env, SettingsIntent.PreviewReminder(ReminderLevel.Daily, 12.seconds))
        env.bus.rejectNext(Rejection.StorageFailure)
        send(env, SettingsIntent.ReadPreviewItem)
        assertTrue(env.effects.isEmpty())
        assertEquals(text(Res.string.settings_save_failed), env.state.toast)
    }

    // ---- navigation and back ---------------------------------------------------------------------------------

    @Test
    fun navigationIntentsBecomeEffects() = runTest(dispatcher) {
        val env = env()
        listOf(
            SettingsIntent.NavigateBack, SettingsIntent.OpenArchive, SettingsIntent.OpenWidgetPreview, SettingsIntent.OpenSourceCode,
        ).forEach { send(env, it) }
        assertEquals(
            listOf(
                SettingsEffect.Back, SettingsEffect.OpenArchive, SettingsEffect.OpenWidgetPreview,
                SettingsEffect.OpenExternally("https://github.com/GvHarish9894"),
            ),
            env.effects,
        )
    }

    @Test
    fun systemBackClosesTheConfirmBeforeThePreview() = runTest(dispatcher) {
        val env = env(unreadItems + read("old", readAgo = 40.days))
        send(env, SettingsIntent.PreviewReminder(ReminderLevel.Daily, Duration.INFINITE))
        send(env, SettingsIntent.SetAutoArchive(AutoArchivePeriod.Days7))
        assertNotNull(env.state.preview)
        assertNotNull(env.state.autoArchiveConfirm)
        send(env, SettingsIntent.DismissTopLayer)
        assertNull(env.state.autoArchiveConfirm)
        assertNotNull(env.state.preview)
        send(env, SettingsIntent.DismissTopLayer)
        assertNull(env.state.preview)
        assertFalse(env.state.hasTopLayer)
        assertTrue(env.effects.isEmpty(), "system back with a layer open never leaves the screen")
    }
}
