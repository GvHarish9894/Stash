package com.techgv.stash.platform

import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.ScheduledNotification
import com.techgv.stash.core.platform.requestChanges
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** `IosNotificationScheduler.apply` without the notification center, which needs an app bundle. */
class RequestChangesTest {
    private val tomorrow = Clock.System.now() + 1.days
    private val daily = ScheduledNotification("daily", tomorrow, "Time to read", "“Slow reading” · 5 min", ItemId("a"))
    private val saver = ScheduledNotification("saver-1", tomorrow + 10.hours, "Keep your streak", "3 min", ItemId("b"))

    /** What the OS would report after the requests built for [plan] were added. */
    private fun pendingAfter(plan: List<ScheduledNotification>) = requestChanges(emptyList(), plan, Clock.System.now()).add

    @Test
    fun requestsBuiltForAPlanReadBackAsThatPlan() {
        val added = pendingAfter(listOf(daily, saver))
        assertEquals(listOf("daily", "saver-1"), added.map { it.identifier })
        assertEquals(listOf("Time to read", "Keep your streak"), added.map { it.content.title })
        assertEquals(listOf<Any?>("a", "b"), added.map { it.content.userInfo["itemId"] })
        val again = requestChanges(added, listOf(daily, saver), Clock.System.now())
        assertEquals(emptyList(), again.remove)
        assertEquals(emptyList(), again.add)
    }

    @Test
    fun aChangedEntryIsReplacedAndOneThatLeftThePlanIsRemoved() {
        val changed = daily.copy(body = "8 min")
        val changes = requestChanges(pendingAfter(listOf(daily, saver)), listOf(changed), Clock.System.now())
        assertEquals(listOf("daily", "saver-1"), changes.remove)
        assertEquals(listOf("8 min"), changes.add.map { it.content.body })
    }

    @Test
    fun requestsThatCannotBeReadBackAreRemoved() {
        val noItem = UNNotificationRequest.requestWithIdentifier(
            "no-item",
            UNMutableNotificationContent().apply { setTitle("Old") },
            pendingAfter(listOf(daily)).single().trigger,
        )
        val noCalendar = UNNotificationRequest.requestWithIdentifier(
            "interval",
            UNMutableNotificationContent(),
            UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(600.0, repeats = false),
        )
        val changes = requestChanges(listOf(noItem, noCalendar), emptyList(), Clock.System.now())
        assertEquals(listOf("no-item", "interval"), changes.remove)
        assertEquals(emptyList(), changes.add)
    }
}
