package com.techgv.stash.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

internal const val STASH_DATABASE_NAME = "stash.db"

// No release has shipped: the schema stays at version 1 until one does. Never add a destructive-migration fallback.
// The v1 schema changed in place on 14 Nov 2026 (saved_item indexes, for the 50,000-item target): Room refuses to open a
// database file from before that, so every existing debug install must be reinstalled or have its data cleared.
@Database(
    entities = [
        SavedItemEntity::class,
        SavedItemFtsEntity::class,
        ArticleContentEntity::class,
        MetadataAttemptEntity::class,
        OperationEntity::class,
        StreakDayEntity::class,
        PlannedNotificationEntity::class,
        RecentSearchEntity::class,
        AppStateEntity::class,
    ],
    version = 1,
)
@TypeConverters(StashConverters::class)
@ConstructedBy(StashDatabaseConstructor::class)
abstract class StashDatabase : RoomDatabase() {
    abstract fun savedItemDao(): SavedItemDao
    abstract fun operationDao(): OperationDao
    abstract fun appStateDao(): AppStateDao
    abstract fun streakDayDao(): StreakDayDao
    abstract fun plannedNotificationDao(): PlannedNotificationDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun articleContentDao(): ArticleContentDao
    abstract fun metadataAttemptDao(): MetadataAttemptDao
}

// Room's KSP processor generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
expect object StashDatabaseConstructor : RoomDatabaseConstructor<StashDatabase> {
    override fun initialize(): StashDatabase
}

/** Tests may pass their dispatcher as [queryContext] so database work runs on the test scheduler. */
fun RoomDatabase.Builder<StashDatabase>.buildStashDatabase(queryContext: CoroutineContext = Dispatchers.IO): StashDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryContext)
        .build()

/**
 * Runs [block] in one IMMEDIATE transaction on the writer connection. Every DAO call made inside [block] joins it, so
 * an item write, its operation rows and the app-state update commit together, or all roll back when [block] throws.
 * Readers on other connections see the state before or after the whole block, never a part of it.
 *
 * Cancelling the caller does NOT roll back: Room runs [block] under its own job, so it runs to completion and commits
 * while holding the only writer, and every other write waits. So [block] must contain only database work and pure
 * computation: no network, no settings writes, no waiting on events, no concurrent child coroutines.
 */
suspend fun <R> StashDatabase.writeTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }

/** Instants are stored as epoch milliseconds (UTC); dates as ISO text, which sorts like the date. */
internal object StashConverters {
    @TypeConverter
    fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun millisToInstant(millis: Long?): Instant? = millis?.let(Instant::fromEpochMilliseconds)

    // A name this version does not know (written by a newer one, before a downgrade) reads as the default, so one row
    // cannot fail a whole page. Renaming a constant has the same effect on existing rows: keep the stored names.
    @TypeConverter
    fun contentTypeToName(type: ContentType): String = type.name

    @TypeConverter
    fun nameToContentType(name: String): ContentType = ContentType.entries.firstOrNull { it.name == name } ?: ContentType.Article

    @TypeConverter
    fun fetchStatusToName(status: FetchStatus): String = status.name

    /** An unknown status reads as pending, so the metadata is simply fetched again. */
    @TypeConverter
    fun nameToFetchStatus(name: String): FetchStatus = FetchStatus.entries.firstOrNull { it.name == name } ?: FetchStatus.Pending

    @TypeConverter
    fun dateToText(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun textToDate(text: String?): LocalDate? = text?.let(LocalDate::parse)
}
