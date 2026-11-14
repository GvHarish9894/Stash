package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.model.WidgetItem
import com.techgv.stash.core.model.WidgetSnapshot

object WidgetSnapshotPolicy {
    const val MAX_ITEMS = 3

    /** The next 3 unread items in shelf newest-first order, plus counts and streak. [items] may be any superset. */
    fun snapshot(items: List<SavedItem>, unreadCount: Int, streak: StreakState): WidgetSnapshot {
        val next = items
            .filter { it.lifecycle == ItemLifecycle.Unread }
            .sortedWith(ShelfQuery.comparator(ShelfSort.Newest, emptyMap()))
            .take(MAX_ITEMS)
            .map { WidgetItem(it.id, it.title, it.siteName, it.contentType, it.readingMinutes, it.imageUrl) }
        return WidgetSnapshot(next, unreadCount, streak.count, streak.readToday)
    }
}
