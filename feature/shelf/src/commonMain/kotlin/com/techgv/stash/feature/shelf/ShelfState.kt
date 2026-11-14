package com.techgv.stash.feature.shelf

import androidx.compose.runtime.Immutable
import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.domain.ChipCounts
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Everything Home draws except the paged list ([ShelfViewModel.rows]). */
@Immutable
data class ShelfState(
    val now: Instant,
    val zone: TimeZone,
    val preferences: UserPreferences = UserPreferences(),
    val chips: ChipCounts = ChipCounts(all = 0, unread = 0, sources = emptyList()),
    /** The applied filter: a selected source that disappeared falls back to All. */
    val filter: ShelfFilter = ShelfFilter.All,
    /** Null until the first streak state arrives. */
    val streak: StreakState? = null,
    val nudge: ShelfNudge? = null,
    val archiveCount: Int = 0,
    /** Cards rise in, staggered, until the first load settles. */
    val firstLoad: Boolean = true,
    /** The item just saved here, which drops in with the paper flutter. */
    val newItemId: ItemId? = null,
    /** The item just marked read here, whose pencil strike animates. */
    val struckItemId: ItemId? = null,
    /** The item whose card plays the page tear of Remove from the item menu; the command goes out when it is torn. */
    val tearingItemId: ItemId? = null,
    /** The item an accepted Undo just brought back, whose card flies back in. */
    val flyBackItemId: ItemId? = null,
    /** The item whose options sheet is open. */
    val menuItem: SavedItem? = null,
    val moreSourcesOpen: Boolean = false,
    /** The add-link sheet, null while closed. */
    val addLink: AddLinkState? = null,
    val undo: UndoOffer? = null,
    val toast: ShelfToast? = null,
) {
    val showGestureTip: Boolean get() = !preferences.gestureTipDismissed
}

/** The nudge banner: [title] is the shortest unread item's title, already capped. */
@Immutable
data class ShelfNudge(val level: ReminderLevel, val streak: Int, val itemId: ItemId, val title: String, val minutes: Int?)

@Immutable
data class AddLinkState(
    val url: String = "",
    /** What shared found on the clipboard when the sheet opened; null for nothing (or not answered yet). */
    val clipboard: ClipboardSuggestion? = null,
    val error: AddLinkError? = null,
)

enum class AddLinkError { Empty, NotALink }

/** Undo for the last undoable command on [itemId]; [type] picks the message. A newer offer replaces it. */
@Immutable
data class UndoOffer(val opId: String, val type: OperationType, val itemId: ItemId)

enum class ShelfToast { OpenedInBrowser, BrowserFailed, LinkCopied, AlreadyOnShelf, SaveFailed, ActionFailed }

/** A row of the paged shelf list: a group header, or a card with the swipe outcomes resolved for it. */
sealed interface ShelfListRow {
    val key: String

    data class Header(val label: GroupLabel) : ShelfListRow {
        override val key: String get() = "header:$label"
    }

    data class Card(val item: SavedItem, val startAction: SwipeOutcome, val endAction: SwipeOutcome) : ShelfListRow {
        override val key: String get() = item.id.value
    }
}
