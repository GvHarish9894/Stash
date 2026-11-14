package com.techgv.stash.core.data

import com.techgv.stash.core.domain.RecentSearches
import com.techgv.stash.core.model.RecentSearch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class StashRecentSearchRepositoryTest {
    @Test
    fun keepsFiveNewestFirstWithACaseInsensitiveRepeatReplacingTheOlder() = runTest {
        val data = TestData(this)
        var expected = emptyList<RecentSearch>()
        for (query in listOf("swift", "Kotlin", "room", "fts", "paging", "compose", "SWIFT")) {
            data.recentSearches.add(query)
            expected = RecentSearches.add(expected, query, data.clock.now)
            data.clock.advanceBy(1.minutes)
        }

        assertEquals(expected, data.recentSearches.observe().first())
        assertEquals(listOf("SWIFT", "compose", "paging", "fts", "room"), expected.map { it.query })
    }
}
