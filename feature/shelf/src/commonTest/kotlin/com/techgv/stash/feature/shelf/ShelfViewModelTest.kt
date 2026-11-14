package com.techgv.stash.feature.shelf

import kotlin.time.Duration.Companion.seconds
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.techgv.stash.core.designsystem.HapticKind
import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.SourceCount
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.StreakDay
import com.techgv.stash.core.model.SwipeAction
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModelTest {
    private val scheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A view model on the fakes; [state] and [effects] are collected from the start. Time is FIXTURE_NOW (10:00 UTC). */
    private class Shelf(
        val scope: TestScope,
        items: List<SavedItem>,
        preferences: UserPreferences = UserPreferences(),
        /** False when the test reads [vm]'s effects with Turbine: the effect channel has one consumer. */
        collectEffects: Boolean = true,
    ) {
        val clock = TestClock()
        val repo = FakeItemRepository(items, clock)
        val settings = FakeSettingsRepository(preferences)
        val bus = FakeCommandBus(repo, settings)
        val vm = ShelfViewModel(repo, bus, settings, bus.streak, clock, repo.zones)
        val effects = mutableListOf<ShelfEffect>()

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            if (collectEffects) scope.backgroundScope.launch { vm.effects.collect { effects += it } }
            scope.runCurrent()
        }

        val state: ShelfState get() = vm.state.value

        fun send(intent: ShelfIntent) {
            vm.onIntent(intent)
            scope.runCurrent()
        }

        /** Lets the one-minute ticker run once. */
        fun tick() {
            scope.advanceTimeBy(1.minutes)
            scope.runCurrent()
        }

        fun item(id: String) = repo.items.value.single { it.id.value == id }
    }

    private fun shelfTest(
        items: List<SavedItem> = listOf(savedItem("a"), savedItem("b", addedAt = FIXTURE_NOW - 1.hours)),
        preferences: UserPreferences = UserPreferences(),
        block: suspend TestScope.(Shelf) -> Unit,
    ) = runTest(scheduler) { block(Shelf(this, items, preferences)) }

    private fun at(time: String) = Instant.parse("2026-11-12T$time:00Z")

    // ---- state from the repositories -----------------------------------------------------------------------------

    @Test
    fun stateCarriesCountsSettingsAndStreak() = shelfTest(
        items = listOf(savedItem("a"), savedItem("b", lifecycle = ItemLifecycle.Read), savedItem("c", lifecycle = ItemLifecycle.Archived)),
    ) { shelf ->
        assertEquals(2, shelf.state.chips.all)
        assertEquals(1, shelf.state.chips.unread)
        assertEquals(1, shelf.state.archiveCount)
        assertNotNull(shelf.state.streak)
        assertTrue(shelf.state.showGestureTip)
        shelf.settings.preferences.value = UserPreferences(gestureTipDismissed = true)
        runCurrent()
        assertFalse(shelf.state.showGestureTip)
    }

    @Test
    fun rowsHaveHeadersAndSwipeOutcomesFromTheSettings() = shelfTest(
        items = listOf(savedItem("a"), savedItem("b", lifecycle = ItemLifecycle.Read, addedAt = FIXTURE_NOW - 30.hours)),
    ) { shelf ->
        val rows = shelf.vm.rows.asSnapshot()
        assertEquals(
            listOf(
                ShelfListRow.Header(GroupLabel.Today),
                ShelfListRow.Card(shelf.item("a"), startAction = SwipeOutcome.Remove, endAction = SwipeOutcome.MarkRead),
                ShelfListRow.Header(GroupLabel.Yesterday),
                ShelfListRow.Card(shelf.item("b"), startAction = SwipeOutcome.Remove, endAction = SwipeOutcome.MarkUnread),
            ),
            rows,
        )
        shelf.settings.preferences.value = UserPreferences(swipeStartAction = SwipeAction.Nothing, swipeEndAction = SwipeAction.Share)
        val changed = shelf.vm.rows.asSnapshot().filterIsInstance<ShelfListRow.Card>().first()
        assertEquals(SwipeOutcome.RubberBand, changed.startAction)
        assertEquals(SwipeOutcome.Share, changed.endAction)
    }

    // ---- chips (decision D5) -------------------------------------------------------------------------------------

    /** Sources s01..s14 with 2 items each, s01 newest; s15 has 1 item and gets no chip. */
    private val manySources = (1..14).flatMap { n ->
        val key = "s" + n.toString().padStart(2, '0')
        List(2) { k -> savedItem("$key-$k", sourceKey = key, addedAt = FIXTURE_NOW - (n * 2 + k).minutes) }
    } + savedItem("lonely", sourceKey = "s15", addedAt = FIXTURE_NOW - 1.hours)

    @Test
    fun twelveSourceChipsThenMoreAndASourceFromMoreFilters() = shelfTest(items = manySources) { shelf ->
        val chips = shelf.state.chips
        assertEquals((1..12).map { SourceCount("s" + it.toString().padStart(2, '0'), 2) }, chips.sources)
        assertEquals(listOf(SourceCount("s13", 2), SourceCount("s14", 2)), chips.more)

        shelf.send(ShelfIntent.OpenMoreSources)
        assertTrue(shelf.state.moreSourcesOpen)
        shelf.send(ShelfIntent.SelectFilter(ShelfFilter.Source("s14")))
        assertFalse(shelf.state.moreSourcesOpen)
        assertEquals(ShelfFilter.Source("s14"), shelf.state.filter)
        val cards = shelf.vm.rows.asSnapshot().filterIsInstance<ShelfListRow.Card>()
        assertEquals(listOf("s14-0", "s14-1"), cards.map { it.item.id.value })
    }

    @Test
    fun aSelectedSourceThatDisappearsFallsBackToAll() = shelfTest(
        items = listOf(savedItem("a", sourceKey = "x"), savedItem("b", sourceKey = "x"), savedItem("c", sourceKey = "y")),
    ) { shelf ->
        shelf.send(ShelfIntent.SelectFilter(ShelfFilter.Source("x")))
        assertEquals(ShelfFilter.Source("x"), shelf.state.filter)
        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.Remove))
        assertEquals(ShelfFilter.All, shelf.state.filter)
        assertEquals(listOf("b", "c"), shelf.vm.rows.asSnapshot().filterIsInstance<ShelfListRow.Card>().map { it.item.id.value }.sorted())
    }

    // ---- opening ---------------------------------------------------------------------------------------------------

    @Test
    fun openingRecordsTheReadOpensTheReaderAndCelebratesOncePerDay() = runTest(scheduler) {
        val shelf = Shelf(this, listOf(savedItem("a"), savedItem("b")), collectEffects = false)
        shelf.vm.effects.test {
            shelf.vm.onIntent(ShelfIntent.Open(ItemId("a")))
            assertEquals(ShelfEffect.OpenReader(ItemId("a"), markedRead = true), awaitItem())
            assertEquals(ShelfEffect.Celebrate(Celebration(from = 0, to = 1, milestone = null)), awaitItem())
            shelf.vm.onIntent(ShelfIntent.Open(ItemId("b")))
            assertEquals(ShelfEffect.OpenReader(ItemId("b"), markedRead = true), awaitItem())
            runCurrent()
            expectNoEvents()
        }
        assertEquals(listOf<Command>(Command.OpenItem(ItemId("a")), Command.OpenItem(ItemId("b"))), shelf.bus.dispatched)
        assertEquals(ItemLifecycle.Read, shelf.item("a").lifecycle)
    }

    @Test
    fun openingInTheBrowserToastsOnlyWhenTheBrowserShowedIt() = shelfTest(
        preferences = UserPreferences(openLinksIn = OpenLinksIn.Browser),
    ) { shelf ->
        shelf.send(ShelfIntent.Open(ItemId("a")))
        assertEquals(ShelfEffect.OpenInBrowser(shelf.item("a").url), shelf.effects.first())
        assertEquals(ItemId("a"), shelf.state.struckItemId)
        assertNull(shelf.state.toast)

        shelf.send(ShelfIntent.BrowserOpened(opened = true))
        assertEquals(ShelfToast.OpenedInBrowser, shelf.state.toast)
        advanceTimeBy(ShelfViewModel.TOAST_MILLIS + 1)
        assertNull(shelf.state.toast)
        assertNull(shelf.state.struckItemId)

        shelf.send(ShelfIntent.BrowserOpened(opened = false))
        assertEquals(ShelfToast.BrowserFailed, shelf.state.toast)
    }

    @Test
    fun aFailedReadStillOpensThePage() = shelfTest { shelf ->
        shelf.bus.rejectNext(Rejection.StorageFailure)
        shelf.send(ShelfIntent.Open(ItemId("a")))
        assertEquals(listOf<ShelfEffect>(ShelfEffect.OpenReader(ItemId("a"), markedRead = false)), shelf.effects)
        assertEquals(ItemLifecycle.Unread, shelf.item("a").lifecycle)
    }

    @Test
    fun openingAMissingItemDoesNothing() = shelfTest { shelf ->
        shelf.send(ShelfIntent.Open(ItemId("gone")))
        assertEquals(emptyList<ShelfEffect>(), shelf.effects)
        assertEquals(emptyList<Command>(), shelf.bus.dispatched)
    }

    // ---- swipes, accessibility actions and undo -------------------------------------------------------------------

    @Test
    fun removingOffersUndoAndUndoBringsTheItemBack() = shelfTest { shelf ->
        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.Remove))
        val offer = assertNotNull(shelf.state.undo)
        assertEquals(OperationType.Remove, offer.type)
        assertEquals(ItemLifecycle.Deleted, shelf.item("a").lifecycle)

        shelf.send(ShelfIntent.Undo)
        assertNull(shelf.state.undo)
        assertEquals(Command.Undo(offer.opId), shelf.bus.dispatched.last())
        assertEquals(ItemLifecycle.Unread, shelf.item("a").lifecycle)
    }

    @Test
    fun aNewerUndoableActionReplacesTheOfferAndAStaleDismissIsIgnored() = shelfTest { shelf ->
        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.Archive))
        val first = assertNotNull(shelf.state.undo)
        assertEquals(OperationType.Archive, first.type)
        shelf.send(ShelfIntent.Act(ItemId("b"), SwipeOutcome.Remove))
        val second = assertNotNull(shelf.state.undo)
        assertEquals(OperationType.Remove, second.type)

        shelf.send(ShelfIntent.DismissUndo(first.opId))
        assertEquals(second, shelf.state.undo)
        shelf.send(ShelfIntent.DismissUndo(second.opId))
        assertNull(shelf.state.undo)
    }

    @Test
    fun aRefusedActionShowsAToastAndOffersNoUndo() = shelfTest(preferences = UserPreferences(gestureTipDismissed = true)) { shelf ->
        shelf.bus.rejectNext(Rejection.StorageFailure)
        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.Remove))
        assertNull(shelf.state.undo)
        assertEquals(ShelfToast.ActionFailed, shelf.state.toast)
        assertEquals(ItemLifecycle.Unread, shelf.item("a").lifecycle)
    }

    @Test
    fun aRefusedUndoShowsAToast() = shelfTest { shelf ->
        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.Remove))
        shelf.bus.rejectNext(Rejection.StorageFailure)
        shelf.send(ShelfIntent.Undo)
        assertEquals(ShelfToast.ActionFailed, shelf.state.toast)
        assertEquals(ItemLifecycle.Deleted, shelf.item("a").lifecycle)
    }

    @Test
    fun markingReadStrikesCelebratesAndRetiresTheGestureTip() = shelfTest { shelf ->
        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.MarkRead))
        assertEquals(ItemLifecycle.Read, shelf.item("a").lifecycle)
        assertEquals(ItemId("a"), shelf.state.struckItemId)
        assertEquals(listOf<ShelfEffect>(ShelfEffect.Celebrate(Celebration(0, 1, null))), shelf.effects)
        assertTrue(shelf.settings.preferences.value.gestureTipDismissed)
        assertNull(shelf.state.undo)

        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.MarkUnread))
        assertEquals(ItemLifecycle.Unread, shelf.item("a").lifecycle)
        // The tip was already dismissed: no second settings write.
        assertEquals(1, shelf.bus.dispatched.count { it is Command.ChangeSetting })
    }

    @Test
    fun sharingAndRubberBandWriteNothing() = shelfTest { shelf ->
        shelf.send(ShelfIntent.DismissGestureTip)
        shelf.bus.dispatched.clear()
        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.Share))
        shelf.send(ShelfIntent.Act(ItemId("a"), SwipeOutcome.RubberBand))
        val item = shelf.item("a")
        assertEquals(listOf<ShelfEffect>(ShelfEffect.Share(item.url, item.title)), shelf.effects)
        assertEquals(emptyList<Command>(), shelf.bus.dispatched)
    }

    @Test
    fun retryAsksForTheDetailsAgain() = shelfTest(items = listOf(savedItem("a", fetchStatus = FetchStatus.Failed))) { shelf ->
        shelf.send(ShelfIntent.Retry(ItemId("a")))
        assertEquals(listOf<Command>(Command.RetryMetadata(ItemId("a"))), shelf.bus.dispatched)
        assertEquals(FetchStatus.Pending, shelf.item("a").fetchStatus)
    }

    // ---- item menu ----------------------------------------------------------------------------------------------

    @Test
    fun menuActionsRunAndCloseTheMenu() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenMenu(ItemId("a")))
        assertEquals(shelf.item("a"), shelf.state.menuItem)
        assertTrue(shelf.settings.preferences.value.gestureTipDismissed)

        shelf.send(ShelfIntent.MenuAction(ItemMenuAction.CopyLink))
        assertNull(shelf.state.menuItem)
        assertEquals(ShelfEffect.Copy(shelf.item("a").url), shelf.effects.last())
        assertEquals(ShelfToast.LinkCopied, shelf.state.toast)

        shelf.send(ShelfIntent.OpenMenu(ItemId("a")))
        shelf.send(ShelfIntent.MenuAction(ItemMenuAction.Archive))
        assertEquals(ItemLifecycle.Archived, shelf.item("a").lifecycle)
        assertEquals(OperationType.Archive, shelf.state.undo?.type)

        shelf.send(ShelfIntent.OpenMenu(ItemId("b")))
        shelf.send(ShelfIntent.CloseMenu)
        assertNull(shelf.state.menuItem)
        shelf.send(ShelfIntent.MenuAction(ItemMenuAction.Remove))
        assertEquals(ItemLifecycle.Unread, shelf.item("b").lifecycle)
    }

    // ---- Milestone 4 card motion: page tear and undo fly-back ---------------------------------------------------

    @Test
    fun removeFromTheMenuTearsTheCardFirstThenRemovesItOnce() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenMenu(ItemId("a")))
        shelf.send(ShelfIntent.MenuAction(ItemMenuAction.Remove))
        assertNull(shelf.state.menuItem)
        assertEquals(ItemId("a"), shelf.state.tearingItemId)
        assertEquals(ItemLifecycle.Unread, shelf.item("a").lifecycle)

        shelf.send(ShelfIntent.Torn(ItemId("a")))
        assertEquals(ItemLifecycle.Deleted, shelf.item("a").lifecycle)
        assertEquals(OperationType.Remove, shelf.state.undo?.type)
        shelf.send(ShelfIntent.Torn(ItemId("a")))
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, shelf.bus.dispatched.count { it is Command.Remove })
        assertNull(shelf.state.tearingItemId)
    }

    @Test
    fun aRefusedTearShowsTheCardInPlaceAgain() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenMenu(ItemId("a")))
        shelf.send(ShelfIntent.MenuAction(ItemMenuAction.Remove))
        shelf.bus.rejectNext(Rejection.StorageFailure)
        shelf.send(ShelfIntent.Torn(ItemId("a")))
        assertNull(shelf.state.tearingItemId)
        assertEquals(ShelfToast.ActionFailed, shelf.state.toast)
        assertEquals(ItemLifecycle.Unread, shelf.item("a").lifecycle)
    }

    @Test
    fun theRemoveStillGoesOutWhenTheTearingCardNeverReportsBack() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenMenu(ItemId("a")))
        shelf.send(ShelfIntent.MenuAction(ItemMenuAction.Remove))
        advanceTimeBy(ShelfViewModel.TEAR_FALLBACK_MILLIS + 1)
        runCurrent()
        assertEquals(ItemLifecycle.Deleted, shelf.item("a").lifecycle)
    }

    @Test
    fun anAcceptedUndoFliesTheCardBackAndARefusedOneDoesNot() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenMenu(ItemId("a")))
        shelf.send(ShelfIntent.MenuAction(ItemMenuAction.Remove))
        shelf.send(ShelfIntent.Torn(ItemId("a")))
        shelf.send(ShelfIntent.Undo)
        assertEquals(ItemId("a"), shelf.state.flyBackItemId)
        assertNull(shelf.state.tearingItemId, "the returning card does not tear again")
        advanceTimeBy(ShelfViewModel.FLY_BACK_MILLIS + 1)
        runCurrent()
        assertNull(shelf.state.flyBackItemId)

        shelf.send(ShelfIntent.Act(ItemId("b"), SwipeOutcome.Archive))
        shelf.bus.rejectNext(Rejection.StorageFailure)
        shelf.send(ShelfIntent.Undo)
        assertNull(shelf.state.flyBackItemId)
    }

    // ---- nudge ---------------------------------------------------------------------------------------------------

    @Test
    fun theNudgeEscalatesWithTheClockAndHidesOnceRead() = shelfTest(
        items = listOf(savedItem("long", readingMinutes = 20), savedItem("short", readingMinutes = 3)),
    ) { shelf ->
        // A 1-day streak from yesterday keeps the streak saver slots alive.
        shelf.bus.streak.days.value = listOf(StreakDay(LocalDate(2026, 11, 11), FIXTURE_NOW - 24.hours))
        runCurrent()
        val nudge = assertNotNull(shelf.state.nudge)
        assertEquals(ShelfNudge(ReminderLevel.Daily, 1, ItemId("short"), "Title short", 3), nudge)

        for ((time, level) in listOf("19:00" to ReminderLevel.Evening, "21:30" to ReminderLevel.Late, "23:00" to ReminderLevel.LastCall)) {
            shelf.clock.now = at(time)
            shelf.tick()
            assertEquals(level, shelf.state.nudge?.level, time)
        }

        shelf.send(ShelfIntent.ReadNudge)
        assertEquals(ShelfEffect.OpenReader(ItemId("short"), markedRead = true), shelf.effects.first())
        assertNull(shelf.state.nudge)

        // Past midnight the day is open again.
        shelf.clock.now = Instant.parse("2026-11-13T09:30:00Z")
        shelf.tick()
        assertEquals(ShelfNudge(ReminderLevel.Daily, 2, ItemId("long"), "Title long", 20), shelf.state.nudge)
    }

    @Test
    fun theNudgeNamesTheSiteWhenTheTitleIsOnlyTheLink() = shelfTest(
        items = listOf(savedItem("pending", siteName = "example.com", title = "https://example.com/pending")),
    ) { shelf ->
        shelf.bus.streak.days.value = listOf(StreakDay(LocalDate(2026, 11, 11), FIXTURE_NOW - 24.hours))
        runCurrent()
        assertEquals("example.com", shelf.state.nudge?.title)
    }

    @Test
    fun noNudgeWithNothingUnread() = shelfTest(items = listOf(savedItem("a", lifecycle = ItemLifecycle.Read))) { shelf ->
        assertNull(shelf.state.nudge)
        shelf.send(ShelfIntent.ReadNudge)
        assertEquals(emptyList<ShelfEffect>(), shelf.effects)
    }

    // ---- add link ------------------------------------------------------------------------------------------------

    @Test
    fun openingAddLinkPeeksAtTheClipboardAndUseSavesAVisibleLink() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenAddLink)
        assertEquals(AddLinkState(), shelf.state.addLink)
        assertEquals(listOf<ShelfEffect>(ShelfEffect.PeekClipboard), shelf.effects)

        shelf.send(ShelfIntent.ClipboardPeeked(ClipboardSuggestion.Visible("https://new.example/page")))
        assertEquals(ClipboardSuggestion.Visible("https://new.example/page"), shelf.state.addLink?.clipboard)
        shelf.send(ShelfIntent.UseClipboard)

        assertNull(shelf.state.addLink)
        val saved = shelf.repo.items.value.single { it.url == "https://new.example/page" }
        assertEquals(saved.id, shelf.state.newItemId)
        assertEquals(ShelfEffect.Haptic(HapticKind.Confirm), shelf.effects.last())
        advanceTimeBy(ShelfViewModel.NEW_ITEM_MILLIS + 1)
        assertNull(shelf.state.newItemId)
    }

    @Test
    fun useOnAHiddenLinkAsksSharedToReadIt() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenAddLink)
        shelf.send(ShelfIntent.ClipboardPeeked(ClipboardSuggestion.Hidden))
        shelf.send(ShelfIntent.UseClipboard)
        assertEquals(ShelfEffect.ReadClipboard, shelf.effects.last())

        // Declined: the suggestion goes, the sheet stays.
        shelf.send(ShelfIntent.ClipboardRead(null))
        assertEquals(AddLinkState(), shelf.state.addLink)

        shelf.send(ShelfIntent.ClipboardPeeked(ClipboardSuggestion.Hidden))
        shelf.send(ShelfIntent.ClipboardRead("https://hidden.example/x"))
        assertNull(shelf.state.addLink)
        assertTrue(shelf.repo.items.value.any { it.url == "https://hidden.example/x" })
    }

    @Test
    fun noClipboardLinkMeansNoSuggestion() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenAddLink)
        shelf.send(ShelfIntent.ClipboardPeeked(null))
        shelf.send(ShelfIntent.UseClipboard)
        assertEquals(AddLinkState(), shelf.state.addLink)
        assertEquals(listOf<ShelfEffect>(ShelfEffect.PeekClipboard), shelf.effects)
    }

    @Test
    fun invalidInputShowsTheTwoErrorsWhichClearOnEdit() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenAddLink)
        shelf.send(ShelfIntent.SaveLink)
        assertEquals(AddLinkError.Empty, shelf.state.addLink?.error)
        assertEquals(ShelfEffect.Haptic(HapticKind.Reject), shelf.effects.last())

        shelf.send(ShelfIntent.AddLinkUrlChanged("not a link"))
        assertNull(shelf.state.addLink?.error)
        shelf.send(ShelfIntent.SaveLink)
        assertEquals(AddLinkError.NotALink, shelf.state.addLink?.error)
        assertEquals("not a link", shelf.state.addLink?.url)

        shelf.send(ShelfIntent.AddLinkUrlChanged("example.org/ok"))
        assertNull(shelf.state.addLink?.error)
        shelf.send(ShelfIntent.SaveLink)
        assertNull(shelf.state.addLink)
        assertTrue(shelf.repo.items.value.any { it.url == "https://example.org/ok" })
    }

    @Test
    fun savingALinkAlreadyOnTheShelfSaysSoAndShowsAll() = shelfTest(
        items = listOf(savedItem("a", sourceKey = "x"), savedItem("b", sourceKey = "x")),
    ) { shelf ->
        shelf.send(ShelfIntent.SelectFilter(ShelfFilter.Unread))
        shelf.send(ShelfIntent.OpenAddLink)
        shelf.send(ShelfIntent.AddLinkUrlChanged(shelf.item("a").url))
        shelf.send(ShelfIntent.SaveLink)
        assertNull(shelf.state.addLink)
        assertEquals(ShelfToast.AlreadyOnShelf, shelf.state.toast)
        assertEquals(ShelfFilter.All, shelf.state.filter)
        assertEquals(ItemId("a"), shelf.state.newItemId)
        assertEquals(2, shelf.repo.items.value.size)
    }

    @Test
    fun aStorageFailureKeepsTheSheetOpen() = shelfTest { shelf ->
        shelf.send(ShelfIntent.OpenAddLink)
        shelf.send(ShelfIntent.AddLinkUrlChanged("https://example.org/new"))
        shelf.bus.rejectNext(Rejection.StorageFailure)
        shelf.send(ShelfIntent.SaveLink)
        assertEquals("https://example.org/new", shelf.state.addLink?.url)
        assertEquals(ShelfToast.SaveFailed, shelf.state.toast)
        assertEquals(ShelfEffect.Haptic(HapticKind.Reject), shelf.effects.last())
        assertEquals(2, shelf.repo.items.value.size)

        shelf.send(ShelfIntent.CloseAddLink)
        assertNull(shelf.state.addLink)
    }

    // ---- header, navigation, tip and first load ------------------------------------------------------------------

    @Test
    fun navigationIntentsBecomeEffects() = runTest(scheduler) {
        val shelf = Shelf(this, emptyList(), collectEffects = false)
        shelf.vm.effects.test {
            shelf.vm.onIntent(ShelfIntent.OpenSearch)
            shelf.vm.onIntent(ShelfIntent.OpenArchive)
            shelf.vm.onIntent(ShelfIntent.OpenSettings)
            shelf.vm.onIntent(ShelfIntent.OpenStreak)
            assertEquals(
                listOf(ShelfEffect.OpenSearch, ShelfEffect.OpenArchive, ShelfEffect.OpenSettings, ShelfEffect.OpenStreak),
                List(4) { awaitItem() },
            )
        }
    }

    @Test
    fun dismissingTheTipPersistsIt() = shelfTest { shelf ->
        shelf.send(ShelfIntent.DismissGestureTip)
        assertFalse(shelf.state.showGestureTip)
        assertTrue(shelf.settings.preferences.value.gestureTipDismissed)
    }

    @Test
    fun theFirstLoadStaggerEnds() = shelfTest { shelf ->
        assertTrue(shelf.state.firstLoad)
        advanceTimeBy(ShelfViewModel.FIRST_LOAD_MILLIS + 1)
        assertFalse(shelf.state.firstLoad)
    }
}
