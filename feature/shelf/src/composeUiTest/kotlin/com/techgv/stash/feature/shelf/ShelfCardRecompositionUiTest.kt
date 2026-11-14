package com.techgv.stash.feature.shelf

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.testing.asPagingSourceFactory
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.ChipCounts
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Backlog U-21: a state change of Home that does not touch a card neither maps its item again nor recomposes it.
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ShelfCardRecompositionUiTest {
    private val fresh = savedItem("fresh", addedAt = FIXTURE_NOW - 5.minutes - 30.seconds)
    private val older = savedItem("older", lifecycle = ItemLifecycle.Read, addedAt = FIXTURE_NOW - 3.hours)
    private val oldest = savedItem("oldest", addedAt = FIXTURE_NOW - 30.hours)

    private fun rows(vararg items: SavedItem) =
        listOf(ShelfListRow.Header(GroupLabel.Today)) + items.map { ShelfListRow.Card(it, SwipeOutcome.Remove, SwipeOutcome.MarkRead) }

    @Test
    fun unrelatedStateChangesNeitherMapNorRecomposeCards() = runComposeUiTest {
        val state = mutableStateOf(
            ShelfState(FIXTURE_NOW, TimeZone.UTC, chips = ChipCounts(all = 3, unread = 2, sources = emptyList()), firstLoad = false),
        )
        val list = MutableStateFlow(rows(fresh, older, oldest))
        val trace = mutableListOf<String>()
        val record: (String) -> Unit = { trace += it }
        setContent {
            StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = false, systemReduceMotion = false) {
                val paged = remember { list.flatMapLatest { Pager(PagingConfig(pageSize = 30), pagingSourceFactory = it.asPagingSourceFactory()).flow } }
                    .collectAsLazyPagingItems()
                CompositionLocalProvider(LocalShelfCardTrace provides record) {
                    ShelfScreen(state.value, paged, WidthClass.Compact, { 10.seconds }, {}, {})
                }
            }
        }
        waitForIdle()
        assertEquals(setOf("map fresh", "compose fresh", "map older", "compose older", "map oldest", "compose oldest"), trace.toSet())

        fun change(update: ShelfState.() -> ShelfState): List<String> {
            trace.clear()
            state.value = state.value.update()
            waitForIdle()
            return trace.toList()
        }
        assertEquals(emptyList(), change { copy(toast = ShelfToast.LinkCopied) }, "a toast")
        assertEquals(emptyList(), change { copy(undo = UndoOffer("op-1", OperationType.Archive, ItemId("x"))) }, "an undo bar")
        assertEquals(emptyList(), change { copy(toast = null, undo = null, archiveCount = 7, struckItemId = ItemId("x")) }, "other state")

        // The minute tick maps the visible items again, but only the card whose text changed ("5m ago" to "6m ago") recomposes.
        val tick = change { copy(now = now + 1.minutes) }
        assertEquals(listOf("compose fresh"), tick.filter { it.startsWith("compose") }, "the minute tick")

        // Any write reloads the page: equal items are neither mapped nor composed again; a changed one is.
        trace.clear()
        list.value = rows(fresh.copy(), older.copy(readAt = null), oldest.copy())
        waitUntil(timeoutMillis = 5_000) { trace.isNotEmpty() }
        waitForIdle()
        assertEquals(setOf("map older", "compose older"), trace.toSet(), "a reloaded page")
    }
}
