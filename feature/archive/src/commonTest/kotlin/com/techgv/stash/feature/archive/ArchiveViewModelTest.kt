package com.techgv.stash.feature.archive

import kotlinx.coroutines.test.runCurrent
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.card_archived_at
import com.techgv.stash.core.resources.search_change_failed
import com.techgv.stash.core.resources.toast_link_copied
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.FakeCommandBus
import com.techgv.stash.core.testing.FakeItemRepository
import com.techgv.stash.core.testing.FakeSettingsRepository
import com.techgv.stash.core.testing.FixedZoneProvider
import com.techgv.stash.core.testing.TestClock
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Env(items: List<SavedItem>, prefs: UserPreferences = UserPreferences()) {
        val clock = TestClock()
        val repo = FakeItemRepository(items, clock)
        val settings = FakeSettingsRepository(prefs)
        val bus = FakeCommandBus(repo, settings)
        val vm = ArchiveViewModel(repo, bus, settings, clock, FixedZoneProvider())

        fun item(id: String) = repo.items.value.first { it.id.value == id }
    }

    // FIXTURE_NOW is 2026-11-12: "recent" was archived in November, "old" in October.
    private val recent = savedItem(
        "recent", ItemLifecycle.Archived, title = "Room database guide", siteName = "github.com",
        addedAt = FIXTURE_NOW - 5.days, archivedAt = FIXTURE_NOW - 2.days,
    )
    private val old = savedItem(
        "old", ItemLifecycle.Archived, title = "Typography basics", siteName = "medium.com",
        addedAt = FIXTURE_NOW - 60.days, archivedAt = FIXTURE_NOW - 40.days,
    )
    private val shelf = savedItem("shelf", title = "Room on the shelf")

    private fun TestScope.subscribe(vm: ArchiveViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
    }

    private suspend fun ArchiveViewModel.snapshot(): List<String> = rows.asSnapshot().map {
        when (it) {
            is ArchiveRowUi.Header -> "# ${it.label}"
            is ArchiveRowUi.Card -> it.card.id
        }
    }

    @Test
    fun archivedItemsAreGroupedThisMonthThenEarlier() = runTest(dispatcher) {
        val env = Env(listOf(old, shelf, recent))
        assertEquals(listOf("# ${GroupLabel.ThisMonth}", "recent", "# ${GroupLabel.Earlier}", "old"), env.vm.snapshot())
    }

    @Test
    fun cardsShowTheArchivedDate() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        val card = env.vm.rows.asSnapshot().filterIsInstance<ArchiveRowUi.Card>().single().card
        assertEquals(Res.string.card_archived_at, (card.time as UiText.Res).res)
    }

    @Test
    fun aNewMonthMovesItemsToEarlier() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        assertEquals(listOf("# ${GroupLabel.ThisMonth}", "recent"), env.vm.snapshot())
        env.clock.advanceBy(19.days) // 2026-12-01
        advanceUntilIdle()
        assertEquals(listOf("# ${GroupLabel.Earlier}", "recent"), env.vm.snapshot())
    }

    @Test
    fun swipesAreFixedWhateverTheShelfSettings() = runTest(dispatcher) {
        val prefs = UserPreferences(swipeStartAction = SwipeAction.Archive, swipeEndAction = SwipeAction.Share)
        val env = Env(listOf(recent), prefs)
        val card = env.vm.rows.asSnapshot().filterIsInstance<ArchiveRowUi.Card>().single()
        assertEquals(SwipeOutcome.Delete, card.startAction)
        assertEquals(SwipeOutcome.Restore, card.endAction)
    }

    @Test
    fun aSettingChangeDoesNotReloadTheList() = runTest(dispatcher) {
        val repo = FakeItemRepository(listOf(recent, old))
        var loads = 0
        val counting = object : ItemRepository by repo {
            override fun observeArchive(searchText: String) = repo.observeArchive(searchText).also { loads++ }
        }
        val settings = FakeSettingsRepository()
        val vm = ArchiveViewModel(counting, FakeCommandBus(repo, settings), settings, repo.clock, FixedZoneProvider())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.rows.collect {} }
        advanceUntilIdle()
        assertEquals(1, loads)

        settings.preferences.value = UserPreferences(cardLayout = CardLayout.Compact, swipeStartAction = SwipeAction.Share, autoArchive = AutoArchivePeriod.Days7)
        advanceUntilIdle()
        assertEquals(1, loads)
    }

    @Test
    fun searchFiltersInPlaceByWordPrefix() = runTest(dispatcher) {
        val env = Env(listOf(old, recent))
        env.vm.onIntent(ArchiveIntent.QueryChanged("datab"))
        advanceUntilIdle()
        val rows = env.vm.rows.asSnapshot()
        assertEquals(listOf("recent"), rows.filterIsInstance<ArchiveRowUi.Card>().map { it.card.id })
        assertEquals(listOf(5..9), rows.filterIsInstance<ArchiveRowUi.Card>().single().titleHighlights)

        env.vm.onIntent(ArchiveIntent.QueryChanged("abase"))
        advanceUntilIdle()
        assertEquals(emptyList(), env.vm.snapshot())

        env.vm.onIntent(ArchiveIntent.QueryChanged(""))
        advanceUntilIdle()
        assertEquals(4, env.vm.snapshot().size)
    }

    @Test
    fun stateCarriesCountRuleAndLayout() = runTest(dispatcher) {
        val env = Env(listOf(old, recent, shelf), UserPreferences(autoArchive = AutoArchivePeriod.Off, cardLayout = CardLayout.Compact))
        subscribe(env.vm)
        advanceUntilIdle()
        assertEquals(ArchiveState(count = 2, autoArchive = AutoArchivePeriod.Off, layout = CardLayout.Compact), env.vm.state.value)
    }

    @Test
    fun menuDeleteTearsTheCardFirstAndARefusedDeleteShowsItAgain() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        subscribe(env.vm)
        env.vm.onIntent(ArchiveIntent.ShowMenu(ItemId("recent")))
        runCurrent()
        env.vm.onIntent(ArchiveIntent.MenuAction(ItemMenuAction.Delete))
        runCurrent()
        assertEquals("recent", env.vm.state.value.tearingItemId)
        assertEquals(ItemLifecycle.Archived, env.item("recent").lifecycle)
        env.bus.rejectNext(Rejection.StorageFailure)
        env.vm.onIntent(ArchiveIntent.Torn(ItemId("recent")))
        runCurrent()
        assertNull(env.vm.state.value.tearingItemId)
        assertEquals(ItemLifecycle.Archived, env.item("recent").lifecycle)
    }

    @Test
    fun aTornCardIsDeletedOnceAndUndoFliesItBack() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        subscribe(env.vm)
        env.vm.onIntent(ArchiveIntent.ShowMenu(ItemId("recent")))
        runCurrent()
        env.vm.onIntent(ArchiveIntent.MenuAction(ItemMenuAction.Delete))
        env.vm.onIntent(ArchiveIntent.Torn(ItemId("recent")))
        runCurrent()
        assertEquals(ItemLifecycle.Deleted, env.item("recent").lifecycle)
        env.vm.onIntent(ArchiveIntent.Undo)
        runCurrent()
        assertEquals("recent", env.vm.state.value.flyBackItemId)
        assertNull(env.vm.state.value.tearingItemId, "the returning card does not tear again")
        advanceUntilIdle()
        assertNull(env.vm.state.value.flyBackItemId)
        assertEquals(1, env.bus.dispatched.count { it is Command.Delete })
    }

    @Test
    fun restoreOffersUndo() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        subscribe(env.vm)
        env.vm.onIntent(ArchiveIntent.CardAction(ItemId("recent"), SwipeOutcome.Restore))
        advanceUntilIdle()
        val undo = assertNotNull(env.vm.state.value.undo)
        assertEquals(OperationType.Restore, undo.type)
        assertEquals(ItemLifecycle.Read, env.item("recent").lifecycle)
        assertEquals(0, env.vm.state.value.count)

        env.vm.onIntent(ArchiveIntent.Undo)
        advanceUntilIdle()
        assertEquals(ItemLifecycle.Archived, env.item("recent").lifecycle)
        assertEquals(Command.Undo(undo.opId), env.bus.dispatched.last())
        assertNull(env.vm.state.value.undo)
    }

    @Test
    fun deleteOffersUndo() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        subscribe(env.vm)
        env.vm.onIntent(ArchiveIntent.CardAction(ItemId("recent"), SwipeOutcome.Delete))
        advanceUntilIdle()
        val undo = assertNotNull(env.vm.state.value.undo)
        assertEquals(OperationType.Delete, undo.type)
        assertEquals(ItemLifecycle.Deleted, env.item("recent").lifecycle)
        assertEquals(emptyList(), env.vm.snapshot())

        env.vm.onIntent(ArchiveIntent.Undo)
        advanceUntilIdle()
        assertEquals(ItemLifecycle.Archived, env.item("recent").lifecycle)
    }

    @Test
    fun undoClosesOnTimeoutOrDismissForItsOwnOperationOnly() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        subscribe(env.vm)
        env.vm.onIntent(ArchiveIntent.CardAction(ItemId("recent"), SwipeOutcome.Restore))
        advanceUntilIdle()
        val undo = assertNotNull(env.vm.state.value.undo)
        env.vm.onIntent(ArchiveIntent.UndoClosed("another"))
        advanceUntilIdle()
        assertEquals(undo, env.vm.state.value.undo)
        env.vm.onIntent(ArchiveIntent.UndoClosed(undo.opId))
        advanceUntilIdle()
        assertNull(env.vm.state.value.undo)
    }

    @Test
    fun aStorageFailureSaysSoAndOffersNoUndo() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        subscribe(env.vm)
        env.bus.rejectNext(Rejection.StorageFailure)
        env.vm.onIntent(ArchiveIntent.CardAction(ItemId("recent"), SwipeOutcome.Delete))
        advanceTimeBy(1)
        assertNull(env.vm.state.value.undo)
        assertEquals(text(Res.string.search_change_failed), env.vm.state.value.toast)
        assertEquals(ItemLifecycle.Archived, env.item("recent").lifecycle)
        advanceTimeBy(ArchiveViewModel.TOAST_MILLIS)
        assertNull(env.vm.state.value.toast)
    }

    @Test
    fun openingAnArchivedItemReadsItAndOpensTheReader() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        env.vm.effects.test {
            env.vm.onIntent(ArchiveIntent.Open(ItemId("recent")))
            assertEquals(ArchiveEffect.OpenReader(ItemId("recent"), markedRead = false), awaitItem())
            // The open counts for the streak (decision D21): first read of the day.
            assertTrue(awaitItem() is ArchiveEffect.Celebrate)
        }
        assertEquals(listOf<Command>(Command.OpenItem(ItemId("recent"))), env.bus.dispatched)
        assertEquals(ItemLifecycle.Archived, env.item("recent").lifecycle)
    }

    @Test
    fun browserSettingOpensTheBrowser() = runTest(dispatcher) {
        val env = Env(listOf(recent), UserPreferences(openLinksIn = OpenLinksIn.Browser))
        env.vm.effects.test {
            env.vm.onIntent(ArchiveIntent.Open(ItemId("recent")))
            assertEquals(ArchiveEffect.OpenInBrowser(recent.url), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theMenuOffersTheArchivedActions() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        subscribe(env.vm)
        env.vm.effects.test {
            env.vm.onIntent(ArchiveIntent.ShowMenu(ItemId("recent")))
            advanceUntilIdle()
            assertEquals("recent", env.vm.state.value.menu?.id)
            env.vm.onIntent(ArchiveIntent.MenuAction(ItemMenuAction.CopyLink))
            assertEquals(ArchiveEffect.Copy(recent.url), awaitItem())
            assertEquals(text(Res.string.toast_link_copied), env.vm.state.value.toast)

            env.vm.onIntent(ArchiveIntent.ShowMenu(ItemId("recent")))
            advanceUntilIdle()
            env.vm.onIntent(ArchiveIntent.MenuAction(ItemMenuAction.Share))
            assertEquals(ArchiveEffect.Share(recent.url, recent.title), awaitItem())

            env.vm.onIntent(ArchiveIntent.ShowMenu(ItemId("recent")))
            advanceUntilIdle()
            env.vm.onIntent(ArchiveIntent.MenuAction(ItemMenuAction.Open))
            assertEquals(ArchiveEffect.OpenReader(ItemId("recent"), markedRead = false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        env.vm.onIntent(ArchiveIntent.ShowMenu(ItemId("recent")))
        advanceUntilIdle()
        env.vm.onIntent(ArchiveIntent.MenuAction(ItemMenuAction.Delete))
        advanceUntilIdle()
        assertNull(env.vm.state.value.menu)
        assertEquals(OperationType.Delete, env.vm.state.value.undo?.type)
    }

    @Test
    fun shareFromAnAccessibilityAction() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        env.vm.effects.test {
            env.vm.onIntent(ArchiveIntent.CardAction(ItemId("recent"), SwipeOutcome.Share))
            assertEquals(ArchiveEffect.Share(recent.url, recent.title), awaitItem())
        }
    }

    @Test
    fun backLeaves() = runTest(dispatcher) {
        val env = Env(emptyList())
        env.vm.effects.test {
            env.vm.onIntent(ArchiveIntent.Back)
            assertEquals(ArchiveEffect.Back, awaitItem())
        }
    }

    @Test
    fun emptyArchive() = runTest(dispatcher) {
        val env = Env(listOf(shelf))
        subscribe(env.vm)
        advanceUntilIdle()
        assertEquals(0, env.vm.state.value.count)
        assertEquals(emptyList(), env.vm.snapshot())
    }

    @Test
    fun retryAsksForTheDetailsAgain() = runTest(dispatcher) {
        val env = Env(listOf(recent))
        env.vm.onIntent(ArchiveIntent.RetryMetadata(ItemId("recent")))
        advanceUntilIdle()
        assertEquals(listOf<Command>(Command.RetryMetadata(ItemId("recent"))), env.bus.dispatched)
    }
}
