package com.techgv.stash.feature.search

import com.techgv.stash.core.designsystem.presentation.plural
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.search_summary
import com.techgv.stash.core.resources.search_summary_in_archive
import com.techgv.stash.core.resources.search_summary_in_source
import com.techgv.stash.core.resources.search_summary_with_archived
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchSummaryTest {
    private val medium = SearchFilter.Source("Medium")

    private fun results(total: Int, archived: Int?, filter: SearchFilter = SearchFilter.All) = SearchResults(
        text = "compose",
        total = total,
        chips = listOf(SearchChipUi(SearchFilter.All, total), SearchChipUi(medium, 2)),
        filter = filter,
        archived = archived,
    )

    @Test
    fun shelfOnly() = assertEquals(
        plural(Res.plurals.search_summary, 2, 2, "compose"),
        searchSummary(results(2, archived = null), shelfEmpty = false),
    )

    @Test
    fun shelfAndArchive() = assertEquals(
        plural(Res.plurals.search_summary_with_archived, 3, 3, "compose", 1),
        searchSummary(results(3, archived = 1), shelfEmpty = false),
    )

    @Test
    fun everyHitArchived() = assertEquals(
        plural(Res.plurals.search_summary_with_archived, 2, 2, "compose", 2),
        searchSummary(results(2, archived = null), shelfEmpty = true),
    )

    @Test
    fun inASource() = assertEquals(
        plural(Res.plurals.search_summary_in_source, 2, 2, "compose", "Medium"),
        searchSummary(results(3, archived = 1, filter = medium), shelfEmpty = false),
    )

    @Test
    fun inTheArchive() = assertEquals(
        plural(Res.plurals.search_summary_in_archive, 1, 1, "compose"),
        searchSummary(results(3, archived = 1, filter = SearchFilter.Archived), shelfEmpty = true),
    )

    @Test
    fun noMatchHasNoSummary() = assertNull(searchSummary(results(0, archived = null), shelfEmpty = true))
}
