package com.techgv.stash.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlin.time.Instant

/** The operation log keeps 30 days. */
internal const val OPERATION_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

@Dao
interface OperationDao {
    /** Append-only: a repeated op id fails instead of overwriting the log. */
    @Insert
    suspend fun insert(operations: List<OperationEntity>)

    @Query("SELECT * FROM operation WHERE op_id = :opId")
    suspend fun get(opId: String): OperationEntity?

    /** Deletes rows created more than 30 days before [now]; a row exactly 30 days old stays. Returns the count. */
    @Query("DELETE FROM operation WHERE created_at < :now - $OPERATION_RETENTION_MILLIS")
    suspend fun prune(now: Instant): Int
}
