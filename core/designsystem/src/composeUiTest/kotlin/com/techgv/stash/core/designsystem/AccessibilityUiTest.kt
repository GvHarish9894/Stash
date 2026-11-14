package com.techgv.stash.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.test.SemanticsNodeInteraction
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.WidgetSize
import com.techgv.stash.core.designsystem.component.WidgetPreview
import com.techgv.stash.core.designsystem.component.ArchiveLinkCard
import com.techgv.stash.core.designsystem.component.GestureTip
import com.techgv.stash.core.designsystem.component.NudgeBanner
import com.techgv.stash.core.designsystem.component.SearchPill
import com.techgv.stash.core.designsystem.component.StreakChip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.designsystem.component.SavedItemCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.component.AddLinkSheet
import com.techgv.stash.core.designsystem.component.BottomSheet
import com.techgv.stash.core.designsystem.component.CelebrationDialog
import com.techgv.stash.core.designsystem.component.InlineConfirm
import com.techgv.stash.core.designsystem.component.ItemActionSheet
import com.techgv.stash.core.designsystem.component.OptionCard
import com.techgv.stash.core.designsystem.component.PrimaryButton
import com.techgv.stash.core.designsystem.component.ReaderModeView
import com.techgv.stash.core.designsystem.component.ReaderTopBar
import com.techgv.stash.core.designsystem.component.RowOrStack
import com.techgv.stash.core.designsystem.component.SegmentedControl
import com.techgv.stash.core.designsystem.component.StashIcons
import com.techgv.stash.core.designsystem.component.StreakSheet
import com.techgv.stash.core.designsystem.component.StreakSheetStatus
import com.techgv.stash.core.designsystem.component.ThemePreview
import com.techgv.stash.core.designsystem.component.Txt
import com.techgv.stash.core.designsystem.component.coveredByModal
import com.techgv.stash.core.designsystem.component.itemMenuActions
import com.techgv.stash.core.designsystem.component.radioGroupOption
import com.techgv.stash.core.designsystem.component.rememberRadioGroupFocus
import com.techgv.stash.core.designsystem.presentation.NOW
import com.techgv.stash.core.designsystem.presentation.savedItem
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.DayMark
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.domain.WeekDay
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.RichSpan
import com.techgv.stash.core.model.RichText
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// Milestone 4 accessibility (handoff "Accessibility notes"): focus ring, sheets, radio-group keys, roles, RTL icons,
// selectable reader text and large-text button layout. iOS simulator and Android device, in English.

private var inputModes: InputModeManager? = null

@Composable
private fun Themed(
    direction: LayoutDirection = LayoutDirection.Ltr,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    inputModes = LocalInputModeManager.current
    CompositionLocalProvider(LocalLayoutDirection provides direction, LocalDensity provides Density(density.density, fontScale)) {
        StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false, content = content)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.keyboardMode() {
    leaveTouchMode()
    runOnIdle { inputModes?.requestInputMode(InputMode.Keyboard) }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.press(key: Key) = onNode(isFocused()).performKeyInput { pressKey(key) }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.shiftTab() = onNode(isFocused()).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }

private val card = savedItem().toCardUi(NOW, TimeZone.UTC)

private val week = (6 downTo 0).map { back ->
    WeekDay(LocalDate(2026, 11, 12 - back), isToday = back == 0, mark = if (back == 0) DayMark.NotRead else DayMark.Read)
}

private fun Color.near(other: Color) =
    abs(red - other.red) < 0.06f && abs(green - other.green) < 0.06f && abs(blue - other.blue) < 0.06f

@OptIn(ExperimentalTestApi::class)
class AccessibilityUiTest {

    // --- Focus ring -------------------------------------------------------------------------------------------------

    @Test
    fun focusRingIsDrawnAroundTheKeyboardFocusedControl() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            Themed {
                primary = Stash.colors.primary
                Box(Modifier.background(Stash.colors.bg).padding(24.dp)) { PrimaryButton("Save", {}, Modifier.testTag("button")) }
            }
        }
        keyboardMode()
        // Sample the ring's centre line: 3 dp left of the button, halfway down.
        fun ringPixel(): Color {
            val bounds = onNodeWithTag("button").fetchSemanticsNode().boundsInRoot
            val px = with(Density(density.density)) { 3.dp.toPx() }
            return onRoot().captureToImage().toPixelMap()[(bounds.left - px).toInt(), bounds.center.y.toInt()]
        }
        assertTrue(!ringPixel().near(primary), "no ring before focus")
        onNodeWithText("Save").requestFocus()
        onNodeWithText("Save").assertIsFocused()
        assertTrue(ringPixel().near(primary), "ring while focused")
    }

    // --- Cards: focus ring, Options action, Menu key (T4.4) ---------------------------------------------------------

    private fun cardFocusTest(block: ComposeUiTest.(menus: () -> Int, opened: () -> Int, primary: () -> Color) -> Unit) = runComposeUiTest {
        var menus = 0
        var opened = 0
        var primary = Color.Unspecified
        setContent {
            Themed {
                primary = Stash.colors.primary
                Column(Modifier.background(Stash.colors.bg).padding(24.dp)) {
                    PrimaryButton("Before", {})
                    SavedItemCard(
                        card, CardLayout.Comfortable, SwipeOutcome.Remove, SwipeOutcome.MarkRead,
                        onOpen = { opened++ }, onAction = {}, onMore = { menus++ },
                    )
                    PrimaryButton("After", {})
                }
            }
        }
        keyboardMode()
        block({ menus }, { opened }, { primary })
    }

    private fun ComposeUiTest.keyOnRoot(key: Key) = onRoot().performKeyInput { pressKey(key) }

    @Test
    fun cardThenItsOptionsActionAreTabStopsAndBothOpenTheItemSheet() = cardFocusTest { menus, opened, _ ->
        val options = onNodeWithContentDescription("More options for ${card.title}")
        options.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // Out of sight for touch users; a 40 dp pill while it has focus.
        fun optionsShown() = options.fetchSemanticsNode().size.height > with(Density(density.density)) { 30.dp.toPx() }
        assertTrue(!optionsShown())
        onNodeWithText("Before").requestFocus()
        keyOnRoot(Key.Tab)
        // The card itself: Enter opens it, the Menu key opens the item sheet.
        keyOnRoot(Key.Enter)
        assertEquals(1, opened())
        keyOnRoot(Key.Menu)
        assertEquals(1, menus())
        keyOnRoot(Key.Tab)
        options.assertIsFocused()
        assertTrue(optionsShown())
        keyOnRoot(Key.Enter)
        assertEquals(2, menus())
        keyOnRoot(Key.Tab)
        onNodeWithText("After").assertIsFocused()
        assertTrue(!optionsShown())
        // The card keeps one merged announcement with its custom actions, More options included.
        onNodeWithTag("item-${card.id}").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assert(SemanticsMatcher("has More options") { node -> node.config[SemanticsActions.CustomActions].any { it.label == "More options" } })
    }

    @Test
    fun focusedCardShowsTheInsetFocusRing() = cardFocusTest { _, _, primary ->
        // The inset ring's centre line is 3 dp inside the card's start edge.
        fun ringPixel(): Color {
            val bounds = onNodeWithTag("item-${card.id}").fetchSemanticsNode().boundsInRoot
            val px = with(Density(density.density)) { 3.dp.toPx() }
            return onRoot().captureToImage().toPixelMap()[(bounds.left + px).toInt(), bounds.center.y.toInt()]
        }
        assertTrue(!ringPixel().near(primary()), "no ring before focus")
        onNodeWithText("Before").requestFocus()
        keyOnRoot(Key.Tab)
        assertTrue(ringPixel().near(primary()), "ring while the card has keyboard focus")
    }

    @Test
    fun shelfControlsShowTheFocusRingAndTheTipDismissAndWidgetRowsAreButtons() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            Themed {
                primary = Stash.colors.primary
                Column(Modifier.background(Stash.colors.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StreakChip(4, StreakStatus.Unlit, {}, Modifier.testTag("chip"))
                    SearchPill("Search your shelf", {}, Modifier.testTag("pill"))
                    NudgeBanner(ReminderLevel.Daily, 4, "Title", 5, {})
                    GestureTip("Swipe a card", {})
                    ArchiveLinkCard("Archive", "Rule", {}, Modifier.testTag("archive"))
                    WidgetPreview(WidgetSize.Medium, AppIcon.Leather, 4, StreakStatus.Unlit, 1, listOf(card), {})
                }
            }
        }
        keyboardMode()
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        onNodeWithContentDescription("Dismiss tip").assert(button)
        onNodeWithText(card.title, substring = true).assert(button)
        // The ring's centre line is 3 dp outside the control's start edge.
        fun ringPixel(node: SemanticsNodeInteraction): Color {
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val px = with(Density(density.density)) { 3.dp.toPx() }
            return onRoot().captureToImage().toPixelMap()[(bounds.left - px).toInt(), bounds.center.y.toInt()]
        }
        listOf(
            onNodeWithTag("chip"), onNodeWithTag("pill"), onNodeWithText("Read now"), onNodeWithContentDescription("Dismiss tip"),
            onNodeWithTag("archive"),
        ).forEach { node ->
            assertTrue(!ringPixel(node).near(primary), "no ring before focus")
            node.requestFocus()
            assertTrue(ringPixel(node).near(primary), "ring on ${node.fetchSemanticsNode().config}")
        }
    }

    // --- Radio groups ------------------------------------------------------------------------------------------------

    private fun radioTest(direction: LayoutDirection = LayoutDirection.Ltr, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        var size by mutableStateOf("M")
        setContent {
            Themed(direction) {
                Column {
                    PrimaryButton("Before", {})
                    SegmentedControl(listOf("S", "M", "L"), size, { it }, { size = it })
                    PrimaryButton("After", {})
                }
            }
        }
        keyboardMode()
        block()
    }

    @Test
    fun arrowKeysHomeAndEndMoveTheSelection() = radioTest {
        onNodeWithText("M").requestFocus()
        press(Key.DirectionRight)
        onNodeWithText("L").assertIsSelected().assertIsFocused()
        press(Key.DirectionRight)
        onNodeWithText("S").assertIsSelected().assertIsFocused()
        press(Key.MoveEnd)
        onNodeWithText("L").assertIsSelected().assertIsFocused()
        press(Key.MoveHome)
        onNodeWithText("S").assertIsSelected().assertIsFocused()
        press(Key.DirectionUp)
        onNodeWithText("L").assertIsSelected().assertIsFocused()
    }

    @Test
    fun leftAndRightFollowTheReadingDirection() = radioTest(LayoutDirection.Rtl) {
        onNodeWithText("M").requestFocus()
        press(Key.DirectionLeft)
        onNodeWithText("L").assertIsSelected().assertIsFocused()
    }

    @Test
    fun onlyTheSelectedOptionIsATabStop() = radioTest {
        onNodeWithText("Before").requestFocus()
        press(Key.Tab)
        onNodeWithText("M").assertIsFocused()
        press(Key.Tab)
        onNodeWithText("After").assertIsFocused()
        shiftTab()
        onNodeWithText("M").assertIsFocused()
    }

    @Test
    fun optionCardsTakeTheRadioGroupKeys() = runComposeUiTest {
        val themes = ThemeMode.entries
        var theme by mutableStateOf(ThemeMode.Auto)
        setContent {
            Themed {
                val group = rememberRadioGroupFocus()
                RowOrStack(List(themes.size) { 1f }) {
                    themes.forEachIndexed { i, t ->
                        OptionCard(
                            t.name, theme == t, { theme = t },
                            Modifier.radioGroupOption(group, i, themes.indexOf(theme), themes.size) { theme = themes[it] },
                        ) { ThemePreview(t) }
                    }
                }
            }
        }
        keyboardMode()
        onNodeWithText(theme.name).requestFocus()
        press(Key.MoveEnd)
        assertEquals(themes.last(), theme)
        onNodeWithText(themes.last().name).assertIsFocused()
    }

    @Test
    fun textSizeOptionsAreAnnouncedByTheirNames() = runComposeUiTest {
        val names = mapOf("S" to "Small", "M" to "Medium", "L" to "Large")
        setContent { Themed { SegmentedControl(listOf("S", "M", "L"), "M", { it }, {}, description = { names.getValue(it) }) } }
        onNodeWithContentDescription("Medium").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        onNodeWithContentDescription("Small").assertExists()
    }

    // --- Sheets ------------------------------------------------------------------------------------------------------

    @Test
    fun sheetTakesTrapsAndReturnsFocus() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent {
            Themed {
                Box {
                    Column(Modifier.coveredByModal(open)) {
                        PrimaryButton("Open", { open = true })
                        PrimaryButton("Other", {})
                    }
                    BottomSheet(open, { open = false }, paneTitle = "Item options") { PrimaryButton("Inside", {}) }
                }
            }
        }
        keyboardMode()
        onNodeWithText("Open").requestFocus()
        press(Key.Enter)
        assertTrue(open)
        onNodeWithContentDescription("Close").assertIsFocused()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Item options")).assertExists()
        // Underneath is out of the accessibility tree.
        onNodeWithText("Open").assertDoesNotExist()
        press(Key.Tab)
        onNodeWithText("Inside").assertIsFocused()
        press(Key.Tab)
        onNodeWithText("Inside").assertIsFocused()
        shiftTab()
        shiftTab()
        onNodeWithContentDescription("Close").assertIsFocused()
        press(Key.Escape)
        assertTrue(!open)
        onNodeWithText("Open").assertIsFocused()
    }

    private fun dragTest(block: ComposeUiTest.(dismissed: () -> Int) -> Unit) = runComposeUiTest {
        var dismissed = 0
        setContent {
            Themed {
                Box(Modifier.size(390.dp, 844.dp)) {
                    BottomSheet(true, { dismissed++ }) { Txt("Body text of the sheet", Stash.type.body, modifier = Modifier.testTag("body")) }
                }
            }
        }
        block { dismissed }
    }

    @Test
    fun sheetFollowsTheFingerAndSpringsBackFromAShortDrag() = dragTest { dismissed ->
        val handle = onNodeWithContentDescription("Close")
        val top = handle.fetchSemanticsNode().boundsInRoot.top
        handle.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, 40.dp.toPx())) }
        handle.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, 0.1f)) }
        val moved = handle.fetchSemanticsNode().boundsInRoot.top - top
        assertTrue(moved > with(Density(density.density)) { 20.dp.toPx() }, "sheet follows the finger: $moved")
        // Wait so the release has no fling velocity.
        mainClock.advanceTimeBy(1_000)
        handle.performTouchInput { up() }
        waitForIdle()
        assertEquals(0, dismissed())
        assertEquals(top, handle.fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun draggingTheHandleDownCloses() = dragTest { dismissed ->
        onNodeWithContentDescription("Close").performTouchInput { swipeDown(centerY, centerY + 160.dp.toPx(), durationMillis = 400) }
        waitForIdle()
        assertEquals(1, dismissed())
    }

    @Test
    fun draggingTheContentDownCloses() = dragTest { dismissed ->
        onNodeWithTag("body").performTouchInput { swipeDown(centerY, centerY + 160.dp.toPx(), durationMillis = 400) }
        waitForIdle()
        assertEquals(1, dismissed())
    }

    @Test
    fun sheetHandleIsA48dpTarget() = dragTest {
        val node = onNodeWithContentDescription("Close").fetchSemanticsNode()
        val height = with(node.layoutInfo.density) { node.size.height.toDp() }
        assertTrue(height >= 48.dp, "handle height $height")
        node.config[SemanticsProperties.Role].let { assertEquals(Role.Button, it) }
    }

    @Test
    fun celebrationTakesFocusAndClosesOnEscape() = runComposeUiTest {
        var closed = 0
        var visible by mutableStateOf(false)
        setContent { Themed { CelebrationDialog(visible, 12, 13, "Today’s read is in the books.", week, { closed++ }) } }
        keyboardMode()
        visible = true
        onNodeWithText("Keep reading").assertIsFocused()
        press(Key.Tab)
        onNodeWithText("Keep reading").assertIsFocused()
        press(Key.Escape)
        assertEquals(1, closed)
    }

    @Test
    fun celebrationScrollsSoKeepReadingIsReachableInAShortWindow() = runComposeUiTest {
        var closed = 0
        // A phone in landscape: 423 dp of height.
        setContent { Themed { Box(Modifier.fillMaxWidth().height(423.dp)) { CelebrationDialog(true, 12, 13, "Today’s read is in the books.", week, { closed++ }) } } }
        val button = onNodeWithText("Keep reading").performScrollTo()
        button.assertIsDisplayed()
        assertTrue(button.fetchSemanticsNode().size.height > with(Density(density.density)) { 48.dp.toPx() }, "full height")
        button.performClick()
        assertEquals(1, closed)
    }

    // --- Add-link error and roles ------------------------------------------------------------------------------------

    @Test
    fun addLinkErrorIsAnnouncedOnEverySave() = runComposeUiTest {
        val message = "Paste or type a link first."
        var saves = 0
        setContent { Themed { AddLinkSheet(true, "", {}, null, message, {}, { saves++ }, {}) } }
        val error = onNodeWithText(message, useUnmergedTree = true)
        val live = SemanticsMatcher("in a live region") { node ->
            generateSequence(node) { it.parent }.any { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Assertive }
        }
        error.assert(live)
        val before = error.fetchSemanticsNode().id
        onNodeWithText("Save to shelf").performClick()
        assertEquals(1, saves)
        // The same message is shown again as a new node, so it is read again.
        assertNotEquals(before, onNodeWithText(message, useUnmergedTree = true).fetchSemanticsNode().id)
        onNodeWithText("Cancel").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun actionsAreAnnouncedAsButtons() = runComposeUiTest {
        setContent {
            Themed {
                Column {
                    ReaderTopBar("Title", "medium.com", false, { 0f }, {}, {}, {})
                    Box(Modifier.size(390.dp, 600.dp)) { ItemActionSheet(card, itemMenuActions(card), {}, {}) }
                }
            }
        }
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        onNodeWithText("Mark unread").assert(button)
        onNodeWithText("Share").assert(button)
    }

    @Test
    fun streakRemindersIsAButton() = runComposeUiTest {
        setContent {
            Themed {
                StreakSheet(true, 12, StreakStatus.Unlit, StreakSheetStatus.NotYet, week, ReminderTime.Morning, false, {}, {}, {})
            }
        }
        onNodeWithText("Reminders").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    // --- Right-to-left icons -----------------------------------------------------------------------------------------

    /** The icon's inked pixels, drawn 48 dp in [direction]. */
    private fun ink(icon: ImageVector, direction: LayoutDirection): List<List<Boolean>> {
        var ink = emptyList<List<Boolean>>()
        runComposeUiTest {
            setContent { Themed(direction) { Icon(icon, null, Modifier.size(48.dp).testTag("icon"), tint = Color.Black) } }
            val pixels = onNodeWithTag("icon").captureToImage().toPixelMap()
            ink = List(pixels.height) { y -> List(pixels.width) { x -> pixels[x, y].alpha > 0.5f } }
        }
        return ink
    }

    /** Whether the right-to-left drawing is the left-to-right one flipped: it matches the flip better than the original. */
    private fun mirrors(icon: ImageVector): Boolean {
        val ltr = ink(icon, LayoutDirection.Ltr)
        val rtl = ink(icon, LayoutDirection.Rtl)
        val asIs = ltr.indices.sumOf { y -> ltr[y].indices.count { x -> rtl[y][x] != ltr[y][x] } }
        val flipped = ltr.indices.sumOf { y -> ltr[y].indices.count { x -> rtl[y][x] != ltr[y][ltr[y].size - 1 - x] } }
        return flipped < asIs
    }

    @Test
    fun directionalIconsMirrorInRtl() {
        assertTrue(mirrors(StashIcons.back()), "back")
        assertTrue(mirrors(StashIcons.chevronRight()), "chevron")
        assertTrue(mirrors(StashIcons.external()), "open outward")
        assertTrue(mirrors(StashIcons.hand()), "swipe hand")
        assertTrue(!mirrors(StashIcons.share()), "share does not mirror")
        assertTrue(!mirrors(StashIcons.restore()), "restore does not mirror")
    }

    // --- Reader text selection ---------------------------------------------------------------------------------------

    private class RecordingToolbar : TextToolbar {
        var copy: (() -> Unit)? = null
        override var status = TextToolbarStatus.Hidden
        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) {
            copy = onCopyRequested
            status = TextToolbarStatus.Shown
        }

        override fun hide() {
            status = TextToolbarStatus.Hidden
        }
    }

    private val longArticle = ReaderArticle(
        "Designing for slow reading", "medium.com", null, 3,
        listOf(ReaderBlock.Paragraph(RichText("Read more about it", listOf(RichSpan.Link(0, 9, "https://example.com/more"))))) +
            List(30) { ReaderBlock.Paragraph(RichText("Paragraph $it of a long article about reading slowly in a fast feed.")) },
    )

    @Test
    fun readerTextCanBeSelectedAndCopiedAndStillScrollsAndOpensLinks() = runComposeUiTest {
        val toolbar = RecordingToolbar()
        val clicked = mutableListOf<String>()
        setContent {
            Themed {
                CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                    Box(Modifier.size(390.dp, 700.dp)) { ReaderModeView(longArticle, 1, {}, onLinkClick = { clicked += it }) }
                }
            }
        }
        onNodeWithText("Read more about it").performClick()
        assertEquals(listOf("https://example.com/more"), clicked)
        onNodeWithText("Paragraph 2 of", substring = true).performTouchInput { longClick(center) }
        waitForIdle()
        assertTrue(toolbar.copy != null, "the selection offers Copy")
        val top = onNodeWithText("Designing for slow reading").fetchSemanticsNode().boundsInRoot.top
        onNodeWithText("Paragraph 5 of", substring = true).performTouchInput { swipeUp(centerY, centerY - 300.dp.toPx(), durationMillis = 300) }
        waitForIdle()
        assertTrue(onNodeWithText("Designing for slow reading").fetchSemanticsNode().boundsInRoot.top < top, "the text still scrolls")
    }

    // --- Large text: equal heights, no breaks inside a word ----------------------------------------------------------

    /** No line of the [labels] ends between two letters (a word broken without a hyphen, "Canc-el"). */
    private fun ComposeUiTest.assertNoMidWordBreaks(labels: List<String>) {
        val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        val broken = nodes.mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.firstOrNull() ?: return@mapNotNull null
            val text = layout.layoutInput.text.text.takeIf { it in labels } ?: return@mapNotNull null
            (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
                .firstOrNull { it in 1 until text.length && text[it - 1].isLetter() && text[it].isLetter() }
                ?.let { "${text.substring(0, it)}|${text.substring(it)}" }
        }
        assertEquals(labels.size, nodes.count { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text in labels } == true }, "labels found")
        assertEquals(emptyList(), broken, "words broken inside")
    }

    private fun largeText(labels: List<String>, width: Int = 390, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { Themed(fontScale = 3f) { Box(Modifier.size(width.dp, 844.dp)) { content() } } }
        waitForIdle()
        assertNoMidWordBreaks(labels)
    }

    @Test
    fun streakSheetButtonsDoNotBreakWordsAtTheLargestText() = largeText(listOf("Reminders", "Read something short")) {
        StreakSheet(true, 12, StreakStatus.Unlit, StreakSheetStatus.NotYet, week, ReminderTime.Morning, false, {}, {}, {})
    }

    @Test
    fun addLinkButtonsDoNotBreakWordsAtTheLargestText() = largeText(listOf("Cancel", "Save to shelf")) { AddLinkSheet(true, "", {}, null, null, {}, {}, {}) }

    @Test
    fun inlineConfirmButtonsDoNotBreakWordsAtTheLargestText() = largeText(listOf("Cancel", "Clear"), width = 340) {
        InlineConfirm("Clear the archive?", null, "Cancel", {}, "Clear", {}, Stash.colors.danger, Stash.colors.surface)
    }

    @Test
    fun optionCardsStackRatherThanBreakAWord() = largeText(listOf("Auto", "Parchment", "Night")) {
        RowOrStack(List(3) { 1f }) {
            listOf(ThemeMode.Auto, ThemeMode.Parchment, ThemeMode.Night).forEach { OptionCard(it.name, false, {}) { ThemePreview(it) } }
        }
    }

    @Test
    fun sideBySideButtonsKeepEqualHeights() = runComposeUiTest {
        setContent {
            Themed {
                RowOrStack(listOf(1f, 1f), Modifier.width(300.dp)) {
                    PrimaryButton("Go", {}, Modifier.testTag("short"))
                    PrimaryButton("Read something short today", {}, Modifier.testTag("long"))
                }
            }
        }
        val short = onNodeWithTag("short").fetchSemanticsNode().boundsInRoot
        val long = onNodeWithTag("long").fetchSemanticsNode().boundsInRoot
        assertTrue(long.height > with(Density(density.density)) { 60.dp.toPx() }, "the long label wraps")
        assertEquals(long.height, short.height)
        assertEquals(long.top, short.top)
    }
}
