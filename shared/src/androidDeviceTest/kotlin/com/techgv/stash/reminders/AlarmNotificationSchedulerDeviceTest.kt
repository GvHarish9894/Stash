package com.techgv.stash.reminders

import android.app.AlarmManager
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.AlarmNotificationScheduler
import com.techgv.stash.core.platform.ScheduledNotification
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class AlarmNotificationSchedulerDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler = AlarmNotificationScheduler(context)

    // Whole milliseconds, the precision the applied plan is stored with.
    private val now = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    private fun entry(id: String, at: Instant = now + 1.hours) =
        ScheduledNotification(id, at, "Title $id", "Body $id", ItemId("item-$id"))

    // Two days ahead nothing is still in its delivery window, so this cancels every alarm.
    @AfterTest
    fun cancelEverything() = runTest { AlarmNotificationScheduler(context, clock(now + 2.days)).apply(emptyList()) }

    @Test
    fun applyStoresThePlanAndSchedulesEveryEntry() = runTest {
        val plan = listOf(entry("daily"), entry("late", now + 12.hours))
        scheduler.apply(plan)
        assertEquals(plan, scheduler.applied())
        plan.forEach { assertNotNull(scheduler.pendingAlarm(it.id), it.id) }
    }

    @Test
    fun aSmallerPlanCancelsTheEntriesThatLeftIt() = runTest {
        scheduler.apply(listOf(entry("daily"), entry("evening"), entry("late")))
        scheduler.apply(listOf(entry("late")))
        assertNull(scheduler.pendingAlarm("daily"))
        assertNull(scheduler.pendingAlarm("evening"))
        assertNotNull(scheduler.pendingAlarm("late"))
        assertEquals(listOf(entry("late")), scheduler.applied())
    }

    @Test
    fun anEmptyPlanCancelsEverything() = runTest {
        scheduler.apply(listOf(entry("daily"), entry("late")))
        scheduler.apply(emptyList())
        assertNull(scheduler.pendingAlarm("daily"))
        assertNull(scheduler.pendingAlarm("late"))
        assertEquals(emptyList(), scheduler.applied())
    }

    @Test
    fun rearmSchedulesTheAppliedPlanAgainAfterTheOsDroppedIt() = runTest {
        // Its clock an hour ahead of real time, so the entry it sees as just passed is not really due during the test.
        val later = now + 1.hours
        val scheduler = AlarmNotificationScheduler(context, clock(later))
        val plan = listOf(entry("daily", later + 1.hours), entry("missed", later - 1.hours), entry("justPassed", later - 1.minutes))
        scheduler.apply(plan)
        // What a reboot does: the OS forgets every alarm and pending intent; the stored plan survives.
        val alarms = context.getSystemService(AlarmManager::class.java)
        plan.forEach { scheduler.pendingAlarm(it.id)?.let { pending -> alarms.cancel(pending); pending.cancel() } }
        assertNull(scheduler.pendingAlarm("daily"))

        scheduler.rearm()

        assertNotNull(scheduler.pendingAlarm("daily"))
        assertNotNull(scheduler.pendingAlarm("justPassed"), "the receiver would still post it")
        assertNull(scheduler.pendingAlarm("missed"), "an entry whose time passed while the phone was off is not re-armed")
        assertEquals(plan, scheduler.applied())
    }
}
