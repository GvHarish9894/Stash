package com.techgv.stash.feature.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.paging.compose.collectAsLazyPagingItems
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.FakeCommandBus
import com.techgv.stash.core.testing.FakeItemRepository
import com.techgv.stash.core.testing.FakeRecentSearchRepository
import com.techgv.stash.core.testing.FakeSettingsRepository
import com.techgv.stash.core.testing.FixedZoneProvider
import com.techgv.stash.core.testing.TestClock
import com.techgv.stash.core.testing.savedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** The real [SearchViewModel] over the repository fakes, driven through the screen. */
@OptIn(ExperimentalTestApi::class)
class SearchFlowUiTest {
    private val items = listOf(
        savedItem("a", title = "Compose layouts", siteName = "medium.com", addedAt = FIXTURE_NOW - 1.hours),
        savedItem("b", title = "Room with compose", siteName = "github.com", addedAt = FIXTURE_NOW - 2.hours),
        savedItem("c", ItemLifecycle.Archived, title = "Compose testing", siteName = "medium.com", addedAt = FIXTURE_NOW - 300.hours),
    )

    private fun flowTest(block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        val clock = TestClock()
        val repo = FakeItemRepository(items, clock)
        val settings = FakeSettingsRepository()
        val vm = SearchViewModel(repo, FakeCommandBus(repo, settings), settings, FakeRecentSearchRepository(clock), clock, FixedZoneProvider())
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null)) {
                StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false) {
                    val state by vm.state.collectAsState()
                    SearchScreen(
                        state, vm.query, vm.shelfResults.collectAsLazyPagingItems(), vm.archiveResults.collectAsLazyPagingItems(),
                        WidthClass.Compact, { 10.seconds }, vm::onIntent,
                    )
                }
            }
        }
        block()
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun anInfixFindsNothingAndAWordPrefixFindsAndHighlights() = flowTest {
        val field = onNodeWithContentDescription("Search saved items")
        field.performTextInput("pose")
        waitForText("No matches on your shelf")

        field.performTextClearance()
        field.performTextInput("comp")
        waitForText("3 results for “comp” · 1 archived")
        // Shelf hits, newest first, then the archive section.
        val top = { tag: String -> onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top }
        val header = onNodeWithText("IN YOUR ARCHIVE").fetchSemanticsNode().boundsInRoot.top
        assertTrue(top("item-a") < top("item-b") && top("item-b") < header && header < top("item-c"))

        // "Comp" of "Compose layouts" is highlighted.
        val title = onNodeWithText("Compose layouts", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single()
        assertEquals(listOf(0 to 4), title.spanStyles.filter { it.item.background.alpha > 0f }.map { it.start to it.end })
    }

    @Test
    fun chipsFilterAndTheSummaryFollows() = flowTest {
        onNodeWithContentDescription("Search saved items").performTextInput("compose")
        waitForText("Archived · 1")
        onNodeWithText("Archived · 1").performClick()
        waitForText("1 result for “compose” in your archive")
        onNodeWithText("github.com · 1").performClick()
        waitForText("1 result for “compose” in github.com")
    }
}
