package com.techgv.stash.core.database

import androidx.room.useWriterConnection
import com.techgv.stash.core.domain.RecentSearches
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.OperationPayload
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.RecentSearch
import com.techgv.stash.core.model.ReminderCopy
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.StreakDay
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class HousekeepingTest {
    private val database = inMemoryDatabase()

    @AfterTest
    fun close() = database.close()

    private suspend fun articleBytes(): Long = database.useWriterConnection { connection ->
        connection.usePrepared("SELECT COALESCE(SUM(html_bytes), 0) FROM article_content") { it.step(); it.getLong(0) }
    }

    @Test
    fun operationsArePrunedWhenOlderThanThirtyDays() = runTest {
        val dao = database.operationDao()
        fun op(id: String, at: Instant) =
            Operation(id, ItemId("a"), OperationType.RetryMetadata, OperationPayload.Metadata(FetchStatus.Failed, FetchStatus.Pending), at).toEntity()
        dao.insert(listOf(op("exactly-30-days", FIXTURE_NOW - 30.days), op("older", FIXTURE_NOW - 30.days - 1.milliseconds), op("new", FIXTURE_NOW)))

        assertEquals(1, dao.prune(now = FIXTURE_NOW))

        assertNull(dao.get("older"))
        assertEquals("exactly-30-days", dao.get("exactly-30-days")?.opId)
        assertEquals("new", dao.get("new")?.opId)
    }

    @Test
    fun recentSearchesKeepFiveAndDeDupeIgnoringCase() = runTest {
        val dao = database.recentSearchDao()
        val queries = listOf("Swift", "kotlin", "Room", "fts", "paging", "compose", "SWIFT", "  ", "Café", "CAFÉ")
        var expected = emptyList<RecentSearch>()
        queries.forEachIndexed { i, query ->
            val at = FIXTURE_NOW + i.minutes
            dao.add(query, at)
            expected = RecentSearches.add(expected, query, at)
            assertEquals(expected, dao.observe().first().map { it.toModel() }, "after \"$query\"")
        }
        assertEquals(listOf("CAFÉ", "SWIFT", "compose", "paging", "fts"), expected.map { it.query })
    }

    @Test
    fun aNewRecentSearchComesFirstEvenWhenTheClockWentBackwards() = runTest {
        val dao = database.recentSearchDao()
        dao.add("before", FIXTURE_NOW + 10.minutes)
        dao.add("later", FIXTURE_NOW + 11.minutes)

        dao.add("skewed", FIXTURE_NOW) // the clock was set back

        val recent = dao.observe().first().map { it.toModel() }
        assertEquals(listOf("skewed", "later", "before"), recent.map { it.query })
        assertEquals(FIXTURE_NOW + 11.minutes + 1.milliseconds, recent.first().usedAt)
    }

    @Test
    fun articlesAreEvictedLeastRecentlyOpenedFirst() = runTest {
        database.insertItems(listOf(savedItem("a"), savedItem("b"), savedItem("c")))
        val dao = database.articleContentDao()
        fun article(id: String, html: String, openedAt: Instant) =
            ArticleContentEntity(id, html, null, html.length, FIXTURE_NOW, 1, html.encodeToByteArray().size.toLong(), openedAt)
        dao.upsert(article("a", "x".repeat(100), FIXTURE_NOW))
        dao.upsert(article("b", "é".repeat(100), FIXTURE_NOW + 1.minutes)) // 2 bytes each in UTF-8
        dao.upsert(article("c", "y".repeat(100), FIXTURE_NOW + 2.minutes))
        assertEquals(400, articleBytes())

        dao.touch("a", FIXTURE_NOW + 3.minutes) // now b is the least recently opened
        assertEquals(0, dao.evictBeyond(maxBytes = 400))
        assertEquals(1, dao.evictBeyond(maxBytes = 399))

        assertNull(dao.get("b"))
        assertEquals(200, articleBytes())
        assertEquals(article("a", "x".repeat(100), FIXTURE_NOW + 3.minutes), dao.get("a"))
        assertEquals(1, dao.evictBeyond(maxBytes = 150))
        assertNull(dao.get("c"))
    }

    @Test
    fun articlesOpenedAtTheSameMomentAreEvictedLowestIdFirst() = runTest {
        database.insertItems(listOf(savedItem("a"), savedItem("b")))
        val dao = database.articleContentDao()
        listOf("a", "b").forEach { dao.upsert(ArticleContentEntity(it, "x".repeat(100), null, 100, FIXTURE_NOW, 1, 100, FIXTURE_NOW)) }

        assertEquals(1, dao.evictBeyond(maxBytes = 150))

        assertNull(dao.get("a"))
        assertNotNull(dao.get("b"))
    }

    @Test
    fun articlesAndAttemptsFollowTheirItemOnPhysicalDelete() = runTest {
        database.insertItems(listOf(savedItem("a")))
        database.articleContentDao().upsert(ArticleContentEntity("a", "<p>hi</p>", "Standfirst", 2, FIXTURE_NOW, 1, 9, FIXTURE_NOW))
        database.metadataAttemptDao().upsert(MetadataAttemptEntity("a", 2, FIXTURE_NOW))

        database.useWriterConnection { connection -> connection.usePrepared("DELETE FROM saved_item WHERE id = 'a'") { it.step() } }

        assertNull(database.articleContentDao().get("a"))
        assertEquals(0, database.metadataAttemptDao().failedAttempts("a"))
    }

    @Test
    fun plannedNotificationsAreReplacedAsAWhole() = runTest {
        val dao = database.plannedNotificationDao()
        fun planned(level: ReminderLevel, minutes: Int?) =
            PlannedNotification("2026-11-12-${level.name}", FIXTURE_NOW + level.ordinal.days, level, ItemId("a"), ReminderCopy(3, 7, "Title", minutes))
        dao.replaceAll(ReminderLevel.entries.map { planned(it, 5) }.map { it.toEntity() })
        val next = listOf(planned(ReminderLevel.Late, null), planned(ReminderLevel.Daily, 2))

        dao.replaceAll(next.map { it.toEntity() })

        assertEquals(next.sortedBy { it.fireAt }, dao.all().map { it.toModel() })
    }

    @Test
    fun streakDaysUpsertAndReadARange() = runTest {
        val dao = database.streakDayDao()
        val nov10 = LocalDate(2026, 11, 10)
        val nov11 = LocalDate(2026, 11, 11)
        val nov12 = LocalDate(2026, 11, 12)
        dao.upsert(listOf(StreakDay(nov10, FIXTURE_NOW - 2.days), StreakDay(nov11, null), StreakDay(nov12, null)).map { it.toEntity() })
        dao.upsert(listOf(StreakDay(nov12, FIXTURE_NOW).toEntity())) // a read replaces the rest row

        assertEquals(
            listOf(StreakDay(nov11, null), StreakDay(nov12, FIXTURE_NOW)),
            dao.between(nov11, nov12).map { it.toModel() },
        )
        assertEquals(3, dao.between(LocalDate(2026, 1, 1), LocalDate(2026, 12, 31)).size)
        assertEquals(emptyList(), dao.between(LocalDate(2026, 11, 13), LocalDate(2026, 12, 1)))
    }

    @Test
    fun appStateStartsEmptyAndHoldsUnreadZeroSince() = runTest {
        val dao = database.appStateDao()
        assertNull(dao.unreadZeroSince())

        dao.setUnreadZeroSince(FIXTURE_NOW)
        assertEquals(FIXTURE_NOW, dao.observeUnreadZeroSince().first())

        dao.setUnreadZeroSince(null)
        assertNull(dao.unreadZeroSince())
    }
}
