package com.techgv.stash.feature.search

import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.ItemId

sealed interface SearchIntent {
    /** Typing, clearing (empty text) or tapping a recent search. Resets the chip to All. */
    data class QueryChanged(val query: String) : SearchIntent

    data class SelectFilter(val filter: SearchFilter) : SearchIntent

    data class Open(val id: ItemId) : SearchIntent

    /** A card accessibility action (Mark read, Archive, Share, Remove, Restore, Delete, ...). */
    data class CardAction(val id: ItemId, val outcome: SwipeOutcome) : SearchIntent

    data class RetryMetadata(val id: ItemId) : SearchIntent

    /** Long-press or "More options". */
    data class ShowMenu(val id: ItemId) : SearchIntent

    data class MenuAction(val action: ItemMenuAction) : SearchIntent

    data object DismissMenu : SearchIntent

    data object Undo : SearchIntent

    /** The undo bar was dismissed or ran out. */
    data class UndoClosed(val opId: String) : SearchIntent

    /** What `shared` reports after [SearchEffect.OpenInBrowser]. */
    data class BrowserOpened(val opened: Boolean) : SearchIntent

    /** Back button or system back: saves the query to recents and leaves. */
    data object Back : SearchIntent
}
