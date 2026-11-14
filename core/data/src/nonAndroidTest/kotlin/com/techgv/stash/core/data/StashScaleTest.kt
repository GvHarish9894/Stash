package com.techgv.stash.core.data

import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.ShelfQuerySpec
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.scaleItems
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

/**
 * The data layer at the scale target (50,000 items, [scaleItems]): a long scroll keeps only a few pages, and what the
 * app does at launch costs what is printed. Timings are for information (the database runs on the test scheduler).
 */
class StashScaleTest {
    private val driver = RowCountingDriver()
    private val report = StringBuilder()

    @AfterTest
    fun printReport() = println(report)

    private suspend fun TestScope.scaleData(): TestData {
        val database = Room.inMemoryDatabaseBuilder<StashDatabase>().setDriver(driver)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler)).build()
        return TestData(this, database).also { data -> items.chunked(500).forEach { data.insert(*it.toTypedArray()) } }
    }

    @Test
    fun aLongScrollKeepsOnlyAFewPagesLoaded() = runTest(timeout = 10.minutes) {
        val data = scaleData()
        assertKeepsAFewPages("shelf", data.items.observeShelf(ShelfQuerySpec()), ShelfQuery.shelf(items, ShelfQuerySpec(), FIXTURE_NOW, TimeZone.UTC).rows)
        assertKeepsAFewPages("archive", data.items.observeArchive(""), ShelfQuery.archive(items, "", FIXTURE_NOW, TimeZone.UTC))
    }

    /**
     * Scrolls like a list does, asking for the last loaded row until 6,000 cards went by: the pager then holds a window of
     * at most [StashItemRepository.MAX_LOADED] of them. (paging-testing's `asSnapshot` loses its place when pages drop.)
     */
    private suspend fun TestScope.assertKeepsAFewPages(name: String, rows: Flow<PagingData<ShelfRow>>, expected: List<ShelfRow>) {
        val expectedCards = expected.filterIsInstance<ShelfRow.Card>()
        val presenter = object : PagingDataPresenter<ShelfRow>(StandardTestDispatcher(testScheduler)) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<ShelfRow>) = Unit
        }
        val collecting = backgroundScope.launch { rows.collectLatest { presenter.collectFrom(it) } }
        var loadedCards = emptyList<ShelfRow.Card>()
        val took = measureTime {
            runCurrent()
            while (expectedCards.indexOf(loadedCards.lastOrNull()) < SCROLLED) {
                presenter[presenter.size - 1]
                runCurrent()
                val now = presenter.snapshot().items.filterIsInstance<ShelfRow.Card>()
                check(now != loadedCards) { "$name: no page after ${loadedCards.size} cards" }
                loadedCards = now
            }
        }
        collecting.cancel()
        report.appendLine("  ${took.inWholeMilliseconds} ms  $name: scroll through $SCROLLED cards")
        val start = expectedCards.indexOf(loadedCards.first())
        assertTrue(loadedCards.size <= StashItemRepository.MAX_LOADED, "$name keeps ${loadedCards.size} cards loaded")
        assertEquals(expectedCards.subList(start, start + loadedCards.size), loadedCards, "$name: a window of the list")
    }

    /**
     * Before the first frame the app only starts Koin and WorkManager; the shelf's first frame then reads these. The
     * foreground catch-up runs beside it on the application scope: the first one archives everything due, later ones
     * find nothing due from an indexed count and skip the candidate walk.
     */
    @Test
    fun theLaunchPath() = runTest(timeout = 10.minutes) {
        val data = scaleData()
        timed("shelf chips (counts and sources)") { data.items.observeChipCounts().first() }
        timed("streak") { data.streak.observeStreak().first() }
        timed("archive count") { data.items.observeArchiveCount().first() }
        timed("nudge: shortest unread") { data.items.shortestUnread() }
        timed("shelf: first pages and Room's count") { data.items.observeShelf(ShelfQuerySpec()).asSnapshot() }

        val due = data.items.countAutoArchiveDue(AutoArchivePeriod.Days30)
        timed("first catch-up: archives $due due items, re-plans, republishes, 50 offline metadata attempts") { data.sync.onForeground() }
        assertEquals(0, data.items.countAutoArchiveDue(AutoArchivePeriod.Days30))

        driver.prepared.clear()
        timed("later catch-up: nothing due") { data.sync.onForeground() }
        assertTrue(driver.prepared.none { "+read_at <= ?" in it }, "nothing was due, yet the candidate walk ran")
        data.close()
    }

    private suspend fun <T> timed(name: String, block: suspend () -> T): T {
        val (value, took) = measureTimedValue { block() }
        report.appendLine("  ${took.inWholeMilliseconds} ms  $name")
        return value
    }

    private companion object {
        const val SCROLLED = 6_000
        val items by lazy { scaleItems() }
    }
}
