package com.techgv.stash.core.domain

import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Moves read items to the archive once they have been read for the chosen period. Unread items never move. */
object AutoArchivePolicy {
    /** Items read at or before this instant are due; null when auto-archive is off. Lets the database query by `read_at`. */
    fun cutoff(period: AutoArchivePeriod, now: Instant): Instant? = period.days?.let { now - it.days }

    fun isDue(item: SavedItem, period: AutoArchivePeriod, now: Instant): Boolean {
        val cutoff = cutoff(period, now) ?: return false
        val readAt = item.readAt ?: return false
        return item.lifecycle == ItemLifecycle.Read && readAt <= cutoff
    }
}
