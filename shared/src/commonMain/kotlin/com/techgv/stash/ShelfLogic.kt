package com.techgv.stash

import com.techgv.stash.model.GroupBy
import com.techgv.stash.model.Item
import com.techgv.stash.model.SortBy
import com.techgv.stash.model.SwipeAction
import com.techgv.stash.ui.theme.Strings
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A list row: a group header or an item card. */
sealed interface Row {
    data class Header(val label: String) : Row
    data class Card(val item: Item) : Row
}

data class FilterChip(val key: String, val label: String)

fun List<Item>.onShelf() = filter { !it.removed && !it.archived }
fun List<Item>.inArchive() = filter { !it.removed && it.archived }

/** Source → count, most items first; ties keep first-seen order. */
fun sourceCounts(items: List<Item>): List<Pair<String, Int>> =
    items.groupingBy { it.src }.eachCount().toList().sortedByDescending { it.second }

fun shelfChips(visible: List<Item>, s: Strings): List<FilterChip> =
    listOf(
        FilterChip("all", "${s.all} · ${visible.size}"),
        FilterChip("unread", "${s.unreadChip} · ${visible.count { !it.read }}"),
    ) + sourceCounts(visible).map { (k, n) -> FilterChip(k, "$k · $n") }

/** Falls back to "all" when the picked source has no items left. */
fun effectiveFilter(filter: String, visible: List<Item>) =
    if (filter == "all" || filter == "unread" || visible.any { it.src == filter }) filter else "all"

/** Seconds the device's local time is ahead of UTC right now. */
expect fun localUtcOffsetSeconds(): Int

/** Today's date in the device's time zone, as days since 1970-01-01. */
@OptIn(ExperimentalTime::class)
fun todayEpochDay(): Long = (Clock.System.now().epochSeconds + localUtcOffsetSeconds()).floorDiv(86_400L)

/** 0 = Sunday. 1970-01-01 was a Thursday. */
fun weekdayOf(epochDay: Long): Int = (((epochDay + 4) % 7 + 7) % 7).toInt()

/** 0 = January (civil-from-days). */
fun monthOf(epochDay: Long): Int {
    val z = epochDay + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    return (if (mp < 10) mp + 2 else mp - 10).toInt()
}

fun shelfRows(
    items: List<Item>,
    filter: String,
    sortBy: SortBy,
    groupBy: GroupBy,
    s: Strings,
    today: Long = todayEpochDay(),
): List<Row> {
    val visible = items.onShelf()
    val f = effectiveFilter(filter, visible)
    val list = visible.filter {
        when (f) {
            "all" -> true
            "unread" -> !it.read
            else -> it.src == f
        }
    }
    val idx = items.withIndex().associate { it.value.id to it.index }
    val sCount = list.groupingBy { it.src }.eachCount()
    val sorted = when (sortBy) {
        SortBy.Source -> list.sortedWith(
            compareByDescending<Item> { sCount[it.src] }.thenBy { it.src }.thenBy { it.daysAgo }.thenBy { idx[it.id] },
        )
        SortBy.Oldest -> list.sortedWith(compareByDescending<Item> { it.daysAgo }.thenByDescending { idx[it.id] })
        SortBy.Newest -> list.sortedWith(compareBy<Item> { it.daysAgo }.thenBy { idx[it.id] })
    }
    fun label(i: Item): String? {
        if (sortBy == SortBy.Source) return i.src
        val d = i.daysAgo
        return when (groupBy) {
            GroupBy.None -> null
            GroupBy.Week -> if (d < 7) s.thisWeek else if (d < 14) s.lastWeek else s.earlier
            GroupBy.Month -> s.months[monthOf(today - d)]
            GroupBy.Day -> when (d) {
                0 -> s.today
                1 -> s.yesterday
                else -> s.weekdays[weekdayOf(today - d)]
            }
        }
    }
    val rows = mutableListOf<Row>()
    var last: String? = "__"
    sorted.forEach {
        val lab = label(it)
        if (lab != null && lab != last) rows += Row.Header(lab)
        last = lab
        rows += Row.Card(it)
    }
    return rows
}

fun archiveRows(items: List<Item>, query: String): List<Row> {
    val q = query.trim().lowercase()
    val list = items.inArchive().filter { q.isEmpty() || "${it.title} ${it.site}".lowercase().contains(q) }
    return listOf("This month", "Earlier").flatMap { b ->
        val inB = list.filter { it.archMonth == b }
        if (inB.isEmpty()) emptyList() else listOf(Row.Header(b)) + inB.map { Row.Card(it) }
    }
}

/** Splits [text] around the first case-insensitive match of [q]: before, match, after. */
fun splitMatch(text: String, q: String): Triple<String, String, String> {
    val needle = q.trim()
    val at = if (needle.isEmpty()) -1 else text.lowercase().indexOf(needle.lowercase())
    return if (at == -1) Triple(text, "", "") else
        Triple(text.substring(0, at), text.substring(at, at + needle.length), text.substring(at + needle.length))
}

data class SearchResult(
    val chips: List<FilterChip>,
    val filter: String,
    val shelf: List<Item>,
    val archived: List<Item>,
    val total: Int,
    val summary: String,
)

fun search(items: List<Item>, query: String, sFilter: String): SearchResult {
    val q = query.trim().lowercase()
    fun hit(i: Item) = q.isNotEmpty() && "${i.title} ${i.site}".lowercase().contains(q)
    val shelfM = items.onShelf().filter(::hit)
    val archM = items.inArchive().filter(::hit)
    val matches = shelfM + archM
    val counts = sourceCounts(matches)
    val showArchChip = archM.isNotEmpty() && shelfM.isNotEmpty()
    val f = if (counts.any { it.first == sFilter } || (sFilter == "archive" && showArchChip)) sFilter else "all"
    val chips = listOf(FilterChip("all", "All · ${matches.size}")) +
        (if (counts.size > 1) counts.map { (k, n) -> FilterChip(k, "$k · $n") } else emptyList()) +
        (if (showArchChip) listOf(FilterChip("archive", "Archived · ${archM.size}")) else emptyList())
    fun keep(i: Item) = f == "all" || f == "archive" || i.src == f
    val sShelf = if (f == "archive") emptyList() else shelfM.filter(::keep)
    val sArch = archM.filter(::keep)
    val n = sShelf.size + sArch.size
    val summary = if (matches.isEmpty()) "" else buildString {
        append("$n ${if (n == 1) "result" else "results"} for “${query.trim()}”")
        if (f == "archive") append(" in your archive") else if (f != "all") append(" in $f")
        if (f == "all" && sArch.isNotEmpty()) append(" · ${sArch.size} archived")
    }
    return SearchResult(chips, f, sShelf, sArch, matches.size, summary)
}

private val urlRegex = Regex("""^(https?://)?([\w-]+\.)+[a-z]{2,}(:\d+)?(/\S*)?$""", RegexOption.IGNORE_CASE)

/** Error copy for a typed link, or null when it looks valid. */
fun urlError(raw: String): String? {
    val v = raw.trim()
    return when {
        v.isEmpty() -> "Paste or type a link first."
        !urlRegex.matches(v) -> "That doesn’t look like a link. Try one that starts with https://"
        else -> null
    }
}

fun hostOf(raw: String): String =
    raw.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').ifEmpty { raw }

/** Minutes parsed from meta like "6 min read"; 99 when unknown. */
fun minutesOf(i: Item): Int = Regex("""(\d+) min""").find(i.meta)?.groupValues?.get(1)?.toInt() ?: 99

/** The quickest unread item on the shelf, used by nudges and "Read something short". */
fun shortestUnread(items: List<Item>): Item? = items.onShelf().filter { !it.read }.minByOrNull(::minutesOf)

fun nextUp(items: List<Item>): List<Item> {
    val idx = items.withIndex().associate { it.value.id to it.index }
    return items.onShelf().filter { !it.read }.sortedWith(compareBy<Item> { it.daysAgo }.thenBy { idx[it.id] }).take(3)
}

fun hintText(swipeR: SwipeAction, swipeL: SwipeAction): String {
    val verb = mapOf(SwipeAction.Read to "mark read", SwipeAction.Archive to "archive", SwipeAction.Share to "share", SwipeAction.Remove to "remove")
    val parts = mutableListOf<String>()
    if (swipeL != SwipeAction.None) parts += "Swipe left to ${verb[swipeL]}"
    if (swipeR != SwipeAction.None) parts += (if (parts.isEmpty()) "Swipe right to " else "right to ") + verb[swipeR]
    return (if (parts.isEmpty()) "" else parts.joinToString(", ") + ". ") + "Long-press for more."
}

fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
