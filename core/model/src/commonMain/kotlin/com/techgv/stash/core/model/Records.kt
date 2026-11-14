package com.techgv.stash.core.model

import kotlin.time.Instant

/** What another app shared into Stash, as the share target received it. */
data class SharedPayload(val text: String?, val subject: String?, val sourceApp: String?, val at: Instant)

data class RecentSearch(val query: String, val usedAt: Instant)

/** What the home-screen widget shows. Empty [items] means "all caught up". */
data class WidgetSnapshot(
    val items: List<WidgetItem>,
    val unreadCount: Int,
    val streak: Int,
    val readToday: Boolean,
) {
    val allCaughtUp: Boolean get() = items.isEmpty()
}

data class WidgetItem(
    val id: ItemId,
    val title: String,
    val siteName: String,
    val contentType: ContentType,
    val readingMinutes: Int?,
    val imageUrl: String?,
)
