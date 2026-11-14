package com.techgv.stash.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.insertSeparators
import androidx.paging.map
import com.techgv.stash.core.database.FtsMatch
import com.techgv.stash.core.database.SavedItemEntity
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.chipCounts
import com.techgv.stash.core.database.ftsMatch
import com.techgv.stash.core.database.searchChips
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.domain.AutoArchivePolicy
import com.techgv.stash.core.domain.ChipCounts
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.SearchChips
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SearchHit
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.ShelfQuerySpec
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.ShelfSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Paged lists over the database (decisions D4, D5). A pager is rebuilt only when what it shows changes: the applied
 * filter (a selected source chip that disappears falls back to All) or the local day its headers are computed for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StashItemRepository(
    database: StashDatabase,
    private val clock: Clock,
    private val zones: ZoneProvider,
    private val ticks: LocalTicks,
) : ItemRepository {
    private val items = database.savedItemDao()

    private data class Today(val date: LocalDate, val zone: TimeZone)

    private fun today(): Flow<Today> = ticks.at(MIDNIGHT).map {
        val zone = zones.zone()
        Today(clock.now().toLocalDateTime(zone).date, zone)
    }.distinctUntilChanged()

    private fun pager(source: () -> PagingSource<Int, SavedItemEntity>): Flow<PagingData<SavedItemEntity>> =
        // Without placeholders: the inserted headers are not counted in the source's item count, so positions would jump.
        // Pages far from the visible ones are dropped, so a long scroll keeps a few pages, not the list.
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false, maxSize = MAX_LOADED), pagingSourceFactory = source).flow

    override fun observeShelf(spec: ShelfQuerySpec): Flow<PagingData<ShelfRow>> {
        val match = ftsMatch(SearchMatcher.tokens(spec.searchText))
        val filters = if (spec.filter is ShelfFilter.Source) {
            observeChipCounts().map { ShelfQuery.effectiveFilter(spec.filter, it) }.distinctUntilChanged()
        } else {
            flowOf(spec.filter)
        }
        return combine(filters, today(), ::Pair).flatMapLatest { (filter, today) ->
            pager { items.shelf(filter, spec.sort, match) }.map { page ->
                page.withHeaders { ShelfQuery.groupLabel(it, spec.sort, spec.group, today.date, today.zone) }
            }
        }
    }

    override fun observeArchive(searchText: String): Flow<PagingData<ShelfRow>> {
        val match = ftsMatch(SearchMatcher.tokens(searchText))
        return today().flatMapLatest { today ->
            pager { items.archive(match) }.map { page ->
                page.withHeaders { ShelfQuery.archiveLabel(it.archivedAt ?: it.updatedAt, today.date, today.zone) }
            }
        }
    }

    override fun observeShelfSearch(text: String, filter: SearchFilter): Flow<PagingData<SearchHit>> {
        val tokens = SearchMatcher.tokens(text)
        val match = ftsMatch(tokens) ?: return flowOf(PagingData.empty())
        return appliedSearchFilter(match, filter).flatMapLatest { applied ->
            if (applied == SearchFilter.Archived) {
                flowOf(PagingData.empty())
            } else {
                val shelfFilter = (applied as? SearchFilter.Source)?.let { ShelfFilter.Source(it.key) } ?: ShelfFilter.All
                pager { items.shelf(shelfFilter, ShelfSort.Newest, match) }.map { page -> page.map { hit(it, tokens) } }
            }
        }
    }

    override fun observeArchiveSearch(text: String, filter: SearchFilter): Flow<PagingData<SearchHit>> {
        val tokens = SearchMatcher.tokens(text)
        val match = ftsMatch(tokens) ?: return flowOf(PagingData.empty())
        return appliedSearchFilter(match, filter).flatMapLatest { applied ->
            pager { items.archiveSearch(match, (applied as? SearchFilter.Source)?.key) }.map { page -> page.map { hit(it, tokens) } }
        }
    }

    override fun observeSearchChips(text: String): Flow<SearchChips> {
        val match = ftsMatch(SearchMatcher.tokens(text)) ?: return flowOf(SearchChips(all = 0, sources = emptyList(), archived = null))
        return items.observeSearchSources(match).map(::searchChips)
    }

    override fun observeChipCounts(): Flow<ChipCounts> =
        combine(items.observeShelfCounts(), items.observeShelfSources(), ::chipCounts).distinctUntilChanged()

    override fun observeArchiveCount(): Flow<Int> = items.observeArchiveCount()

    override fun observe(id: ItemId): Flow<SavedItem?> = items.observe(id.value).map { it?.toModel() }

    override suspend fun get(id: ItemId): SavedItem? = items.get(id.value)?.toModel()

    override suspend fun shortestUnread(): SavedItem? = items.shortestUnread()?.toModel()

    override suspend fun newestUnread(limit: Int): List<SavedItem> = items.newestUnread(limit).map { it.toModel() }

    override suspend fun countAutoArchiveDue(period: AutoArchivePeriod): Int =
        AutoArchivePolicy.cutoff(period, clock.now())?.let { items.countAutoArchiveDue(it) } ?: 0

    /** The filter `ShelfQuery.search` would apply: a chip that is not offered for these results falls back to All. */
    private fun appliedSearchFilter(match: FtsMatch, filter: SearchFilter): Flow<SearchFilter> =
        if (filter == SearchFilter.All) {
            flowOf(filter)
        } else {
            items.observeSearchSources(match).map { ShelfQuery.effectiveSearchFilter(filter, searchChips(it)) }.distinctUntilChanged()
        }

    private fun hit(entity: SavedItemEntity, tokens: List<String>): SearchHit {
        val item = entity.toModel()
        // Null only for the FTS parity limits SearchMatcher documents; the row still matched, without highlights.
        val (title, site) = SearchMatcher.match(tokens, item.title, item.siteName) ?: (emptyList<IntRange>() to emptyList())
        return SearchHit(item, title, site)
    }

    /** Cards with a header before each change of label, like `ShelfQuery`'s rows. */
    private fun PagingData<SavedItemEntity>.withHeaders(labelOf: (SavedItem) -> GroupLabel?): PagingData<ShelfRow> =
        map { ShelfRow.Card(it.toModel()) }.insertSeparators { before: ShelfRow.Card?, after: ShelfRow.Card? ->
            val label = after?.let { labelOf(it.item) } ?: return@insertSeparators null
            if (label != before?.let { labelOf(it.item) }) ShelfRow.Header(label) else null
        }

    companion object {
        /** Decision D5. */
        const val PAGE_SIZE = 30

        /** Items a list keeps loaded at most: the visible ones plus a prefetch page on each side, several times over. */
        const val MAX_LOADED = PAGE_SIZE * 8
    }
}
