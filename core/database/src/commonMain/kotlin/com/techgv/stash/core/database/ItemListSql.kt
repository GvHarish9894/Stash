package com.techgv.stash.core.database

import androidx.room.RoomRawQuery
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.model.ShelfSort

internal const val ON_SHELF = "deleted_at IS NULL AND archived_at IS NULL"
internal const val IN_ARCHIVE = "deleted_at IS NULL AND archived_at IS NOT NULL"
internal const val NEWEST_FIRST = "added_at DESC, id DESC"

/**
 * [ON_SHELF] for keyset chunks walked in id order. The unary `+` keeps SQLite off the shelf and read_at indexes, so each
 * chunk seeks the primary key from the last id instead of sorting every shelf row again; the whole walk reads the table
 * once.
 */
internal const val CANDIDATE_ON_SHELF = "+deleted_at IS NULL AND +archived_at IS NULL"

/** An FTS4 `MATCH` expression. Only [ftsMatch] builds one, so raw user text never reaches `MATCH`. */
@kotlin.jvm.JvmInline
value class FtsMatch internal constructor(internal val query: String)

/**
 * The FTS4 query for search [tokens] from [SearchMatcher.tokens]: every token as a quoted prefix (decision D11).
 * Null for no tokens, which must issue no `MATCH` at all. Tokens hold only letters, digits and word marks, so the
 * quotes cannot be broken out of.
 */
fun ftsMatch(tokens: List<String>): FtsMatch? =
    if (tokens.isEmpty()) null else FtsMatch(tokens.joinToString(" ") { "\"$it*\"" })

/**
 * Builds every paged item list; the filter × sort matrix is dynamic SQL, so it lives only here. User values (source
 * key, FTS query) are always bound parameters, never part of the SQL text.
 * Orders match `ShelfQuery.comparator` and `ShelfQuery.archive` / `ShelfQuery.search`.
 */
internal object ItemListSql {
    private const val MATCHES = "rowid IN (SELECT docid FROM saved_item_fts WHERE saved_item_fts MATCH ?)"

    fun shelf(filter: ShelfFilter, sort: ShelfSort, match: String?): RoomRawQuery {
        val where = mutableListOf(ON_SHELF)
        val args = mutableListOf<String>()
        when (filter) {
            ShelfFilter.All -> Unit
            ShelfFilter.Unread -> where += "read_at IS NULL"
            is ShelfFilter.Source -> {
                where += "source_key = ?"
                args += filter.key
            }
        }
        val orderBy = when (sort) {
            ShelfSort.Newest -> NEWEST_FIRST
            ShelfSort.Oldest -> "added_at, id"
            ShelfSort.BySource -> return bySource(where, args, match)
        }
        return query(where, args, match, orderBy)
    }

    /**
     * Bigger sources first, counted over the filtered rows, then newest first within a source. The sources are ranked
     * once (one row each) in that order, and CROSS JOIN keeps them the outer loop, so each source's rows are read from
     * the source index. SQLite still sorts within each source (it does not trust the ranked rows to be distinct), so a
     * page sorts the sources up to it, not the whole shelf; Room's COUNT(*) around it still reads every filtered row.
     */
    private fun bySource(where: MutableList<String>, args: MutableList<String>, match: String?): RoomRawQuery {
        if (match != null) {
            where += MATCHES
            args += match
        }
        val filter = where.joinToString(" AND ")
        val sql = "SELECT saved_item.* FROM (SELECT source_key AS ranked_key, COUNT(*) AS ranked_count FROM saved_item " +
            "WHERE $filter GROUP BY source_key ORDER BY ranked_count DESC, ranked_key) " +
            "CROSS JOIN saved_item ON source_key = ranked_key WHERE $filter " +
            "ORDER BY ranked_count DESC, ranked_key, $NEWEST_FIRST"
        val bound = args + args
        return RoomRawQuery(sql) { statement -> bound.forEachIndexed { i, arg -> statement.bindText(i + 1, arg) } }
    }

    /** The archive screen: newest archived first. */
    fun archive(match: String?): RoomRawQuery =
        query(mutableListOf(IN_ARCHIVE), mutableListOf(), match, "archived_at DESC, id DESC")

    /** Archive search hits: newest added first, like every search result. */
    fun archiveSearch(match: String, sourceKey: String?): RoomRawQuery {
        val where = mutableListOf(IN_ARCHIVE)
        val args = mutableListOf<String>()
        if (sourceKey != null) {
            where += "source_key = ?"
            args += sourceKey
        }
        return query(where, args, match, NEWEST_FIRST)
    }

    private fun query(where: MutableList<String>, args: MutableList<String>, match: String?, orderBy: String): RoomRawQuery {
        if (match != null) {
            where += MATCHES
            args += match
        }
        val sql = "SELECT * FROM saved_item WHERE ${where.joinToString(" AND ")} ORDER BY $orderBy"
        return RoomRawQuery(sql) { statement -> args.forEachIndexed { i, arg -> statement.bindText(i + 1, arg) } }
    }
}
