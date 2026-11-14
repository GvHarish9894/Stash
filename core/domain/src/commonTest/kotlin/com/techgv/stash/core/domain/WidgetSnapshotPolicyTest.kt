package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.WidgetItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class WidgetSnapshotPolicyTest {
    private val streak = StreakState(count = 12, readToday = true, status = StreakStatus.Lit, week = emptyList())

    @Test
    fun nextThreeUnreadNewestFirstWithCountsAndStreak() {
        val items = listOf(
            item("u1", T0 - 5.hours),
            item("u2", T0 - 1.hours, readingMinutes = 7),
            item("read", T0, readAt = T0),
            item("u3", T0 - 3.hours),
            item("archived", T0, archivedAt = T0),
            item("deleted", T0, deletedAt = T0),
            item("u4", T0 - 2.hours),
        )
        val snapshot = WidgetSnapshotPolicy.snapshot(items, unreadCount = 4, streak = streak)
        assertEquals(listOf("u2", "u4", "u3"), snapshot.items.map { it.id.value })
        assertEquals(WidgetItem(ItemId("u2"), "Title u2", "example.com", ContentType.Article, 7, null), snapshot.items.first())
        assertEquals(4, snapshot.unreadCount)
        assertEquals(12, snapshot.streak)
        assertEquals(true, snapshot.readToday)
        assertEquals(false, snapshot.allCaughtUp)
    }

    @Test
    fun fewerThanThreeAndAllCaughtUp() {
        assertEquals(1, WidgetSnapshotPolicy.snapshot(listOf(item("only")), 1, streak).items.size)
        val empty = WidgetSnapshotPolicy.snapshot(listOf(item("read", readAt = T0)), 0, streak)
        assertEquals(emptyList(), empty.items)
        assertEquals(true, empty.allCaughtUp)
    }
}
