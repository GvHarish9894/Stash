package com.techgv.stash.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.datetime.LocalDate

@Dao
interface StreakDayDao {
    @Upsert
    suspend fun upsert(days: List<StreakDayEntity>)

    /** Days from [from] to [to], both included, oldest first. */
    @Query("SELECT * FROM streak_day WHERE local_date BETWEEN :from AND :to ORDER BY local_date")
    suspend fun between(from: LocalDate, to: LocalDate): List<StreakDayEntity>
}
