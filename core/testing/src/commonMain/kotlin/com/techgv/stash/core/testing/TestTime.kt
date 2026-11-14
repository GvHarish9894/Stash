package com.techgv.stash.core.testing

import com.techgv.stash.core.domain.ZoneProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** The instant fixtures and [TestClock] start from. */
val FIXTURE_NOW: Instant = Instant.parse("2026-11-12T10:00:00Z")

/** A clock a test sets and advances. [instants] lets fakes re-emit when "now" changes. */
class TestClock(now: Instant = FIXTURE_NOW) : Clock {
    private val current = MutableStateFlow(now)
    val instants: StateFlow<Instant> = current

    var now: Instant
        get() = current.value
        set(value) {
            current.value = value
        }

    override fun now(): Instant = now

    fun advanceBy(duration: Duration) {
        now += duration
    }
}

class FixedZoneProvider(private val zone: TimeZone = TimeZone.UTC) : ZoneProvider {
    override fun zone(): TimeZone = zone
}
