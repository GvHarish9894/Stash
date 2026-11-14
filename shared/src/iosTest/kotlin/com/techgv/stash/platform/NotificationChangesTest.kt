package com.techgv.stash.platform

import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.NotificationChanges
import com.techgv.stash.core.platform.ScheduledNotification
import com.techgv.stash.core.platform.notificationChanges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NotificationChangesTest {
    private val nine = Instant.parse("2026-11-12T09:00:00Z")
    private val daily = ScheduledNotification("2026-11-12-daily", nine, "Time to read", "5 min", ItemId("a"))
    private val saver = ScheduledNotification("2026-11-12-saver-1", nine + 600.minutes, "Keep your streak", "3 min", ItemId("b"))
    private val before = nine - 1.minutes

    @Test
    fun anIdenticalPlanChangesNothing() {
        assertEquals(NotificationChanges(emptyList(), emptyList()), notificationChanges(listOf(daily, saver), listOf(daily, saver), before))
    }

    @Test
    fun pendingRequestsThatLeftThePlanAreRemovedAndNewEntriesAdded() {
        assertEquals(NotificationChanges(listOf(saver.id), listOf(daily)), notificationChanges(listOf(saver), listOf(daily), before))
    }

    @Test
    fun anEntryWhoseContentChangedIsReplaced() {
        listOf(
            daily.copy(body = "8 min"),
            daily.copy(title = "Last call"),
            daily.copy(itemId = ItemId("c")),
            daily.copy(fireAt = nine + 1.minutes),
        ).forEach { changed ->
            assertEquals(NotificationChanges(listOf(daily.id), listOf(changed)), notificationChanges(listOf(daily), listOf(changed), before))
        }
    }

    @Test
    fun fireTimesAreComparedToTheSecondLikeTheCalendarTrigger() {
        assertEquals(NotificationChanges(emptyList(), emptyList()), notificationChanges(listOf(daily), listOf(daily.copy(fireAt = nine + 400.milliseconds)), before))
    }

    @Test
    fun anEmptyPlanRemovesEverything() {
        assertEquals(NotificationChanges(listOf(daily.id, saver.id), emptyList()), notificationChanges(listOf(daily, saver), emptyList(), before))
    }

    @Test
    fun nothingPendingAddsTheWholePlan() {
        assertEquals(NotificationChanges(emptyList(), listOf(daily, saver)), notificationChanges(emptyList(), listOf(daily, saver), before))
    }

    @Test
    fun aPlanEntryWhoseTimePassedIsNotAdded() {
        // The planner keeps a slot for 30 minutes after its time; iOS delivered it on time.
        assertEquals(NotificationChanges(emptyList(), listOf(saver)), notificationChanges(emptyList(), listOf(daily, saver), nine + 20.seconds))
        assertEquals(NotificationChanges(emptyList(), listOf(saver)), notificationChanges(emptyList(), listOf(daily, saver), nine))
    }
}
