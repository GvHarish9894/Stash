package com.techgv.stash.core.data

import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.StreakEngine
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.domain.ZoneProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * The streak from stored days, `unreadZeroSince` and the unread count (decision D13). Reads are recorded by the command
 * bus for `MarkRead` and `OpenItem` (decision D21).
 */
internal class StashStreakRepository(
    private val database: StashDatabase,
    private val clock: Clock,
    private val zones: ZoneProvider,
    private val ticks: LocalTicks,
) : StreakRepository {
    // One read transaction per change, so the days, unreadZeroSince and the unread count always come from one state.
    override fun observeStreak(): Flow<StreakState> = combine(
        database.invalidationTracker.createFlow("streak_day", "app_state", "saved_item"),
        ticks.at(MIDNIGHT, StreakEngine.AT_RISK_FROM),
    ) { _, _ -> }
        .map {
            val now = clock.now()
            val zone = zones.zone()
            database.readTransaction { database.streakNow(now, zone).state }
        }
        .distinctUntilChanged()
}
