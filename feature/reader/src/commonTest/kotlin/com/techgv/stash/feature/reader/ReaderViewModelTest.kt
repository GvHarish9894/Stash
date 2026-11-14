package com.techgv.stash.feature.reader

import app.cash.turbine.test
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.ReaderResult
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.model.RichText
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.testing.FakeCommandBus
import com.techgv.stash.core.testing.FakeItemRepository
import com.techgv.stash.core.testing.FakeReaderRepository
import com.techgv.stash.core.testing.FakeSettingsRepository
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val id = ItemId("a")
    private val article = ReaderArticle("Title a", "example.com", null, 5, listOf(ReaderBlock.Paragraph(RichText("Body"))))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Fixture(val viewModel: ReaderViewModel, val bus: FakeCommandBus, val reader: FakeReaderRepository) {
        val state get() = viewModel.state.value
    }

    private fun fixture(
        item: SavedItem? = savedItem("a"),
        prefs: UserPreferences = UserPreferences(),
        result: ReaderResult = ReaderResult.Unavailable,
        markedRead: Boolean = false,
        readerMode: Boolean? = null,
    ): Fixture {
        val bus = FakeCommandBus(FakeItemRepository(listOfNotNull(item)), FakeSettingsRepository(prefs))
        val reader = FakeReaderRepository(result)
        return Fixture(ReaderViewModel(id, markedRead, readerMode, bus.items, reader, bus, bus.settings), bus, reader)
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    // ---- initial mode ---------------------------------------------------------------------------------------------

    @Test
    fun modeIsUndecidedUntilTheSettingIsRead() = test {
        val f = fixture()
        assertNull(f.state.readerMode)
        runCurrent()
        assertEquals(false, f.state.readerMode)
    }

    @Test
    fun webModeByDefaultDoesNotAskForTheArticle() = test {
        val f = fixture(result = ReaderResult.Ready(article))
        advanceUntilIdle()
        assertEquals(false, f.state.readerMode)
        assertEquals(ArticleState.Idle, f.state.article)
        assertTrue(f.reader.requested.isEmpty())
    }

    @Test
    fun readerModeSettingOpensInReaderModeAndLoadsTheArticle() = test {
        val f = fixture(prefs = UserPreferences(readerModeDefault = true), result = ReaderResult.Ready(article))
        advanceUntilIdle()
        assertEquals(true, f.state.readerMode)
        assertEquals(ArticleState.Ready(article), f.state.article)
        assertEquals(listOf(id), f.reader.requested)
    }

    @Test
    fun aCarriedOverModeWinsOverTheSetting() = test {
        val on = fixture(result = ReaderResult.Ready(article), readerMode = true)
        val off = fixture(prefs = UserPreferences(readerModeDefault = true), readerMode = false)
        advanceUntilIdle()
        assertEquals(true, on.state.readerMode)
        assertEquals(ArticleState.Ready(article), on.state.article)
        assertEquals(false, off.state.readerMode)
        assertEquals(emptyList(), off.reader.requested)
    }

    @Test
    fun theSettingChangingLaterDoesNotFlipTheOpenReader() = test {
        val f = fixture()
        advanceUntilIdle()
        f.bus.settings.preferences.value = UserPreferences(readerModeDefault = true)
        advanceUntilIdle()
        assertEquals(false, f.state.readerMode)
    }

    // ---- toggle and article states --------------------------------------------------------------------------------

    @Test
    fun toggleShowsTheSkeletonThenTheArticleAndKeepsItForTheNextToggle() = test {
        val f = fixture(result = ReaderResult.Ready(article))
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        assertEquals(true, f.state.readerMode)
        assertEquals(ArticleState.Loading, f.state.article)
        assertEquals(0f, f.state.loadProgress)
        advanceUntilIdle()
        assertEquals(ArticleState.Ready(article), f.state.article)
        assertEquals(1f, f.state.loadProgress)

        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        assertEquals(false, f.state.readerMode)
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        advanceUntilIdle()
        assertEquals(true, f.state.readerMode)
        assertEquals(1, f.reader.requested.size)
    }

    @Test
    fun unavailableFallsBackToWebModeWithTheMessage() = test {
        val f = fixture(result = ReaderResult.Unavailable)
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        runCurrent()
        assertEquals(false, f.state.readerMode)
        assertEquals(ArticleState.Unavailable, f.state.article)
        assertEquals(ReaderToast.ReaderUnavailable, f.state.toast)
        advanceUntilIdle()
        assertNull(f.state.toast)

        // Asking again answers at once from what this screen already knows.
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        assertEquals(false, f.state.readerMode)
        assertEquals(ReaderToast.ReaderUnavailable, f.state.toast)
        assertEquals(1, f.reader.requested.size)
    }

    @Test
    fun unavailableAfterTheUserWentBackToWebModeSaysNothing() = test {
        val f = fixture(result = ReaderResult.Unavailable)
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        runCurrent()
        assertEquals(false, f.state.readerMode)
        assertEquals(ArticleState.Unavailable, f.state.article)
        assertNull(f.state.toast)
    }

    @Test
    fun offlineStaysInReaderModeAndRetryLoadsAgain() = test {
        val f = fixture(prefs = UserPreferences(readerModeDefault = true), result = ReaderResult.Offline)
        advanceUntilIdle()
        assertEquals(true, f.state.readerMode)
        assertEquals(ArticleState.Offline, f.state.article)

        f.reader.default = ReaderResult.Ready(article)
        f.viewModel.onIntent(ReaderIntent.RetryArticle)
        assertEquals(ArticleState.Loading, f.state.article)
        advanceUntilIdle()
        assertEquals(ArticleState.Ready(article), f.state.article)
        assertEquals(2, f.reader.requested.size)
    }

    @Test
    fun retryIsIgnoredUnlessOffline() = test {
        val f = fixture(prefs = UserPreferences(readerModeDefault = true), result = ReaderResult.Ready(article))
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.RetryArticle)
        advanceUntilIdle()
        assertEquals(1, f.reader.requested.size)
    }

    @Test
    fun togglingBackOnAfterOfflineTriesAgain() = test {
        val f = fixture(prefs = UserPreferences(readerModeDefault = true), result = ReaderResult.Offline)
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        f.reader.default = ReaderResult.Ready(article)
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        advanceUntilIdle()
        assertEquals(ArticleState.Ready(article), f.state.article)
        assertEquals(2, f.reader.requested.size)
    }

    // ---- text size ------------------------------------------------------------------------------------------------

    @Test
    fun textStepIsPersistedThroughChangeSetting() = test {
        val f = fixture(prefs = UserPreferences(readerTextStep = 1))
        advanceUntilIdle()
        assertEquals(1, f.state.textStep)
        f.viewModel.onIntent(ReaderIntent.ChangeTextStep(2))
        advanceUntilIdle()
        assertIs<Command.ChangeSetting>(f.bus.dispatched.single())
        assertEquals(2, f.bus.settings.preferences.value.readerTextStep)
        assertEquals(2, f.state.textStep)
    }

    @Test
    fun textStepIsClampedToFourSteps() = test {
        val f = fixture(prefs = UserPreferences(readerTextStep = 2))
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.ChangeTextStep(9))
        advanceUntilIdle()
        assertEquals(3, f.state.textStep)
        f.viewModel.onIntent(ReaderIntent.ChangeTextStep(-4))
        advanceUntilIdle()
        assertEquals(0, f.state.textStep)
    }

    @Test
    fun textStepAtItsBoundWritesNothing() = test {
        val f = fixture(prefs = UserPreferences(readerTextStep = 3))
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.ChangeTextStep(4))
        f.viewModel.onIntent(ReaderIntent.ChangeTextStep(3))
        advanceUntilIdle()
        assertTrue(f.bus.dispatched.isEmpty())
    }

    @Test
    fun textStepThatFailsToSaveSaysSoAndKeepsTheStep() = test {
        val f = fixture(prefs = UserPreferences(readerTextStep = 1))
        advanceUntilIdle()
        f.bus.rejectNext(Rejection.StorageFailure)
        f.viewModel.onIntent(ReaderIntent.ChangeTextStep(2))
        runCurrent()
        assertEquals(1, f.state.textStep)
        assertEquals(ReaderToast.ActionFailed, f.state.toast)
    }

    // ---- mark unread ----------------------------------------------------------------------------------------------

    @Test
    fun markUnreadIsOfferedOnlyForAReadItem() = test {
        val unread = fixture(item = savedItem("a"))
        val read = fixture(item = savedItem("a", lifecycle = ItemLifecycle.Read))
        val archivedRead = fixture(item = savedItem("a", lifecycle = ItemLifecycle.Archived))
        advanceUntilIdle()
        assertFalse(unread.state.canMarkUnread)
        assertTrue(read.state.canMarkUnread)
        assertTrue(archivedRead.state.canMarkUnread)
    }

    @Test
    fun markUnreadPutsItBackAndSaysSo() = test {
        val f = fixture(item = savedItem("a", lifecycle = ItemLifecycle.Read))
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.MarkUnread)
        runCurrent()
        assertEquals(listOf<Command>(Command.MarkUnread(id)), f.bus.dispatched)
        assertEquals(ReaderToast.BackOnUnread, f.state.toast)
        assertFalse(f.state.canMarkUnread)
        advanceTimeBy(1_799)
        assertEquals(ReaderToast.BackOnUnread, f.state.toast)
        advanceTimeBy(2)
        assertNull(f.state.toast)
    }

    @Test
    fun markUnreadThatFailsToSaveSaysSo() = test {
        val f = fixture(item = savedItem("a", lifecycle = ItemLifecycle.Read))
        advanceUntilIdle()
        f.bus.rejectNext(Rejection.StorageFailure)
        f.viewModel.onIntent(ReaderIntent.MarkUnread)
        runCurrent()
        assertEquals(ReaderToast.ActionFailed, f.state.toast)
        assertTrue(f.state.canMarkUnread)
    }

    // ---- marked as read toast -------------------------------------------------------------------------------------

    @Test
    fun markedAsReadToastShowsOnceForTheOpenThatMarkedIt() = test {
        val f = fixture(item = savedItem("a", lifecycle = ItemLifecycle.Read), markedRead = true)
        runCurrent()
        assertEquals(ReaderToast.MarkedRead, f.state.toast)
        advanceTimeBy(2_199)
        assertEquals(ReaderToast.MarkedRead, f.state.toast)
        advanceTimeBy(2)
        assertNull(f.state.toast)
        // The item changing again (here: new metadata) does not bring it back.
        f.bus.items.upsert(listOf(savedItem("a", lifecycle = ItemLifecycle.Read, title = "New title")))
        advanceUntilIdle()
        assertEquals("New title", f.state.item?.title)
        assertNull(f.state.toast)
    }

    @Test
    fun noMarkedAsReadToastWhenTheOpenDidNotMarkIt() = test {
        val f = fixture(item = savedItem("a", lifecycle = ItemLifecycle.Read), markedRead = false)
        runCurrent()
        assertNull(f.state.toast)
    }

    // ---- missing item ---------------------------------------------------------------------------------------------

    @Test
    fun itemThatDoesNotExistIsMissing() = test {
        val f = fixture(item = null)
        assertFalse(f.state.missing)
        advanceUntilIdle()
        assertTrue(f.state.missing)
        assertNull(f.state.item)
    }

    @Test
    fun deletedItemIsMissing() = test {
        val f = fixture(item = savedItem("a", lifecycle = ItemLifecycle.Deleted))
        advanceUntilIdle()
        assertTrue(f.state.missing)
    }

    @Test
    fun itemDeletedWhileOpenBecomesMissing() = test {
        val f = fixture()
        advanceUntilIdle()
        assertFalse(f.state.missing)
        f.bus.items.items.value = emptyList()
        advanceUntilIdle()
        assertTrue(f.state.missing)
    }

    // ---- effects --------------------------------------------------------------------------------------------------

    @Test
    fun closeAndLinksBecomeEffects() = test {
        val f = fixture(item = savedItem("a", url = "http://example.com/a"))
        advanceUntilIdle()
        f.viewModel.effects.test {
            f.viewModel.onIntent(ReaderIntent.Close)
            assertEquals(ReaderEffect.Close, awaitItem())
            f.viewModel.onIntent(ReaderIntent.OpenLink("https://example.com/more"))
            assertEquals(ReaderEffect.OpenInBrowser("https://example.com/more"), awaitItem())
            f.viewModel.onIntent(ReaderIntent.OpenLink("mailto:someone@example.com"))
            f.viewModel.onIntent(ReaderIntent.OpenLink("intent://scan#Intent;scheme=zxing;end"))
            // The browser gets the saved URL as it is: it can still show an http page the in-app view cannot.
            f.viewModel.onIntent(ReaderIntent.OpenInBrowser)
            assertEquals(ReaderEffect.OpenInBrowser("http://example.com/a"), awaitItem())
            expectNoEvents()
        }
    }

    // ---- web page -------------------------------------------------------------------------------------------------

    @Test
    fun webProgressFailureAndRetry() = test {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.Web(WebEvent.Progress(0.5f)))
        assertEquals(0.5f, f.state.loadProgress)
        f.viewModel.onIntent(ReaderIntent.Web(WebEvent.Failed))
        assertTrue(f.state.webFailed)
        assertEquals(1f, f.state.loadProgress)
        // A late progress report from the failed page does not hide the failure.
        f.viewModel.onIntent(ReaderIntent.Web(WebEvent.Progress(1f)))
        assertTrue(f.state.webFailed)
        f.viewModel.onIntent(ReaderIntent.RetryWeb)
        assertFalse(f.state.webFailed)
        assertEquals(0f, f.state.loadProgress)
    }

    @Test
    fun switchingBackToWebModeLoadsThePageAfresh() = test {
        val f = fixture(result = ReaderResult.Ready(article))
        advanceUntilIdle()
        f.viewModel.onIntent(ReaderIntent.Web(WebEvent.Failed))
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        f.viewModel.onIntent(ReaderIntent.ToggleReaderMode)
        assertFalse(f.state.webFailed)
        assertEquals(0f, f.state.webProgress)
    }
}
