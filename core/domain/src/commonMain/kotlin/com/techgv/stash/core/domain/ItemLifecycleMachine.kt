package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.ItemLifecycle.Archived
import com.techgv.stash.core.model.ItemLifecycle.Deleted
import com.techgv.stash.core.model.ItemLifecycle.Read
import com.techgv.stash.core.model.ItemLifecycle.Unread
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import kotlin.time.Instant

/** The saved-item state machine. The lifecycle is derived from the timestamps, so a transition sets timestamps. */
object ItemLifecycleMachine {
    /**
     * States each lifecycle operation may start from. Anything not listed is invalid. On an archived item, MarkRead and
     * MarkUnread only change `readAt` (the reader can open archived items), and need it to be unset or set respectively.
     */
    val allowedFrom: Map<OperationType, Set<ItemLifecycle>> = mapOf(
        OperationType.MarkRead to setOf(Unread, Archived),
        OperationType.MarkUnread to setOf(Read, Archived),
        OperationType.Archive to setOf(Unread, Read),
        OperationType.AutoArchive to setOf(Read),
        OperationType.Restore to setOf(Archived),
        OperationType.Remove to setOf(Unread, Read),
        OperationType.ClearRead to setOf(Read),
        OperationType.Delete to setOf(Archived),
        OperationType.Reshare to setOf(Unread, Read, Archived, Deleted),
    )

    fun allows(from: ItemLifecycle, type: OperationType): Boolean = from in allowedFrom[type].orEmpty()

    /** The item after [type], or null when [type] is not a valid lifecycle transition for it. */
    fun transition(item: SavedItem, type: OperationType, now: Instant): SavedItem? {
        if (!allows(item.lifecycle, type)) return null
        val next = when (type) {
            OperationType.MarkRead -> if (item.readAt == null) item.copy(readAt = now) else return null
            OperationType.MarkUnread -> if (item.readAt != null) item.copy(readAt = null) else return null
            OperationType.Archive, OperationType.AutoArchive -> item.copy(archivedAt = now)
            // Back to Read or Unread, whichever it was when archived.
            OperationType.Restore -> item.copy(archivedAt = null)
            OperationType.Remove, OperationType.ClearRead, OperationType.Delete -> item.copy(deletedAt = now)
            // Re-sharing brings any saved link back to the top of the shelf, unread.
            OperationType.Reshare -> item.copy(addedAt = now, readAt = null, archivedAt = null, deletedAt = null)
            else -> return null
        }
        return next.copy(updatedAt = now)
    }
}
