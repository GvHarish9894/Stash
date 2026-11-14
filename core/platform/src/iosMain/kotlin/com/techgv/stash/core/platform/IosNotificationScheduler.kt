package com.techgv.stash.core.platform

import com.techgv.stash.core.model.ItemId
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Instant

/** What [IosNotificationScheduler.apply] changes: pending ids to remove, then requests to add. */
data class NotificationChanges(val remove: List<String>, val add: List<ScheduledNotification>)

/**
 * The difference between what the OS reports as [pending] and the [plan]. A pending request stays only when the plan
 * has one with the same id, fire time (to the second, the trigger's precision), title, body and item. A plan entry
 * whose time is not after [now] is never added: iOS delivered it on time, and a calendar trigger in the past never
 * fires (the planner keeps a slot for a while after its time for Android's delayed alarms).
 */
fun notificationChanges(pending: List<ScheduledNotification>, plan: List<ScheduledNotification>, now: Instant): NotificationChanges {
    fun ScheduledNotification.key() = copy(fireAt = Instant.fromEpochSeconds(fireAt.epochSeconds))
    val kept = pending.map { it.key() }.toSet()
    val planned = plan.map { it.key() }.toSet()
    return NotificationChanges(
        remove = pending.filter { it.key() !in planned }.map { it.id }.distinct(),
        add = plan.filter { it.fireAt > now && it.key() !in kept },
    )
}

/** What [IosNotificationScheduler.apply] asks the OS to do: remove these pending ids, then add these requests. */
data class RequestChanges(val remove: List<String>, val add: List<UNNotificationRequest>)

/**
 * [notificationChanges] on the requests the OS reports as [pending]. A request that cannot be read back as a
 * [ScheduledNotification] (no calendar date or no item) is not in any plan and is removed.
 */
fun requestChanges(pending: List<UNNotificationRequest>, plan: List<ScheduledNotification>, now: Instant): RequestChanges {
    val readable = pending.mapNotNull { it.toScheduled() }
    val changes = notificationChanges(readable, plan, now)
    val strays = pending.map { it.identifier } - readable.map { it.id }.toSet()
    return RequestChanges(changes.remove + strays, changes.add.map { it.toRequest() })
}

/**
 * `UNUserNotificationCenter` with calendar triggers. The request's `userInfo["itemId"]` carries the item id; the app's
 * notification delegate (`iosApp/iosApp/iOSApp.swift`) opens `DeepLinks` with it. Without permission the OS refuses the
 * requests and they are dropped silently.
 */
class IosNotificationScheduler : NotificationScheduler {
    // Lazy: the center needs an app bundle, which a test runner does not have.
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }

    override suspend fun apply(plan: List<ScheduledNotification>) {
        val changes = requestChanges(pendingRequests(), plan, Clock.System.now())
        if (changes.remove.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(changes.remove)
        changes.add.forEach { center.addNotificationRequest(it, withCompletionHandler = null) }
    }

    private suspend fun pendingRequests(): List<UNNotificationRequest> = suspendCancellableCoroutine { done ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            done.resume(requests.orEmpty().filterIsInstance<UNNotificationRequest>())
        }
    }

    companion object {
        /** Also read by the Swift notification delegate. */
        const val ITEM_ID_KEY = "itemId"
    }
}

private fun UNNotificationRequest.toScheduled(): ScheduledNotification? {
    val fireAt = (trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() ?: return null
    val itemId = content.userInfo[IosNotificationScheduler.ITEM_ID_KEY] as? String ?: return null
    // iOS hands back nil for an empty title or body although the header says nonnull.
    val title: String? = content.title
    val body: String? = content.body
    return ScheduledNotification(
        identifier,
        Instant.fromEpochSeconds(fireAt.timeIntervalSince1970.toLong()),
        title ?: "",
        body ?: "",
        ItemId(itemId),
    )
}

private fun ScheduledNotification.toRequest(): UNNotificationRequest {
    val content = UNMutableNotificationContent()
    content.setTitle(title)
    content.setBody(body)
    content.setSound(UNNotificationSound.defaultSound)
    content.setUserInfo(mapOf(IosNotificationScheduler.ITEM_ID_KEY to itemId.value))
    val date = NSDate.dateWithTimeIntervalSince1970(fireAt.epochSeconds.toDouble())
    val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond
    val components = NSCalendar.currentCalendar.components(units, fromDate = date)
    val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
    return UNNotificationRequest.requestWithIdentifier(id, content, trigger)
}
