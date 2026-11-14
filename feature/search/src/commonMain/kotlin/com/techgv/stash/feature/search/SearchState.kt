package com.techgv.stash.feature.search

import com.techgv.stash.core.model.ItemId
import androidx.compose.runtime.Immutable
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.model.OperationType

/**
 * The search screen. [query] is the field as typed; [results] belong to [SearchResults.text], the debounced, trimmed
 * query, and are null while the field is blank or before the first results for it arrive.
 */
@Immutable
data class SearchState(
    val query: String = "",
    /** Newest first, at most 5. */
    val recents: List<String> = emptyList(),
    val results: SearchResults? = null,
    /** The item whose options sheet is open. */
    val menu: SavedItemCardUi? = null,
    val undo: PendingUndo? = null,
    val toast: UiText? = null,
    /** The result an accepted Undo just brought back, which flies back in. */
    val flyBackItemId: String? = null,
)

/** Chips and the applied filter for [text]; [archived] is the archived hit count when the Archived chip is offered. */
@Immutable
data class SearchResults(
    val text: String,
    val total: Int,
    val chips: List<SearchChipUi>,
    val filter: SearchFilter,
    val archived: Int?,
)

@Immutable
data class SearchChipUi(val filter: SearchFilter, val count: Int)

/** One search hit, ready to draw: the card and its highlight ranges (end inclusive). */
@Immutable
data class SearchResultUi(val card: SavedItemCardUi, val titleHighlights: List<IntRange>, val siteHighlights: List<IntRange>)

/** An undoable command that just ran on [itemId]: [type] picks the snackbar message. */
@Immutable
data class PendingUndo(val opId: String, val type: OperationType, val itemId: ItemId)
