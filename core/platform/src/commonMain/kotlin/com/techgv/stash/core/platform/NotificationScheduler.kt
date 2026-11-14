package com.techgv.stash.core.platform

import com.techgv.stash.core.model.ItemId
import kotlin.time.Instant

/**
 * A reminder ready for the OS. [title] and [body] are already rendered (the data layer uses `ReminderTextRenderer`).
 * [id] is the planned notification's id, stable per day and level, so plans can be diffed. Tapping it opens the reader
 * for [itemId] (`stash://reader/{id}`).
 */
data class ScheduledNotification(
    val id: String,
    val fireAt: Instant,
    val title: String,
    val body: String,
    val itemId: ItemId,
)

interface NotificationScheduler {
    /**
     * Makes what the OS has scheduled for this app equal [plan]: notifications not in [plan] are cancelled and every
     * entry of [plan] is scheduled. An empty plan cancels all of them. An entry may be left alone only when the OS
     * itself reports it as still pending with the same content; a record the app kept of earlier calls is not enough,
     * because the OS drops alarms on reboot and on app update, and those are exactly the moments [apply] is called to
     * re-arm. Never throws, including when the notification or exact-alarm permission is missing: it schedules what
     * the OS allows and leaves the rest.
     */
    suspend fun apply(plan: List<ScheduledNotification>)
}
