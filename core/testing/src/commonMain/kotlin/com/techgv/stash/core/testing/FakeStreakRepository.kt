package com.techgv.stash.core.testing

import com.techgv.stash.core.domain.StreakEngine
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.StreakDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Instant

/**
 * [StreakEngine.state] over [days] and [unreadZeroSince], which [FakeCommandBus] keeps up to date like the data layer
 * (decisions D13, D21), with the unread count and "now" from [items]. Set [scripted] to emit a fixed state instead.
 */
class FakeStreakRepository(private val items: FakeItemRepository) : StreakRepository {
    val days = MutableStateFlow<List<StreakDay>>(emptyList())
    val unreadZeroSince = MutableStateFlow<Instant?>(null)
    val scripted = MutableStateFlow<StreakState?>(null)

    override fun observeStreak(): Flow<StreakState> =
        combine(days, unreadZeroSince, items.items, items.clock.instants, scripted) { days, since, list, now, scripted ->
            scripted ?: StreakEngine.state(
                days,
                since?.let { minOf(it, now) },
                list.count { it.lifecycle == ItemLifecycle.Unread },
                now,
                items.zones.zone(),
            )
        }.distinctUntilChanged()
}
