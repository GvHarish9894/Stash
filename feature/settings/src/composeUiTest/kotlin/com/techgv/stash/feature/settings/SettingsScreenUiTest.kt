package com.techgv.stash.feature.settings

import com.techgv.stash.core.model.CardLayout
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.AppLanguage
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.time_9_00_am
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// Runs on the iOS simulator and as an Android device test, in English.

@Composable
private fun Themed(content: @Composable () -> Unit) =
    StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false, content = content)

private val loaded = SettingsState(loaded = true)

@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest {

    private fun ComposeUiTest.show(
        state: SettingsState = loaded,
        widthClass: WidthClass = WidthClass.Compact,
        canChangeIcon: Boolean = true,
        languageInApp: Boolean = true,
        languageTag: String? = null,
        initialSection: SettingsSection? = null,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
    ): MutableList<SettingsIntent> {
        val intents = mutableListOf<SettingsIntent>()
        setContent {
            Themed {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    SettingsScreen(
                        state, { intents += it }, widthClass, canChangeIcon, languageInApp, languageTag, "1.0 (7)", { 12.seconds },
                        initialSection = initialSection,
                    )
                }
            }
        }
        return intents
    }

    private fun ComposeUiTest.scrollTo(section: SettingsSection) =
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag(settingsSectionTag(section)))

    /** Scrolls the single-column list until [matcher] is composed and shown, and returns it. */
    private fun ComposeUiTest.reveal(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
        return onNode(matcher)
    }

    private fun ComposeUiTest.reveal(text: String) = reveal(hasText(text))

    private fun inSection(section: SettingsSection) = hasAnyAncestor(hasTestTag(settingsSectionTag(section)))

    private fun ComposeUiTest.node(text: String, section: SettingsSection): SemanticsNodeInteraction =
        reveal(hasText(text) and inSection(section))

    // ---- Milestone 4 accessibility wiring (T4.4) ---------------------------------------------------------------

    private fun ComposeUiTest.arrowRight(node: SemanticsNodeInteraction) {
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionRight) }
    }

    @Test
    fun optionCardsAccentsAndSwipeChipsMoveTheSelectionWithArrowKeys() = runComposeUiTest {
        val intents = show()
        arrowRight(node("Auto", SettingsSection.Appearance))
        arrowRight(reveal(hasContentDescription("Leather")))
        arrowRight(node("Comfortable", SettingsSection.Appearance))
        arrowRight(reveal(isSelected() and hasText("Mark read", substring = true)))
        assertEquals(
            listOf(
                SettingsIntent.SetTheme(ThemeMode.Parchment), SettingsIntent.SetAccent(Accent.Oxblood),
                SettingsIntent.SetCardLayout(CardLayout.Compact), SettingsIntent.SetSwipeEnd(SwipeAction.Archive),
            ),
            intents.filter {
                it is SettingsIntent.SetTheme || it is SettingsIntent.SetAccent || it is SettingsIntent.SetCardLayout || it is SettingsIntent.SetSwipeEnd
            },
        )
    }

    @Test
    fun openingMovesFocusToTheBackButton() = runComposeUiTest {
        show()
        onNodeWithContentDescription("Back to shelf").assertIsFocused()
    }

    @Test
    fun textSizeOptionsAreSpokenByName() = runComposeUiTest {
        show()
        reveal(hasContentDescription("Medium")).assertIsSelected()
        onNodeWithContentDescription("Small").assertExists()
        onNodeWithContentDescription("Large").assertExists()
    }

    @Test
    fun themeCardsStackAtTheLargestTextSizeAndSitSideBySideOtherwise() {
        fun tops(fontScale: Float): List<Float> {
            var tops = emptyList<Float>()
            runComposeUiTest {
                show(fontScale = fontScale)
                tops = listOf("Auto", "Parchment", "Night library").map { node(it, SettingsSection.Appearance).fetchSemanticsNode().boundsInRoot.top }
            }
            return tops
        }
        assertEquals(1, tops(1f).distinct().size, "side by side at 100 %")
        assertEquals(3, tops(3f).distinct().size, "stacked at the largest size, 300 %")
    }

    // ---- decision D1: the backup group is unchanged ----------------------------------------------------------

    @Test
    fun accountAndBackupGroupIsExactlyTheOldOneAndDoesNothing() = runComposeUiTest {
        val intents = show()
        scrollTo(SettingsSection.Account)
        val nodes = onAllNodes(inSection(SettingsSection.Account) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
        // The old screen's group, in order: the title, then each row's title, description and badge.
        assertEquals(
            listOf(
                "ACCOUNT AND BACKUP",
                "Sign in with Google", "Keep your shelf when you change phones", "Phase 2",
                "Back up to Google Drive", "Private app folder in your own Drive", "Phase 2",
                "Export and import", "Your shelf as a JSON file", "Soon",
            ),
            nodes.map { it.config[SemanticsProperties.Text].joinToString() },
        )
        onNodeWithText("ACCOUNT AND BACKUP").assert(isHeading())
        // Like the old rows: no action, no switch, not marked disabled (they were plain rows with a badge).
        val group = onAllNodes(inSection(SettingsSection.Account)).fetchSemanticsNodes()
        assertTrue(group.isNotEmpty())
        for (n in group) {
            assertEquals(null, n.config.getOrNull(SemanticsActions.OnClick), "no click action")
            assertEquals(null, n.config.getOrNull(SemanticsProperties.ToggleableState), "no switch")
            assertEquals(null, n.config.getOrNull(SemanticsProperties.Disabled), "same enabled look as before")
        }
        onNodeWithText("Sign in with Google").performClick()
        assertTrue(intents.isEmpty())
    }

    // ---- appearance and reading ------------------------------------------------------------------------------

    @Test
    fun headerTitleIsAHeadingAndBackLeaves() = runComposeUiTest {
        val intents = show()
        onNodeWithText("Settings").assert(isHeading())
        onNodeWithText("APPEARANCE").assert(isHeading())
        onNodeWithContentDescription("Back to shelf").performClick()
        assertEquals(listOf<SettingsIntent>(SettingsIntent.NavigateBack), intents)
    }

    @Test
    fun themeCardsAreRadioButtons() = runComposeUiTest {
        val intents = show(loaded.copy(prefs = UserPreferences(theme = ThemeMode.Parchment)))
        val parchment = node("Parchment", SettingsSection.Appearance)
        parchment.assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        node("Night library", SettingsSection.Appearance).assertIsNotSelected().performClick()
        onNodeWithContentDescription("Forest").performClick()
        assertEquals(listOf(SettingsIntent.SetTheme(ThemeMode.Night), SettingsIntent.SetAccent(Accent.Forest)), intents)
    }

    @Test
    fun aSwitchRowIsOneSwitchNamedByItsTitle() = runComposeUiTest {
        val intents = show()
        val row = onNode(hasText("Reduce motion") and isToggleable())
        row.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        assertEquals(listOf<SettingsIntent>(SettingsIntent.SetReduceMotion(true)), intents)
    }

    // ---- streak and reminders --------------------------------------------------------------------------------

    @Test
    fun turningTheDailyReminderOnSendsTheIntent() = runComposeUiTest {
        val intents = show(loaded.copy(prefs = UserPreferences(dailyReminder = false)), initialSection = SettingsSection.Streak)
        reveal(hasText("Daily reminder") and isToggleable()).assertIsOff().performClick()
        reveal(hasText("Streak saver") and isToggleable()).assertIsOn()
        assertEquals(listOf<SettingsIntent>(SettingsIntent.SetDailyReminder(true)), intents)
    }

    @Test
    fun aDeniedPermissionOffersTheNotificationSettings() = runComposeUiTest {
        val intents = show(loaded.copy(notificationsDenied = true), initialSection = SettingsSection.Streak)
        // Both are on in the preferences, but no reminder can arrive.
        reveal(hasText("Daily reminder") and isToggleable()).assertIsOff()
        reveal(hasText("Streak saver") and isToggleable()).assertIsOff()
        reveal("Open notification settings").assertIsDisplayed().performClick()
        assertEquals(listOf<SettingsIntent>(SettingsIntent.OpenNotificationSettings), intents)
    }

    @Test
    fun noSettingsOfferBeforeADenial() = runComposeUiTest {
        show(initialSection = SettingsSection.Streak)
        reveal("Preview a reminder").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Open notification settings").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theDemoResetIsGone() = runComposeUiTest {
        show(initialSection = SettingsSection.Streak)
        reveal("Preview a reminder").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Reset today (demo)").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun previewSlotsSendTheTimeoutAndTheBannerActs() = runComposeUiTest {
        val preview = ReminderPreview(ReminderLevel.Daily, text(Res.string.time_9_00_am), "Your shelf is waiting", "2 unread.", ItemId("a"))
        val intents = show(loaded.copy(preview = preview, previewSlot = ReminderLevel.Daily), initialSection = SettingsSection.Streak)
        node("9:30 PM", SettingsSection.Streak).performClick()
        onNodeWithText("Your shelf is waiting").assertIsDisplayed()
        onNodeWithText("Stash · 9:00 AM").assertIsDisplayed()
        onNodeWithText("Read now").performClick()
        onNodeWithText("Later").performClick()
        assertEquals(
            listOf(SettingsIntent.PreviewReminder(ReminderLevel.Late, 12.seconds), SettingsIntent.ReadPreviewItem, SettingsIntent.DismissPreview),
            intents,
        )
    }

    // ---- swipe gestures --------------------------------------------------------------------------------------

    @Test
    fun swipeLabelsFollowTheLayoutDirection() = runComposeUiTest {
        val intents = show(
            loaded.copy(prefs = UserPreferences(swipeEndAction = SwipeAction.MarkRead, swipeStartAction = SwipeAction.Remove)),
            initialSection = SettingsSection.Swipe, layoutDirection = LayoutDirection.Rtl,
        )
        // In RTL the end edge is on the left, so the first block (the end action) reads "Swipe left".
        reveal("In the archive, right always restores and left deletes. Every action is also in the long-press menu.")
        val labels = onAllNodes(inSection(SettingsSection.Swipe) and (hasText("Swipe left") or hasText("Swipe right"))).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals(listOf("Swipe left", "Swipe right"), labels)
        val archiveChips = onAllNodes(hasText("Archive") and inSection(SettingsSection.Swipe))
        archiveChips[0].performClick()
        archiveChips[1].performClick()
        assertEquals(listOf(SettingsIntent.SetSwipeEnd(SwipeAction.Archive), SettingsIntent.SetSwipeStart(SwipeAction.Archive)), intents)
    }

    // ---- shelf -----------------------------------------------------------------------------------------------

    @Test
    fun autoArchiveConfirmShowsTheCountAndBothAnswers() = runComposeUiTest {
        val intents = show(loaded.copy(autoArchiveConfirm = AutoArchiveConfirm(AutoArchivePeriod.Days30, 3)), initialSection = SettingsSection.Shelf)
        reveal("Move 3 read items to Archive now?").assertIsDisplayed()
        reveal("Not yet").performClick()
        reveal("Move now").performClick()
        node("7 days", SettingsSection.Shelf).performClick()
        assertEquals(
            listOf(SettingsIntent.MoveDueLater, SettingsIntent.MoveDueNow, SettingsIntent.SetAutoArchive(AutoArchivePeriod.Days7)),
            intents,
        )
    }

    @Test
    fun noConfirmWhenNothingIsDue() = runComposeUiTest {
        show(initialSection = SettingsSection.Shelf)
        reveal("Auto-archive read items").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Move now").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun shelfRowsNavigate() = runComposeUiTest {
        val intents = show(loaded.copy(archivedCount = 4), initialSection = SettingsSection.Shelf)
        reveal("4 items").assertIsDisplayed()
        reveal("Open archive").performClick()
        reveal("Show gesture tip again").performClick()
        assertEquals(listOf(SettingsIntent.OpenArchive, SettingsIntent.ShowTipAgain), intents)
    }

    // ---- home screen and language ----------------------------------------------------------------------------

    @Test
    fun appIconCardsShowOnlyWhenTheIconCanChange() = runComposeUiTest {
        val intents = show(loaded.copy(prefs = UserPreferences(appIcon = AppIcon.Leather)), initialSection = SettingsSection.Home)
        node("Leather", SettingsSection.Home).assertIsSelected()
        node("Ink", SettingsSection.Home).performClick()
        reveal("Home-screen widget").performClick()
        assertEquals(listOf(SettingsIntent.ChooseAppIcon(AppIcon.Ink), SettingsIntent.OpenWidgetPreview), intents)
    }

    @Test
    fun noAppIconCardsWhenTheIconCannotChange() = runComposeUiTest {
        show(canChangeIcon = false, initialSection = SettingsSection.Home)
        onNodeWithText("Home-screen widget").assertIsDisplayed()
        assertTrue(onAllNodesWithText("App icon").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun inAppLanguagePickerSelectsTheCurrentLanguage() = runComposeUiTest {
        val intents = show(languageTag = "hi", initialSection = SettingsSection.Home)
        node("हिन्दी", SettingsSection.Home).assertIsSelected()
        node("Phone", SettingsSection.Home).assertIsNotSelected()
        node("Español", SettingsSection.Home).performClick()
        assertEquals(listOf<SettingsIntent>(SettingsIntent.ChooseLanguage(AppLanguage.Spanish)), intents)
    }

    @Test
    fun systemOwnedLanguageIsOneRowThatOpensSettings() = runComposeUiTest {
        val intents = show(languageInApp = false, languageTag = "en", initialSection = SettingsSection.Home)
        assertTrue(onAllNodesWithText("Español").fetchSemanticsNodes().isEmpty())
        reveal("English. Change it in your phone’s settings for Stash.").assertIsDisplayed()
        reveal("App language").performClick()
        assertEquals(listOf<SettingsIntent>(SettingsIntent.OpenLanguageSettings), intents)
    }

    // ---- your data and about ---------------------------------------------------------------------------------

    @Test
    fun clearReadShowsTheCountThenAsks() = runComposeUiTest {
        val intents = show(loaded.copy(readCount = 0), initialSection = SettingsSection.Data)
        reveal("No read items yet").assertIsDisplayed()
        reveal("Clear read items").performClick()
        assertEquals(listOf<SettingsIntent>(SettingsIntent.ClearRead), intents)
    }

    @Test
    fun clearReadConfirm() = runComposeUiTest {
        val intents = show(loaded.copy(readCount = 2, clearReadConfirm = 2), initialSection = SettingsSection.Data)
        reveal("Clear 2 read items?").assertIsDisplayed()
        reveal("Cancel").performClick()
        reveal("Clear").performClick()
        assertEquals(listOf(SettingsIntent.CancelClearRead, SettingsIntent.ConfirmClearRead), intents)
    }

    @Test
    fun aboutShowsTheVersionAndOpensTheSource() = runComposeUiTest {
        val intents = show(initialSection = SettingsSection.About)
        reveal("1.0 (7)").assertIsDisplayed()
        reveal("Source code on GitHub").performClick()
        assertEquals(listOf<SettingsIntent>(SettingsIntent.OpenSourceCode), intents)
    }

    // ---- width classes ---------------------------------------------------------------------------------------

    @Test
    fun expandedWidthShowsTheSectionsBesideTheSelectedGroup() = runComposeUiTest {
        show(widthClass = WidthClass.Expanded, initialSection = SettingsSection.Streak)
        onNode(hasText("Streak and reminders") and hasClickAction()).assertIsSelected()
        onNodeWithText("Daily reminder").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Mark read when opened").fetchSemanticsNodes().isEmpty())
        onNode(hasText("Reading") and hasClickAction()).performClick()
        onNode(hasText("Reading") and hasClickAction()).assertIsSelected()
        onNodeWithText("Mark read when opened").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Daily reminder").fetchSemanticsNodes().isEmpty())
    }

    // ---- 200 % font scale ------------------------------------------------------------------------------------

    @Test
    fun primaryActionsStayReachableAt200PercentText() = runComposeUiTest {
        val intents = show(
            loaded.copy(readCount = 2, clearReadConfirm = 2, autoArchiveConfirm = AutoArchiveConfirm(AutoArchivePeriod.Days30, 3)),
            initialSection = SettingsSection.Shelf, fontScale = 2f,
        )
        reveal("Move now").assertIsDisplayed().performClick()
        reveal("Clear").assertIsDisplayed().performClick()
        assertEquals(listOf(SettingsIntent.MoveDueNow, SettingsIntent.ConfirmClearRead), intents)
    }
}
