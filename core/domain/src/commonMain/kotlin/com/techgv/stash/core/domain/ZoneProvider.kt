package com.techgv.stash.core.domain

import kotlinx.datetime.TimeZone

/**
 * The user's current time zone, asked again on every use so a zone change applies at once. Data-layer classes take it
 * and a `kotlin.time.Clock` by constructor instead of calling `TimeZone.currentSystemDefault()` or `Clock.System`.
 */
fun interface ZoneProvider {
    fun zone(): TimeZone
}
