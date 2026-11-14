package com.techgv.stash.feature.archive

import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.ItemId

sealed interface ArchiveIntent {
    /** The archive search field; filters the list in place. */
    data class QueryChanged(val query: String) : ArchiveIntent

    data class Open(val id: ItemId) : ArchiveIntent

    /** A swipe that reached the threshold, or a card accessibility action (Restore, Share, Delete). */
    data class CardAction(val id: ItemId, val outcome: SwipeOutcome) : ArchiveIntent

    data class RetryMetadata(val id: ItemId) : ArchiveIntent

    /** Long-press or "More options". */
    data class ShowMenu(val id: ItemId) : ArchiveIntent

    data class MenuAction(val action: ItemMenuAction) : ArchiveIntent

    data object DismissMenu : ArchiveIntent

    /** The card of Delete from the menu finished its page tear: delete the item now. */
    data class Torn(val id: ItemId) : ArchiveIntent

    data object Undo : ArchiveIntent

    /** The undo bar was dismissed or ran out. */
    data class UndoClosed(val opId: String) : ArchiveIntent

    /** What `shared` reports after [ArchiveEffect.OpenInBrowser]. */
    data class BrowserOpened(val opened: Boolean) : ArchiveIntent

    data object Back : ArchiveIntent
}
