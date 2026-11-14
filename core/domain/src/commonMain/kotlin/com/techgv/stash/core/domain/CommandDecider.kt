package com.techgv.stash.core.domain

import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.LifecycleStamps
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.OperationPayload
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.lifecycleStamps
import kotlin.time.Instant

/**
 * Pure decider: current state + command + now -> the items to write and the operations to append, or a rejection.
 * The data layer loads the state, calls this inside its transaction and writes the result. It never throws.
 *
 * Precision: every entry point truncates `now` to whole milliseconds, the precision the database stores, so the
 * timestamps an undo compares survive a round trip through storage. Settings changes do not come here (decision D12).
 */
class CommandDecider(private val ids: IdGenerator) {

    /** [existing] is the item with the same normalized URL, including a deleted one. */
    fun saveLink(existing: SavedItem?, command: Command.SaveLink, at: Instant): Decision {
        val now = at.toMillis()
        if (existing != null) return lifecycleChange(existing, OperationType.Reshare, now)
        val c = command.capture
        val item = SavedItem(
            id = ItemId(ids.next()),
            url = c.url,
            normalizedUrl = c.normalizedUrl,
            title = c.title,
            description = null,
            imageUrl = null,
            faviconUrl = null,
            siteName = c.siteName,
            domain = c.domain,
            sourceKey = c.sourceKey,
            contentType = c.contentType,
            sourceApp = command.sourceApp,
            readingMinutes = null,
            addedAt = now,
            readAt = null,
            archivedAt = null,
            deletedAt = null,
            fetchStatus = FetchStatus.Pending,
            updatedAt = now,
        )
        val payload = OperationPayload.Lifecycle(before = null, after = item.lifecycleStamps)
        return Decision.Applied(listOf(item), listOf(operation(item.id, OperationType.Save, payload, now)))
    }

    fun decide(item: SavedItem?, command: Command.ForItem, at: Instant): Decision {
        val now = at.toMillis()
        item ?: return Decision.Rejected(Rejection.NotFound)
        val type = when (command) {
            is Command.MarkRead -> return read(item, markRead = true, now)
            is Command.MarkUnread -> OperationType.MarkUnread
            is Command.Archive -> OperationType.Archive
            is Command.Restore -> OperationType.Restore
            is Command.Remove -> OperationType.Remove
            is Command.Delete -> OperationType.Delete
            is Command.RetryMetadata -> return retryMetadata(item, now)
        }
        return lifecycleChange(item, type, now)
    }

    /**
     * [Command.OpenItem]: marks the item read when [markReadOnOpen] (the user's setting) and it is unread, like
     * [Command.MarkRead]; otherwise changes nothing. Rejected for a missing or deleted item.
     */
    fun open(item: SavedItem?, markReadOnOpen: Boolean, at: Instant): Decision {
        item ?: return Decision.Rejected(Rejection.NotFound)
        return read(item, markRead = markReadOnOpen, at.toMillis())
    }

    /**
     * A read (decision D21). An item that is already read, or an open that does not mark read, is still a read the data
     * layer records for the streak, so it is applied with nothing to write instead of rejected.
     */
    private fun read(item: SavedItem, markRead: Boolean, now: Instant): Decision = when {
        item.lifecycle == ItemLifecycle.Deleted ->
            Decision.Rejected(Rejection.InvalidTransition(ItemLifecycle.Deleted, OperationType.MarkRead))
        !markRead || item.readAt != null -> Decision.Applied(emptyList(), emptyList())
        else -> lifecycleChange(item, OperationType.MarkRead, now)
    }

    /** Undoes [operation] on [item]: restores the timestamps it had before, if nothing changed it since. */
    fun undo(operation: Operation?, item: SavedItem?, at: Instant): Decision {
        val now = at.toMillis()
        operation ?: return Decision.Rejected(Rejection.NotFound)
        if (operation.type !in UNDOABLE) return Decision.Rejected(Rejection.NotUndoable)
        val change = operation.payload as? OperationPayload.Lifecycle ?: return Decision.Rejected(Rejection.NotUndoable)
        val before = change.before ?: return Decision.Rejected(Rejection.NotUndoable)
        if (item == null || item.id != operation.itemId) return Decision.Rejected(Rejection.NotFound)
        if (item.lifecycleStamps != change.after) return Decision.Rejected(Rejection.UndoConflict)
        val restored = item.withStamps(before, now)
        val payload = OperationPayload.Undo(operation.opId, before = item.lifecycleStamps, after = before)
        return Decision.Applied(listOf(restored), listOf(operation(item.id, OperationType.Undo, payload, now)))
    }

    /**
     * Removes the read items among [items] from the shelf; other items are ignored. Each call decides only the items it
     * is given: the data layer passes SQL-filtered candidates (read, on the shelf) in chunks inside one transaction and
     * sums the results. [Rejection.NothingToApply] means this chunk had nothing to change.
     */
    fun clearRead(items: List<SavedItem>, at: Instant): Decision = bulk(items, OperationType.ClearRead, at.toMillis())

    /**
     * Archives the items among [items] that [AutoArchivePolicy] says are due. Chunked like [clearRead]; the data layer
     * selects candidates with `read_at <= AutoArchivePolicy.cutoff(...)`.
     */
    fun applyAutoArchive(items: List<SavedItem>, period: AutoArchivePeriod, at: Instant): Decision {
        val now = at.toMillis()
        return bulk(items.filter { AutoArchivePolicy.isDue(it, period, now) }, OperationType.AutoArchive, now)
    }

    /** Applies [type] to every item it is valid for and skips the rest. */
    private fun bulk(candidates: List<SavedItem>, type: OperationType, now: Instant): Decision {
        val items = ArrayList<SavedItem>()
        val operations = ArrayList<Operation>()
        for (item in candidates) {
            val next = ItemLifecycleMachine.transition(item, type, now) ?: continue
            items += next
            operations += operation(item.id, type, OperationPayload.Lifecycle(item.lifecycleStamps, next.lifecycleStamps), now)
        }
        if (items.isEmpty()) return Decision.Rejected(Rejection.NothingToApply)
        return Decision.Applied(items, operations)
    }

    private fun lifecycleChange(item: SavedItem, type: OperationType, now: Instant): Decision {
        val next = ItemLifecycleMachine.transition(item, type, now)
            ?: return Decision.Rejected(Rejection.InvalidTransition(item.lifecycle, type))
        val payload = OperationPayload.Lifecycle(item.lifecycleStamps, next.lifecycleStamps)
        return Decision.Applied(listOf(next), listOf(operation(item.id, type, payload, now)))
    }

    private fun retryMetadata(item: SavedItem, now: Instant): Decision {
        if (item.lifecycle == ItemLifecycle.Deleted) {
            return Decision.Rejected(Rejection.InvalidTransition(item.lifecycle, OperationType.RetryMetadata))
        }
        if (item.fetchStatus != FetchStatus.Failed) return Decision.Rejected(Rejection.NothingToApply)
        val payload = OperationPayload.Metadata(before = FetchStatus.Failed, after = FetchStatus.Pending)
        return Decision.Applied(
            listOf(item.copy(fetchStatus = FetchStatus.Pending, updatedAt = now)),
            listOf(operation(item.id, OperationType.RetryMetadata, payload, now)),
        )
    }

    private fun operation(itemId: ItemId, type: OperationType, payload: OperationPayload, now: Instant) =
        Operation(ids.next(), itemId, type, payload, now)

    private fun SavedItem.withStamps(stamps: LifecycleStamps, now: Instant) = copy(
        addedAt = stamps.addedAt,
        readAt = stamps.readAt,
        archivedAt = stamps.archivedAt,
        deletedAt = stamps.deletedAt,
        updatedAt = now,
    )

    private fun Instant.toMillis() = Instant.fromEpochMilliseconds(toEpochMilliseconds())

    companion object {
        /** The commands the undo snackbar offers, as in the prototype. */
        val UNDOABLE: Set<OperationType> =
            setOf(OperationType.Archive, OperationType.Restore, OperationType.Remove, OperationType.Delete)
    }
}
