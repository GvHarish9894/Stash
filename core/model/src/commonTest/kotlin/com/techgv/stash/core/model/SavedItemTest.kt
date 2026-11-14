package com.techgv.stash.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SavedItemTest {
    private val t = Instant.parse("2026-11-12T10:00:00Z")

    private fun item(readAt: Instant? = null, archivedAt: Instant? = null, deletedAt: Instant? = null) = SavedItem(
        ItemId("i"), "https://example.com", "https://example.com/", "Title", null, null, null, "example.com",
        "example.com", "example.com", ContentType.Article, null, null, t, readAt, archivedAt, deletedAt, FetchStatus.Pending, t,
    )

    @Test
    fun lifecycleIsDerivedFromTheTimestamps() {
        val cases = listOf(
            item() to ItemLifecycle.Unread,
            item(readAt = t) to ItemLifecycle.Read,
            item(archivedAt = t) to ItemLifecycle.Archived,
            item(readAt = t, archivedAt = t) to ItemLifecycle.Archived,
            item(deletedAt = t) to ItemLifecycle.Deleted,
            item(readAt = t, archivedAt = t, deletedAt = t) to ItemLifecycle.Deleted,
        )
        for ((item, lifecycle) in cases) {
            assertEquals(lifecycle, item.lifecycle)
            assertEquals(lifecycle == ItemLifecycle.Unread || lifecycle == ItemLifecycle.Read, item.isOnShelf)
        }
    }

    @Test
    fun lifecycleStampsCopyTheFourTimestamps() {
        val item = item(readAt = t)
        assertEquals(LifecycleStamps(t, t, null, null), item.lifecycleStamps)
    }
}
