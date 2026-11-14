package com.techgv.stash.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlannedNotificationDao {
    /** The last applied plan, soonest first. */
    @Query("SELECT * FROM planned_notification ORDER BY fire_at, id")
    suspend fun all(): List<PlannedNotificationEntity>

    @Transaction
    suspend fun replaceAll(plan: List<PlannedNotificationEntity>) {
        deleteAll()
        insert(plan)
    }

    @Query("DELETE FROM planned_notification")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(plan: List<PlannedNotificationEntity>)
}
