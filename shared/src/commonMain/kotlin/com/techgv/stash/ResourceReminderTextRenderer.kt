package com.techgv.stash

import com.techgv.stash.core.domain.ReminderText
import com.techgv.stash.core.domain.ReminderTextRenderer
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.reminder_daily_body
import com.techgv.stash.core.resources.reminder_daily_body_no_minutes
import com.techgv.stash.core.resources.reminder_daily_body_no_minutes_no_streak
import com.techgv.stash.core.resources.reminder_daily_body_no_streak
import com.techgv.stash.core.resources.reminder_daily_title
import com.techgv.stash.core.resources.reminder_evening_body
import com.techgv.stash.core.resources.reminder_evening_body_no_minutes
import com.techgv.stash.core.resources.reminder_evening_title
import com.techgv.stash.core.resources.reminder_last_call_body
import com.techgv.stash.core.resources.reminder_last_call_body_no_minutes
import com.techgv.stash.core.resources.reminder_last_call_title
import com.techgv.stash.core.resources.reminder_late_body
import com.techgv.stash.core.resources.reminder_late_body_no_minutes
import com.techgv.stash.core.resources.reminder_late_title
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Reminder text from `core:resources` strings in the system's current language, English where a language has no
 * translation (decision D10). Reads the language on every render, so a change applies to the next plan.
 */
object ResourceReminderTextRenderer : ReminderTextRenderer {
    override suspend fun render(notification: PlannedNotification): ReminderText {
        val copy = notification.copy
        val minutes = copy.minutes
        return when (notification.level) {
            // The only level planned with no streak yet (the saver levels need one), so the only one with "no streak" text.
            ReminderLevel.Daily -> ReminderText(
                getString(Res.string.reminder_daily_title),
                when {
                    minutes == null && copy.streak == 0 ->
                        getString(Res.string.reminder_daily_body_no_minutes_no_streak, copy.unreadCount, copy.itemTitle)
                    minutes == null ->
                        getString(Res.string.reminder_daily_body_no_minutes, copy.unreadCount, copy.itemTitle, copy.streak)
                    copy.streak == 0 -> getString(Res.string.reminder_daily_body_no_streak, copy.unreadCount, copy.itemTitle, minutes)
                    else -> getString(Res.string.reminder_daily_body, copy.unreadCount, copy.itemTitle, minutes, copy.streak)
                },
            )
            ReminderLevel.Evening -> ReminderText(
                getString(Res.string.reminder_evening_title, copy.streak),
                if (minutes == null) {
                    getString(Res.string.reminder_evening_body_no_minutes)
                } else {
                    getString(Res.string.reminder_evening_body, minutes)
                },
            )
            ReminderLevel.Late -> ReminderText(
                getString(Res.string.reminder_late_title, copy.streak),
                if (minutes == null) {
                    getString(Res.string.reminder_late_body_no_minutes, copy.itemTitle)
                } else {
                    getString(Res.string.reminder_late_body, copy.itemTitle, minutes)
                },
            )
            ReminderLevel.LastCall -> ReminderText(
                getPluralString(Res.plurals.reminder_last_call_title, copy.streak, copy.streak),
                if (minutes == null) {
                    getString(Res.string.reminder_last_call_body_no_minutes, copy.itemTitle)
                } else {
                    getString(Res.string.reminder_last_call_body, copy.itemTitle, minutes)
                },
            )
        }
    }
}
