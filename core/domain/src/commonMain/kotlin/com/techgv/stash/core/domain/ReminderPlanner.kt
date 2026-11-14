package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.ReminderCopy
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Plans every pending reminder from the current state; platforms only apply the plan. Re-plan after every command,
 * on app start, after boot and on a time-zone change.
 */
object ReminderPlanner {
    const val TITLE_CAP = 40

    /**
     * How long after its time a slot stays in the plan while it still applies, so a re-plan while the OS still holds
     * its alarm (Android delivers in a 10-minute window, later in Doze) does not cancel it. Platforms post a reminder
     * at most this late.
     */
    val DELIVERY_GRACE = 30.minutes

    private val saverSlots = listOf(
        LocalTime(19, 0) to ReminderLevel.Evening,
        LocalTime(21, 30) to ReminderLevel.Late,
        LocalTime(23, 0) to ReminderLevel.LastCall,
    )

    /**
     * Nothing when nothing is unread. Otherwise, unless the user read today, today's remaining slots: the daily reminder
     * and, when the streak is alive, the streak saver (at most 3). Plus tomorrow's slots, so reminders fire even if the
     * app is not opened again: after a read, tomorrow's daily reminder and saver (at most 4); before one, only tomorrow's
     * daily reminder, because an unread day ends the streak. A saver slot replaces a daily reminder at the same time.
     * A slot stays for [DELIVERY_GRACE] after its time, on the same conditions as before it, so a read, a settings
     * change or nothing left unread removes it at once; older slots are skipped. At most 5 are pending (before 9 AM with
     * nothing read: today's four plus tomorrow's daily), and at most 4 after a read (decision D15).
     */
    fun plan(
        preferences: UserPreferences,
        streak: Int,
        readToday: Boolean,
        unreadCount: Int,
        shortestUnread: SavedItem?,
        now: Instant,
        zone: TimeZone,
    ): List<PlannedNotification> {
        if (unreadCount == 0 || shortestUnread == null) return emptyList()
        val today = now.toLocalDateTime(zone).date
        val tomorrowStreak = if (readToday) streak else 0
        val todaySlots = if (readToday) emptyList() else slots(preferences, streak).map { Triple(today, it, streak) }
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val tomorrowSlots = slots(preferences, tomorrowStreak).map { Triple(tomorrow, it, tomorrowStreak) }
        return (todaySlots + tomorrowSlots)
            .map { (date, slot, slotStreak) ->
                val (time, level) = slot
                val copy = ReminderCopy(
                    streak = slotStreak,
                    unreadCount = unreadCount,
                    itemTitle = copyTitle(shortestUnread),
                    minutes = shortestUnread.readingMinutes,
                )
                PlannedNotification("$date-${level.name}", LocalDateTime(date, time).toInstant(zone), level, shortestUnread.id, copy)
            }
            .filter { now - it.fireAt <= DELIVERY_GRACE }
            .sortedBy { it.fireAt }
    }

    /**
     * The in-app nudge banner level, or null when it is hidden (read today or nothing unread). It escalates with the
     * reminder schedule: the level of the latest of today's slots that has passed, given the settings and streak, else
     * [ReminderLevel.Daily]. A 7 PM daily reminder counts as [ReminderLevel.Evening], as in the prototype.
     */
    fun nudgeLevel(
        preferences: UserPreferences,
        streak: Int,
        readToday: Boolean,
        unreadCount: Int,
        now: Instant,
        zone: TimeZone,
    ): ReminderLevel? {
        if (readToday || unreadCount == 0) return null
        val time = now.toLocalDateTime(zone).time
        val (slotTime, level) = slots(preferences, streak).lastOrNull { time >= it.first } ?: return ReminderLevel.Daily
        return if (level == ReminderLevel.Daily && slotTime == saverSlots.first().first) ReminderLevel.Evening else level
    }

    /**
     * Today's reminder at [level]'s slot as [plan] would send it if nothing had been read yet today, for the settings
     * preview: the daily reminder at the chosen time, a saver slot at its own time. A 7 PM daily reminder fills the
     * [ReminderLevel.Evening] slot even with the saver off. Otherwise the first reason in [NoReminderReason]'s order.
     */
    fun preview(
        level: ReminderLevel,
        preferences: UserPreferences,
        streak: Int,
        readToday: Boolean,
        unreadCount: Int,
        shortestUnread: SavedItem?,
        now: Instant,
        zone: TimeZone,
    ): ReminderPreviewResult {
        val dailyTime = LocalTime(preferences.reminderTime.hour, 0)
        val time = saverSlots.firstOrNull { it.second == level }?.first ?: dailyTime
        val dailyFillsSlot = preferences.dailyReminder && dailyTime == time
        val reason = when {
            level == ReminderLevel.Daily && !preferences.dailyReminder -> NoReminderReason.ReminderOff
            level != ReminderLevel.Daily && !preferences.streakSaver && !(level == ReminderLevel.Evening && dailyFillsSlot) ->
                NoReminderReason.SaverOff
            readToday -> NoReminderReason.ReadToday
            unreadCount == 0 || shortestUnread == null -> NoReminderReason.NothingUnread
            else -> null
        }
        if (reason != null) return ReminderPreviewResult.NoReminder(reason)
        val today = now.toLocalDateTime(zone).date
        val planned = plan(preferences, streak, readToday = false, unreadCount, shortestUnread, today.atStartOfDayIn(zone), zone)
            .firstOrNull { it.fireAt == LocalDateTime(today, time).toInstant(zone) }
        // Only a saver slot can be missing here: the plan holds it only while there is a streak to save.
        return planned?.let(ReminderPreviewResult::Planned) ?: ReminderPreviewResult.NoReminder(NoReminderReason.NoStreak)
    }

    /** One day's slots in time order: the daily reminder, and the saver when it is on and the streak is alive. */
    private fun slots(preferences: UserPreferences, streak: Int): List<Pair<LocalTime, ReminderLevel>> {
        val saver = if (preferences.streakSaver && streak > 0) saverSlots else emptyList()
        val dailyTime = LocalTime(preferences.reminderTime.hour, 0)
        val daily = if (preferences.dailyReminder && saver.none { it.first == dailyTime }) listOf(dailyTime to ReminderLevel.Daily) else emptyList()
        return (daily + saver).sortedBy { it.first }
    }

    /** The quickest unread item: fewest minutes, unknown minutes last, then newest. Used by nudges and "Read now". */
    fun shortestUnread(items: List<SavedItem>): SavedItem? =
        items.filter { it.lifecycle == ItemLifecycle.Unread }.minWithOrNull(
            compareBy<SavedItem, Int?>(nullsLast()) { it.readingMinutes }
                .thenByDescending { it.addedAt }
                .thenByDescending { it.id.value },
        )

    /**
     * [item]'s title for reminder and nudge copy, capped with [capTitle]. A title that is only a link (metadata not
     * fetched yet, or failed) is replaced by the site name, so the copy never shows a raw URL.
     */
    fun copyTitle(item: SavedItem): String {
        val title = item.title.trim()
        val isLink = title == item.url || title == item.normalizedUrl ||
            (title.none { it.isWhitespace() } && UrlParts.parse(title) != null)
        return capTitle(if (isLink) item.siteName else item.title)
    }

    /**
     * Caps a title at [TITLE_CAP] characters plus an ellipsis, without splitting a surrogate pair.
     * Known limit: it can separate a base character from a following combining mark.
     */
    fun capTitle(title: String): String {
        if (title.length <= TITLE_CAP) return title
        val end = if (title[TITLE_CAP - 1].isHighSurrogate()) TITLE_CAP - 1 else TITLE_CAP
        return title.substring(0, end) + "…"
    }
}

/** What [ReminderPlanner.preview] found at a slot. */
sealed interface ReminderPreviewResult {
    data class Planned(val notification: PlannedNotification) : ReminderPreviewResult

    data class NoReminder(val reason: NoReminderReason) : ReminderPreviewResult
}

/** Why a previewed slot would send nothing, in the order [ReminderPlanner.preview] checks. */
enum class NoReminderReason {
    /** The daily reminder is off. */
    ReminderOff,

    /** The streak saver is off (and no 7 PM daily reminder fills the Evening slot). */
    SaverOff,

    /** Something was read today: no reminder until tomorrow. */
    ReadToday,

    /** Nothing is unread. */
    NothingUnread,

    /** A saver slot with no streak to save. */
    NoStreak,
}
