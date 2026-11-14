package com.techgv.stash.navigation

import kotlin.math.abs
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import com.techgv.stash.core.designsystem.theme.StashMotion
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.platform.A11yPrefs
import com.techgv.stash.core.platform.BrowserLauncher
import com.techgv.stash.core.platform.NotificationPermission
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.platform.ScheduledNotification
import com.techgv.stash.core.testing.FakeA11yPrefs
import com.techgv.stash.core.testing.FakeBrowserLauncher
import com.techgv.stash.core.testing.FakeNotificationPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Whole-app journeys through the navigation host: every feature on the real data layer (in-memory database), the
 * capability fakes and a mock network. Each one is a path a user takes; the assertions are what they would see.
 */
@OptIn(ExperimentalTestApi::class)
class StashJourneyTest {
    private val searchPill = hasText("Search your shelf")
    private val keepReading = hasText("Keep reading")
    private val readerClose = "Close and return to Stash"

    private fun Journey.openMenu(id: ItemId) = card(id).performSemanticsAction(SemanticsActions.OnLongClick)

    private fun Journey.swipeStartward(id: ItemId) =
        card(id).performTouchInput { swipeLeft(startX = right - 20f, endX = right - 20f - 160.dp.toPx(), durationMillis = 300) }

    private fun Journey.swipeEndward(id: ItemId) =
        card(id).performTouchInput { swipeRight(startX = 20f, endX = 20f + 160.dp.toPx(), durationMillis = 300) }

    @Test
    fun anAddedLinkOpensInTheReaderAndTheFirstReadCelebratesOnceAfterClosing() =
        journeyTest(seed = { settings { it.copy(readerModeDefault = true) } }) {
            test.onNodeWithText("Add link").performClick()
            waitForText("Save to shelf")
            test.onNodeWithContentDescription("URL").performTextInput("https://example.org/Slow-reading")
            test.onNodeWithText("Save to shelf").performClick()
            // The pending card: the fetch job is a fake, so its details stay loading.
            waitFor(labelled("Unread. Loading details."))
            test.onNode(labelled("Loading details")).performClick()

            waitForText("Paragraph 1 of Slow-reading", substring = true)
            // The read happened in the reader: the celebration waits for it to close.
            test.onNode(keepReading).assertDoesNotExist()
            test.onNodeWithContentDescription(readerClose).performClick()
            waitFor(keepReading)
            test.onNode(keepReading).performClick()
            waitGone(keepReading)

            // Read again the same day: no second celebration.
            test.onNode(labelled("Read.")).performClick()
            waitForText("Paragraph 1 of Slow-reading", substring = true)
            test.onNodeWithContentDescription(readerClose).performClick()
            waitFor(searchPill)
            test.waitForIdle()
            test.onNode(keepReading).assertDoesNotExist()
        }

    @Test
    fun theMenuMarksUnreadAndReadAndASwipeArchivesWithUndoThenTheArchiveRestores() {
        var id = ItemId("")
        journeyTest(seed = {
            id = saveFetched("https://example.org/Menu-and-swipes")
            settings { it.copy(swipeStartAction = SwipeAction.Archive) }
        }) {
            // As with a screen reader on: the undo bar stays until it is used (the test clock skips timeouts).
            (koin.get<A11yPrefs>() as FakeA11yPrefs).recommended = { Duration.INFINITE }
            waitFor(labelled("Unread. Menu-and-swipes."))
            openMenu(id)
            waitForText("Mark as read")
            test.onNodeWithText("Mark as read").performClick()
            // The first read of the day, outside a reader: celebrated at once.
            waitFor(keepReading)
            test.onNode(keepReading).performClick()
            waitFor(labelled("Read. Menu-and-swipes."))
            openMenu(id)
            waitForText("Mark as unread")
            test.onNodeWithText("Mark as unread").performClick()
            waitFor(labelled("Unread. Menu-and-swipes."))

            swipeStartward(id)
            waitForText("Moved to Archive")
            test.onNodeWithText("Undo").performClick()
            waitFor(labelled("Unread. Menu-and-swipes."))

            swipeStartward(id)
            waitForText("Moved to Archive")
            waitGone(cardMatcher(id))
            test.onAllNodesWithContentDescription("Archive").onFirst().performClick()
            waitFor(labelled("Archived. Menu-and-swipes."))
            // The archive restores with a right swipe whatever the reading direction.
            swipeEndward(id)
            waitForText("Restored to your shelf")
            pressBack()
            waitFor(searchPill)
            waitFor(labelled("Unread. Menu-and-swipes."))
        }
    }

    @Test
    fun searchFindsByTitlePrefixOpensTheReaderAndTheReaderReturnsToSearch() {
        var compose = ItemId("")
        var room = ItemId("")
        journeyTest(seed = {
            settings { it.copy(readerModeDefault = true) }
            compose = saveFetched("https://example.org/Compose-layouts")
            room = saveFetched("https://example.org/Room-guide")
        }) {
            waitFor(cardMatcher(room))
            test.onNode(searchPill).performClick()
            test.onNodeWithContentDescription("Search saved items").performTextInput("comp")
            waitFor(cardMatcher(compose))
            card(room).assertDoesNotExist()

            card(compose).performClick()
            waitForText("Paragraph 1 of Compose-layouts", substring = true)
            test.onNodeWithContentDescription(readerClose).performClick()
            waitFor(keepReading)
            test.onNode(keepReading).performClick()

            // Back in search, query and result as they were.
            waitFor(cardMatcher(compose))
            test.onNode(hasSetTextAction() and hasText("comp")).assertIsDisplayed()
            test.onNode(searchPill).assertDoesNotExist()
            pressBack()
            waitFor(searchPill)
        }
    }

    @Test
    fun oneBackLeavesSearchAfterTheReaderReturnsToIt() {
        var id = ItemId("")
        journeyTest(seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Back-from-search")
            // Today's read already happened, so no celebration stands between the reader and Search.
            bus.dispatch(Command.OpenItem(id))
        }) {
            waitFor(cardMatcher(id))
            test.onNode(searchPill).performClick()
            val field = test.onNodeWithContentDescription("Search saved items")
            field.assertIsFocused()
            field.performTextInput("back")
            waitFor(cardMatcher(id))
            card(id).performClick()
            waitForText("Paragraph 1 of Back-from-search", substring = true)
            pressBack()
            waitFor(cardMatcher(id))
            test.waitForIdle()
            // The field does not take focus again: on a phone that reopened the keyboard, which took the first Back.
            test.onNodeWithContentDescription("Search saved items").assertIsNotFocused()
            pressBack()
            waitFor(searchPill)
        }
    }

    // ---- Milestone 4 wiring (T4.4) ----------------------------------------------------------------------------

    @Test
    fun closingTheReaderSettlesItsCardAfterTheCloseTransition() {
        var id = ItemId("")
        journeyTest(seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Settle")
            bus.dispatch(Command.OpenItem(id))
        }) {
            settings { it.copy(reduceMotion = false) }
            waitFor(cardMatcher(id))
            fun width() = card(id).fetchSemanticsNode().boundsInRoot.width
            test.waitForIdle()
            val rest = width()
            card(id).performClick()
            waitFor(labelled(readerClose))
            test.waitForIdle()
            test.mainClock.autoAdvance = false
            pressBack()
            // The reader drops for 240 ms, then the card settles from 0.95.
            test.mainClock.advanceTimeBy(StashMotion.READER_CLOSE + 64L)
            assertTrue(width() < rest * 0.98f, "settling: ${width()} of $rest")
            test.mainClock.advanceTimeBy(800)
            assertEquals(rest, width(), 1f)
            test.mainClock.autoAdvance = true
        }
    }

    @Test
    fun homeToSearchCarriesTheSearchBarWhileHomeRecedesOverTheRootColour() {
        journeyTest(seed = { saveFetched("https://example.org/Shared-bar") }) {
            settings { it.copy(reduceMotion = false) }
            waitFor(searchPill)
            test.waitForIdle()
            val pill = test.onNode(searchPill).fetchSemanticsNode().boundsInRoot
            test.mainClock.autoAdvance = false
            test.onNode(searchPill).performClick()
            test.mainClock.advanceTimeBy(48)
            val frame = test.onRoot().captureToImage().toPixelMap()
            test.mainClock.advanceTimeBy(600)
            test.mainClock.autoAdvance = true
            test.waitForIdle()
            val dp = test.density.density
            // Below where the field ends up and above the pill's bottom, only the travelling bar draws the field's 2 dp
            // primary border (Parchment, Leather).
            val fieldBottom = test.onNodeWithContentDescription("Search saved items").fetchSemanticsNode().boundsInRoot.center.y + 26 * dp
            val x = pill.center.x.toInt()
            val border = Color(0xFF7A4E2D)
            assertTrue(
                (fieldBottom.toInt()..(pill.bottom + 2 * dp).toInt()).any { y -> frame[x, y].near(border) },
                "no search bar between ${fieldBottom / dp} dp and ${pill.bottom / dp} dp",
            )
            // Home scales to 0.97: at the window's edge color-root (dark) shows through instead of the parchment.
            val edge = frame[1, (frame.height * 0.6f).toInt()]
            assertTrue(edge.red < 0.93f, "edge $edge (parchment is 0.96)")
        }
    }

    @Test
    fun closingTheStreakSheetReturnsFocusToTheStreakChip() {
        journeyTest(seed = { saveFetched("https://example.org/Focus") }) {
            val chip = test.onNode(labelled("reading streak"))
            waitFor(labelled("reading streak"))
            leaveTouchMode()
            chip.requestFocus()
            chip.performKeyInput { pressKey(Key.Enter) }
            waitForText("Reminders")
            // Underneath leaves the accessibility tree while the sheet is open.
            test.onNode(searchPill).assertDoesNotExist()
            pressBack()
            waitFor(searchPill)
            test.waitForIdle()
            chip.assertIsFocused()
        }
    }

    @Test
    fun aThemeAndACardLayoutChosenInSettingsChangeTheShelf() {
        var id = ItemId("")
        journeyTest(seed = { id = saveFetched("https://example.org/Layouts") }) {
            waitFor(cardMatcher(id))
            fun background() = test.onRoot().captureToImage().toPixelMap().let { it[2, it.height / 2] }
            assertEquals(Color(0xFFF4ECD8), background())
            val comfortable = card(id).fetchSemanticsNode().boundsInRoot.height

            test.onNodeWithContentDescription("Settings").performClick()
            test.onNodeWithText("Night library").performClick()
            test.onNodeWithText("Compact").performClick()
            pressBack()
            waitFor(searchPill)
            test.waitForIdle()

            assertEquals(Color(0xFF1E1812), background())
            assertTrue(card(id).fetchSemanticsNode().boundsInRoot.height < comfortable, "compact cards are shorter")
        }
    }

    @Test
    fun theStreakSheetsRemindersOpensSettingsWhereAGrantedPermissionTurnsTheReminderOn() =
        journeyTest(seed = { settings { it.copy(dailyReminder = false) } }) {
            val permission = koin.get<NotificationPermission>() as FakeNotificationPermission
            permission.grantsOnRequest = true
            test.onNode(labelled("reading streak")).performClick()
            test.onNodeWithText("Reminders").performClick()

            val reminder = hasText("Daily reminder") and isToggleable()
            waitFor(reminder)
            test.onNode(reminder).assertIsOff().performClick()
            waitUntilOn(reminder)
            assertEquals(1, permission.requestCount)
            assertTrue(koin.get<SettingsRepository>().preferences.first().dailyReminder)
        }

    @Test
    fun aDeniedPermissionLeavesTheReminderOffAndOffersTheSystemSettings() =
        journeyTest(seed = { settings { it.copy(dailyReminder = false) } }) {
            val permission = koin.get<NotificationPermission>() as FakeNotificationPermission
            permission.grantsOnRequest = false
            test.onNode(labelled("reading streak")).performClick()
            test.onNodeWithText("Reminders").performClick()

            val reminder = hasText("Daily reminder") and isToggleable()
            waitFor(reminder)
            test.onNode(reminder).performClick()
            waitForText("Open notification settings")
            test.onNode(reminder).assertIsOff()
            test.onNodeWithText("Open notification settings").performClick()
            test.waitUntil(timeoutMillis = 5_000) { permission.settingsOpened == 1 }
            assertFalse(koin.get<SettingsRepository>().preferences.first().dailyReminder)
        }

    @Test
    fun theFirstSaveAsksForNotificationsOnceAndAGrantPlansTheReminders() {
        val permission = PromptedPermission()
        val scheduler = PermittedScheduler(permission)
        journeyTest(overrides = module {
            single<NotificationPermission> { permission }
            single<NotificationScheduler> { scheduler }
        }) {
            waitFor(searchPill)
            test.waitForIdle()
            assertEquals(0, permission.requests, "not asked on an empty shelf")

            save("https://example.org/First-save")
            test.waitUntil(timeoutMillis = 5_000) { permission.requests == 1 }
            assertTrue(koin.get<SettingsRepository>().preferences.first().notificationPermissionAsked)
            // The save re-planned while notifications were not permitted, so nothing reached the OS yet.
            test.waitUntil(timeoutMillis = 5_000) { scheduler.applies > 0 }
            assertEquals(emptyList(), scheduler.scheduled)

            permission.answer.complete(true)
            test.waitUntil(timeoutMillis = 5_000) { scheduler.scheduled.isNotEmpty() }
            save("https://example.org/Second-save")
            test.waitForIdle()
            assertEquals(1, permission.requests)
        }
    }

    @Test
    fun aDeniedFirstAskIsNotRepeatedAndSettingsOffersTheSystemSettings() =
        journeyTest {
            val permission = koin.get<NotificationPermission>() as FakeNotificationPermission
            permission.grantsOnRequest = false
            save("https://example.org/Denied")
            test.waitUntil(timeoutMillis = 5_000) { permission.requestCount == 1 }

            recreate()
            save("https://example.org/Denied-again")
            waitFor(searchPill)
            test.waitForIdle()
            assertEquals(1, permission.requestCount)

            test.onNode(labelled("reading streak")).performClick()
            test.onNodeWithText("Reminders").performClick()
            waitForText("Open notification settings")
            assertTrue(koin.get<SettingsRepository>().preferences.first().dailyReminder, "the reminder setting stays on")
        }

    /** Notification permission whose prompt waits for [answer]. */
    private class PromptedPermission : NotificationPermission {
        val answer = CompletableDeferred<Boolean>()
        var requests = 0
        private var granted = false

        override suspend fun isGranted() = granted

        override suspend fun request(): Boolean {
            requests++
            granted = answer.await()
            return granted
        }

        override fun openSettings() = Unit
    }

    /** Like iOS: a plan applied while notifications are not permitted schedules nothing. */
    private class PermittedScheduler(private val permission: NotificationPermission) : NotificationScheduler {
        var applies = 0
        var scheduled: List<ScheduledNotification> = emptyList()

        override suspend fun apply(plan: List<ScheduledNotification>) {
            applies++
            scheduled = if (permission.isGranted()) plan else emptyList()
        }
    }

    @Test
    fun aDeepLinkOpensTheReaderWithHomeUnderneathAndAMissingIdSaysSo() {
        var id = ItemId("")
        journeyTest(seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/From-a-widget")
        }) {
            waitFor(cardMatcher(id))
            test.onNodeWithContentDescription("Settings").performClick()
            test.runOnIdle { DeepLinks.open(id.value) }
            waitForText("Paragraph 1 of From-a-widget", substring = true)
            assertEquals(null, DeepLinks.pending.value)
            test.onNodeWithContentDescription(readerClose).performClick()
            // The link's open was the first read of the day; then Home, not the Settings screen the link interrupted.
            waitFor(keepReading)
            test.onNode(keepReading).performClick()
            waitFor(searchPill)

            test.runOnIdle { DeepLinks.open("no-such-item") }
            waitForText("This link is gone")
            pressBack()
            waitFor(searchPill)
        }
    }

    @Test
    fun backClosesTheTopLayerFirst() {
        var id = ItemId("")
        journeyTest(seed = {
            settings { it.copy(openLinksIn = OpenLinksIn.Browser) }
            id = saveFetched("https://example.org/Back-order")
        }) {
            waitFor(cardMatcher(id))
            // An item sheet on Home.
            openMenu(id)
            waitForText("Mark as read")
            pressBack()
            waitGone(hasText("Mark as read"))
            test.onNode(searchPill).assertIsDisplayed()

            // The streak sheet.
            test.onNode(labelled("reading streak")).performClick()
            waitForText("Read something short")
            pressBack()
            waitGone(hasText("Read something short"))

            // A celebration over Search (the result opened in the browser): back closes it, then Search.
            test.onNode(searchPill).performClick()
            test.onNodeWithContentDescription("Search saved items").performTextInput("back")
            waitFor(cardMatcher(id))
            card(id).performClick()
            waitFor(keepReading)
            assertEquals(1, (koin.get<BrowserLauncher>() as FakeBrowserLauncher).opened.size)
            pressBack()
            waitGone(keepReading)
            test.onNodeWithContentDescription("Search saved items").assertIsDisplayed()
            pressBack()
            waitFor(searchPill)
        }
    }

    @Test
    fun aDeepLinkedReaderOverAnOpenSheetClosesFirst() {
        var id = ItemId("")
        journeyTest(seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Over-a-sheet")
            // Today's read already happened, so no celebration covers Home.
            bus.dispatch(Command.OpenItem(id))
        }) {
            waitFor(cardMatcher(id))
            test.onNodeWithText("Add link").performClick()
            waitForText("Save to shelf")
            test.runOnIdle { DeepLinks.open(id.value) }
            waitForText("Paragraph 1 of Over-a-sheet", substring = true)

            // Back closes the reader on top, not the sheet hidden under it.
            pressBack()
            waitGone(labelled(readerClose))
            waitForText("Save to shelf")
            pressBack()
            waitGone(hasText("Save to shelf"))
        }
    }

    @Test
    fun backClosesAScreenOverTheReaderPaneAndKeepsThePane() {
        var id = ItemId("")
        journeyTest(width = TabletWidth, seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Pane-under-settings")
            // Today's read already happened, so no celebration covers the pane.
            bus.dispatch(Command.OpenItem(id))
        }) {
            waitFor(cardMatcher(id))
            card(id).performClick()
            waitForText("Paragraph 1 of Pane-under-settings", substring = true)
            test.onNodeWithContentDescription("Settings").performClick()
            waitForText("SECTIONS")

            pressBack()
            waitGone(hasText("SECTIONS"))
            waitForText("Paragraph 1 of Pane-under-settings", substring = true)
            test.onNodeWithText("Nothing open yet").assertDoesNotExist()
        }
    }

    @Test
    fun aDoubleTapOnACardOpensOneReader() {
        var id = ItemId("")
        journeyTest(seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Double-tap")
            // Today's read already happened, so no celebration stands between Close and the shelf.
            bus.dispatch(Command.OpenItem(id))
        }) {
            waitFor(cardMatcher(id))
            // 10 ms apart: the second tap lands before the first reader covers the card.
            card(id).performTouchInput {
                click()
                advanceEventTime(10)
                click()
            }
            waitForText("Paragraph 1 of Double-tap", substring = true)
            test.waitForIdle()
            test.onNodeWithContentDescription(readerClose).performClick()
            // One Close was enough: no second reader underneath.
            waitFor(searchPill)
            test.onNode(labelled(readerClose)).assertDoesNotExist()
        }
    }

    @Test
    fun searchAndItsQuerySurviveARecreation() {
        var id = ItemId("")
        journeyTest(seed = { id = saveFetched("https://example.org/Recreated-search") }) {
            waitFor(cardMatcher(id))
            test.onNode(searchPill).performClick()
            test.onNodeWithContentDescription("Search saved items").performTextInput("recre")
            waitFor(cardMatcher(id))

            recreate()

            waitFor(cardMatcher(id))
            test.onNode(hasSetTextAction() and hasText("recre")).assertIsDisplayed()
            pressBack()
            waitFor(searchPill)
        }
    }

    @Test
    fun anExpandedWindowSelectsIntoTheReaderPaneAndCelebratesWhenThePaneCloses() {
        var id = ItemId("")
        journeyTest(width = TabletWidth, seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Side-by-side")
        }) {
            waitForText("Nothing open yet")
            card(id).performClick()
            waitForText("Paragraph 1 of Side-by-side", substring = true)
            // Selected, not navigated: Home is still there beside it.
            test.onNode(searchPill).assertIsDisplayed()
            test.onNodeWithText("Nothing open yet").assertDoesNotExist()

            recreate()
            waitForText("Paragraph 1 of Side-by-side", substring = true)
            test.onNode(keepReading).assertDoesNotExist()

            // Narrowed, the selection becomes the full-screen reader over Home and widened, the pane again; the
            // celebration keeps waiting for the item to close.
            width = PhoneWidth
            waitFor(labelled(readerClose))
            test.onNode(searchPill).assertDoesNotExist()
            width = TabletWidth
            waitFor(searchPill)
            waitForText("Paragraph 1 of Side-by-side", substring = true)
            test.onNode(keepReading).assertDoesNotExist()

            // Back empties the pane; the read happened there, so now the celebration shows.
            pressBack()
            waitFor(keepReading)
            test.onNode(keepReading).performClick()
            waitForText("Nothing open yet")

            // Settings in two panes.
            test.onNodeWithContentDescription("Settings").performClick()
            waitForText("SECTIONS")
        }
    }

    @Test
    fun reopeningAnItemInTheReaderPaneStartsAFreshReader() {
        var id = ItemId("")
        journeyTest(width = TabletWidth, seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Opened-twice")
        }) {
            waitFor(cardMatcher(id))
            card(id).performClick()
            waitForText("Marked as read")
            // The toast's own time runs out first, so only a new reader can show it again.
            waitGone(hasText("Marked as read"))
            pressBack()
            waitFor(keepReading)
            test.onNode(keepReading).performClick()
            waitForText("Nothing open yet")
            openMenu(id)
            waitForText("Mark as unread")
            test.onNodeWithText("Mark as unread").performClick()
            waitFor(labelled("Unread. Opened-twice."))

            card(id).performClick()
            waitForText("Marked as read")
        }
    }

    @Test
    fun theReaderPaneClearsThePreviousItemsViewModels() {
        var first = ItemId("")
        var second = ItemId("")
        journeyTest(width = TabletWidth, seed = {
            settings { it.copy(readerModeDefault = true) }
            first = saveFetched("https://example.org/First-pick")
            second = saveFetched("https://example.org/Second-pick")
            // Today's read already happened, so no celebration covers the pane.
            bus.dispatch(Command.OpenItem(first))
        }) {
            val pane = ViewModelProvider.create(viewModels, viewModelFactory { initializer { ReaderPaneStore() } })[ReaderPaneStore::class]
            waitFor(cardMatcher(first))
            card(first).performClick()
            waitForText("Paragraph 1 of First-pick", substring = true)
            val firstProbe = ClearedProbe.putIn(checkNotNull(pane.store))

            card(second).performClick()
            waitForText("Paragraph 1 of Second-pick", substring = true)
            assertTrue(firstProbe.cleared, "the first item's store is cleared")
            val secondProbe = ClearedProbe.putIn(checkNotNull(pane.store))

            pressBack()
            waitForText("Nothing open yet")
            assertTrue(secondProbe.cleared, "an empty pane clears its store")
            assertEquals(null, pane.store)
        }
    }

    @Test
    fun readerModeSurvivesAResizeBetweenThePaneAndTheFullScreenReader() {
        var id = ItemId("")
        val paragraph = "Paragraph 1 of Kept-in-reader-mode"
        val readerMode = labelled("Reader mode")
        journeyTest(width = TabletWidth, seed = {
            settings { it.copy(readerModeDefault = true) }
            id = saveFetched("https://example.org/Kept-in-reader-mode")
        }) {
            waitFor(cardMatcher(id))
            card(id).performClick()
            waitForText("Marked as read")
            waitGone(hasText("Marked as read"))
            waitForText(paragraph, substring = true)
            // The open reader's mode now differs from the setting, as after an Aa tap (web mode itself cannot be
            // composed here: UIKitView needs an interop container these tests do not have).
            settings { it.copy(readerModeDefault = false) }

            width = PhoneWidth
            waitFor(labelled(readerClose))
            test.onNode(searchPill).assertDoesNotExist()
            waitForText(paragraph, substring = true)
            waitUntilOn(readerMode)
            test.onNodeWithText("Marked as read").assertDoesNotExist()

            width = TabletWidth
            waitFor(searchPill)
            waitForText(paragraph, substring = true)
            waitUntilOn(readerMode)
            test.onNodeWithText("Marked as read").assertDoesNotExist()
        }
    }

    /** Stands in for the pane's reader view models: it records that its store was cleared. */
    private class ClearedProbe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }

        companion object {
            fun putIn(store: ViewModelStore) = ViewModelProvider.create(store, viewModelFactory { initializer { ClearedProbe() } })[ClearedProbe::class]
        }
    }

    private fun Journey.waitUntilOn(matcher: SemanticsMatcher) =
        test.waitUntil(timeoutMillis = 5_000) { runCatching { test.onNode(matcher).assertIsOn() }.isSuccess }
}

private fun Color.near(other: Color) = abs(red - other.red) < 0.08f && abs(green - other.green) < 0.08f && abs(blue - other.blue) < 0.08f
