package com.techgv.stash.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.techgv.stash.core.domain.RecentSearches
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Dao
interface RecentSearchDao {
    /** Newest first, at most [RecentSearches.MAX]. */
    @Query("SELECT * FROM recent_search ORDER BY used_at DESC")
    fun observe(): Flow<List<RecentSearchEntity>>

    /**
     * Applies [RecentSearches.add] (cap and case-insensitive de-dupe) to the stored list. The new query is stamped at
     * least 1 ms after the newest stored one, so it comes first even when the clock went backwards.
     */
    @Transaction
    suspend fun add(query: String, at: Instant) {
        val recent = all().map { it.toModel() }
        val stamp = recent.maxOfOrNull { it.usedAt + 1.milliseconds }?.let { maxOf(it, at) } ?: at
        val next = RecentSearches.add(recent, query, stamp)
        deleteAll()
        insert(next.map { it.toEntity() })
    }

    @Query("SELECT * FROM recent_search")
    suspend fun all(): List<RecentSearchEntity>

    @Query("DELETE FROM recent_search")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(searches: List<RecentSearchEntity>)
}
