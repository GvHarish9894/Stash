package com.techgv.stash.core.data

import com.techgv.stash.core.domain.ZoneProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Ticks for values that depend on the local wall clock ("today", "after 21:30"): a flow from [at] emits on collection,
 * whenever the local time passes one of its times, and on every [poke], since the zone is only known by asking [zones]
 * again and a clock set by hand jumps past the scheduled wait. `StashSync.onForeground` and `StashSync.onTimeChanged`
 * poke; platforms call `onTimeChanged` on boot, on a time-zone change and on a manual clock change.
 */
internal class LocalTicks(private val clock: Clock, private val zones: ZoneProvider) {
    private val pokes = MutableStateFlow(0)

    fun poke() {
        pokes.update { it + 1 }
    }

    fun at(vararg times: LocalTime): Flow<Unit> = flow {
        emit(Unit)
        while (true) {
            val seen = pokes.value
            val next = nextBoundary(times)
            val poked = withTimeoutOrNull(next - clock.now()) { pokes.first { it != seen } } != null
            // A wait can end before the clock reaches the boundary (a clock set back, an early wake-up): then wait again.
            if (poked || clock.now() >= next) emit(Unit)
        }
    }

    private fun nextBoundary(times: Array<out LocalTime>): Instant {
        val now = clock.now()
        val zone = zones.zone()
        val today = now.toLocalDateTime(zone).date
        return listOf(today, today.plus(1, DateTimeUnit.DAY))
            .flatMap { date -> times.map { LocalDateTime(date, it).toInstant(zone) } }
            .filter { it > now }
            .min()
    }
}

internal val MIDNIGHT = LocalTime(0, 0)
