package com.techgv.stash.core.database

import com.techgv.stash.core.domain.AutoArchivePolicy
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.ShelfQuerySpec
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.testing.FIXTURE_NOW
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

/** Decision D4: the SQL lists, counts and candidates equal `ShelfQuery` / `SearchMatcher` over the same items. */
class ShelfParityTest {
    private val items = ParityFixture.items
    private val database = inMemoryDatabase()
    private val dao = database.savedItemDao()

    @AfterTest
    fun close() = database.close()

    private fun cardIds(rows: List<ShelfRow>) = rows.filterIsInstance<ShelfRow.Card>().map { it.item.id.value }

    @Test
    fun shelfMatchesTheDomainForEveryFilterSortAndQuery() = runTest(timeout = 5.minutes) {
        database.insertItems(items)
        val chips = chipCounts(dao.observeShelfCounts().first(), dao.observeShelfSources().first())
        assertEquals(ShelfQuery.chipCounts(items.filter { it.isOnShelf }), chips)
        assertTrue(chips.more.isNotEmpty() && chips.sources.size == ShelfQuery.MAX_SOURCE_CHIPS, "fixture spans the More list")

        val filters = listOf(ShelfFilter.All, ShelfFilter.Unread) + ParityFixture.sourceKeys.map { ShelfFilter.Source(it) }
        var compared = 0
        var nonEmpty = 0
        for (query in ParityFixture.queries) {
            val match = ftsMatch(SearchMatcher.tokens(query))
            for (filter in filters) {
                for (sort in ShelfSort.entries) {
                    val spec = ShelfQuerySpec(filter, sort, searchText = query)
                    val expected = ShelfQuery.shelf(items, spec, FIXTURE_NOW, TimeZone.UTC)
                    val applied = ShelfQuery.effectiveFilter(filter, chips)
                    assertEquals(expected.filter, applied)
                    val actual = dao.shelf(applied, sort, match).loadAllIds()
                    assertEquals(cardIds(expected.rows), actual, "shelf $filter $sort \"$query\"")
                    compared++
                    if (actual.isNotEmpty()) nonEmpty++
                }
            }
        }
        println("Shelf parity: $compared filter × sort × query combinations, $nonEmpty non-empty")
        assertTrue(nonEmpty > compared / 4, "most combinations must return rows, not compare two empty lists")
    }

    @Test
    fun archiveMatchesTheDomainForEveryQuery() = runTest(timeout = 5.minutes) {
        database.insertItems(items)
        assertEquals(items.count { it.lifecycle == ItemLifecycle.Archived }, dao.observeArchiveCount().first())
        for (query in ParityFixture.queries) {
            val expected = cardIds(ShelfQuery.archive(items, query, FIXTURE_NOW, TimeZone.UTC))
            assertEquals(expected, dao.archive(ftsMatch(SearchMatcher.tokens(query))).loadAllIds(), "archive \"$query\"")
        }
    }

    @Test
    fun searchMatchesTheDomainForEveryQueryAndChip() = runTest(timeout = 5.minutes) {
        database.insertItems(items)
        val filters = listOf(SearchFilter.All, SearchFilter.Archived) + ParityFixture.sourceKeys.map { SearchFilter.Source(it) }
        for (query in ParityFixture.queries) {
            val match = ftsMatch(SearchMatcher.tokens(query))
            for (filter in filters) {
                val expected = ShelfQuery.search(items, query, filter)
                if (match == null) {
                    // No tokens: the data layer issues no query and shows no results, like the domain.
                    assertTrue(expected.shelf.isEmpty() && expected.archive.isEmpty() && expected.chips.all == 0)
                    continue
                }
                val chips = searchChips(dao.observeSearchSources(match).first())
                assertEquals(expected.chips, chips, "chips \"$query\"")
                val applied = ShelfQuery.effectiveSearchFilter(filter, chips)
                assertEquals(expected.filter, applied)
                val sourceKey = (applied as? SearchFilter.Source)?.key
                val shelf = if (applied == SearchFilter.Archived) {
                    emptyList()
                } else {
                    dao.shelf(sourceKey?.let { ShelfFilter.Source(it) } ?: ShelfFilter.All, ShelfSort.Newest, match).loadAllIds()
                }
                assertEquals(expected.shelf.map { it.item.id.value }, shelf, "shelf hits $filter \"$query\"")
                assertEquals(expected.archive.map { it.item.id.value }, dao.archiveSearch(match, sourceKey).loadAllIds(), "archive hits $filter \"$query\"")
            }
        }
        // Guard against a vacuous pass: these find something, the infixes and unknown words find nothing.
        val hits = ParityFixture.queries.associateWith { ShelfQuery.search(items, it, SearchFilter.All).chips.all }
        val expectedHits = listOf("swi", "swift ui", "ui swift", "medium.com", "c++", "or", "near", "éco", "οδος", "прив", "हिं", "தமிழ்", "東京")
        assertTrue(expectedHits.all { hits.getValue(it) > 0 }, hits.toString())
        assertTrue(listOf("ift", "ecole", "天気", "zzz").all { hits.getValue(it) == 0 }, hits.toString())
    }

    @Test
    fun lookupsAndCandidatesMatchTheDomain() = runTest {
        database.insertItems(items)
        val unread = items.filter { it.lifecycle == ItemLifecycle.Unread }
        assertEquals(ReminderPlanner.shortestUnread(items)?.id?.value, dao.shortestUnread()?.id)
        assertEquals(
            unread.sortedWith(ShelfQuery.comparator(ShelfSort.Newest, emptyMap())).take(5).map { it.id.value },
            dao.newestUnread(5).map { it.id },
        )
        assertEquals(unread.size, dao.unreadCount())

        for (period in AutoArchivePeriod.entries) {
            val cutoff = AutoArchivePolicy.cutoff(period, FIXTURE_NOW)
            val due = items.filter { AutoArchivePolicy.isDue(it, period, FIXTURE_NOW) }.map { it.id.value }.sorted()
            if (cutoff == null) {
                assertTrue(due.isEmpty())
                continue
            }
            assertEquals(due.size, dao.countAutoArchiveDue(cutoff), "count $period")
            assertEquals(due, chunked { after -> dao.autoArchiveCandidates(cutoff, after, 7) }, "candidates $period")
        }
        val read = items.filter { it.lifecycle == ItemLifecycle.Read }.map { it.id.value }.sorted()
        assertEquals(read, chunked { after -> dao.clearReadCandidates(after, 7) })
        assertNull(dao.get("missing"))
    }

    /** Reads keyset chunks until one comes back empty, the way the data layer walks bulk-command candidates. */
    private suspend fun chunked(next: suspend (afterId: String) -> List<SavedItemEntity>): List<String> {
        val ids = ArrayList<String>()
        var after = ""
        while (true) {
            val chunk = next(after)
            if (chunk.isEmpty()) return ids
            ids += chunk.map { it.id }
            after = chunk.last().id
        }
    }
}
