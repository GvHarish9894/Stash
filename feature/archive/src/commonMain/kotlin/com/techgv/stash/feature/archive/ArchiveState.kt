package com.techgv.stash.feature.archive

import com.techgv.stash.core.model.ItemId
import androidx.compose.runtime.Immutable
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.OperationType

/** The archive screen; the rows are paged separately. */
@Immutable
data class ArchiveState(
    /** All archived items, whatever the search. */
    val count: Int = 0,
    /** Drives the rule line and the empty state. */
    val autoArchive: AutoArchivePeriod = AutoArchivePeriod.Days30,
    val layout: CardLayout = CardLayout.Comfortable,
    /** The item whose options sheet is open. */
    val menu: SavedItemCardUi? = null,
    val undo: PendingUndo? = null,
    val toast: UiText? = null,
    /** The card playing the page tear of Delete from the item menu; the command goes out when it is torn. */
    val tearingItemId: String? = null,
    /** The card an accepted Undo just brought back, which flies back in. */
    val flyBackItemId: String? = null,
)

@Immutable
sealed interface ArchiveRowUi {
    val key: String

    /** "This month" / "Earlier". */
    data class Header(val label: GroupLabel) : ArchiveRowUi {
        override val key: String get() = "h:$label"
    }

    /** [startAction] / [endAction] come from `SwipeActionResolver` for the archived item (fixed: delete / restore). */
    data class Card(
        val card: SavedItemCardUi,
        val startAction: SwipeOutcome,
        val endAction: SwipeOutcome,
        val titleHighlights: List<IntRange>,
    ) : ArchiveRowUi {
        override val key: String get() = card.id
    }
}

/** An undoable command that just ran on [itemId]: [type] picks the snackbar message. */
@Immutable
data class PendingUndo(val opId: String, val type: OperationType, val itemId: ItemId)
