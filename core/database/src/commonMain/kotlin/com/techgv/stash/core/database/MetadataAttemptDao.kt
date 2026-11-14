package com.techgv.stash.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MetadataAttemptDao {
    /** 0 when the item has no failed attempt since its last success or retry. */
    @Query("SELECT COALESCE((SELECT failed_attempts FROM metadata_attempt WHERE item_id = :itemId), 0)")
    suspend fun failedAttempts(itemId: String): Int

    @Upsert
    suspend fun upsert(attempt: MetadataAttemptEntity)

    @Query("DELETE FROM metadata_attempt WHERE item_id = :itemId")
    suspend fun reset(itemId: String)

    /**
     * Pending items to fetch, never attempted first (newest first), then least recently attempted, so a run of links
     * that keep failing cannot starve the others.
     */
    @Query(
        "SELECT s.id FROM saved_item s LEFT JOIN metadata_attempt a ON a.item_id = s.id " +
            "WHERE s.fetch_status = 'Pending' AND s.deleted_at IS NULL " +
            "ORDER BY a.last_attempt_at IS NOT NULL, a.last_attempt_at, s.added_at DESC, s.id DESC LIMIT :limit",
    )
    suspend fun pendingIds(limit: Int): List<String>
}
