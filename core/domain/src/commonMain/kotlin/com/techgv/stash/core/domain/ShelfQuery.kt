package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

sealed interface ShelfFilter {
    data object All : ShelfFilter
    data object Unread : ShelfFilter
    data class Source(val key: String) : ShelfFilter
}

/** What a repository needs to page the shelf; mirrors the user's sort and group settings plus the chip and search text. */
data class ShelfQuerySpec(
    val filter: ShelfFilter = ShelfFilter.All,
    val sort: ShelfSort = ShelfSort.Newest,
    val group: ShelfGrouping = ShelfGrouping.Day,
    val searchText: String = "",
)

/** Typed list headers; the UI turns them into localized text. */
sealed interface GroupLabel {
    data object Today : GroupLabel
    data object Yesterday : GroupLabel

    /** Two to six days ago. */
    data class Weekday(val day: DayOfWeek) : GroupLabel
    data object ThisWeek : GroupLabel
    data object LastWeek : GroupLabel
    data object Earlier : GroupLabel
    data class Month(val year: Int, val month: kotlinx.datetime.Month) : GroupLabel
    data class Source(val key: String) : GroupLabel

    /** Archive only, with [Earlier]. */
    data object ThisMonth : GroupLabel
}

sealed interface ShelfRow {
    data class Header(val label: GroupLabel) : ShelfRow
    data class Card(val item: SavedItem) : ShelfRow
}

data class SourceCount(val key: String, val count: Int)

/**
 * Shelf chip counts: All, Unread, the top 12 source chips and, in [more], the other sources with at least 2 items
 * (the "More…" sheet), in the same order.
 */
data class ChipCounts(val all: Int, val unread: Int, val sources: List<SourceCount>, val more: List<SourceCount> = emptyList())

/** [filter] is the filter actually applied: a source chip that disappeared falls back to All. */
data class Shelf(val rows: List<ShelfRow>, val chips: ChipCounts, val filter: ShelfFilter)

sealed interface SearchFilter {
    data object All : SearchFilter
    data object Archived : SearchFilter
    data class Source(val key: String) : SearchFilter
}

/** A search match with the ranges to highlight in the title and the site name, in order and non-overlapping. */
data class SearchHit(val item: SavedItem, val titleMatches: List<IntRange>, val siteMatches: List<IntRange>)

/** Chips built from the results only, not capped. [archived] is null when the Archived chip is hidden. */
data class SearchChips(val all: Int, val sources: List<SourceCount>, val archived: Int?)

data class SearchResult(
    val shelf: List<SearchHit>,
    val archive: List<SearchHit>,
    val chips: SearchChips,
    val filter: SearchFilter,
)

/**
 * The pure reference for shelf, archive and search queries. The database implements the same filter and sort in SQL
 * and inserts headers between paged rows with [groupLabel] (decision D4).
 */
object ShelfQuery {
    const val MAX_SOURCE_CHIPS = 12
    private const val MIN_SOURCE_CHIP_ITEMS = 2

    fun shelf(items: List<SavedItem>, spec: ShelfQuerySpec, now: Instant, zone: TimeZone): Shelf {
        val onShelf = items.filter { it.isOnShelf }
        val chips = chipCounts(onShelf)
        val filter = effectiveFilter(spec.filter, chips)
        val tokens = SearchMatcher.tokens(spec.searchText)
        val visible = onShelf.filter { item ->
            val passes = when (filter) {
                ShelfFilter.All -> true
                ShelfFilter.Unread -> item.lifecycle == ItemLifecycle.Unread
                is ShelfFilter.Source -> item.sourceKey == filter.key
            }
            passes && (tokens.isEmpty() || hit(item, tokens) != null)
        }
        val sorted = visible.sortedWith(comparator(spec.sort, visible.groupingBy { it.sourceKey }.eachCount()))
        val today = now.toLocalDateTime(zone).date
        return Shelf(withHeaders(sorted) { groupLabel(it, spec.sort, spec.group, today, zone) }, chips, filter)
    }

    /** Chip counts over the items on the shelf. Source chips need at least 2 items; ordered by count, then newest, then key. */
    fun chipCounts(onShelf: List<SavedItem>): ChipCounts {
        val sources = sourceCounts(onShelf, MIN_SOURCE_CHIP_ITEMS)
        return ChipCounts(
            all = onShelf.size,
            unread = onShelf.count { it.lifecycle == ItemLifecycle.Unread },
            sources = sources.take(MAX_SOURCE_CHIPS),
            more = sources.drop(MAX_SOURCE_CHIPS),
        )
    }

    /** A selected source stays while it has a chip or a "More…" entry; otherwise the filter falls back to All. */
    fun effectiveFilter(filter: ShelfFilter, chips: ChipCounts): ShelfFilter =
        if (filter is ShelfFilter.Source && (chips.sources + chips.more).none { it.key == filter.key }) ShelfFilter.All else filter

    /**
     * Total order for a sort. [sourceCounts] (items per source in the filtered list) is only used by [ShelfSort.BySource],
     * which puts bigger sources first, then sorts by key and newest first. Ties fall back to the id.
     */
    fun comparator(sort: ShelfSort, sourceCounts: Map<String, Int>): Comparator<SavedItem> = when (sort) {
        ShelfSort.Newest -> NEWEST_FIRST
        ShelfSort.Oldest -> compareBy<SavedItem> { it.addedAt }.thenBy { it.id.value }
        ShelfSort.BySource -> compareByDescending<SavedItem> { sourceCounts[it.sourceKey] ?: 0 }
            .thenBy { it.sourceKey }
            .then(NEWEST_FIRST)
    }

    /** The header an item falls under, or null for no headers. By-source sort always groups by source. */
    fun groupLabel(item: SavedItem, sort: ShelfSort, group: ShelfGrouping, today: LocalDate, zone: TimeZone): GroupLabel? {
        if (sort == ShelfSort.BySource) return GroupLabel.Source(item.sourceKey)
        val date = item.addedAt.toLocalDateTime(zone).date
        // A clock-skewed future date counts as today.
        val daysAgo = date.daysUntil(today).coerceAtLeast(0)
        return when (group) {
            ShelfGrouping.None -> null
            ShelfGrouping.Day -> when (daysAgo) {
                0 -> GroupLabel.Today
                1 -> GroupLabel.Yesterday
                in 2..6 -> GroupLabel.Weekday(date.dayOfWeek)
                else -> GroupLabel.Earlier
            }
            ShelfGrouping.Week -> when {
                daysAgo < 7 -> GroupLabel.ThisWeek
                daysAgo < 14 -> GroupLabel.LastWeek
                else -> GroupLabel.Earlier
            }
            ShelfGrouping.Month -> GroupLabel.Month(date.year, date.month)
        }
    }

    /** Archived items, newest archived first, under This month / Earlier, optionally filtered by search text. */
    fun archive(items: List<SavedItem>, searchText: String, now: Instant, zone: TimeZone): List<ShelfRow> {
        val tokens = SearchMatcher.tokens(searchText)
        val today = now.toLocalDateTime(zone).date
        val archived = items
            .filter { it.lifecycle == ItemLifecycle.Archived && (tokens.isEmpty() || hit(it, tokens) != null) }
            .sortedWith(compareByDescending<SavedItem> { it.archivedAt }.thenByDescending { it.id.value })
        return withHeaders(archived) { archiveLabel(it.archivedAt ?: it.updatedAt, today, zone) }
    }

    fun archiveLabel(archivedAt: Instant, today: LocalDate, zone: TimeZone): GroupLabel {
        val date = archivedAt.toLocalDateTime(zone).date
        return if (date.year == today.year && date.month == today.month) GroupLabel.ThisMonth else GroupLabel.Earlier
    }

    /**
     * Searches title and site name with [SearchMatcher], newest first, split into shelf and archive.
     * Source chips appear only when the results span more than one source; the Archived chip only when results are in both.
     */
    fun search(items: List<SavedItem>, text: String, filter: SearchFilter): SearchResult {
        val tokens = SearchMatcher.tokens(text)
        val hits = items.asSequence()
            .filter { it.lifecycle != ItemLifecycle.Deleted }
            .mapNotNull { hit(it, tokens) }
            .sortedWith(compareBy(NEWEST_FIRST) { it.item })
            .toList()
        val (shelfHits, archiveHits) = hits.partition { it.item.isOnShelf }
        val chips = SearchChips(
            all = hits.size,
            sources = sourceCounts(hits.map { it.item }, minCount = 1).takeIf { it.size > 1 }.orEmpty(),
            archived = archiveHits.size.takeIf { it > 0 && shelfHits.isNotEmpty() },
        )
        val applied = effectiveSearchFilter(filter, chips)
        fun keep(hit: SearchHit) = applied !is SearchFilter.Source || hit.item.sourceKey == applied.key
        return SearchResult(
            shelf = if (applied == SearchFilter.Archived) emptyList() else shelfHits.filter(::keep),
            archive = archiveHits.filter(::keep),
            chips = chips,
            filter = applied,
        )
    }

    /** A search chip that is not offered falls back to All. */
    fun effectiveSearchFilter(filter: SearchFilter, chips: SearchChips): SearchFilter = when (filter) {
        SearchFilter.All -> filter
        SearchFilter.Archived -> if (chips.archived != null) filter else SearchFilter.All
        is SearchFilter.Source -> if (chips.sources.any { it.key == filter.key }) filter else SearchFilter.All
    }

    private fun hit(item: SavedItem, tokens: List<String>): SearchHit? =
        SearchMatcher.match(tokens, item.title, item.siteName)?.let { (title, site) -> SearchHit(item, title, site) }

    private fun sourceCounts(items: List<SavedItem>, minCount: Int): List<SourceCount> =
        items.groupBy { it.sourceKey }
            .filterValues { it.size >= minCount }
            .map { (key, group) -> Triple(key, group.size, group.maxOf { it.addedAt }) }
            .sortedWith(compareByDescending<Triple<String, Int, Instant>> { it.second }.thenByDescending { it.third }.thenBy { it.first })
            .map { SourceCount(it.first, it.second) }

    private fun withHeaders(sorted: List<SavedItem>, labelOf: (SavedItem) -> GroupLabel?): List<ShelfRow> {
        val rows = ArrayList<ShelfRow>(sorted.size + 16)
        var last: GroupLabel? = null
        for (item in sorted) {
            val label = labelOf(item)
            if (label != null && label != last) rows += ShelfRow.Header(label)
            last = label
            rows += ShelfRow.Card(item)
        }
        return rows
    }

    private val NEWEST_FIRST: Comparator<SavedItem> =
        compareByDescending<SavedItem> { it.addedAt }.thenByDescending { it.id.value }
}
