package com.techgv.stash.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * One kept day of the streak, dated in the zone the user was in at the time.
 * [firstReadAt] is null for a rest day: nothing was unread at the end of it, so it keeps the streak without adding to it.
 * Rest rows are written only when the unread count goes from 0 to more (see `StreakEngine.restDays`).
 */
data class StreakDay(val date: LocalDate, val firstReadAt: Instant?)

/** Reminder slots: the daily reminder, then the streak saver at 19:00, 21:30 and 23:00. Also the in-app nudge level 1-4. */
enum class ReminderLevel { Daily, Evening, Late, LastCall }

/** A notification the platform should schedule. [id] is stable per day and level so plans can be diffed. */
data class PlannedNotification(
    val id: String,
    val fireAt: Instant,
    val level: ReminderLevel,
    val itemId: ItemId,
    val copy: ReminderCopy,
)

/** Parameters for the localized notification text. [itemTitle] is already capped for the notification body. */
data class ReminderCopy(
    val streak: Int,
    val unreadCount: Int,
    val itemTitle: String,
    val minutes: Int?,
)
