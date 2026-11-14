package com.techgv.stash.core.testing

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.testing.asPagingSourceFactory
import com.techgv.stash.core.domain.AutoArchivePolicy
import com.techgv.stash.core.domain.ChipCounts
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.domain.SearchChips
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SearchHit
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.ShelfQuerySpec
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * An in-memory [ItemRepository] over [items], answered with the pure domain policies ([ShelfQuery], [AutoArchivePolicy],
 * [ReminderPlanner]) the real one must match. Every flow re-emits when [items] or [clock] changes. Set [items]' value
 * directly to arrange a test, or write through [FakeCommandBus].
 */
class FakeItemRepository(
    initial: List<SavedItem> = emptyList(),
    val clock: TestClock = TestClock(),
    val zones: ZoneProvider = FixedZoneProvider(),
) : ItemRepository {
    val items = MutableStateFlow(initial)

    // A real Pager over each new list, not PagingData.from: asSnapshot cannot wait out a static PagingData in a live flow.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T : Any> paged(rows: (List<SavedItem>, Instant) -> List<T>): Flow<PagingData<T>> =
        combine(items, clock.instants, rows).flatMapLatest { list ->
            Pager(PagingConfig(pageSize = PAGE_SIZE), pagingSourceFactory = list.asPagingSourceFactory()).flow
        }

    override fun observeShelf(spec: ShelfQuerySpec): Flow<PagingData<ShelfRow>> =
        paged { list, now -> ShelfQuery.shelf(list, spec, now, zones.zone()).rows }

    override fun observeArchive(searchText: String): Flow<PagingData<ShelfRow>> =
        paged { list, now -> ShelfQuery.archive(list, searchText, now, zones.zone()) }

    override fun observeShelfSearch(text: String, filter: SearchFilter): Flow<PagingData<SearchHit>> =
        paged { list, _ -> ShelfQuery.search(list, text, filter).shelf }

    override fun observeArchiveSearch(text: String, filter: SearchFilter): Flow<PagingData<SearchHit>> =
        paged { list, _ -> ShelfQuery.search(list, text, filter).archive }

    override fun observeSearchChips(text: String): Flow<SearchChips> =
        items.map { ShelfQuery.search(it, text, SearchFilter.All).chips }.distinctUntilChanged()

    override fun observeChipCounts(): Flow<ChipCounts> =
        items.map { list -> ShelfQuery.chipCounts(list.filter { it.isOnShelf }) }.distinctUntilChanged()

    override fun observeArchiveCount(): Flow<Int> =
        items.map { list -> list.count { it.lifecycle == ItemLifecycle.Archived } }.distinctUntilChanged()

    override fun observe(id: ItemId): Flow<SavedItem?> = items.map { list -> list.find { it.id == id } }.distinctUntilChanged()

    override suspend fun get(id: ItemId): SavedItem? = items.value.find { it.id == id }

    override suspend fun shortestUnread(): SavedItem? = ReminderPlanner.shortestUnread(items.value)

    override suspend fun newestUnread(limit: Int): List<SavedItem> = items.value
        .filter { it.lifecycle == ItemLifecycle.Unread }
        .sortedWith(compareByDescending<SavedItem> { it.addedAt }.thenByDescending { it.id.value })
        .take(limit)

    override suspend fun countAutoArchiveDue(period: AutoArchivePeriod): Int =
        items.value.count { AutoArchivePolicy.isDue(it, period, clock.now()) }

    private companion object {
        const val PAGE_SIZE = 30 // decision D5
    }

    /** Inserts [changed] items or replaces those with the same id. */
    fun upsert(changed: List<SavedItem>) {
        val byId = changed.associateBy { it.id }
        val kept = items.value.map { byId[it.id] ?: it }
        items.value = kept + changed.filter { new -> kept.none { it.id == new.id } }
    }
}
