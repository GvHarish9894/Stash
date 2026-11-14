package com.techgv.stash.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.component.AddLinkSheet
import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.designsystem.component.DateHeader
import com.techgv.stash.core.designsystem.component.FilterChip
import com.techgv.stash.core.designsystem.component.MetaLine
import com.techgv.stash.core.designsystem.component.ReaderModeView
import com.techgv.stash.core.designsystem.component.ReaderTopBar
import com.techgv.stash.core.designsystem.component.SavedItemCard
import com.techgv.stash.core.designsystem.component.SearchResultCard
import com.techgv.stash.core.designsystem.component.SegmentedControl
import com.techgv.stash.core.designsystem.component.SettingsSwitchRow
import com.techgv.stash.core.designsystem.component.UndoSnackbar
import com.techgv.stash.core.designsystem.presentation.CardLifecycle
import com.techgv.stash.core.designsystem.presentation.NOW
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.savedItem
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.model.RichSpan
import com.techgv.stash.core.model.RichText
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

// Runs on the iOS simulator and as an Android device test, in English.

@Composable
private fun Themed(reduceMotion: Boolean = true, content: @Composable () -> Unit) =
    StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = reduceMotion, systemReduceMotion = false, content = content)

private val unread: SavedItemCardUi = savedItem().toCardUi(NOW, TimeZone.UTC)

private fun contentDescriptionStartsWith(prefix: String) = SemanticsMatcher("contentDescription starts with \"$prefix\"") {
    it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.startsWith(prefix) == true
}

@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class ComponentUiTest {

    private fun cardTest(
        item: SavedItemCardUi = unread,
        start: SwipeOutcome = SwipeOutcome.Remove,
        end: SwipeOutcome = SwipeOutcome.MarkRead,
        block: ComposeUiTest.(events: MutableList<String>) -> Unit,
    ) = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            Themed {
                Box(Modifier.width(600.dp)) {
                    SavedItemCard(
                        item, CardLayout.Comfortable, start, end,
                        onOpen = { events += "open" }, onAction = { events += "action:$it" }, onMore = { events += "more" },
                        onRetry = { events += "retry" },
                    )
                }
            }
        }
        block(events)
    }

    private fun ComposeUiTest.actionLabels(): List<String> =
        onNodeWithTag("item-a").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    private fun ComposeUiTest.runAction(index: Int) {
        val action = onNodeWithTag("item-a").fetchSemanticsNode().config[SemanticsActions.CustomActions][index]
        runOnIdle { action.action() }
    }

    @Test
    fun unreadCardLabelAndActions() = cardTest { events ->
        onNodeWithTag("item-a")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(
                "Unread. Designing for slow reading in a fast feed. medium.com, 2 hours ago, 7 min read.",
            )))
        assertEquals(listOf("Mark as read", "Archive", "Share", "Remove from shelf", "More options"), actionLabels())
        runAction(1)
        onNodeWithTag("item-a").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("action:Archive", "open"), events)
    }

    @Test
    fun lifecycleAndDetailsStatesAreIndependent() {
        cardTest(item = unread.copy(lifecycle = CardLifecycle.Read, unread = false)) {
            onNodeWithTag("item-a").assert(contentDescriptionStartsWith("Read. Designing"))
            assertEquals("Mark as unread", actionLabels().first())
        }
        val archived = savedItem(age = 20.days, readAt = NOW - 15.days, archivedAt = NOW - 10.days).toCardUi(NOW, TimeZone.UTC)
        cardTest(item = archived, start = SwipeOutcome.Delete, end = SwipeOutcome.Restore) {
            onNodeWithTag("item-a").assert(contentDescriptionStartsWith("Archived. Designing"))
            assertEquals(listOf("Restore to shelf", "Share", "Delete forever", "More options"), actionLabels())
            onNodeWithText("medium.com · Archived Nov 2 · 7 min read", useUnmergedTree = true).assertExists()
        }
        cardTest(item = savedItem(readAt = NOW - 1.days, fetchStatus = FetchStatus.Pending).toCardUi(NOW, TimeZone.UTC)) {
            onNodeWithTag("item-a").assert(contentDescriptionStartsWith("Read. Loading details. Designing"))
            assertEquals("Mark as unread", actionLabels().first())
        }
        val archivedFailed = savedItem(age = 20.days, readAt = NOW - 15.days, archivedAt = NOW - 10.days, fetchStatus = FetchStatus.Failed)
            .toCardUi(NOW, TimeZone.UTC)
        cardTest(item = archivedFailed, start = SwipeOutcome.Delete, end = SwipeOutcome.Restore) { events ->
            onNodeWithTag("item-a").assert(contentDescriptionStartsWith("Archived. Couldn’t load details. https://medium.com/slow-reading."))
            assertEquals(listOf("Restore to shelf", "Share", "Delete forever", "Retry", "More options"), actionLabels())
            onNodeWithText("Retry", useUnmergedTree = true).assertExists()
            runAction(3)
            assertEquals(listOf("retry"), events)
        }
    }

    @Test
    fun swipePastThresholdActs() = cardTest { events ->
        onNodeWithTag("item-a").performTouchInput { swipeRight(startX = 20f, endX = 20f + 140.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        assertEquals(listOf("action:MarkRead"), events)
    }

    @Test
    fun swipeShortOfThresholdSpringsBack() = cardTest { events ->
        onNodeWithTag("item-a").performTouchInput { swipeRight(startX = 20f, endX = 20f + 90.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        assertEquals(emptyList(), events)
    }

    @Test
    fun swipeTowardNothingRubberBandsAndDoesNothing() = cardTest(start = SwipeOutcome.RubberBand) { events ->
        onNodeWithTag("item-a").performTouchInput { swipeLeft(startX = right - 20f, endX = right - 20f - 300.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        assertEquals(emptyList(), events)
    }

    @Test
    fun flownOffCardComesBackWhenTheActionIsRefused() = cardTest { events ->
        // Remove flies the card off; the callback changes nothing, as when the command is refused.
        onNodeWithTag("item-a").performTouchInput { swipeLeft(startX = right - 20f, endX = right - 20f - 200.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        assertEquals(listOf("action:Remove"), events)
        onNodeWithTag("item-a").assert(SemanticsMatcher.expectValue(com.techgv.stash.core.designsystem.component.CardDisplacedKey, true))
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        // The card is back in place (it stays off screen when the reset is missing).
        onNodeWithTag("item-a").assert(SemanticsMatcher.expectValue(com.techgv.stash.core.designsystem.component.CardDisplacedKey, false))
    }

    @Test
    fun longPressOpensMenu() = cardTest { events ->
        onNodeWithTag("item-a").performTouchInput { longClick(durationMillis = 600) }
        waitForIdle()
        assertEquals(listOf("more"), events)
    }

    @Test
    fun searchResultCardHasTheCardSemantics() = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            Themed {
                SearchResultCard(
                    unread, onOpen = { events += "open" }, onAction = { events += "action:$it" }, onMore = { events += "more" },
                    titleHighlights = listOf(0..8), siteHighlights = listOf(0..5),
                )
            }
        }
        onNodeWithTag("item-a").assert(contentDescriptionStartsWith("Unread. Designing"))
        onNodeWithText("medium.com · 2h ago", useUnmergedTree = true).assertExists()
        onNodeWithTag("item-a").performTouchInput { longClick(durationMillis = 600) }
        onNodeWithTag("item-a").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("more", "open"), events)
    }

    @Test
    fun metaLineDropsLengthFirst() = runComposeUiTest {
        setContent { Themed { Box(Modifier.width(150.dp)) { MetaLine(unread) } } }
        // Both variants are composed; only the one that fits is placed (and so shown and announced).
        onNodeWithText("medium.com · 2h ago").assertIsDisplayed()
        onNodeWithText("medium.com · 2h ago · 7 min read").assertIsNotDisplayed()
    }

    @Test
    fun filterChipSelection() = runComposeUiTest {
        var selected by mutableStateOf(false)
        setContent { Themed { FilterChip("Medium", selected, { selected = true }, count = 6) } }
        onNodeWithText("Medium · 6").assertIsNotSelected().performClick()
        onNodeWithText("Medium · 6").assertIsSelected()
    }

    @Test
    fun segmentedControlIsARadioGroup() = runComposeUiTest {
        setContent { Themed { SegmentedControl(listOf("S", "M", "L"), "M", { it }, {}) } }
        val m = onNodeWithText("M")
        m.assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        onNodeWithText("S").assertIsNotSelected()
        m.onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    }

    @Test
    fun settingsSwitchRowTogglesAsOneSwitch() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent { Themed { SettingsSwitchRow("Reduce motion", "Turns off page and card animations", on, { on = it }) } }
        val row = onNodeWithText("Reduce motion")
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun dateHeaderIsAHeading() = runComposeUiTest {
        setContent { Themed { DateHeader("Today") } }
        onNodeWithText("TODAY").assert(isHeading())
    }

    private var inputMode: InputModeManager? = null

    private fun undoTest(timeout: Duration = 10.seconds, block: ComposeUiTest.(events: MutableList<String>) -> Unit) = runComposeUiTest {
        val events = mutableListOf<String>()
        mainClock.autoAdvance = false
        setContent {
            inputMode = LocalInputModeManager.current
            Themed(reduceMotion = false) {
                UndoSnackbar("Removed from your shelf", { events += "undo" }, { events += "dismiss" }, { events += "timeout" }, timeout = timeout)
            }
        }
        mainClock.advanceTimeByFrame()
        block(events)
    }

    @Test
    fun undoSnackbarActions() = undoTest { events ->
        onNodeWithText("Undo").performClick()
        onNodeWithContentDescription("Dismiss").performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(listOf("undo", "dismiss"), events)
    }

    @Test
    fun undoSnackbarTimesOut() = undoTest { events ->
        mainClock.advanceTimeBy(9_000)
        assertEquals(emptyList(), events)
        mainClock.advanceTimeBy(1_500)
        assertEquals(listOf("timeout"), events)
    }

    @Test
    fun undoSnackbarPausesWhileHeld() = undoTest { events ->
        mainClock.advanceTimeBy(5_000)
        onNodeWithText("Removed from your shelf").performTouchInput { down(center) }
        mainClock.advanceTimeBy(20_000)
        assertEquals(emptyList(), events, "held: no timeout")
        onNodeWithText("Removed from your shelf").performTouchInput { up() }
        mainClock.advanceTimeBy(4_000)
        assertEquals(emptyList(), events, "the countdown resumes where it stopped")
        mainClock.advanceTimeBy(2_000)
        assertEquals(listOf("timeout"), events)
    }

    @Test
    fun undoSnackbarPausesWhileFocused() = undoTest { events ->
        // Keyboard mode, as for a keyboard or switch user; in touch mode Android does not focus a clickable.
        runOnIdle { inputMode?.requestInputMode(InputMode.Keyboard) }
        onNodeWithText("Undo").requestFocus()
        mainClock.advanceTimeByFrame()
        onNodeWithText("Undo").assertIsFocused()
        mainClock.advanceTimeBy(30_000)
        assertEquals(emptyList(), events)
    }

    @Test
    fun undoSnackbarWithInfiniteTimeoutStays() = undoTest(timeout = Duration.INFINITE) { events ->
        mainClock.advanceTimeBy(120_000)
        assertEquals(emptyList(), events)
        onNodeWithText("Undo").assertExists()
    }

    @Test
    fun addLinkErrorIsExposed() = runComposeUiTest {
        val message = "That doesn’t look like a link. Try one that starts with https://"
        setContent { Themed { AddLinkSheet(true, "not a link", {}, ClipboardSuggestion.Hidden, message, {}, {}, {}) } }
        onNodeWithContentDescription("URL").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, message))
        onNodeWithText(message).assertExists()
        onNodeWithText("There is a link on your clipboard", useUnmergedTree = true).assertExists()
    }

    @Test
    fun readerTopBarToggleAndOptionalMarkUnread() = runComposeUiTest {
        var mode by mutableStateOf(false)
        setContent { Themed { ReaderTopBar("Title", "medium.com", mode, { 0.5f }, {}, { mode = !mode }, {}, showMarkUnread = false) } }
        val aa = onNodeWithContentDescription("Reader mode")
        aa.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        aa.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        onNodeWithText("Mark unread").assertDoesNotExist()
    }

    @Test
    fun readerLinksReportTheirUrl() = runComposeUiTest {
        val clicked = mutableListOf<String>()
        val article = ReaderArticle(
            "Title", "medium.com", null, 3,
            listOf(ReaderBlock.Paragraph(RichText("Read more", listOf(RichSpan.Link(0, 9, "https://example.com/more"))))),
        )
        setContent { Themed { ReaderModeView(article, 1, {}, onLinkClick = { clicked += it }) } }
        onNodeWithText("Read more").performClick()
        assertEquals(listOf("https://example.com/more"), clicked)
    }

    @Test
    fun materialThemeUsesTheStashTokens() = runComposeUiTest {
        val checks = mutableListOf<Pair<String, Boolean>>()
        setContent {
            Themed {
                val c = Stash.colors
                val m = MaterialTheme.colorScheme
                checks += "surfaceContainerLow" to (m.surfaceContainerLow == c.surface)
                checks += "surfaceContainerHigh" to (m.surfaceContainerHigh == c.surface)
                checks += "inverseSurface" to (m.inverseSurface == c.toast)
                checks += "inversePrimary" to (m.inversePrimary == c.toastAction)
                checks += "outline" to (m.outline == c.inputBorder)
                checks += "outlineVariant" to (m.outlineVariant == c.outline)
                checks += "secondaryContainer" to (m.secondaryContainer == c.variant)
                checks += "bodyLarge font" to (MaterialTheme.typography.bodyLarge.fontFamily == Stash.fonts.inter)
                checks += "titleLarge font" to (MaterialTheme.typography.titleLarge.fontFamily == Stash.fonts.lora)
                checks += "labelLarge font" to (MaterialTheme.typography.labelLarge.fontFamily == Stash.fonts.inter)
                checks += "extraLarge shape" to (MaterialTheme.shapes.extraLarge == androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            }
        }
        waitForIdle()
        assertEquals(emptyList(), checks.filterNot { it.second }.map { it.first })
        assertTrue(checks.size == 11)
    }
}
