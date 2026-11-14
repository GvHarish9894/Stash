package com.techgv.stash.core.database

import androidx.paging.PagingSource
import com.techgv.stash.core.domain.AutoArchivePolicy
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.ShelfQuerySpec
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.SCALE_ITEM_COUNT
import com.techgv.stash.core.testing.SCALE_ZEBRACORN_COUNT
import com.techgv.stash.core.testing.scaleItems
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/**
 * The scale target: 50,000 items ([scaleItems]) plus a realistic operation log, streak, article cache and metadata
 * attempts. Counts and first pages equal the domain's answer over the same items, a page materialises only its rows,
 * every query the app runs is explained (no full scan of `saved_item`, and other tables are scanned only where the
 * table is small by construction) and timed. Timings are printed for information; only a very generous bound is asserted.
 */
class ScaleTest {
    private val driver = RecordingDriver()
    private val database = recordingInMemoryDatabase(driver)
    private val dao = database.savedItemDao()
    private val report = StringBuilder()

    @AfterTest
    fun close() {
        println(report)
        database.close()
    }

    @Test
    fun fiftyThousandItems() = runTest(timeout = 15.minutes) {
        val items = scaleItems()
        assertEquals(SCALE_ZEBRACORN_COUNT, items.count { "zebracorn" in it.title })
        val onShelf = items.filter { it.isOnShelf }
        timed("insert $SCALE_ITEM_COUNT items in transactions of 500", bound = 5.minutes) { database.insertItems(items) }
        seedOtherTables(items.filter { it.fetchStatus == FetchStatus.Pending }.map { it.id.value })

        val chips = timed("chip counts") { chipCounts(dao.observeShelfCounts().first(), dao.observeShelfSources().first()) }
        assertEquals(ShelfQuery.chipCounts(onShelf), chips)
        assertEquals(items.count { it.lifecycle == ItemLifecycle.Archived }, timed("archive count") { dao.observeArchiveCount().first() })

        val topSource = chips.sources.first().key
        for (filter in listOf(ShelfFilter.All, ShelfFilter.Unread, ShelfFilter.Source(topSource))) {
            for (sort in ShelfSort.entries) {
                for (text in listOf("", "sw")) {
                    val expected = cardIds(ShelfQuery.shelf(items, ShelfQuerySpec(filter, sort, searchText = text), FIXTURE_NOW, TimeZone.UTC).rows)
                    val source = dao.shelf(filter, sort, ftsMatch(SearchMatcher.tokens(text)))
                    assertFirstPages("shelf $filter $sort \"$text\"", expected, source)
                }
            }
        }

        val archived = cardIds(ShelfQuery.archive(items, "", FIXTURE_NOW, TimeZone.UTC))
        assertFirstPages("archive", archived, dao.archive(null))
        assertFirstPages("archive \"zebra\"", cardIds(ShelfQuery.archive(items, "zebra", FIXTURE_NOW, TimeZone.UTC)), dao.archive(ftsMatch(listOf("zebra"))))

        // Search as you type: every prefix of "compose", then two and three words. Each is what one debounced query runs.
        val typed = "compose".indices.map { "compose".substring(0, it + 1) } + listOf("zebracorn", "news digest", "slow reading library")
        for (text in typed) {
            val match = checkNotNull(ftsMatch(SearchMatcher.tokens(text)))
            val expected = ShelfQuery.search(items, text, SearchFilter.All)
            assertEquals(expected.chips, timed("search chips \"$text\"") { searchChips(dao.observeSearchSources(match).first()) })
            assertFirstPages("shelf search \"$text\"", expected.shelf.map { it.item.id.value }, dao.shelf(ShelfFilter.All, ShelfSort.Newest, match))
            assertFirstPages("archive search \"$text\"", expected.archive.map { it.item.id.value }, dao.archiveSearch(match, null))
        }
        assertTrue(ShelfQuery.search(items, "zebracorn", SearchFilter.All).chips.all > 0)

        // Room pages by LIMIT / OFFSET: a page far down walks the rows before it.
        val shelfOrder = cardIds(ShelfQuery.shelf(items, ShelfQuerySpec(ShelfFilter.All, ShelfSort.Newest), FIXTURE_NOW, TimeZone.UTC).rows)
        for (offset in listOf(3_000, 15_000, shelfOrder.size - PAGE_SIZE)) {
            val page = timed("shelf All Newest: page at offset $offset") { dao.shelf(ShelfFilter.All, ShelfSort.Newest, null).page(offset) }
            assertEquals(shelfOrder.drop(offset).take(PAGE_SIZE), page.data.map { it.id }, "page at $offset")
        }
        val bySource = cardIds(ShelfQuery.shelf(items, ShelfQuerySpec(ShelfFilter.All, ShelfSort.BySource), FIXTURE_NOW, TimeZone.UTC).rows)
        val deepBySource = timed("shelf All BySource: page at offset 15000") { dao.shelf(ShelfFilter.All, ShelfSort.BySource, null).page(15_000) }
        assertEquals(bySource.drop(15_000).take(PAGE_SIZE), deepBySource.data.map { it.id })
        val archivePage = timed("archive: page at offset ${archived.size - PAGE_SIZE}") { dao.archive(null).page(archived.size - PAGE_SIZE) }
        assertEquals(archived.takeLast(PAGE_SIZE), archivePage.data.map { it.id })

        val unread = onShelf.filter { it.readAt == null }
        assertEquals(unread.size, timed("unread count") { dao.unreadCount() })
        assertEquals(ReminderPlanner.shortestUnread(items)?.id?.value, timed("shortest unread") { dao.shortestUnread()?.id })
        assertEquals(
            unread.sortedWith(ShelfQuery.comparator(ShelfSort.Newest, emptyMap())).take(3).map { it.id.value },
            timed("newest unread") { dao.newestUnread(3).map { it.id } },
        )

        val cutoff = checkNotNull(AutoArchivePolicy.cutoff(AutoArchivePeriod.Days30, FIXTURE_NOW))
        val due = items.filter { AutoArchivePolicy.isDue(it, AutoArchivePeriod.Days30, FIXTURE_NOW) }.map { it.id.value }.sorted()
        assertEquals(due.size, timed("auto-archive due count") { dao.countAutoArchiveDue(cutoff) })
        assertEquals(due.take(500), timed("auto-archive first chunk") { dao.autoArchiveCandidates(cutoff, "", 500).map { it.id } })
        val read = onShelf.filter { it.readAt != null }.map { it.id.value }.sorted()
        val walked = timed("clear-read: walk all ${read.size} candidates in chunks of 500") {
            buildList {
                var after = ""
                while (true) {
                    val chunk = dao.clearReadCandidates(after, 500).map { it.id }
                    if (chunk.isEmpty()) break
                    addAll(chunk)
                    after = chunk.last()
                }
            }
        }
        assertEquals(read, walked)

        explainEveryQuery(topSource, cutoff, items.first { it.isOnShelf }.toEntity())

        // The write path: every index on saved_item is maintained on each update.
        val updates = onShelf.take(5_000).map { it.copy(readAt = FIXTURE_NOW, updatedAt = FIXTURE_NOW).toEntity() }
        timed("update 5000 items in transactions of 500") { updates.chunked(500).forEach { database.writeTransaction { dao.upsert(it) } } }
    }

    /** The first page holds only its 30 rows, the count is right and the second page continues the order. */
    private suspend fun assertFirstPages(name: String, expected: List<String>, source: PagingSource<Int, SavedItemEntity>) {
        val first = timed("$name: first page") { source.firstPage(PAGE_SIZE) }
        assertEquals(expected.take(PAGE_SIZE), first.data.map { it.id }, name)
        assertEquals(expected.size, first.itemsBefore + first.data.size + first.itemsAfter, "$name: count")
        val nextKey = first.nextKey ?: return assertTrue(expected.size <= PAGE_SIZE, name)
        val second = source.page(nextKey)
        assertEquals(expected.drop(PAGE_SIZE).take(PAGE_SIZE), second.data.map { it.id }, "$name: second page")
    }

    private suspend fun PagingSource<Int, SavedItemEntity>.page(offset: Int) =
        load(PagingSource.LoadParams.Append(offset, PAGE_SIZE, false)) as PagingSource.LoadResult.Page

    /**
     * What a long-used install holds besides items: two years of streak days, 40 days of operation log (prune drops the
     * oldest 10), a full article cache and attempts for half of the pending items.
     */
    private suspend fun seedOtherTables(pendingIds: List<String>) {
        val today = LocalDate(2026, 11, 12)
        database.streakDayDao().upsert(List(730) { StreakDayEntity(today.minus(it, DateTimeUnit.DAY), FIXTURE_NOW - it.days) })
        database.writeTransaction {
            database.operationDao().insert(
                List(OPERATION_ROWS) { n ->
                    OperationEntity("op-$n", "item-$n", OperationType.MarkRead, "{}", FIXTURE_NOW - (40.days * n / OPERATION_ROWS))
                },
            )
        }
        val ids = dao.newestUnread(ARTICLE_ROWS).map { it.id }
        ids.forEachIndexed { n, id ->
            database.articleContentDao().upsert(ArticleContentEntity(id, "<p>${"text ".repeat(200)}</p>", null, 1_000, FIXTURE_NOW, 1, 400_000, FIXTURE_NOW - n.hours))
        }
        pendingIds.take(pendingIds.size / 2).forEach { database.metadataAttemptDao().upsert(MetadataAttemptEntity(it, 1, FIXTURE_NOW - 1.hours)) }
        database.plannedNotificationDao().replaceAll(
            List(7) { PlannedNotificationEntity("plan-$it", FIXTURE_NOW + it.days, ReminderLevel.Daily, ids.first(), 3, 10, "Title", 5) },
        )
        listOf("kotlin", "swift", "paging", "room", "compose").forEachIndexed { n, query ->
            database.recentSearchDao().add(query, FIXTURE_NOW - n.minutes)
        }
    }

    /**
     * Runs every query the app issues once while recording the SQL Room prepares, explains exactly that SQL and times
     * the call (median of three warm runs). No plan may scan `saved_item` without an index; a full scan of another
     * table is allowed only for the tables in [SMALL_TABLES].
     */
    private suspend fun explainEveryQuery(topSource: String, cutoff: Instant, onShelf: SavedItemEntity) {
        val match = checkNotNull(ftsMatch(listOf("sw")))
        val calls = buildMap<String, suspend () -> Unit> {
            for (sort in ShelfSort.entries) {
                put("shelf All $sort") { dao.shelf(ShelfFilter.All, sort, null).firstPage() }
                put("shelf Unread $sort") { dao.shelf(ShelfFilter.Unread, sort, null).firstPage() }
                put("shelf Source $sort") { dao.shelf(ShelfFilter.Source(topSource), sort, null).firstPage() }
                put("shelf All $sort + search") { dao.shelf(ShelfFilter.All, sort, match).firstPage() }
            }
            put("shelf search hits by source") { dao.shelf(ShelfFilter.Source(topSource), ShelfSort.Newest, match).firstPage() }
            put("archive") { dao.archive(null).firstPage() }
            put("archive + search") { dao.archive(match).firstPage() }
            put("archive search hits") { dao.archiveSearch(match, null).firstPage() }
            put("archive search hits by source") { dao.archiveSearch(match, topSource).firstPage() }
            put("shelf counts") { dao.observeShelfCounts().first() }
            put("shelf sources") { dao.observeShelfSources().first() }
            put("archive count") { dao.observeArchiveCount().first() }
            put("search sources") { dao.observeSearchSources(match).first() }
            // One SQL text: Room reuses the statement it prepared for the first, so they are recorded together.
            put("get and observe by id") {
                dao.get(onShelf.id)
                dao.observe(onShelf.id).first()
            }
            put("get by normalized url (save, re-share)") { dao.getByNormalizedUrl(onShelf.normalizedUrl) }
            put("upsert one item (every command)") { dao.upsert(listOf(onShelf)) }
            put("shortest unread (nudge, reminders)") { dao.shortestUnread() }
            put("newest unread (widget)") { dao.newestUnread(3) }
            put("unread count (every command, streak)") { dao.unreadCount() }
            put("auto-archive candidates") { dao.autoArchiveCandidates(cutoff, "", 500) }
            put("auto-archive due count") { dao.countAutoArchiveDue(cutoff) }
            put("clear-read candidates") { dao.clearReadCandidates("", 500) }
            put("failed metadata ids (pull to refresh)") { dao.idsWithFetchStatus(FetchStatus.Failed, 200) }
            put("pending metadata ids (catch-up)") { database.metadataAttemptDao().pendingIds(50) }
            put("failed attempts of an item") { database.metadataAttemptDao().failedAttempts(onShelf.id) }
            put("reset attempts of an item") { database.metadataAttemptDao().reset("x") }
            put("streak days") { database.streakDayDao().between(LocalDate(1970, 1, 1), LocalDate(9999, 12, 31)) }
            put("get and observe unread zero since") {
                database.appStateDao().unreadZeroSince()
                database.appStateDao().observeUnreadZeroSince().first()
            }
            put("operation by id (undo)") { database.operationDao().get("op-1") }
            put("prune operations (catch-up)") { database.operationDao().prune(FIXTURE_NOW - 1.days) }
            put("article by item (reader)") { database.articleContentDao().get(onShelf.id) }
            put("touch article (reader)") { database.articleContentDao().touch(onShelf.id, FIXTURE_NOW) }
            put("evict articles (catch-up)") { database.articleContentDao().evictBeyond(Long.MAX_VALUE) }
            put("planned notifications") { database.plannedNotificationDao().all() }
            put("recent searches") { database.recentSearchDao().observe().first() }
            put("add a recent search") { database.recentSearchDao().add("compose", FIXTURE_NOW) }
        }
        report.appendLine("EXPLAIN QUERY PLAN and median of 3 warm runs at $SCALE_ITEM_COUNT items:")
        for ((name, call) in calls) {
            driver.clear()
            call()
            val statements = driver.statements.filter(::isAppStatement).distinct()
            assertTrue(statements.isNotEmpty(), name)
            val took = List(3) { measureTime { call() } }.sorted()[1]
            report.appendLine("  ${took.inWholeMicroseconds / 1000.0} ms  $name")
            for (sql in statements) {
                val plan = database.explain(sql)
                report.appendLine("      $sql")
                plan.forEach { report.appendLine("          $it") }
                val scans = plan.map { it.trim() }.filter { it.startsWith("SCAN ") && !it.startsWith("SCAN (") && it != "SCAN CONSTANT ROW" }
                for (scan in scans) {
                    val table = scan.removePrefix("SCAN ").substringBefore(' ')
                    assertTrue(table == "saved_item_fts" || table in SMALL_TABLES, "$name scans $table: $plan")
                }
                expectedPlans[name]?.let { expected ->
                    for (step in expected) assertTrue(plan.any { step in it }, "$name: expected \"$step\" in $plan")
                    if (name in unsorted) assertTrue(plan.none { "TEMP B-TREE" in it }, "$name sorts: $plan")
                }
                if ("candidates" in name) {
                    assertEquals(listOf("SEARCH saved_item USING INDEX sqlite_autoindex_saved_item_1 (id>?)"), plan, "$name seeks by id")
                }
            }
        }
    }

    /** SQL of the DAOs, without Room's own bookkeeping (invalidation tracking, transactions). */
    private fun isAppStatement(sql: String): Boolean {
        val verb = sql.trimStart().substringBefore(' ').uppercase()
        return verb in setOf("SELECT", "UPDATE", "DELETE", "INSERT") && "room_table_modification_log" !in sql && "sqlite_master" !in sql
    }

    private fun cardIds(rows: List<ShelfRow>) = rows.filterIsInstance<ShelfRow.Card>().map { it.item.id.value }

    private suspend fun <T> timed(name: String, bound: Duration = 10.seconds, block: suspend () -> T): T {
        val (value, took) = measureTimedValue { block() }
        report.appendLine("  ${took.inWholeMilliseconds} ms  $name")
        assertTrue(took < bound, "$name took $took")
        return value
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val OPERATION_ROWS = 50_000
        const val ARTICLE_ROWS = 500

        /**
         * Tables a query may read whole: one reminder plan, at most `RecentSearches.MAX` searches, the article cache
         * (eviction ranks every cached article, bounded by its byte budget) and the operation log (pruning reads 30
         * days of commands; no query reads it by time otherwise, so it has no index on `created_at`).
         */
        const val SHELF_INDEX = "index_saved_item_deleted_at_archived_at_added_at_id"
        const val SOURCE_INDEX = "index_saved_item_source_key_deleted_at_archived_at_added_at_id"
        const val READ_INDEX = "index_saved_item_deleted_at_archived_at_read_at"

        /** What each saved_item index is for: these calls must read it (a later index edit that loses it fails here). */
        val expectedPlans = buildMap {
            for (sort in listOf("Newest", "Oldest")) {
                put("shelf All $sort", listOf("USING INDEX $SHELF_INDEX"))
                put("shelf Unread $sort", listOf("USING INDEX $SHELF_INDEX"))
                put("shelf Source $sort", listOf("USING INDEX $SOURCE_INDEX"))
            }
            put("newest unread (widget)", listOf("USING INDEX $SHELF_INDEX"))
            // Sources ranked first, then each source's rows from its index, sorted only within a source.
            for (name in listOf("shelf All BySource", "shelf Unread BySource", "shelf All BySource + search")) {
                put(name, listOf("USING INDEX $SOURCE_INDEX", "USE TEMP B-TREE FOR LAST 2 TERMS OF ORDER BY"))
            }
            put("shelf counts", listOf("USING COVERING INDEX $READ_INDEX"))
            put("unread count (every command, streak)", listOf("USING COVERING INDEX $READ_INDEX"))
            put("auto-archive due count", listOf("USING COVERING INDEX $READ_INDEX"))
            put("shortest unread (nudge, reminders)", listOf("USING INDEX $READ_INDEX"))
        }

        /** Lists the index serves in order: the page and Room's COUNT(*) around it never sort. */
        val unsorted = setOf(
            "shelf All Newest", "shelf All Oldest", "shelf Unread Newest", "shelf Unread Oldest", "shelf Source Newest",
            "shelf Source Oldest", "newest unread (widget)",
        )

        val SMALL_TABLES = setOf("planned_notification", "recent_search", "article_content", "operation")
    }
}
