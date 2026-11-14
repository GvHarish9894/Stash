package com.techgv.stash.core.model

import kotlin.time.Instant

/** One row of the append-only item command log. Settings changes are not logged (decision D12). */
data class Operation(
    val opId: String,
    val itemId: ItemId,
    val type: OperationType,
    val payload: OperationPayload,
    val createdAt: Instant,
)

enum class OperationType {
    Save, Reshare, MarkRead, MarkUnread, Archive, AutoArchive, Restore, Remove, ClearRead, Delete,
    RetryMetadata, Undo,
}

/** The lifecycle timestamps a command can change. */
data class LifecycleStamps(
    val addedAt: Instant,
    val readAt: Instant?,
    val archivedAt: Instant?,
    val deletedAt: Instant?,
)

val SavedItem.lifecycleStamps: LifecycleStamps get() = LifecycleStamps(addedAt, readAt, archivedAt, deletedAt)

sealed interface OperationPayload {
    /** [before] is null when the command created the item. */
    data class Lifecycle(val before: LifecycleStamps?, val after: LifecycleStamps) : OperationPayload

    data class Metadata(val before: FetchStatus, val after: FetchStatus) : OperationPayload

    data class Undo(val undoneOpId: String, val before: LifecycleStamps, val after: LifecycleStamps) : OperationPayload
}
