package com.techgv.stash.core.domain

import com.techgv.stash.core.model.RecentSearch
import kotlin.time.Instant

object RecentSearches {
    const val MAX = 5

    /** Adds [query] at the front: at most 5, newest first, a case-insensitive repeat replaces the older entry. */
    fun add(recent: List<RecentSearch>, query: String, at: Instant): List<RecentSearch> {
        val text = query.trim()
        if (text.isEmpty()) return recent
        val others = recent.filterNot { it.query.equals(text, ignoreCase = true) }.sortedByDescending { it.usedAt }
        return (listOf(RecentSearch(text, at)) + others).take(MAX)
    }
}
