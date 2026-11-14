package com.techgv.stash.core.model

import kotlin.time.Instant

/** A saved link. All timestamps are UTC instants; day logic converts them with the user's zone. */
data class SavedItem(
    val id: ItemId,
    val url: String,
    /** Dedupe key from `LinkCapturePolicy.normalize`; unique across all items, including deleted ones. */
    val normalizedUrl: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val faviconUrl: String?,
    val siteName: String,
    val domain: String,
    /** Groups items into source chips, for example "YouTube" or "example.com". */
    val sourceKey: String,
    val contentType: ContentType,
    val sourceApp: String?,
    val readingMinutes: Int?,
    val addedAt: Instant,
    val readAt: Instant?,
    val archivedAt: Instant?,
    val deletedAt: Instant?,
    val fetchStatus: FetchStatus,
    val updatedAt: Instant,
) {
    /** Derived from the timestamps; deleted wins over archived, archived over read. */
    val lifecycle: ItemLifecycle
        get() = when {
            deletedAt != null -> ItemLifecycle.Deleted
            archivedAt != null -> ItemLifecycle.Archived
            readAt != null -> ItemLifecycle.Read
            else -> ItemLifecycle.Unread
        }

    val isOnShelf: Boolean get() = deletedAt == null && archivedAt == null
}

enum class ItemLifecycle { Unread, Read, Archived, Deleted }

/** Metadata status runs alongside the lifecycle and never blocks a transition. */
enum class FetchStatus { Pending, Fetched, Failed }

enum class ContentType { Article, Video, Repository }
