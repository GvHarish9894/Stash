package com.techgv.stash.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlin.time.Instant

@Dao
interface ArticleContentDao {
    @Upsert
    suspend fun upsert(content: ArticleContentEntity)

    @Query("SELECT * FROM article_content WHERE item_id = :itemId")
    suspend fun get(itemId: String): ArticleContentEntity?

    @Query("UPDATE article_content SET last_opened_at = :at WHERE item_id = :itemId")
    suspend fun touch(itemId: String, at: Instant)

    /**
     * Keeps the most recently opened articles whose sizes add up to at most [maxBytes] and deletes the rest, least
     * recently opened first. Returns how many were deleted.
     */
    @Query(
        "DELETE FROM article_content WHERE item_id IN (SELECT item_id FROM (SELECT item_id, " +
            "SUM(html_bytes) OVER (ORDER BY last_opened_at DESC, item_id DESC) AS kept_bytes FROM article_content) " +
            "WHERE kept_bytes > :maxBytes)",
    )
    suspend fun evictBeyond(maxBytes: Long): Int
}
