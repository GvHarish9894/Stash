package com.techgv.stash.feature.shelf

import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.ItemId

sealed interface ShelfIntent {
    // Header and navigation
    data object OpenSearch : ShelfIntent
    data object OpenArchive : ShelfIntent
    data object OpenSettings : ShelfIntent
    data object OpenStreak : ShelfIntent

    // Filter chips
    data class SelectFilter(val filter: ShelfFilter) : ShelfIntent
    data object OpenMoreSources : ShelfIntent
    data object CloseMoreSources : ShelfIntent

    data object ReadNudge : ShelfIntent
    data object DismissGestureTip : ShelfIntent

    // Cards
    data class Open(val id: ItemId) : ShelfIntent

    /** A swipe that reached the threshold, or a card accessibility action. */
    data class Act(val id: ItemId, val outcome: SwipeOutcome) : ShelfIntent
    data class Retry(val id: ItemId) : ShelfIntent
    data class OpenMenu(val id: ItemId) : ShelfIntent
    data class MenuAction(val action: ItemMenuAction) : ShelfIntent

    /** The card of Remove from the menu finished its page tear: remove the item now. */
    data class Torn(val id: ItemId) : ShelfIntent
    data object CloseMenu : ShelfIntent

    /** Shared's answer to [ShelfEffect.OpenInBrowser]: whether a browser showed the page. */
    data class BrowserOpened(val opened: Boolean) : ShelfIntent

    // Undo bar
    data object Undo : ShelfIntent

    /** × or the bar's timeout; ignored when a newer offer replaced [opId]. */
    data class DismissUndo(val opId: String) : ShelfIntent

    // Add-link sheet
    data object OpenAddLink : ShelfIntent
    data object CloseAddLink : ShelfIntent

    /** Shared's answer to [ShelfEffect.PeekClipboard]. */
    data class ClipboardPeeked(val suggestion: ClipboardSuggestion?) : ShelfIntent
    data class AddLinkUrlChanged(val url: String) : ShelfIntent
    data object UseClipboard : ShelfIntent

    /** Shared's answer to [ShelfEffect.ReadClipboard]: the link, or null when there is none or the user declined. */
    data class ClipboardRead(val url: String?) : ShelfIntent
    data object SaveLink : ShelfIntent
}
