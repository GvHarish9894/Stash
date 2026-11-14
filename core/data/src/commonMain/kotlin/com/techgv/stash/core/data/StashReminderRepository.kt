package com.techgv.stash.core.data

import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.toEntity
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.domain.ReminderRepository
import com.techgv.stash.core.domain.ReminderTextRenderer
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.platform.ScheduledNotification
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

internal class StashReminderRepository(
    private val database: StashDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zones: ZoneProvider,
    private val renderer: ReminderTextRenderer,
    private val scheduler: NotificationScheduler,
) : ReminderRepository {
    override suspend fun replan(): List<PlannedNotification> {
        val preferences = settings.preferences.first()
        val now = clock.now()
        val zone = zones.zone()
        val plan = database.readTransaction {
            val streak = database.streakNow(now, zone)
            val shortest = database.savedItemDao().shortestUnread()?.toModel()
            ReminderPlanner.plan(preferences, streak.state.count, streak.state.readToday, streak.unreadCount, shortest, now, zone)
        }
        scheduler.apply(
            plan.map { planned ->
                val text = renderer.render(planned)
                ScheduledNotification(planned.id, planned.fireAt, text.title, text.body, planned.itemId)
            },
        )
        // Stored after it was applied, so the table never claims a plan the scheduler did not receive.
        database.plannedNotificationDao().replaceAll(plan.map { it.toEntity() })
        return plan
    }
}
