package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ShelfQueryTest {
    private val zone = KOLKATA
    private val now = local("2026-11-12T10:00", zone) // a Thursday

    private val a = item("a", local("2026-11-12T09:00", zone), sourceKey = "Medium")
    private val b = item("b", local("2026-11-12T08:00", zone), readAt = now, sourceKey = "YouTube")
    private val c = item("c", local("2026-11-11T20:00", zone), sourceKey = "Medium")
    private val d = item("d", local("2026-11-09T12:00", zone), sourceKey = "GitHub")
    private val e = item("e", local("2026-11-02T12:00", zone), readAt = now, sourceKey = "YouTube")
    private val f = item("f", local("2026-10-23T12:00", zone), sourceKey = "dev.to")
    private val g = item("g", local("2026-11-01T12:00", zone), readAt = now - 9.days, archivedAt = now - 7.days, sourceKey = "Medium")
    private val h = item("h", local("2026-11-12T09:30", zone), deletedAt = now, sourceKey = "YouTube")
    private val items = listOf(f, h, c, a, g, e, b, d)

    private fun shelf(filter: ShelfFilter, sort: ShelfSort, group: ShelfGrouping) =
        ShelfQuery.shelf(items, ShelfQuerySpec(filter, sort, group), now, zone)

    private fun List<ShelfRow>.ids() = filterIsInstance<ShelfRow.Card>().map { it.item.id.value }

    private fun List<ShelfRow>.render(): List<String> = map {
        when (it) {
            is ShelfRow.Header -> "[${it.label}]"
            is ShelfRow.Card -> it.item.id.value
        }
    }

    private val expectedOrder = mapOf(
        ShelfFilter.All to mapOf(
            ShelfSort.Newest to "abcdef",
            ShelfSort.Oldest to "fedcba",
            ShelfSort.BySource to "acbedf",
        ),
        ShelfFilter.Unread to mapOf(
            ShelfSort.Newest to "acdf",
            ShelfSort.Oldest to "fdca",
            ShelfSort.BySource to "acdf",
        ),
        ShelfFilter.Source("Medium") to mapOf(
            ShelfSort.Newest to "ac",
            ShelfSort.Oldest to "ca",
            ShelfSort.BySource to "ac",
        ),
    )

    @Test
    fun everyFilterSortAndGroupCombination() {
        for ((filter, orders) in expectedOrder) for ((sort, order) in orders) for (group in ShelfGrouping.entries) {
            val name = "$filter $sort $group"
            val result = shelf(filter, sort, group)
            assertEquals(order.map { it.toString() }, result.rows.ids(), name)
            assertEquals(filter, result.filter, name)
            // Every card sits under the header its own label gives, and a header only appears when the label changes.
            var current: GroupLabel? = null
            var previousWasHeader = false
            for (row in result.rows) when (row) {
                is ShelfRow.Header -> {
                    assertTrue(!previousWasHeader && row.label != current, name)
                    current = row.label
                    previousWasHeader = true
                }
                is ShelfRow.Card -> {
                    assertEquals(ShelfQuery.groupLabel(row.item, sort, group, now.toLocalDateTime(zone).date, zone), current, name)
                    previousWasHeader = false
                }
            }
            if (sort != ShelfSort.BySource && group == ShelfGrouping.None) {
                assertTrue(result.rows.none { it is ShelfRow.Header }, name)
            }
        }
    }

    @Test
    fun headersForEachGrouping() {
        assertEquals(
            listOf("[Today]", "a", "b", "[Yesterday]", "c", "[Weekday(day=MONDAY)]", "d", "[Earlier]", "e", "f"),
            shelf(ShelfFilter.All, ShelfSort.Newest, ShelfGrouping.Day).rows.render(),
        )
        assertEquals(
            listOf("[ThisWeek]", "a", "b", "c", "d", "[LastWeek]", "e", "[Earlier]", "f"),
            shelf(ShelfFilter.All, ShelfSort.Newest, ShelfGrouping.Week).rows.render(),
        )
        assertEquals(
            listOf("[Month(year=2026, month=NOVEMBER)]", "a", "b", "c", "d", "e", "[Month(year=2026, month=OCTOBER)]", "f"),
            shelf(ShelfFilter.All, ShelfSort.Newest, ShelfGrouping.Month).rows.render(),
        )
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), shelf(ShelfFilter.All, ShelfSort.Newest, ShelfGrouping.None).rows.render())
        assertEquals(
            listOf("[Earlier]", "f", "e", "[Weekday(day=MONDAY)]", "d", "[Yesterday]", "c", "[Today]", "b", "a"),
            shelf(ShelfFilter.All, ShelfSort.Oldest, ShelfGrouping.Day).rows.render(),
        )
        assertEquals(
            listOf("[Source(key=Medium)]", "a", "c", "[Source(key=YouTube)]", "b", "e", "[Source(key=GitHub)]", "d", "[Source(key=dev.to)]", "f"),
            shelf(ShelfFilter.All, ShelfSort.BySource, ShelfGrouping.Week).rows.render(),
        )
    }

    @Test
    fun dayLabelsFollowTheUsersZone() {
        // 23:50 on 11 November in Kolkata is still the morning of 11 November in Los Angeles, which is "today" there.
        val lateLastNight = item(addedAt = local("2026-11-11T23:50", KOLKATA))
        val today = now.toLocalDateTime(KOLKATA).date
        assertEquals(GroupLabel.Yesterday, ShelfQuery.groupLabel(lateLastNight, ShelfSort.Newest, ShelfGrouping.Day, today, KOLKATA))
        val todayInLa = now.toLocalDateTime(LOS_ANGELES).date
        assertEquals(GroupLabel.Today, ShelfQuery.groupLabel(lateLastNight, ShelfSort.Newest, ShelfGrouping.Day, todayInLa, LOS_ANGELES))
        val sixDaysAgo = item(addedAt = local("2026-11-06T23:59", KOLKATA))
        assertEquals(GroupLabel.Weekday(DayOfWeek.FRIDAY), ShelfQuery.groupLabel(sixDaysAgo, ShelfSort.Newest, ShelfGrouping.Day, today, KOLKATA))
        val sevenDaysAgo = item(addedAt = local("2026-11-05T23:59", KOLKATA))
        assertEquals(GroupLabel.Earlier, ShelfQuery.groupLabel(sevenDaysAgo, ShelfSort.Newest, ShelfGrouping.Day, today, KOLKATA))
        assertEquals(GroupLabel.LastWeek, ShelfQuery.groupLabel(sevenDaysAgo, ShelfSort.Newest, ShelfGrouping.Week, today, KOLKATA))
        val future = item(addedAt = now + 2.days)
        assertEquals(GroupLabel.Today, ShelfQuery.groupLabel(future, ShelfSort.Newest, ShelfGrouping.Day, today, KOLKATA))
    }

    @Test
    fun weekGroupingAtThirteenAndFourteenDays() {
        val today = now.toLocalDateTime(zone).date
        val thirteen = item(addedAt = local("2026-10-30T12:00", zone))
        val fourteen = item(addedAt = local("2026-10-29T12:00", zone))
        assertEquals(GroupLabel.LastWeek, ShelfQuery.groupLabel(thirteen, ShelfSort.Newest, ShelfGrouping.Week, today, zone))
        assertEquals(GroupLabel.Earlier, ShelfQuery.groupLabel(fourteen, ShelfSort.Newest, ShelfGrouping.Week, today, zone))
    }

    @Test
    fun monthAndDayGroupingAcrossTheYearBoundary() {
        val newYear = local("2027-01-01T09:00", zone)
        val today = newYear.toLocalDateTime(zone).date
        val lastDayOfYear = item("dec31", local("2026-12-31T23:30", zone))
        val firstDay = item("jan1", local("2027-01-01T00:10", zone))
        assertEquals(GroupLabel.Month(2026, kotlinx.datetime.Month.DECEMBER), ShelfQuery.groupLabel(lastDayOfYear, ShelfSort.Newest, ShelfGrouping.Month, today, zone))
        assertEquals(GroupLabel.Month(2027, kotlinx.datetime.Month.JANUARY), ShelfQuery.groupLabel(firstDay, ShelfSort.Newest, ShelfGrouping.Month, today, zone))
        assertEquals(GroupLabel.Yesterday, ShelfQuery.groupLabel(lastDayOfYear, ShelfSort.Newest, ShelfGrouping.Day, today, zone))
        assertEquals(
            listOf("[Month(year=2027, month=JANUARY)]", "jan1", "[Month(year=2026, month=DECEMBER)]", "dec31"),
            ShelfQuery.shelf(listOf(lastDayOfYear, firstDay), ShelfQuerySpec(group = ShelfGrouping.Month), newYear, zone).rows.render(),
        )
        // The archive's "This month" is a calendar month of the same year.
        val archivedDec = item("a", archivedAt = local("2026-12-31T23:30", zone))
        assertEquals(GroupLabel.Earlier, ShelfQuery.archiveLabel(archivedDec.archivedAt!!, today, zone))
    }

    @Test
    fun chipCountsAndTheOneVersusTwoItemBoundary() {
        val chips = shelf(ShelfFilter.All, ShelfSort.Newest, ShelfGrouping.Day).chips
        // GitHub and dev.to have 1 item each, so no chip; Medium wins the 2-2 tie with YouTube by having the newest item.
        assertEquals(ChipCounts(all = 6, unread = 4, sources = listOf(SourceCount("Medium", 2), SourceCount("YouTube", 2))), chips)
        val oneMoreGitHub = ShelfQuery.chipCounts(listOf(d, d.copy(id = ItemId("d2"))))
        assertEquals(listOf(SourceCount("GitHub", 2)), oneMoreGitHub.sources)
    }

    @Test
    fun atMostTwelveSourceChips() {
        fun sources(n: Int) = (1..n).flatMap { k ->
            listOf(item("s$k-1", T0 - k.hours, sourceKey = "src$k"), item("s$k-2", T0 - k.hours - 1.minutes, sourceKey = "src$k"))
        }
        assertEquals((1..12).map { SourceCount("src$it", 2) }, ShelfQuery.chipCounts(sources(12)).sources)
        // The 13th has the oldest newest-item, so it moves to "More…".
        val thirteen = ShelfQuery.chipCounts(sources(13))
        assertEquals((1..12).map { SourceCount("src$it", 2) }, thirteen.sources)
        assertEquals(listOf(SourceCount("src13", 2)), thirteen.more)
        assertEquals(emptyList(), ShelfQuery.chipCounts(sources(12)).more)

        val selected = ShelfQuery.shelf(sources(13), ShelfQuerySpec(filter = ShelfFilter.Source("src13")), T0, zone)
        assertEquals(ShelfFilter.Source("src13"), selected.filter)
        assertEquals(listOf("s13-1", "s13-2"), selected.rows.ids())
    }

    @Test
    fun chipTieOrderIsCountThenNewestThenKey() {
        val items = listOf(
            item("old1", T0 - 10.days, sourceKey = "Old"), item("old2", T0 - 10.days, sourceKey = "Old"),
            item("old3", T0 - 10.days, sourceKey = "Old"),
            item("mid1", T0 - 2.days, sourceKey = "Mid"), item("mid2", T0 - 3.days, sourceKey = "Mid"),
            item("new1", T0 - 1.days, sourceKey = "New"), item("new2", T0 - 4.days, sourceKey = "New"),
            item("z1", T0 - 1.days, sourceKey = "Zed"), item("z2", T0 - 5.days, sourceKey = "Zed"),
        )
        assertEquals(listOf("Old", "New", "Zed", "Mid"), ShelfQuery.chipCounts(items).sources.map { it.key })
    }

    @Test
    fun selectedSourceFallsBackToAllWhenItsChipDisappears() {
        val chips = ChipCounts(3, 1, listOf(SourceCount("Medium", 2)))
        assertEquals(ShelfFilter.All, ShelfQuery.effectiveFilter(ShelfFilter.Source("GitHub"), chips))
        assertEquals(ShelfFilter.Source("Medium"), ShelfQuery.effectiveFilter(ShelfFilter.Source("Medium"), chips))
        assertEquals(ShelfFilter.Unread, ShelfQuery.effectiveFilter(ShelfFilter.Unread, chips))
        val result = shelf(ShelfFilter.Source("GitHub"), ShelfSort.Newest, ShelfGrouping.None)
        assertEquals(ShelfFilter.All, result.filter)
        assertEquals("abcdef".map { it.toString() }, result.rows.ids())
    }

    @Test
    fun shelfSpecSearchTextNarrowsTheShelf() {
        val titled = items.map { if (it.id.value == "c") it.copy(title = "Compose tips") else it }
        val result = ShelfQuery.shelf(titled, ShelfQuerySpec(searchText = " compose "), now, zone)
        assertEquals(listOf("c"), result.rows.ids())
    }

    private val s1 = item("s1", T0 - 1.hours, title = "Shared element transitions in Compose", sourceKey = "YouTube", siteName = "youtube.com")
    private val s2 = item("s2", T0 - 1.days, title = "Compose performance tips", sourceKey = "Medium", siteName = "medium.com")
    private val s3 = item("s3", T0 - 2.days, title = "Room FTS guide", sourceKey = "composeweekly.com", siteName = "composeweekly.com")
    private val s4 = item("s4", T0 - 11.days, readAt = T0 - 10.days, archivedAt = T0 - 3.days, title = "Compose colour roles", sourceKey = "Medium", siteName = "medium.com")
    private val s5 = item("s5", T0, deletedAt = T0, title = "Compose deleted")
    private val s6 = item("s6", T0, title = "Unrelated")
    private val searchItems = listOf(s4, s6, s2, s5, s1, s3)

    @Test
    fun searchSplitsShelfAndArchiveWithHighlights() {
        val result = ShelfQuery.search(searchItems, "  COMPOSE ", SearchFilter.All)
        assertEquals(listOf("s1", "s2", "s3"), result.shelf.map { it.item.id.value })
        assertEquals(listOf("s4"), result.archive.map { it.item.id.value })
        assertEquals(listOf(30..36), result.shelf[0].titleMatches)
        assertEquals(emptyList(), result.shelf[0].siteMatches)
        assertEquals(listOf(0..6), result.shelf[1].titleMatches)
        assertEquals(emptyList(), result.shelf[2].titleMatches)
        assertEquals(listOf(0..6), result.shelf[2].siteMatches)
        assertEquals("Compose", s1.title.substring(result.shelf[0].titleMatches.single()))
        assertEquals(
            SearchChips(
                all = 4,
                sources = listOf(SourceCount("Medium", 2), SourceCount("YouTube", 1), SourceCount("composeweekly.com", 1)),
                archived = 1,
            ),
            result.chips,
        )
    }

    @Test
    fun searchFiltersAndTheirFallback() {
        val archivedOnly = ShelfQuery.search(searchItems, "compose", SearchFilter.Archived)
        assertEquals(SearchFilter.Archived, archivedOnly.filter)
        assertEquals(emptyList(), archivedOnly.shelf)
        assertEquals(listOf("s4"), archivedOnly.archive.map { it.item.id.value })

        val medium = ShelfQuery.search(searchItems, "compose", SearchFilter.Source("Medium"))
        assertEquals(listOf("s2"), medium.shelf.map { it.item.id.value })
        assertEquals(listOf("s4"), medium.archive.map { it.item.id.value })

        val missing = ShelfQuery.search(searchItems, "compose", SearchFilter.Source("GitHub"))
        assertEquals(SearchFilter.All, missing.filter)
        assertEquals(3, missing.shelf.size)
    }

    @Test
    fun searchChipsComeFromTheResultsOnly() {
        // One source: no source chips; shelf only: no Archived chip, so asking for it falls back to All.
        val room = ShelfQuery.search(searchItems, "room", SearchFilter.Archived)
        assertEquals(SearchChips(1, emptyList(), null), room.chips)
        assertEquals(SearchFilter.All, room.filter)
        // Archive only: the Archived chip is hidden too.
        val colour = ShelfQuery.search(searchItems, "colour", SearchFilter.All)
        assertEquals(SearchChips(1, emptyList(), null), colour.chips)
        assertEquals(listOf("s4"), colour.archive.map { it.item.id.value })
        // Nothing typed, nothing found.
        assertEquals(SearchChips(0, emptyList(), null), ShelfQuery.search(searchItems, "  ", SearchFilter.All).chips)
        assertEquals(0, ShelfQuery.search(searchItems, "nomatch", SearchFilter.All).chips.all)
    }

    @Test
    fun searchSourceChipsAreNotCapped() {
        val items = (1..13).map { item("k$it", T0 - it.hours, title = "Kotlin note $it", sourceKey = "src$it") }
        val chips = ShelfQuery.search(items, "kotlin", SearchFilter.All).chips
        assertEquals(13, chips.sources.size)
        assertEquals(SearchFilter.Source("src13"), ShelfQuery.search(items, "kotlin", SearchFilter.Source("src13")).filter)
    }

    @Test
    fun searchUsesTokenPrefixesNotSubstrings() {
        val swift = item("sw", T0, title = "Swift concurrency, explained", sourceKey = "Medium", siteName = "medium.com")
        val items = listOf(swift)
        fun found(query: String) = ShelfQuery.search(items, query, SearchFilter.All).chips.all == 1
        assertEquals(true, found("swi"))
        assertEquals(false, found("ift"), "infix does not match")
        assertEquals(true, found("conc swi"), "token order is irrelevant")
        assertEquals(false, found("swift kotlin"), "every token must match")
        assertEquals(true, found("swift med"), "tokens may match different fields")
        assertEquals(true, found("medium.com"), "punctuation splits words in the query too")
        assertEquals(listOf("[ThisMonth]", "sw"), ShelfQuery.archive(listOf(swift.copy(archivedAt = T0)), "conc", T0, zone).render())
        assertEquals(emptyList(), ShelfQuery.archive(listOf(swift.copy(archivedAt = T0)), "rency", T0, zone))
    }

    @Test
    fun archiveGroupsThisMonthAndEarlierInTheUsersZone() {
        val x1 = item("x1", T0 - 30.days, readAt = T0 - 20.days, archivedAt = local("2026-11-10T12:00", zone), title = "One")
        // 2026-10-31T20:00Z is already 1 November in Kolkata.
        val x2 = item("x2", T0 - 30.days, archivedAt = Instant.parse("2026-10-31T20:00:00Z"), title = "Two")
        val x3 = item("x3", T0 - 40.days, archivedAt = Instant.parse("2026-10-31T18:00:00Z"), title = "Three")
        val x4 = item("x4", T0 - 400.days, archivedAt = local("2025-11-20T12:00", zone), title = "Four")
        val all = listOf(x3, a, x1, x4, h, x2, g.copy(deletedAt = now))
        assertEquals(
            listOf("[ThisMonth]", "x1", "x2", "[Earlier]", "x3", "x4"),
            ShelfQuery.archive(all, "", now, zone).render(),
        )
        assertEquals(listOf("[Earlier]", "x3"), ShelfQuery.archive(all, "three", now, zone).render())
    }

    @Test
    fun fiftyThousandItemsKeepCorrectOrderAndCounts() {
        var seed = 42L
        fun next(bound: Int): Int {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33) % bound).toInt()
        }
        val sourceKeys = (0 until 20).map { "source$it" }
        val big = (0 until 50_000).map { i ->
            val added = T0 - next(400 * 24 * 60).minutes - next(60).seconds
            val roll = next(100)
            val src = sourceKeys[minOf(next(20), next(20))] // skewed so counts differ
            item(
                id = "id" + i.toString().padStart(6, '0'),
                addedAt = added,
                readAt = if (roll < 50) added + 1.hours else null,
                archivedAt = if (roll in 40..49) added + 2.hours else null,
                deletedAt = if (roll in 90..94) added + 3.hours else null,
                sourceKey = src,
            )
        }
        val onShelf = big.filter { it.deletedAt == null && it.archivedAt == null }
        val unread = onShelf.count { it.readAt == null }

        val newest = ShelfQuery.shelf(big, ShelfQuerySpec(sort = ShelfSort.Newest, group = ShelfGrouping.Day), now, zone)
        val cards = newest.rows.filterIsInstance<ShelfRow.Card>().map { it.item }
        assertEquals(onShelf.size, cards.size)
        assertEquals(onShelf.map { it.id }.toSet(), cards.map { it.id }.toSet())
        cards.zipWithNext().forEach { (x, y) ->
            assertTrue(x.addedAt > y.addedAt || (x.addedAt == y.addedAt && x.id.value > y.id.value), "$x before $y")
        }
        assertEquals(onShelf.size, newest.chips.all)
        assertEquals(unread, newest.chips.unread)
        val counts = onShelf.groupingBy { it.sourceKey }.eachCount()
        assertEquals(12, newest.chips.sources.size)
        newest.chips.sources.forEach { assertEquals(counts[it.key], it.count) }
        assertEquals(counts.values.sortedDescending().take(12), newest.chips.sources.map { it.count })
        val headers = newest.rows.filterIsInstance<ShelfRow.Header>().map { it.label }
        assertEquals(headers.distinct(), headers, "each day label appears once in newest order")

        val unreadOldest = ShelfQuery.shelf(big, ShelfQuerySpec(ShelfFilter.Unread, ShelfSort.Oldest, ShelfGrouping.Month), now, zone)
        val unreadCards = unreadOldest.rows.filterIsInstance<ShelfRow.Card>().map { it.item }
        assertEquals(unread, unreadCards.size)
        assertTrue(unreadCards.all { it.lifecycle == ItemLifecycle.Unread })
        unreadCards.zipWithNext().forEach { (x, y) -> assertTrue(x.addedAt <= y.addedAt) }

        val bySource = ShelfQuery.shelf(big, ShelfQuerySpec(sort = ShelfSort.BySource), now, zone)
        val sourceHeaders = bySource.rows.filterIsInstance<ShelfRow.Header>().map { (it.label as GroupLabel.Source).key }
        assertEquals(counts.size, sourceHeaders.size)
        assertEquals(sourceHeaders.map { counts.getValue(it) }, sourceHeaders.map { counts.getValue(it) }.sortedDescending())
    }
}
