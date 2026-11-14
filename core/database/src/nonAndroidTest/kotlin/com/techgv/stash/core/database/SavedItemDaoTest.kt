package com.techgv.stash.core.database

import com.techgv.stash.core.domain.AutoArchivePolicy
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** One behaviour of SavedItemDao and its neighbours per test, beyond what the parity and scale tests pin. */
class SavedItemDaoTest {
    private val database = inMemoryDatabase()
    private val dao = database.savedItemDao()

    @AfterTest
    fun close() = database.close()

    @Test
    fun updatingAnItemKeepsItsCachedArticle() = runTest {
        database.insertItems(listOf(savedItem("a")))
        database.articleContentDao().upsert(ArticleContentEntity("a", "<p>hi</p>", null, 2, FIXTURE_NOW, 1, 9, FIXTURE_NOW))

        dao.upsert(listOf(dao.get("a")!!.copy(readAt = FIXTURE_NOW)))

        assertNotNull(database.articleContentDao().get("a"), "an update is not a delete and re-insert, so nothing cascades")
    }

    @Test
    fun theArchivedChipIsHiddenWhenEveryHitIsArchived() = runTest {
        val items = listOf(
            savedItem("a", ItemLifecycle.Archived, title = "swift"),
            savedItem("b", ItemLifecycle.Archived, title = "swift", siteName = "x.com"),
        )
        database.insertItems(items)
        val match = ftsMatch(SearchMatcher.tokens("swift"))!!

        val chips = searchChips(dao.observeSearchSources(match).first())

        assertEquals(ShelfQuery.search(items, "swift", SearchFilter.All).chips, chips)
        assertEquals(null, chips.archived)
    }

    @Test
    fun autoArchiveIncludesAnItemReadExactlyAtTheCutoff() = runTest {
        val cutoff = AutoArchivePolicy.cutoff(AutoArchivePeriod.Days30, FIXTURE_NOW)!!
        val items = listOf(
            savedItem("at", addedAt = FIXTURE_NOW - 40.days, readAt = cutoff),
            savedItem("after", addedAt = FIXTURE_NOW - 40.days, readAt = cutoff + 1.days),
        )
        database.insertItems(items)

        val due = items.filter { AutoArchivePolicy.isDue(it, AutoArchivePeriod.Days30, FIXTURE_NOW) }.map { it.id.value }
        assertEquals(listOf("at"), due)
        assertEquals(1, dao.countAutoArchiveDue(cutoff))
        assertEquals(due, dao.autoArchiveCandidates(cutoff, "", 10).map { it.id })
    }

    @Test
    fun aReshareFindsADeletedItemByItsNormalizedUrl() = runTest {
        val deleted = savedItem("a", ItemLifecycle.Deleted)
        database.insertItems(listOf(deleted))

        assertEquals("a", dao.getByNormalizedUrl(deleted.normalizedUrl)?.id)
    }

    @Test
    fun aBatchUpsertOutsideATransactionIsAllOrNothing() = runTest {
        database.insertItems(listOf(savedItem("a")))

        assertFailsWith<NormalizedUrlTakenException> {
            dao.upsert(listOf(savedItem("c").toEntity(), savedItem("d", normalizedUrl = savedItem("a").normalizedUrl).toEntity()))
        }

        assertEquals(null, dao.get("c"))
    }

    @Test
    fun theClearReadKeysetSkipsNothingAndRepeatsNothing() = runTest {
        val read = List(20) { savedItem("r" + "$it".padStart(2, '0'), ItemLifecycle.Read) }
        database.insertItems(read)
        val ids = ArrayList<String>()
        var after = ""
        while (true) {
            val chunk = dao.clearReadCandidates(after, 7)
            if (chunk.isEmpty()) break
            ids += chunk.map { it.id }
            after = chunk.last().id
        }

        assertEquals(read.map { it.id.value }, ids)
    }

    @Test
    fun theShortestUnreadIgnoresReadItems() = runTest {
        database.insertItems(listOf(savedItem("quick-but-read", ItemLifecycle.Read, readingMinutes = 1), savedItem("long", readingMinutes = 20)))

        assertEquals("long", dao.shortestUnread()?.id)
    }

    @Test
    fun pendingItemsComeNeverAttemptedFirstThenLeastRecentlyAttempted() = runTest {
        database.insertItems(
            listOf(
                savedItem("old-fresh", addedAt = FIXTURE_NOW - 2.days, fetchStatus = FetchStatus.Pending),
                savedItem("new-fresh", addedAt = FIXTURE_NOW, fetchStatus = FetchStatus.Pending),
                savedItem("tried-early", addedAt = FIXTURE_NOW, fetchStatus = FetchStatus.Pending),
                savedItem("tried-late", addedAt = FIXTURE_NOW, fetchStatus = FetchStatus.Pending),
                savedItem("fetched", fetchStatus = FetchStatus.Fetched),
                savedItem("removed", ItemLifecycle.Deleted, fetchStatus = FetchStatus.Pending),
            ),
        )
        database.metadataAttemptDao().upsert(MetadataAttemptEntity("tried-late", 1, FIXTURE_NOW + 5.minutes))
        database.metadataAttemptDao().upsert(MetadataAttemptEntity("tried-early", 0, FIXTURE_NOW + 1.minutes))

        assertEquals(listOf("new-fresh", "old-fresh", "tried-early", "tried-late"), database.metadataAttemptDao().pendingIds(10))
        assertEquals(listOf("new-fresh", "old-fresh"), database.metadataAttemptDao().pendingIds(2))
    }
}
