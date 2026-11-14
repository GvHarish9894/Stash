package com.techgv.stash.feature.widgetpreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.model.WidgetSize
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Runs on the iOS simulator and as an Android device test, in English.

@Composable
private fun Themed(content: @Composable () -> Unit) =
    StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false, content = content)

private fun rows(n: Int) = (1..n).map { savedItem("u$it", title = "Unread $it").toCardUi(FIXTURE_NOW, TimeZone.UTC) }

@OptIn(ExperimentalTestApi::class)
class WidgetPreviewScreenUiTest {

    private fun ComposeUiTest.show(
        state: WidgetPreviewState,
        canPin: Boolean = true,
        widthClass: WidthClass = WidthClass.Compact,
        fontScale: Float = 1f,
    ): MutableList<WidgetPreviewIntent> {
        val intents = mutableListOf<WidgetPreviewIntent>()
        setContent {
            Themed {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    WidgetPreviewScreen(state, { intents += it }, widthClass, canPin)
                }
            }
        }
        return intents
    }

    private fun ComposeUiTest.reveal(text: String) = onNodeWithText(text).performScrollTo()

    private fun state(size: WidgetSize, n: Int) = WidgetPreviewState(loaded = true, size = size, streak = 5, unreadCount = n, items = rows(n))

    @Test
    fun openingMovesFocusToTheBackButton() = runComposeUiTest {
        show(state(WidgetSize.Medium, 1))
        onNodeWithContentDescription("Back to settings").assertIsFocused()
    }

    @Test
    fun mediumWithNoItemsIsAllCaughtUp() = runComposeUiTest {
        show(state(WidgetSize.Medium, 0))
        onNodeWithText("All caught up. Nothing waiting on your shelf.").assertIsDisplayed()
    }

    @Test
    fun smallWithNoItemsIsAllCaughtUp() = runComposeUiTest {
        show(state(WidgetSize.Small, 0))
        onNodeWithText("All caught up").assertIsDisplayed()
        onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun mediumShowsEveryRow() = runComposeUiTest {
        show(state(WidgetSize.Medium, 1))
        onNodeWithText("Unread 1").assertIsDisplayed()
        assertTrue(onAllNodesWithText("All caught up", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun mediumWithThreeItemsShowsThreeRowsAndTheCount() = runComposeUiTest {
        show(state(WidgetSize.Medium, 3).copy(unreadCount = 7))
        listOf("Unread 1", "Unread 2", "Unread 3").forEach { onNodeWithText(it).assertIsDisplayed() }
        onNodeWithText("· 7 unread").assertIsDisplayed()
    }

    @Test
    fun smallShowsOnlyTheNextTitle() = runComposeUiTest {
        show(state(WidgetSize.Small, 3))
        onNodeWithText("Unread 1").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Unread 2").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun smallWithOneItemShowsIt() = runComposeUiTest {
        show(state(WidgetSize.Small, 1))
        onNodeWithText("Unread 1").assertIsDisplayed()
        onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun sizeControlIsARadioGroup() = runComposeUiTest {
        val intents = show(state(WidgetSize.Medium, 0))
        reveal("Medium 4×2").assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        reveal("Small 2×2").assertIsNotSelected().performClick()
        assertEquals(listOf<WidgetPreviewIntent>(WidgetPreviewIntent.SetSize(WidgetSize.Small)), intents)
    }

    @Test
    fun addToHomeScreenPassesWhetherPinningIsPossible() {
        for (canPin in listOf(true, false)) {
            runComposeUiTest {
                val intents = show(state(WidgetSize.Medium, 0), canPin = canPin)
                reveal("Add to home screen").performClick()
                assertEquals(listOf<WidgetPreviewIntent>(WidgetPreviewIntent.AddToHomeScreen(canPin)), intents)
            }
        }
    }

    @Test
    fun stepsShowWhenPinningIsNotPossible() = runComposeUiTest {
        show(state(WidgetSize.Medium, 0).copy(showSteps = true), canPin = false)
        reveal("Add it from your home screen").assertIsDisplayed().assert(isHeading())
        reveal("1. Touch and hold an empty spot on your home screen.\n2. Open the list of widgets.\n3. Find Stash, pick a size and add it.")
            .assertIsDisplayed()
    }

    @Test
    fun noStepsBeforeTheyAreNeeded() = runComposeUiTest {
        show(state(WidgetSize.Medium, 0))
        assertTrue(onAllNodesWithText("Add it from your home screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun backReturnsToSettings() = runComposeUiTest {
        val intents = show(state(WidgetSize.Medium, 0))
        onNodeWithText("Home-screen widget").assert(isHeading())
        onNodeWithContentDescription("Back to settings").performClick()
        assertEquals(listOf<WidgetPreviewIntent>(WidgetPreviewIntent.Back), intents)
    }

    @Test
    fun expandedPutsTheControlsBesideThePreview() = runComposeUiTest {
        show(state(WidgetSize.Medium, 1), widthClass = WidthClass.Expanded)
        onNodeWithText("Unread 1").assertIsDisplayed()
        onNodeWithText("Add to home screen").assertIsDisplayed()
    }

    @Test
    fun addButtonStaysReachableAt200PercentText() = runComposeUiTest {
        val intents = show(state(WidgetSize.Medium, 3), canPin = true, fontScale = 2f)
        reveal("Add to home screen").assertIsDisplayed().performClick()
        assertEquals(listOf<WidgetPreviewIntent>(WidgetPreviewIntent.AddToHomeScreen(true)), intents)
    }
}
