package com.techgv.stash.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.techgv.stash.core.model.SavedItem
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** A writable temporary directory on the host (JVM) or in the simulator sandbox (iOS). */
expect fun temporaryDirectory(): String

expect fun deleteFile(path: String)

/** Where Room exported the schemas; MigrationTestHelper reads them. */
expect fun schemaDirectory(): String

/**
 * Room's migration harness for a file database at [databasePath]: `createDatabase(n)` builds schema version n from
 * `schemas/`, and `runMigrationsAndValidate` is where the first v1 -> v2 migration gets its test.
 */
expect fun migrationTestHelper(databasePath: String): MigrationTestHelper

fun inMemoryDatabase(): StashDatabase = Room.inMemoryDatabaseBuilder<StashDatabase>().buildStashDatabase()

fun temporaryDatabasePath(): String = temporaryDirectory().trimEnd('/') + "/stash-test-${Random.nextLong().toULong()}.db"

fun fileDatabase(path: String): StashDatabase = Room.databaseBuilder<StashDatabase>(path).buildStashDatabase()

fun deleteDatabaseFiles(path: String) = listOf("", "-wal", "-shm", "-journal").forEach { deleteFile(path + it) }

suspend fun StashDatabase.insertItems(items: List<SavedItem>) =
    items.chunked(500).forEach { chunk -> writeTransaction { savedItemDao().upsert(chunk.map { it.toEntity() }) } }

/** Every page of [this] in order, the way a pager appends them. */
suspend fun PagingSource<Int, SavedItemEntity>.loadAllIds(pageSize: Int = 30): List<String> {
    val ids = ArrayList<String>()
    var key: Int? = null
    do {
        val params = if (key == null) {
            PagingSource.LoadParams.Refresh<Int>(null, pageSize, false)
        } else {
            PagingSource.LoadParams.Append(key, pageSize, false)
        }
        val page = load(params) as PagingSource.LoadResult.Page
        ids += page.data.map { it.id }
        key = page.nextKey
    } while (key != null)
    return ids
}

suspend fun PagingSource<Int, SavedItemEntity>.firstPage(pageSize: Int = 30): PagingSource.LoadResult.Page<Int, SavedItemEntity> =
    load(PagingSource.LoadParams.Refresh(null, pageSize, false)) as PagingSource.LoadResult.Page

/** Rows of `EXPLAIN QUERY PLAN` for [sql], each the plan step's detail text. */
suspend fun RoomDatabase.explain(sql: String): List<String> = useReaderConnection { connection ->
    connection.usePrepared("EXPLAIN QUERY PLAN $sql") { statement ->
        buildList { while (statement.step()) add(statement.getText(3)) }
    }
}

/**
 * Records the SQL Room prepares, so tests can explain exactly what a DAO call ran. Only for in-memory databases: Room
 * gives them a single connection. Room's invalidation tracker prepares its own statements on another thread after a
 * write, hence the atomic list.
 */
@OptIn(ExperimentalAtomicApi::class)
class RecordingDriver(private val driver: SQLiteDriver = BundledSQLiteDriver()) : SQLiteDriver {
    private val recorded = AtomicReference(emptyList<String>())

    val statements: List<String> get() = recorded.load()

    fun clear() = recorded.store(emptyList())

    override fun open(fileName: String): SQLiteConnection = RecordingConnection(driver.open(fileName))

    private inner class RecordingConnection(private val connection: SQLiteConnection) : SQLiteConnection by connection {
        override fun prepare(sql: String) = connection.prepare(sql).also {
            while (true) {
                val before = recorded.load()
                if (recorded.compareAndSet(before, before + sql)) break
            }
        }
    }
}

fun recordingInMemoryDatabase(driver: RecordingDriver): StashDatabase =
    Room.inMemoryDatabaseBuilder<StashDatabase>().setDriver(driver).setQueryCoroutineContext(Dispatchers.IO).build()
