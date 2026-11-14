package com.techgv.stash.core.data

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import app.cash.turbine.test
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.ShelfQuerySpec
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import com.techgv.stash.core.testing.scaleItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

class StashItemRepositoryTest {
    /** Spread over three months, several per day, three sources, every lifecycle. */
    private val items: List<SavedItem> = List(120) { n ->
        val lifecycle = ItemLifecycle.entries[n % 7 % 4]
        savedItem(
            "i" + "$n".padStart(3, '0'),
            lifecycle,
            addedAt = FIXTURE_NOW - (n * 17).hours,
            siteName = listOf("medium.com", "github.com", "example.com")[n % 3],
            title = listOf("Swift tips", "Kotlin notes", "Room guide")[n % 3] + " $n",
        )
    }

    private suspend fun TestData.shelf(spec: ShelfQuerySpec): List<ShelfRow> = items.observeShelf(spec).asSnapshot { scrollTo(1_000) }

    @Test
    fun shelfRowsHaveTheDomainsHeadersInOrderForEveryGroupingAndSort() = runTest {
        val data = TestData(this)
        data.insert(*items.toTypedArray())

        for (sort in ShelfSort.entries) {
            for (group in ShelfGrouping.entries) {
                for (filter in listOf(ShelfFilter.All, ShelfFilter.Unread, ShelfFilter.Source("github.com"))) {
                    val spec = ShelfQuerySpec(filter, sort, group)
                    assertEquals(ShelfQuery.shelf(items, spec, FIXTURE_NOW, TimeZone.UTC).rows, data.shelf(spec), "$spec")
                }
            }
        }
    }

    @Test
    fun theArchiveHasThisMonthAndEarlierHeaders() = runTest {
        val data = TestData(this)
        data.insert(*items.toTypedArray())

        val rows = data.items.observeArchive("").asSnapshot { scrollTo(1_000) }

        assertEquals(ShelfQuery.archive(items, "", FIXTURE_NOW, TimeZone.UTC), rows)
    }

    @Test
    fun aSelectedSourceThatLosesItsChipFallsBackToAll() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", siteName = "rare.org"), savedItem("b", siteName = "rare.org", addedAt = FIXTURE_NOW - 1.hours), savedItem("c"))

        data.items.observeShelf(ShelfQuerySpec(ShelfFilter.Source("rare.org"), group = ShelfGrouping.None)).test {
            assertEquals(listOf("a", "b"), cards(awaitItem()))
            data.bus.dispatch(Command.Archive(ItemId("b")))
            data.settle()
            // Room invalidates the source first; the list ends up showing All once the chip is gone.
            var latest = cards(awaitItem())
            while (latest != listOf("c", "a")) latest = cards(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theHeadersMoveOnAtMidnight() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", addedAt = FIXTURE_NOW))
        data.clock.now = FIXTURE_NOW + 13.hours + 59.minutes // 23:59

        data.items.observeShelf(ShelfQuerySpec()).test {
            assertEquals(ShelfRow.Header(GroupLabel.Today), rows(awaitItem()).first())
            data.clock.advanceBy(1.minutes)
            testScheduler.advanceTimeBy(1.minutes) // virtual time follows the test clock to midnight
            testScheduler.runCurrent()
            assertEquals(ShelfRow.Header(GroupLabel.Yesterday), rows(awaitItem()).first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun searchIsPagedWithHighlightsAndChips() = runTest {
        val data = TestData(this)
        data.insert(*items.toTypedArray())

        for (filter in listOf(SearchFilter.All, SearchFilter.Archived, SearchFilter.Source("medium.com"))) {
            val expected = ShelfQuery.search(items, "swi", filter)
            val shelf = data.items.observeShelfSearch("swi", filter)
            // Under the Archived chip the shelf part is a static empty list, which asSnapshot cannot wait out inside a live flow.
            val shelfHits = if (filter == SearchFilter.Archived) flowOf(shelf.first()).asSnapshot() else shelf.asSnapshot { scrollTo(1_000) }
            assertEquals(expected.shelf, shelfHits, "$filter")
            assertEquals(expected.archive, data.items.observeArchiveSearch("swi", filter).asSnapshot { scrollTo(1_000) }, "$filter")
        }
        assertEquals(ShelfQuery.search(items, "swi", SearchFilter.All).chips, data.items.observeSearchChips("swi").first())
        assertEquals(emptyList(), data.items.observeShelfSearch(" ... ", SearchFilter.All).asSnapshot())
        assertEquals(0, data.items.observeSearchChips("").first().all)
    }

    @Test
    fun fiftyThousandItemsLoadOnlyTheFirstPages() = runTest {
        val driver = RowCountingDriver()
        val database = Room.inMemoryDatabaseBuilder<StashDatabase>().setDriver(driver).setQueryCoroutineContext(StandardTestDispatcher(testScheduler)).build()
        val data = TestData(this, database)
        val many = scaleItems()
        many.chunked(500).forEach { data.insert(*it.toTypedArray()) }
        val spec = ShelfQuerySpec(group = ShelfGrouping.None)

        driver.listRowsRead = 0
        val first = data.items.observeShelf(spec).asSnapshot()
        val rowsRead = driver.listRowsRead

        val expected = ShelfQuery.shelf(many, spec, FIXTURE_NOW, TimeZone.UTC).rows
        assertEquals(StashItemRepository.PAGE_SIZE * 3, first.size, "the first emission holds the initial load only")
        assertEquals(StashItemRepository.PAGE_SIZE * 3, rowsRead, "and SQLite stepped through only those rows")
        assertEquals(expected.take(first.size), first)
        assertEquals(ShelfQuery.chipCounts(many.filter { it.isOnShelf }), data.items.observeChipCounts().first())

        driver.listRowsRead = 0
        val hits = data.items.observeShelfSearch("swi", SearchFilter.All).asSnapshot()
        assertEquals(StashItemRepository.PAGE_SIZE * 3, hits.size)
        assertEquals(StashItemRepository.PAGE_SIZE * 3, driver.listRowsRead)
        assertEquals(ShelfQuery.search(many, "swi", SearchFilter.All).shelf.take(hits.size), hits)
    }

    @Test
    fun headersAndTheirDayFollowTheUsersZone() = runTest {
        val newYork = TimeZone.of("America/New_York")
        val data = TestData(this, zone = newYork)
        // 03:00 UTC on Nov 12 is still Nov 11 in New York.
        val lateEvening = savedItem("evening", addedAt = Instant.parse("2026-11-12T03:00:00Z"))
        data.insert(lateEvening, *items.take(30).toTypedArray())

        for (group in ShelfGrouping.entries) {
            val spec = ShelfQuerySpec(group = group)
            assertEquals(ShelfQuery.shelf(items.take(30) + lateEvening, spec, FIXTURE_NOW, newYork).rows, data.shelf(spec), "$group")
        }
        assertEquals(ShelfRow.Header(GroupLabel.Yesterday), data.shelf(ShelfQuerySpec()).let { it[it.indexOf(ShelfRow.Card(lateEvening)) - 1] })
    }

    @Test
    fun shelfSearchTextNarrowsTheShelf() = runTest {
        val data = TestData(this)
        data.insert(*items.toTypedArray())

        for (sort in ShelfSort.entries) {
            val spec = ShelfQuerySpec(sort = sort, searchText = "kot")
            val rows = data.shelf(spec)
            assertEquals(ShelfQuery.shelf(items, spec, FIXTURE_NOW, TimeZone.UTC).rows, rows, "$sort")
            assertEquals(true, rows.filterIsInstance<ShelfRow.Card>().all { it.item.title.startsWith("Kotlin") })
        }
    }

    @Test
    fun aSearchChipThatIsNotOfferedFallsBackToAll() = runTest {
        val data = TestData(this)
        data.insert(*items.toTypedArray())

        val expected = ShelfQuery.search(items, "swi", SearchFilter.Source("not-in-the-results.example"))
        assertEquals(SearchFilter.All, expected.filter)
        assertEquals(expected.shelf, data.items.observeShelfSearch("swi", SearchFilter.Source("not-in-the-results.example")).asSnapshot { scrollTo(1_000) })
        assertEquals(expected.archive, data.items.observeArchiveSearch("swi", SearchFilter.Source("not-in-the-results.example")).asSnapshot { scrollTo(1_000) })
    }

    @Test
    fun archiveHeadersUseWhenTheItemWasArchived() = runTest {
        val data = TestData(this)
        // Archived this month, but its row changed again later (metadata, say): the header still says This month.
        val archived = savedItem("a", ItemLifecycle.Archived, addedAt = FIXTURE_NOW - 40.days, archivedAt = FIXTURE_NOW - 2.days)
            .copy(updatedAt = FIXTURE_NOW - 1.days)
        val earlierArchived = savedItem("b", ItemLifecycle.Archived, addedAt = FIXTURE_NOW - 80.days, archivedAt = FIXTURE_NOW - 40.days)
            .copy(updatedAt = FIXTURE_NOW - 1.days)
        data.insert(archived, earlierArchived)

        val rows = data.items.observeArchive("").asSnapshot()

        assertEquals(ShelfQuery.archive(listOf(archived, earlierArchived), "", FIXTURE_NOW, TimeZone.UTC), rows)
        assertEquals(listOf(ShelfRow.Header(GroupLabel.ThisMonth), ShelfRow.Card(archived), ShelfRow.Header(GroupLabel.Earlier), ShelfRow.Card(earlierArchived)), rows)
    }

    private suspend fun rows(page: PagingData<ShelfRow>) = flowOf(page).asSnapshot()

    private suspend fun cards(page: PagingData<ShelfRow>) = rows(page).filterIsInstance<ShelfRow.Card>().map { it.item.id.value }
}
