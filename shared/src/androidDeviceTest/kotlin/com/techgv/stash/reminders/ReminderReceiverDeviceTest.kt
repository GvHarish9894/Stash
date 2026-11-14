package com.techgv.stash.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.platform.AlarmNotificationScheduler
import com.techgv.stash.core.platform.ReminderReceiver
import com.techgv.stash.core.platform.ScheduledNotification
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The first test checks the fresh install's missing notification permission before it grants it.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReminderReceiverDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)

    // Whole milliseconds, the precision alarms carry; a fresh tag per test so delivery records never carry over.
    private val now = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
    private val tag = "daily-${now.toEpochMilliseconds()}"

    /** The broadcast [AlarmNotificationScheduler] arms for [tag], as the OS delivers it. */
    private fun alarm(fireAt: Instant, tag: String = this.tag) = Intent(context, ReminderReceiver::class.java)
        .setAction(ReminderReceiver.ACTION_REMIND)
        .setData(Uri.fromParts("stash-reminder", tag, null))
        .putExtra(ReminderReceiver.EXTRA_TITLE, "Your shelf is waiting")
        .putExtra(ReminderReceiver.EXTRA_BODY, "5 unread. “Room FTS” takes 6 min.")
        .putExtra(ReminderReceiver.EXTRA_ITEM_ID, "item-42")
        .putExtra(ReminderReceiver.EXTRA_FIRE_AT, fireAt.toEpochMilliseconds())

    private fun posted(tag: String = this.tag) = notifications.activeNotifications.filter { it.tag == tag }

    // The notification service posts and cancels asynchronously: wait for the state to settle before reading it.
    private fun <T> settled(read: () -> T, done: (T) -> Boolean): T {
        val end = SystemClock.elapsedRealtime() + 3_000
        while (SystemClock.elapsedRealtime() < end) read().let { if (done(it)) return it }.also { SystemClock.sleep(50) }
        return read()
    }

    private fun postedOnce(tag: String = this.tag) = settled({ posted(tag) }) { it.isNotEmpty() }.size

    /** Nothing posted for [tag], also after giving a post in flight time to land. */
    private fun nothingPosted(tag: String = this.tag): Boolean {
        SystemClock.sleep(500)
        return posted(tag).isEmpty()
    }

    private fun cancelAll() {
        notifications.cancelAll()
        settled({ notifications.activeNotifications }) { it.isEmpty() }
    }

    private fun notificationsDenied() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Grants the notification permission when missing; skips the test on devices that refuse adb grants (realme). */
    private fun grant() {
        if (!notificationsDenied()) return
        try {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        } catch (e: SecurityException) {
            // Checked below.
        }
        assumeTrue("this device refuses to grant POST_NOTIFICATIONS to the test app; grant it in Settings", !notificationsDenied())
    }

    // The real planner and scheduler around a 09:00 daily slot in 2030, so the real alarm never fires mid-test; the
    // scheduler and planner read [time].
    private val zone = TimeZone.UTC
    private val slotAt = LocalDateTime(2030, 1, 15, 9, 0).toInstant(zone)
    private val slotId = "2030-01-15-Daily"
    private var time = slotAt - 1.minutes
    private val scheduler = AlarmNotificationScheduler(context, clock { time })

    private suspend fun replan(readToday: Boolean = false, dailyReminder: Boolean = true) {
        val preferences = UserPreferences(dailyReminder = dailyReminder, reminderTime = ReminderTime.Morning)
        val plan = ReminderPlanner.plan(preferences, 0, readToday, 1, savedItem("item-42"), time, zone)
        scheduler.apply(plan.map { ScheduledNotification(it.id, it.fireAt, "Your shelf is waiting", "Body", it.itemId) })
    }

    /** What the OS does with the slot's alarm if it still holds it: delivers the broadcast to the receiver. */
    private fun osDeliversTheSlot() {
        scheduler.pendingAlarm(slotId)?.send()
    }

    @AfterTest
    fun clear() = runBlocking {
        cancelAll()
        // Two days after the 2030 slot nothing is in its delivery window, so this cancels every alarm and delivery record.
        AlarmNotificationScheduler(context, clock(slotAt + 2.days)).apply(emptyList())
    }

    @Test
    fun aPostsNothingWithoutPermissionThenTheTitleBodyAndReaderLinkOnceGranted() {
        // Only while the permission is missing, as on a fresh install: a phone may have it granted by hand.
        if (notificationsDenied()) {
            ReminderReceiver().onReceive(context, alarm(now, tag = "$tag-denied"))
            assertTrue(nothingPosted("$tag-denied"), "posted without permission")
        }
        grant()

        ReminderReceiver().onReceive(context, alarm(now))

        assertEquals(1, postedOnce())
        val notification = posted().single().notification
        assertEquals("Your shelf is waiting", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("5 unread. “Room FTS” takes 6 min.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(ReminderReceiver.CHANNEL_ID, notification.channelId)
        assertEquals("Reading reminders", notifications.getNotificationChannel(ReminderReceiver.CHANNEL_ID).name)
        // Pending intents are equal when their intents are: this is the `stash://reader/item-42` activity link.
        assertEquals(ReminderReceiver.readerIntent(context, "item-42"), notification.contentIntent)
    }

    @Test
    fun dropsAReminderThatArrivesMoreThan30MinutesLate() {
        grant()
        ReminderReceiver().onReceive(context, alarm(now - 31.minutes))
        assertTrue(nothingPosted(), "a reminder 31 minutes late is not posted")
    }

    @Test
    fun aReminderIsTimelyUpTo30MinutesLateAndNeverAfterTheMidnightThatFollowsIt() {
        val zone = TimeZone.of("Asia/Kolkata")
        val nine = LocalDateTime(2026, 11, 12, 9, 0).toInstant(zone)
        assertTrue(ReminderReceiver.isTimely(nine, nine, zone), "on time")
        assertTrue(ReminderReceiver.isTimely(nine, nine + 29.minutes, zone))
        assertFalse(ReminderReceiver.isTimely(nine, nine + 31.minutes, zone))
        val lastCall = LocalDateTime(2026, 11, 12, 23, 50).toInstant(zone)
        assertTrue(ReminderReceiver.isTimely(lastCall, lastCall + 9.minutes, zone), "23:59, still the same day")
        assertFalse(ReminderReceiver.isTimely(lastCall, lastCall + 15.minutes, zone), "00:05: the streak it talks about has ended")
    }

    @Test
    fun aReadJustAfterTheSlotCancelsItsReminder() = runBlocking {
        grant()
        replan()
        assertNotNull(scheduler.pendingAlarm(slotId), "precondition: the slot is armed")

        // The OS may still hold the alarm in its delivery window when the user reads.
        time = slotAt + 20.seconds
        replan(readToday = true)

        assertNull(scheduler.pendingAlarm(slotId), "the read cancelled it")
        osDeliversTheSlot()
        assertTrue(nothingPosted(slotId), "posted after the read")
    }

    @Test
    fun switchingRemindersOffJustAfterTheSlotCancelsIt() = runBlocking {
        grant()
        replan()
        time = slotAt + 20.seconds
        replan(dailyReminder = false)

        assertNull(scheduler.pendingAlarm(slotId), "switching off cancelled it")
        osDeliversTheSlot()
        assertTrue(nothingPosted(slotId), "posted after reminders were switched off")
    }

    @Test
    fun aReplanJustAfterTheSlotWhileStillUnreadPostsItOnce() = runBlocking {
        grant()
        replan()
        time = slotAt + 16.seconds
        replan()
        assertNotNull(scheduler.pendingAlarm(slotId), "the slot in its delivery window is still armed")

        osDeliversTheSlot()
        assertEquals(1, postedOnce(slotId))
        cancelAll()
        // The same alarm armed again by a later apply fires a second time: handled once only.
        osDeliversTheSlot()
        assertTrue(nothingPosted(slotId), "posted twice")
    }

    @Test
    fun aReplanAfterDeliveryDoesNotPostItAgain() = runBlocking {
        grant()
        replan()
        osDeliversTheSlot()
        assertEquals(1, postedOnce(slotId))
        cancelAll()

        time = slotAt + 16.seconds
        replan()
        osDeliversTheSlot()
        assertTrue(nothingPosted(slotId), "re-posted after delivery")
    }
}

/** A clock that reads [now] each time. */
fun clock(now: () -> Instant) = object : Clock {
    override fun now() = now()
}

fun clock(fixed: Instant) = clock { fixed }
