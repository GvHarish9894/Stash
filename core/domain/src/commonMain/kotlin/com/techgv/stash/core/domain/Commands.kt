package com.techgv.stash.core.domain

import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences

/** Every user intent that writes data, dispatched through [CommandBus]. */
sealed interface Command {
    /** Saves a captured link; an existing item with the same normalized URL is bumped to the top as unread. */
    data class SaveLink(val capture: LinkCapture.Captured, val sourceApp: String?) : Command

    /** A command on one item. */
    sealed interface ForItem : Command {
        val id: ItemId
    }

    /**
     * Reading an item (decision D21): marks it read, or changes nothing when it already is; either way the day counts for
     * the streak, recorded in the same transaction, and [DispatchResult.Done.celebration] carries the celebration.
     */
    data class MarkRead(override val id: ItemId) : ForItem
    data class MarkUnread(override val id: ItemId) : ForItem
    data class Archive(override val id: ItemId) : ForItem
    data class Restore(override val id: ItemId) : ForItem

    /** Removes an item from the shelf. */
    data class Remove(override val id: ItemId) : ForItem

    /** Deletes an item from the archive. */
    data class Delete(override val id: ItemId) : ForItem

    /** Sets failed metadata back to pending so the fetcher tries again. */
    data class RetryMetadata(override val id: ItemId) : ForItem

    data class Undo(val opId: String) : Command

    /**
     * The user opened an item in the reader (decision D21). Marks it read like [MarkRead] when the `markReadOnOpen`
     * setting is on and it is unread; otherwise changes no item. Either way the day counts for the streak, like [MarkRead].
     */
    data class OpenItem(val id: ItemId) : Command

    /** Applied atomically to the stored preferences by the data layer; writes no operation (decision D12). */
    class ChangeSetting(val transform: (UserPreferences) -> UserPreferences) : Command

    /** Removes every read item from the shelf. Confirmed in the UI, so not undoable. */
    data object ClearRead : Command

    /** Archives every read item older than [period]. Confirmed in the UI or run daily, so not undoable. */
    data class ApplyAutoArchive(val period: AutoArchivePeriod) : Command
}

/** The decider's write plan; internal to the data layer's command handler. */
sealed interface Decision {
    /** Write [items] and append [operations] in one transaction. Both are empty for a read that changes no item. */
    data class Applied(val items: List<SavedItem>, val operations: List<Operation>) : Decision

    data class Rejected(val rejection: Rejection) : Decision
}

sealed interface Rejection {
    /** The item or operation does not exist, or the operation belongs to another item. */
    data object NotFound : Rejection

    data class InvalidTransition(val from: ItemLifecycle, val type: OperationType) : Rejection

    data object NotUndoable : Rejection

    /** The item changed after the operation, so undoing it would overwrite a newer change. */
    data object UndoConflict : Rejection

    /** The command would change nothing. */
    data object NothingToApply : Rejection

    /** The database or the settings file could not be read or written; nothing was changed. */
    data object StorageFailure : Rejection
}

/** What [CommandBus.dispatch] tells the UI. */
sealed interface DispatchResult {
    /**
     * [affected] is how many items changed (0 for a settings change). [undoOpId] is set when the UI may offer Undo.
     * For a single-item command [itemId] is that item; [reshared] is true when [Command.SaveLink] found an existing item.
     * [celebration] is set when a [Command.MarkRead] or [Command.OpenItem] was the first read of the day.
     */
    data class Done(
        val affected: Int,
        val undoOpId: String? = null,
        val itemId: ItemId? = null,
        val reshared: Boolean = false,
        val celebration: Celebration? = null,
    ) : DispatchResult

    data class Rejected(val rejection: Rejection) : DispatchResult

    companion object {
        /** The result for an item [decision], so every bus implementation reports the same thing. */
        fun of(decision: Decision): DispatchResult = when (decision) {
            is Decision.Rejected -> Rejected(decision.rejection)
            is Decision.Applied -> {
                val single = decision.operations.singleOrNull()
                Done(
                    affected = decision.items.size,
                    undoOpId = single?.takeIf { it.type in CommandDecider.UNDOABLE }?.opId,
                    itemId = single?.itemId,
                    reshared = single?.type == OperationType.Reshare,
                )
            }
        }

        /**
         * The combined result of a chunked bulk command ([CommandDecider.clearRead], [CommandDecider.applyAutoArchive]):
         * [Done.affected] is the sum over the chunks, with no item id or undo. It is [Rejection.NothingToApply] only when
         * no chunk applied anything; a chunk's other rejection, if any, is reported instead.
         */
        fun ofBulk(decisions: List<Decision>): DispatchResult {
            val applied = decisions.filterIsInstance<Decision.Applied>()
            if (applied.isNotEmpty()) return Done(affected = applied.sumOf { it.items.size })
            val rejection = decisions.filterIsInstance<Decision.Rejected>().map { it.rejection }
                .firstOrNull { it != Rejection.NothingToApply } ?: Rejection.NothingToApply
            return Rejected(rejection)
        }
    }
}
