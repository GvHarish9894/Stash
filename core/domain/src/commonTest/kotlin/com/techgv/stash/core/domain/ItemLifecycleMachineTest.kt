package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.ItemLifecycle.Archived
import com.techgv.stash.core.model.ItemLifecycle.Deleted
import com.techgv.stash.core.model.ItemLifecycle.Read
import com.techgv.stash.core.model.ItemLifecycle.Unread
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ItemLifecycleMachineTest {
    private val now = T0
    private val unread = item(addedAt = T0 - 3.days)
    private val read = item(addedAt = T0 - 3.days, readAt = T0 - 2.days)
    private val archivedRead = item(addedAt = T0 - 3.days, readAt = T0 - 2.days, archivedAt = T0 - 1.days)
    private val archivedUnread = item(addedAt = T0 - 3.days, archivedAt = T0 - 1.days)
    private val deleted = item(addedAt = T0 - 3.days, readAt = T0 - 2.days, deletedAt = T0 - 1.hours)

    private data class Valid(val from: SavedItem, val type: OperationType, val to: ItemLifecycle)

    private val valid = listOf(
        Valid(unread, OperationType.MarkRead, Read),
        Valid(read, OperationType.MarkUnread, Unread),
        Valid(unread, OperationType.Archive, Archived),
        Valid(read, OperationType.Archive, Archived),
        Valid(read, OperationType.AutoArchive, Archived),
        Valid(archivedRead, OperationType.Restore, Read),
        Valid(archivedUnread, OperationType.Restore, Unread),
        Valid(unread, OperationType.Remove, Deleted),
        Valid(read, OperationType.Remove, Deleted),
        Valid(read, OperationType.ClearRead, Deleted),
        Valid(archivedRead, OperationType.Delete, Deleted),
        Valid(archivedUnread, OperationType.Delete, Deleted),
        Valid(archivedUnread, OperationType.MarkRead, Archived),
        Valid(archivedRead, OperationType.MarkUnread, Archived),
        Valid(archivedUnread, OperationType.Reshare, Unread),
        Valid(unread, OperationType.Reshare, Unread),
        Valid(read, OperationType.Reshare, Unread),
        Valid(archivedRead, OperationType.Reshare, Unread),
        Valid(deleted, OperationType.Reshare, Unread),
    )

    @Test
    fun everyValidTransitionReachesItsTargetAndStampsUpdatedAt() {
        for (case in valid) {
            val next = assertNotNull(ItemLifecycleMachine.transition(case.from, case.type, now), "$case")
            assertEquals(case.to, next.lifecycle, "$case")
            assertEquals(now, next.updatedAt, "$case")
            assertEquals(case.from.copy(addedAt = next.addedAt, readAt = next.readAt, archivedAt = next.archivedAt,
                deletedAt = next.deletedAt, updatedAt = now), next, "only lifecycle fields change: $case")
        }
    }

    @Test
    fun everyOtherItemAndOperationPairIsInvalid() {
        val items = listOf(unread, read, archivedRead, archivedUnread, deleted)
        val validPairs = valid.map { it.from to it.type }.toSet()
        var invalid = 0
        for (from in items) for (type in OperationType.entries) {
            if ((from to type) in validPairs) continue
            invalid++
            assertNull(ItemLifecycleMachine.transition(from, type, now), "$from + $type")
        }
        // 5 item states x 12 operation types minus the 19 valid pairs.
        assertEquals(19, validPairs.size)
        assertEquals(5 * 12 - 19, invalid)
        // Only an archived item's read flag decides between the two; the state table alone allows both.
        assertEquals(true, ItemLifecycleMachine.allows(Archived, OperationType.MarkRead))
        assertEquals(false, ItemLifecycleMachine.allows(Deleted, OperationType.MarkRead))
    }

    @Test
    fun markReadStampsReadAtAndMarkUnreadClearsIt() {
        assertEquals(now, ItemLifecycleMachine.transition(unread, OperationType.MarkRead, now)?.readAt)
        assertNull(ItemLifecycleMachine.transition(read, OperationType.MarkUnread, now)?.readAt)
    }

    @Test
    fun restoreKeepsTheReadStampSoTheItemReturnsAsItWas() {
        val restored = ItemLifecycleMachine.transition(archivedRead, OperationType.Restore, now)
        assertEquals(archivedRead.readAt, restored?.readAt)
        assertNull(restored?.archivedAt)
    }

    @Test
    fun reshareMovesTheItemToTheTopAsUnread() {
        for (from in listOf(unread, read, archivedRead, deleted)) {
            val next = assertNotNull(ItemLifecycleMachine.transition(from, OperationType.Reshare, now))
            assertEquals(now, next.addedAt, "${from.lifecycle}")
            assertNull(next.readAt)
            assertNull(next.archivedAt)
            assertNull(next.deletedAt)
            assertEquals(from.id, next.id)
            assertEquals(from.title, next.title)
        }
    }
}
