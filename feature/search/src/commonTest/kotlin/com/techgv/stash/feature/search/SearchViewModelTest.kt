package com.techgv.stash.feature.search

import kotlinx.coroutines.test.runCurrent
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.search_change_failed
import com.techgv.stash.core.resources.search_open_link_failed
import com.techgv.stash.core.resources.toast_link_copied
import com.techgv.stash.core.resources.toast_opened_in_browser
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.FakeCommandBus
import com.techgv.stash.core.testing.FakeItemRepository
import com.techgv.stash.core.testing.FakeRecentSearchRepository
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
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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
        val recents = FakeRecentSearchRepository(clock)
        val vm = SearchViewModel(repo, bus, settings, recents, clock, FixedZoneProvider())

        fun item(id: String) = repo.items.value.first { it.id.value == id }
    }

    private val compose = savedItem("compose", title = "Compose layouts in depth", siteName = "medium.com", addedAt = FIXTURE_NOW - 1.hours)
    private val room = savedItem("room", title = "Room and compose together", siteName = "github.com", addedAt = FIXTURE_NOW - 2.hours)
    private val oldCompose = savedItem(
        "old", ItemLifecycle.Archived, title = "Compose testing", siteName = "medium.com", addedAt = FIXTURE_NOW - 400.hours,
    )
    private val other = savedItem("other", title = "Typography", siteName = "medium.com", addedAt = FIXTURE_NOW - 3.hours)

    /** Keeps [SearchViewModel.state] subscribed, as the screen does. */
    private fun TestScope.subscribe(vm: SearchViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
    }

    private fun TestScope.type(env: Env, query: String) {
        env.vm.onIntent(SearchIntent.QueryChanged(query))
        advanceUntilIdle()
    }

    @Test
    fun resultsWaitForTypingToPause() = runTest(dispatcher) {
        val env = Env(listOf(compose, room))
        subscribe(env.vm)
        env.vm.onIntent(SearchIntent.QueryChanged("comp"))
        assertEquals("comp", env.vm.query)
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 10)
        assertNull(env.vm.state.value.results)
        advanceUntilIdle()
        val results = assertNotNull(env.vm.state.value.results)
        assertEquals("comp", results.text)
        assertEquals(2, results.total)
    }

    @Test
    fun typingAWordRunsOneQueryNotOnePerLetter() = runTest(dispatcher) {
        val repo = FakeItemRepository(listOf(compose, room, oldCompose))
        val queries = mutableListOf<String>()
        val counting = object : ItemRepository by repo {
            override fun observeSearchChips(text: String) = repo.observeSearchChips(text).also { queries += "chips $text" }
            override fun observeShelfSearch(text: String, filter: SearchFilter) =
                repo.observeShelfSearch(text, filter).also { queries += "shelf $text" }
            override fun observeArchiveSearch(text: String, filter: SearchFilter) =
                repo.observeArchiveSearch(text, filter).also { queries += "archive $text" }
        }
        val settings = FakeSettingsRepository()
        val vm = SearchViewModel(counting, FakeCommandBus(repo, settings), settings, FakeRecentSearchRepository(repo.clock), repo.clock, FixedZoneProvider())
        subscribe(vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.shelfResults.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.archiveResults.collect {} }

        // Four keys a second, a brisk phone typist.
        for (length in 1.."compose".length) {
            vm.onIntent(SearchIntent.QueryChanged("compose".take(length)))
            advanceTimeBy(250)
        }
        advanceUntilIdle()

        assertEquals(listOf("archive compose", "chips compose", "shelf compose"), queries.sorted())
        assertEquals(3, vm.state.value.results?.total)
    }

    @Test
    fun matchesTokenPrefixesNotInfixes() = runTest(dispatcher) {
        val env = Env(listOf(compose, room))
        subscribe(env.vm)
        type(env, "pose")
        assertEquals(0, env.vm.state.value.results?.total)
        assertEquals(emptyList(), env.vm.shelfResults.asSnapshot())

        type(env, "lay comp")
        val hits = env.vm.shelfResults.asSnapshot()
        assertEquals(listOf("compose"), hits.map { it.card.id })
        assertEquals(listOf(0..3, 8..10), hits.single().titleHighlights)
    }

    @Test
    fun siteMatchesAreHighlightedInTheSite() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        type(env, "medi")
        val hit = env.vm.shelfResults.asSnapshot().single()
        assertEquals(listOf(0..3), hit.siteHighlights)
        assertEquals(emptyList(), hit.titleHighlights)
    }

    @Test
    fun chipsComeFromTheResultsOnly() = runTest(dispatcher) {
        val env = Env(listOf(compose, room, oldCompose, other))
        subscribe(env.vm)
        type(env, "compose")
        // Two sources and hits in both the shelf and the archive.
        assertEquals(
            listOf(
                SearchChipUi(SearchFilter.All, 3),
                SearchChipUi(SearchFilter.Source("medium.com"), 2),
                SearchChipUi(SearchFilter.Source("github.com"), 1),
                SearchChipUi(SearchFilter.Archived, 1),
            ),
            env.vm.state.value.results?.chips,
        )
        // One source, shelf only: just All.
        type(env, "layouts")
        assertEquals(listOf(SearchChipUi(SearchFilter.All, 1)), env.vm.state.value.results?.chips)
    }

    @Test
    fun sourceChipFiltersBothSectionsAndFallsBackToAllWhenItGoes() = runTest(dispatcher) {
        val env = Env(listOf(compose, room, oldCompose))
        subscribe(env.vm)
        type(env, "compose")
        env.vm.onIntent(SearchIntent.SelectFilter(SearchFilter.Source("github.com")))
        advanceUntilIdle()
        assertEquals(SearchFilter.Source("github.com"), env.vm.state.value.results?.filter)
        assertEquals(listOf("room"), env.vm.shelfResults.asSnapshot().map { it.card.id })
        assertEquals(emptyList(), env.vm.archiveResults.asSnapshot())

        // github.com's only hit leaves the shelf: its chip disappears and the filter falls back to All.
        env.bus.dispatch(Command.Remove(ItemId("room")))
        advanceUntilIdle()
        assertEquals(SearchFilter.All, env.vm.state.value.results?.filter)
        assertEquals(listOf("compose"), env.vm.shelfResults.asSnapshot().map { it.card.id })
    }

    @Test
    fun archivedChipShowsOnlyArchiveHits() = runTest(dispatcher) {
        val env = Env(listOf(compose, oldCompose))
        subscribe(env.vm)
        type(env, "compose")
        env.vm.onIntent(SearchIntent.SelectFilter(SearchFilter.Archived))
        advanceUntilIdle()
        assertEquals(SearchFilter.Archived, env.vm.state.value.results?.filter)
        assertEquals(emptyList(), env.vm.shelfResults.asSnapshot())
        assertEquals(listOf("old"), env.vm.archiveResults.asSnapshot().map { it.card.id })
    }

    @Test
    fun typingResetsTheChipToAll() = runTest(dispatcher) {
        val env = Env(listOf(compose, oldCompose))
        subscribe(env.vm)
        type(env, "compose")
        env.vm.onIntent(SearchIntent.SelectFilter(SearchFilter.Archived))
        // Still hits in both the shelf and the archive, so only the reset brings back All.
        type(env, "compos")
        assertEquals(SearchFilter.All, env.vm.state.value.results?.filter)
    }

    @Test
    fun shelfAndArchiveHitsArePagedSeparatelyNewestFirst() = runTest(dispatcher) {
        val archivedRecent = savedItem("arch2", ItemLifecycle.Archived, title = "Compose state", addedAt = FIXTURE_NOW - 300.hours)
        val env = Env(listOf(oldCompose, room, compose, archivedRecent))
        subscribe(env.vm)
        type(env, "compose")
        assertEquals(listOf("compose", "room"), env.vm.shelfResults.asSnapshot().map { it.card.id })
        assertEquals(listOf("arch2", "old"), env.vm.archiveResults.asSnapshot().map { it.card.id })
    }

    @Test
    fun clearingTheFieldDropsResultsAtOnce() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        type(env, "comp")
        env.vm.onIntent(SearchIntent.QueryChanged(""))
        advanceTimeBy(1)
        assertNull(env.vm.state.value.results)
    }

    @Test
    fun backSavesTheTrimmedQueryAndLeaves() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.vm.effects.test {
            type(env, "  compose ")
            env.vm.onIntent(SearchIntent.Back)
            assertEquals(SearchEffect.Back, awaitItem())
        }
        assertEquals(listOf("compose"), env.vm.state.value.recents)
    }

    @Test
    fun blankQueriesAreNotSaved() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        type(env, "   ")
        env.vm.onIntent(SearchIntent.Back)
        advanceUntilIdle()
        assertEquals(emptyList(), env.vm.state.value.recents)
    }

    @Test
    fun typingDoesNotSaveRecents() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        type(env, "c")
        type(env, "co")
        type(env, "com")
        assertEquals(emptyList(), env.vm.state.value.recents)
    }

    @Test
    fun recentsKeepTheFiveNewest() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        for (q in listOf("one", "two", "three", "four", "five", "six")) {
            type(env, q)
            env.vm.onIntent(SearchIntent.Back)
            advanceUntilIdle()
            env.clock.advanceBy(1.hours)
        }
        type(env, "TWO") // a case-insensitive repeat replaces the older entry
        env.vm.onIntent(SearchIntent.Back)
        advanceUntilIdle()
        assertEquals(listOf("TWO", "six", "five", "four", "three"), env.vm.state.value.recents)
    }

    @Test
    fun openingAResultSavesTheQueryMarksItReadAndOpensTheReader() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        type(env, "compose")
        env.vm.effects.test {
            env.vm.onIntent(SearchIntent.Open(ItemId("compose")))
            assertEquals(SearchEffect.OpenReader(ItemId("compose"), markedRead = true), awaitItem())
            // First read of the day.
            assertTrue(awaitItem() is SearchEffect.Celebrate)
        }
        assertEquals(listOf<Command>(Command.OpenItem(ItemId("compose"))), env.bus.dispatched)
        assertEquals(ItemLifecycle.Read, env.item("compose").lifecycle)
        assertEquals(listOf("compose"), env.vm.state.value.recents)
    }

    @Test
    fun theSecondReadOfTheDayDoesNotCelebrate() = runTest(dispatcher) {
        val env = Env(listOf(compose, room))
        subscribe(env.vm)
        env.vm.effects.test {
            env.vm.onIntent(SearchIntent.Open(ItemId("compose")))
            skipItems(2)
            env.vm.onIntent(SearchIntent.Open(ItemId("room")))
            assertEquals(SearchEffect.OpenReader(ItemId("room"), markedRead = true), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun browserSettingOpensTheBrowserAndReportsTheResult() = runTest(dispatcher) {
        val env = Env(listOf(compose), UserPreferences(openLinksIn = OpenLinksIn.Browser))
        subscribe(env.vm)
        env.vm.effects.test {
            env.vm.onIntent(SearchIntent.Open(ItemId("compose")))
            assertEquals(SearchEffect.OpenInBrowser(compose.url), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        env.vm.onIntent(SearchIntent.BrowserOpened(true))
        advanceTimeBy(1)
        assertEquals(text(Res.string.toast_opened_in_browser), env.vm.state.value.toast)
        env.vm.onIntent(SearchIntent.BrowserOpened(false))
        advanceTimeBy(1)
        assertEquals(text(Res.string.search_open_link_failed), env.vm.state.value.toast)
    }

    @Test
    fun aMissingItemDoesNotOpen() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.vm.effects.test {
            env.vm.onIntent(SearchIntent.Open(ItemId("gone")))
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun aStorageFailureOnOpenStillOpensThePage() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.bus.rejectNext(Rejection.StorageFailure)
        env.vm.effects.test {
            env.vm.onIntent(SearchIntent.Open(ItemId("compose")))
            assertEquals(SearchEffect.OpenReader(ItemId("compose"), markedRead = false), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(ItemLifecycle.Unread, env.item("compose").lifecycle)
    }

    @Test
    fun anAcceptedUndoFliesTheResultBack() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.vm.onIntent(SearchIntent.CardAction(ItemId("compose"), SwipeOutcome.Remove))
        advanceUntilIdle()
        env.vm.onIntent(SearchIntent.Undo)
        runCurrent()
        assertEquals("compose", env.vm.state.value.flyBackItemId)
        advanceUntilIdle()
        assertNull(env.vm.state.value.flyBackItemId)
    }

    @Test
    fun removeOffersUndoAndUndoPutsItBack() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.vm.onIntent(SearchIntent.CardAction(ItemId("compose"), SwipeOutcome.Remove))
        advanceUntilIdle()
        val undo = assertNotNull(env.vm.state.value.undo)
        assertEquals(OperationType.Remove, undo.type)
        assertEquals(ItemLifecycle.Deleted, env.item("compose").lifecycle)

        env.vm.onIntent(SearchIntent.Undo)
        advanceUntilIdle()
        assertNull(env.vm.state.value.undo)
        assertEquals(Command.Undo(undo.opId), env.bus.dispatched.last())
        assertEquals(ItemLifecycle.Unread, env.item("compose").lifecycle)
    }

    @Test
    fun archivedHitsRestoreAndDeleteWithUndo() = runTest(dispatcher) {
        val env = Env(listOf(oldCompose))
        subscribe(env.vm)
        env.vm.onIntent(SearchIntent.CardAction(ItemId("old"), SwipeOutcome.Restore))
        advanceUntilIdle()
        assertEquals(OperationType.Restore, env.vm.state.value.undo?.type)
        env.vm.onIntent(SearchIntent.CardAction(ItemId("old"), SwipeOutcome.Archive))
        advanceUntilIdle()
        env.vm.onIntent(SearchIntent.CardAction(ItemId("old"), SwipeOutcome.Delete))
        advanceUntilIdle()
        // A newer undoable action replaces the bar.
        assertEquals(OperationType.Delete, env.vm.state.value.undo?.type)
    }

    @Test
    fun theUndoBarClosesOnlyForItsOwnOperation() = runTest(dispatcher) {
        val env = Env(listOf(compose, room))
        subscribe(env.vm)
        env.vm.onIntent(SearchIntent.CardAction(ItemId("compose"), SwipeOutcome.Archive))
        advanceUntilIdle()
        val first = assertNotNull(env.vm.state.value.undo)
        env.vm.onIntent(SearchIntent.CardAction(ItemId("room"), SwipeOutcome.Archive))
        advanceUntilIdle()
        val second = assertNotNull(env.vm.state.value.undo)
        env.vm.onIntent(SearchIntent.UndoClosed(first.opId))
        advanceTimeBy(1)
        assertEquals(second, env.vm.state.value.undo)
        env.vm.onIntent(SearchIntent.UndoClosed(second.opId))
        advanceTimeBy(1)
        assertNull(env.vm.state.value.undo)
    }

    @Test
    fun aStorageFailureSaysSoAndOffersNoUndo() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.bus.rejectNext(Rejection.StorageFailure)
        env.vm.onIntent(SearchIntent.CardAction(ItemId("compose"), SwipeOutcome.Archive))
        advanceTimeBy(1)
        assertNull(env.vm.state.value.undo)
        assertEquals(text(Res.string.search_change_failed), env.vm.state.value.toast)
        assertEquals(ItemLifecycle.Unread, env.item("compose").lifecycle)
        advanceTimeBy(SearchViewModel.TOAST_MILLIS)
        assertNull(env.vm.state.value.toast)
    }

    @Test
    fun otherRefusalsChangeNothingQuietly() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.bus.rejectNext(Rejection.InvalidTransition(ItemLifecycle.Unread, OperationType.Restore))
        env.vm.onIntent(SearchIntent.CardAction(ItemId("compose"), SwipeOutcome.Restore))
        advanceUntilIdle()
        assertNull(env.vm.state.value.undo)
        assertNull(env.vm.state.value.toast)
    }

    @Test
    fun markReadFromAnActionCelebratesTheFirstReadOfTheDay() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.vm.effects.test {
            env.vm.onIntent(SearchIntent.CardAction(ItemId("compose"), SwipeOutcome.MarkRead))
            assertTrue(awaitItem() is SearchEffect.Celebrate)
        }
        env.vm.onIntent(SearchIntent.CardAction(ItemId("compose"), SwipeOutcome.MarkUnread))
        advanceUntilIdle()
        assertEquals(listOf<Command>(Command.MarkRead(ItemId("compose")), Command.MarkUnread(ItemId("compose"))), env.bus.dispatched)
    }

    @Test
    fun theMenuOffersTheItemsActions() = runTest(dispatcher) {
        val env = Env(listOf(compose, oldCompose))
        subscribe(env.vm)
        env.vm.onIntent(SearchIntent.ShowMenu(ItemId("compose")))
        advanceUntilIdle()
        assertEquals("compose", env.vm.state.value.menu?.id)
        env.vm.effects.test {
            env.vm.onIntent(SearchIntent.MenuAction(ItemMenuAction.CopyLink))
            assertEquals(SearchEffect.Copy(compose.url), awaitItem())
            assertNull(env.vm.state.value.menu)
            assertEquals(text(Res.string.toast_link_copied), env.vm.state.value.toast)

            env.vm.onIntent(SearchIntent.ShowMenu(ItemId("old")))
            advanceUntilIdle()
            env.vm.onIntent(SearchIntent.MenuAction(ItemMenuAction.Share))
            assertEquals(SearchEffect.Share(oldCompose.url, oldCompose.title), awaitItem())
        }
        env.vm.onIntent(SearchIntent.ShowMenu(ItemId("old")))
        advanceUntilIdle()
        env.vm.onIntent(SearchIntent.MenuAction(ItemMenuAction.Restore))
        advanceUntilIdle()
        assertEquals(ItemLifecycle.Read, env.item("old").lifecycle)
        assertEquals(OperationType.Restore, env.vm.state.value.undo?.type)

        env.vm.onIntent(SearchIntent.ShowMenu(ItemId("compose")))
        advanceUntilIdle()
        env.vm.onIntent(SearchIntent.DismissMenu)
        advanceUntilIdle()
        assertNull(env.vm.state.value.menu)
    }

    @Test
    fun retryAsksForTheDetailsAgain() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        env.vm.onIntent(SearchIntent.RetryMetadata(ItemId("compose")))
        advanceUntilIdle()
        assertEquals(listOf<Command>(Command.RetryMetadata(ItemId("compose"))), env.bus.dispatched)
    }

    @Test
    fun resultTimesFollowTheClock() = runTest(dispatcher) {
        val env = Env(listOf(compose))
        subscribe(env.vm)
        type(env, "compose")
        val before = env.vm.shelfResults.asSnapshot().single().card.timeSpoken
        env.clock.advanceBy(48.hours)
        advanceUntilIdle()
        val after = env.vm.shelfResults.asSnapshot().single().card.timeSpoken
        assertTrue(before != after, "$before stayed $after")
    }
}
