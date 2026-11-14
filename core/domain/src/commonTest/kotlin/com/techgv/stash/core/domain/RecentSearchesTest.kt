package com.techgv.stash.core.domain

import com.techgv.stash.core.model.RecentSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class RecentSearchesTest {
    private fun recent(vararg queries: String) = queries.mapIndexed { i, q -> RecentSearch(q, T0 - (i + 1).minutes) }

    @Test
    fun newestFirstAtMostFive() {
        val result = RecentSearches.add(recent("e", "d", "c", "b", "a"), "f", T0)
        assertEquals(listOf("f", "e", "d", "c", "b"), result.map { it.query })
        assertEquals(T0, result.first().usedAt)
    }

    @Test
    fun caseInsensitiveRepeatMovesToTheFrontWithTheNewSpelling() {
        val result = RecentSearches.add(recent("compose", "room", "typography"), "  ROOM ", T0)
        assertEquals(listOf("ROOM", "compose", "typography"), result.map { it.query })
    }

    @Test
    fun blankQueriesAreIgnored() {
        val existing = recent("compose")
        assertEquals(existing, RecentSearches.add(existing, "   ", T0))
    }
}
