package com.techgv.stash.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** Reads and writes the single `app_state` row; call the setter inside [writeTransaction] with the item change. */
@Dao
interface AppStateDao {
    /** Null when never set or while something is unread. */
    @Query("SELECT unread_zero_since FROM app_state WHERE id = 0")
    suspend fun unreadZeroSince(): Instant?

    @Query("SELECT unread_zero_since FROM app_state WHERE id = 0")
    fun observeUnreadZeroSince(): Flow<Instant?>

    @Query("INSERT OR REPLACE INTO app_state (id, unread_zero_since) VALUES (0, :at)")
    suspend fun setUnreadZeroSince(at: Instant?)
}
