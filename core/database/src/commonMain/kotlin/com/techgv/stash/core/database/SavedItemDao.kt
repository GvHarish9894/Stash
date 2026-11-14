package com.techgv.stash.core.database

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Transaction
import androidx.room.Update
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.SourceCount
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ShelfSort
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** Thrown by [SavedItemDao.upsert] when another item already has the normalized URL; nothing is written. */
class NormalizedUrlTakenException(val normalizedUrl: String, val existingId: String) :
    IllegalStateException("Item $existingId already has the normalized URL $normalizedUrl")

data class ShelfCounts(val total: Int, val unread: Int)

/** Search hits of one source; [archived] of them are in the archive. */
data class SearchSourceCount(@ColumnInfo(name = "source_key") val sourceKey: String, val count: Int, val archived: Int)

/**
 * Saved items. No function loads the whole item set: lists are paged, counts are SQL aggregates and bulk commands read
 * candidates in keyset chunks. Search takes an [FtsMatch], which only [ftsMatch] builds; raw SQL entry points are internal.
 */
@Dao
abstract class SavedItemDao {
    // Writes

    /**
     * Inserts or updates each item by id. Never uses REPLACE: it deletes the old row, and that delete cascades to the
     * item's cached `article_content` (the FTS triggers would cope, as Room enables recursive triggers). Throws
     * [NormalizedUrlTakenException] when an item's normalized URL belongs to another item.
     */
    @Transaction
    open suspend fun upsert(items: List<SavedItemEntity>) {
        for (item in items) {
            val owner = idWithNormalizedUrl(item.normalizedUrl)
            if (owner != null && owner != item.id) throw NormalizedUrlTakenException(item.normalizedUrl, owner)
            if (update(item) == 0) insert(item)
        }
    }

    // Raw writes skip the normalized-URL check: only upsert may call them.
    @Insert
    internal abstract suspend fun insert(item: SavedItemEntity)

    @Update
    internal abstract suspend fun update(item: SavedItemEntity): Int

    @Query("SELECT id FROM saved_item WHERE normalized_url = :normalizedUrl")
    internal abstract suspend fun idWithNormalizedUrl(normalizedUrl: String): String?

    // Lookups

    @Query("SELECT * FROM saved_item WHERE id = :id")
    abstract suspend fun get(id: String): SavedItemEntity?

    @Query("SELECT * FROM saved_item WHERE id = :id")
    abstract fun observe(id: String): Flow<SavedItemEntity?>

    /** Includes deleted items, so a re-share finds them. */
    @Query("SELECT * FROM saved_item WHERE normalized_url = :normalizedUrl")
    abstract suspend fun getByNormalizedUrl(normalizedUrl: String): SavedItemEntity?

    /** Like `ReminderPlanner.shortestUnread`: fewest minutes, unknown minutes last, then newest. */
    @Query(
        "SELECT * FROM saved_item WHERE $ON_SHELF AND read_at IS NULL " +
            "ORDER BY reading_minutes IS NULL, reading_minutes, $NEWEST_FIRST LIMIT 1",
    )
    abstract suspend fun shortestUnread(): SavedItemEntity?

    @Query("SELECT * FROM saved_item WHERE $ON_SHELF AND read_at IS NULL ORDER BY $NEWEST_FIRST LIMIT :limit")
    abstract suspend fun newestUnread(limit: Int): List<SavedItemEntity>

    @Query("SELECT COUNT(*) FROM saved_item WHERE $ON_SHELF AND read_at IS NULL")
    abstract suspend fun unreadCount(): Int

    /** Ids of items that are not deleted and have [status], newest first: pending or failed metadata to fetch. */
    @Query("SELECT id FROM saved_item WHERE fetch_status = :status AND deleted_at IS NULL ORDER BY $NEWEST_FIRST LIMIT :limit")
    abstract suspend fun idsWithFetchStatus(status: FetchStatus, limit: Int): List<String>

    /** Read items on the shelf read at or before [cutoff] (`AutoArchivePolicy.cutoff`), after [afterId] in id order. */
    @Query("SELECT * FROM saved_item WHERE $CANDIDATE_ON_SHELF AND +read_at <= :cutoff AND id > :afterId ORDER BY id LIMIT :limit")
    abstract suspend fun autoArchiveCandidates(cutoff: Instant, afterId: String, limit: Int): List<SavedItemEntity>

    @Query("SELECT COUNT(*) FROM saved_item WHERE $ON_SHELF AND read_at <= :cutoff")
    abstract suspend fun countAutoArchiveDue(cutoff: Instant): Int

    /** Read items on the shelf after [afterId] in id order; pass "" first, then the last id of the previous chunk. */
    @Query("SELECT * FROM saved_item WHERE $CANDIDATE_ON_SHELF AND +read_at IS NOT NULL AND id > :afterId ORDER BY id LIMIT :limit")
    abstract suspend fun clearReadCandidates(afterId: String, limit: Int): List<SavedItemEntity>

    // Paged lists

    /**
     * The shelf for an applied filter (after `ShelfQuery.effectiveFilter`), ordered like `ShelfQuery.comparator`.
     * Shelf search hits are `shelf(All or Source(key), ShelfSort.Newest, match)`.
     */
    fun shelf(filter: ShelfFilter, sort: ShelfSort, match: FtsMatch?): PagingSource<Int, SavedItemEntity> =
        itemList(ItemListSql.shelf(filter, sort, match?.query))

    /** The archive screen like `ShelfQuery.archive`. */
    fun archive(match: FtsMatch?): PagingSource<Int, SavedItemEntity> = itemList(ItemListSql.archive(match?.query))

    /** Archive search hits like `ShelfQuery.search(...).archive`, optionally for one source. */
    fun archiveSearch(match: FtsMatch, sourceKey: String?): PagingSource<Int, SavedItemEntity> =
        itemList(ItemListSql.archiveSearch(match.query, sourceKey))

    /** The query must come from [ItemListSql]. */
    @RawQuery(observedEntities = [SavedItemEntity::class])
    internal abstract fun itemList(query: RoomRawQuery): PagingSource<Int, SavedItemEntity>

    // Counts

    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(read_at IS NULL), 0) AS unread FROM saved_item WHERE $ON_SHELF")
    abstract fun observeShelfCounts(): Flow<ShelfCounts>

    /** Sources with at least 2 items on the shelf, by count, then newest, then key (see `ShelfQuery.chipCounts`). */
    @Query(
        "SELECT source_key AS `key`, COUNT(*) AS count FROM saved_item WHERE $ON_SHELF GROUP BY source_key " +
            "HAVING COUNT(*) >= 2 ORDER BY count DESC, MAX(added_at) DESC, source_key",
    )
    abstract fun observeShelfSources(): Flow<List<SourceCount>>

    @Query("SELECT COUNT(*) FROM saved_item WHERE $IN_ARCHIVE")
    abstract fun observeArchiveCount(): Flow<Int>

    /** Search hits (shelf and archive) per source, by count, then newest, then key (see `ShelfQuery.search`). */
    @Query(
        "SELECT source_key, COUNT(*) AS count, SUM(archived_at IS NOT NULL) AS archived FROM saved_item " +
            "WHERE deleted_at IS NULL AND rowid IN (SELECT docid FROM saved_item_fts WHERE saved_item_fts MATCH :match) " +
            "GROUP BY source_key ORDER BY count DESC, MAX(added_at) DESC, source_key",
    )
    internal abstract fun searchSources(match: String): Flow<List<SearchSourceCount>>

    fun observeSearchSources(match: FtsMatch): Flow<List<SearchSourceCount>> = searchSources(match.query)
}
