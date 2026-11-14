package com.techgv.stash.core.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.model.ItemId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Reminders as `AlarmManager` alarms that wake [ReminderReceiver], which posts the notification.
 *
 * Precision without the exact-alarm permission (the app does not request it): below Android 12, and whenever the OS
 * reports exact alarms allowed, `setExactAndAllowWhileIdle` (on time, also in Doze). Otherwise `setWindow` with the
 * shortest window Android 12+ accepts, 10 minutes: the reminder fires between its time and 10 minutes later, and Doze
 * may hold it until the next maintenance window. The alarm carries its planned time, and the receiver drops a reminder
 * that arrives more than [LATE_TOLERANCE] late or after the midnight that follows it, when its text is no longer true.
 *
 * `AlarmManager` cannot list pending alarms and drops all of them on reboot and app update, so the applied plan is kept
 * in preferences: [apply] cancels what left the plan from it, and [rearm] schedules it again after those events. The
 * planner keeps a slot for [LATE_TOLERANCE] after its time while it still applies, so [apply] schedules exactly the
 * plan: an entry whose time just passed is armed again (the OS fires it at once) unless the receiver recorded it as
 * delivered.
 */
class AlarmNotificationScheduler(private val context: Context, private val clock: Clock = Clock.System) : NotificationScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val store = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Schedules every undelivered entry again, even one the OS may still hold: an alarm cannot be read back to compare.
     */
    override suspend fun apply(plan: List<ScheduledNotification>) = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val now = clock.now()
            val delivered = deliveries(store)
            val ids = plan.map { it.id }.toSet()
            val removed = applied().filter { it.id !in ids }
            // Old and new are recorded before any alarm is armed, so a process that dies midway leaves no alarm that
            // the next apply cannot cancel.
            store.edit().putString(KEY_PLAN, encode(removed + plan)).commit()
            plan.filter { it.id !in delivered }.forEach(::schedule)
            removed.forEach(::cancel)
            // A delivery is remembered for a day after its slot: long enough to outlive any re-plan of that slot.
            val recent = delivered.filterValues { now - it <= DELIVERY_MEMORY }
            store.edit().putString(KEY_PLAN, encode(plan)).putStringSet(KEY_DELIVERED, encodeDeliveries(recent)).commit()
        }
        Unit
    }

    /**
     * Schedules the applied plan again after a reboot or app update: every entry still due, including one whose time
     * passed so recently that the receiver would still post it; older ones and delivered ones are skipped.
     */
    fun rearm() = synchronized(LOCK) {
        val now = clock.now()
        val delivered = deliveries(store)
        applied().filter { now - it.fireAt <= LATE_TOLERANCE && it.id !in delivered }.forEach(::schedule)
    }

    /** The plan of the last [apply]; empty when none was applied or the record is unreadable. */
    fun applied(): List<ScheduledNotification> = try {
        decode(store.getString(KEY_PLAN, null) ?: "[]")
    } catch (e: Exception) {
        emptyList()
    }

    /** The alarm's pending intent for [id], or null when none is scheduled. */
    fun pendingAlarm(id: String): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, alarmIntent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

    // One entry failing (the OS refused it) leaves the others scheduled.
    private fun schedule(notification: ScheduledNotification) {
        try {
            val intent = alarmIntent(notification.id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, notification.title)
                .putExtra(ReminderReceiver.EXTRA_BODY, notification.body)
                .putExtra(ReminderReceiver.EXTRA_ITEM_ID, notification.itemId.value)
                .putExtra(ReminderReceiver.EXTRA_FIRE_AT, notification.fireAt.toEpochMilliseconds())
            val operation = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = notification.fireAt.toEpochMilliseconds()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, operation)
            }
        } catch (e: RuntimeException) {
            // Never throws: the OS refused this one; the next apply tries again.
        }
    }

    private fun cancel(notification: ScheduledNotification) {
        try {
            pendingAlarm(notification.id)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        } catch (e: RuntimeException) {
            // Never throws.
        }
    }

    // The id is in the data URI so each entry gets its own pending intent (extras do not tell intents apart).
    private fun alarmIntent(id: String) = Intent(context, ReminderReceiver::class.java)
        .setAction(ReminderReceiver.ACTION_REMIND)
        .setData(Uri.fromParts(ALARM_SCHEME, id, null))

    companion object {
        /** How late a reminder may still be posted: as long as the planner keeps its slot. */
        val LATE_TOLERANCE = ReminderPlanner.DELIVERY_GRACE

        private const val PREFERENCES = "stash_reminders"
        private const val KEY_PLAN = "applied_plan"
        private const val KEY_DELIVERED = "delivered"
        private const val ALARM_SCHEME = "stash-reminder"
        private const val WINDOW_MILLIS = 10 * 60 * 1000L

        // Guards the plan and the delivery record between apply, rearm and the receiver (all in this process).
        private val LOCK = Any()

        private val DELIVERY_MEMORY = 1.days

        /** Delivered entries: id to planned time, stored as "millis:id". */
        private fun deliveries(store: SharedPreferences): Map<String, Instant> =
            store.getStringSet(KEY_DELIVERED, null).orEmpty().mapNotNull { entry ->
                val millis = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                entry.substringAfter(':') to Instant.fromEpochMilliseconds(millis)
            }.toMap()

        private fun encodeDeliveries(deliveries: Map<String, Instant>): Set<String> =
            deliveries.map { (id, at) -> "${at.toEpochMilliseconds()}:$id" }.toSet()

        /**
         * Records that the alarm for [id], planned for [fireAt], reached the receiver; false when it already had, so a
         * reminder is handled once even when a re-plan armed it again.
         */
        internal fun recordDelivery(context: Context, id: String, fireAt: Instant): Boolean = synchronized(LOCK) {
            val store = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val delivered = deliveries(store)
            if (id in delivered) return false
            store.edit().putStringSet(KEY_DELIVERED, encodeDeliveries(delivered + (id to fireAt))).commit()
        }

        private fun encode(plan: List<ScheduledNotification>): String = JSONArray(
            plan.map {
                JSONObject()
                    .put("id", it.id)
                    .put("at", it.fireAt.toEpochMilliseconds())
                    .put("title", it.title)
                    .put("body", it.body)
                    .put("item", it.itemId.value)
            },
        ).toString()

        private fun decode(json: String): List<ScheduledNotification> {
            val array = JSONArray(json)
            return List(array.length()) { index ->
                val entry = array.getJSONObject(index)
                ScheduledNotification(
                    id = entry.getString("id"),
                    fireAt = Instant.fromEpochMilliseconds(entry.getLong("at")),
                    title = entry.getString("title"),
                    body = entry.getString("body"),
                    itemId = ItemId(entry.getString("item")),
                )
            }
        }
    }
}
