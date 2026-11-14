package com.techgv.stash.core.domain

import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.SavedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class AutoArchivePolicyTest {
    private val now = T0

    @Test
    fun boundaries() {
        data class Case(val name: String, val period: AutoArchivePeriod, val due: Boolean, val item: SavedItem)
        val cases = listOf(
            Case("exactly at 30 days", AutoArchivePeriod.Days30, true, item(readAt = now - 30.days)),
            Case("1 ms short of 30 days", AutoArchivePeriod.Days30, false, item(readAt = now - 30.days + 1.milliseconds)),
            Case("well past 30 days", AutoArchivePeriod.Days30, true, item(readAt = now - 400.days)),
            Case("exactly at 7 days", AutoArchivePeriod.Days7, true, item(readAt = now - 7.days)),
            Case("6 days with 7", AutoArchivePeriod.Days7, false, item(readAt = now - 6.days)),
            Case("exactly at 90 days", AutoArchivePeriod.Days90, true, item(readAt = now - 90.days)),
            Case("89 days with 90", AutoArchivePeriod.Days90, false, item(readAt = now - 89.days)),
            Case("off", AutoArchivePeriod.Off, false, item(readAt = now - 400.days)),
            Case("unread, added long ago", AutoArchivePeriod.Days7, false, item(addedAt = now - 400.days)),
            Case("already archived", AutoArchivePeriod.Days7, false, item(readAt = now - 40.days, archivedAt = now - 1.days)),
            Case("deleted", AutoArchivePeriod.Days7, false, item(readAt = now - 40.days, deletedAt = now - 1.days)),
        )
        for (case in cases) assertEquals(case.due, AutoArchivePolicy.isDue(case.item, case.period, now), case.name)
    }

    @Test
    fun cutoffIsNowMinusThePeriod() {
        assertNull(AutoArchivePolicy.cutoff(AutoArchivePeriod.Off, now))
        assertEquals(now - 7.days, AutoArchivePolicy.cutoff(AutoArchivePeriod.Days7, now))
        assertEquals(now - 30.days, AutoArchivePolicy.cutoff(AutoArchivePeriod.Days30, now))
        assertEquals(now - 90.days, AutoArchivePolicy.cutoff(AutoArchivePeriod.Days90, now))
    }
}
