package com.techgv.stash

import com.techgv.stash.model.GroupBy
import com.techgv.stash.model.Lang
import com.techgv.stash.model.SortBy
import com.techgv.stash.model.SwipeAction
import com.techgv.stash.model.sampleItems
import com.techgv.stash.ui.theme.stringsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedCommonTest {
    private val en = stringsFor(Lang.En)

    @Test
    fun chipsCountShelfAndSources() {
        val chips = shelfChips(sampleItems.onShelf(), en).map { it.label }
        assertEquals(listOf("All · 6", "Unread · 5", "Medium · 2", "YouTube · 2", "dev.to · 1", "GitHub · 1"), chips)
    }

    @Test
    fun dayGroupingMatchesDesign() {
        // 2026-11-12 is a Thursday; items 5 days back land on Saturday.
        val today = 20_769L
        assertEquals(4, weekdayOf(today))
        val headers = shelfRows(sampleItems, "all", SortBy.Newest, GroupBy.Day, en, today).filterIsInstance<Row.Header>().map { it.label }
        assertEquals(listOf("Today", "Yesterday", "Saturday"), headers)
    }

    @Test
    fun monthOfCivilDate() {
        assertEquals(10, monthOf(20_769L)) // November
        assertEquals(0, monthOf(0L)) // 1970-01-01
        assertEquals(1, monthOf(31L)) // 1970-02-01
    }

    @Test
    fun sortBySourceGroupsBySource() {
        val rows = shelfRows(sampleItems, "all", SortBy.Source, GroupBy.Day, en)
        val headers = rows.filterIsInstance<Row.Header>().map { it.label }
        assertEquals(listOf("Medium", "YouTube", "GitHub", "dev.to"), headers)
    }

    @Test
    fun unreadFilterAndStaleSourceFallback() {
        val unread = shelfRows(sampleItems, "unread", SortBy.Newest, GroupBy.None, en).filterIsInstance<Row.Card>()
        assertTrue(unread.none { it.item.read })
        assertEquals("all", effectiveFilter("Nope", sampleItems.onShelf()))
    }

    @Test
    fun searchIncludesArchiveAndHighlights() {
        val r = search(sampleItems, "design", "all")
        assertEquals(listOf("a"), r.shelf.map { it.id })
        assertEquals(listOf("i"), r.archived.map { it.id })
        assertEquals("2 results for “design” · 1 archived", r.summary)
        assertEquals(Triple("", "Design", "ing for slow reading in a fast feed"), splitMatch(sampleItems[0].title, "design"))
    }

    @Test
    fun urlValidation() {
        assertEquals("Paste or type a link first.", urlError("  "))
        assertTrue(urlError("not a link")!!.startsWith("That doesn’t look like a link"))
        assertNull(urlError("https://www.example.com/a?b=1"))
        assertNull(urlError("example.org"))
        assertEquals("example.com", hostOf("https://www.example.com/path"))
    }

    @Test
    fun shortestUnreadAndHint() {
        assertEquals("c", shortestUnread(sampleItems)?.id) // 6 min read
        assertEquals("Swipe left to remove, right to mark read. Long-press for more.", hintText(SwipeAction.Read, SwipeAction.Remove))
        assertEquals("Long-press for more.", hintText(SwipeAction.None, SwipeAction.None))
    }
}
