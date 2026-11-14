package com.techgv.stash.core.database

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteException
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.OperationPayload
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.lifecycleStamps
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** An item write, its operation and the app-state update commit or roll back together. Uses a WAL file database. */
class WriteTransactionTest {
    private val path = temporaryDatabasePath()
    private val database = fileDatabase(path)
    private val items = database.savedItemDao()
    private val operations = database.operationDao()
    private val appState = database.appStateDao()

    private val saved = savedItem("a")
    private val operation = Operation("op-1", ItemId("a"), OperationType.Save, OperationPayload.Lifecycle(null, saved.lifecycleStamps), FIXTURE_NOW)
    private val earlier = FIXTURE_NOW - 1.hours

    @AfterTest
    fun close() {
        database.close()
        deleteDatabaseFiles(path)
    }

    private data class Snapshot(val item: SavedItemEntity?, val operation: OperationEntity?, val unreadZeroSince: Instant?)

    /** Reads all three in one read transaction on a reader connection. */
    private suspend fun snapshot() = database.useReaderConnection { connection ->
        connection.deferredTransaction { Snapshot(items.get("a"), operations.get("op-1"), appState.unreadZeroSince()) }
    }

    private suspend fun writeAll() {
        items.upsert(listOf(saved.toEntity()))
        operations.insert(listOf(operation.toEntity()))
        appState.setUnreadZeroSince(FIXTURE_NOW)
    }

    @Test
    fun commitsTogether() = runTest {
        database.writeTransaction { writeAll() }

        assertEquals(Snapshot(saved.toEntity(), operation.toEntity(), FIXTURE_NOW), snapshot())
    }

    @Test
    fun rollsBackTogetherWhenTheBlockThrows() = runTest {
        appState.setUnreadZeroSince(earlier)

        assertFailsWith<IllegalStateException> {
            database.writeTransaction {
                writeAll()
                error("decider failed after the writes")
            }
        }

        assertEquals(Snapshot(null, null, earlier), snapshot())
    }

    @Test
    fun aConcurrentReaderSeesNothingOrEverything() = runTest {
        appState.setUnreadZeroSince(earlier)
        val itemWritten = CompletableDeferred<Unit>()
        val readerDone = CompletableDeferred<Unit>()
        val writer = launch {
            database.writeTransaction {
                items.upsert(listOf(saved.toEntity()))
                itemWritten.complete(Unit)
                readerDone.await()
                operations.insert(listOf(operation.toEntity()))
                appState.setUnreadZeroSince(FIXTURE_NOW)
            }
        }

        itemWritten.await()
        val during = snapshot()
        readerDone.complete(Unit)
        writer.join()

        assertEquals(Snapshot(null, null, earlier), during)
        assertEquals(Snapshot(saved.toEntity(), operation.toEntity(), FIXTURE_NOW), snapshot())
    }

    @Test
    fun aTakenNormalizedUrlThrowsATypedExceptionAndRollsBack() = runTest {
        database.insertItems(listOf(saved))
        val duplicate = savedItem("b").copy(normalizedUrl = saved.normalizedUrl)

        val error = assertFailsWith<NormalizedUrlTakenException> {
            database.writeTransaction {
                operations.insert(listOf(operation.toEntity()))
                items.upsert(listOf(duplicate.toEntity()))
            }
        }

        assertEquals("a", error.existingId)
        assertEquals(saved.normalizedUrl, error.normalizedUrl)
        assertEquals(Snapshot(saved.toEntity(), null, null), snapshot())
        assertEquals(null, items.get("b"))
    }

    @Test
    fun cancellingTheCallerDoesNotRollBackTheBlock() = runTest {
        val firstWritten = CompletableDeferred<Unit>()
        val caller = launch(Dispatchers.Default) {
            database.writeTransaction {
                items.upsert(listOf(saved.toEntity()))
                firstWritten.complete(Unit)
                delay(300)
                operations.insert(listOf(operation.toEntity()))
            }
        }
        firstWritten.await()

        caller.cancelAndJoin()
        assertEquals(true, caller.isCancelled)
        // The block runs to completion under Room's own job; poll until it has committed.
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (operations.get("op-1") == null) delay(20) } }

        assertEquals(Snapshot(saved.toEntity(), operation.toEntity(), null), snapshot(), "both writes committed")
    }

    @Test
    fun aTransactionTakesTheWriteLockBeforeItReads() = runTest {
        // Two database objects on one file have two writer connections, like two processes. Each transaction reads
        // a counter and writes it back plus one. IMMEDIATE makes the second wait at BEGIN and read the first's value;
        // a DEFERRED transaction would read the old value and fail when it tries to write.
        val other = fileDatabase(path)
        appState.setUnreadZeroSince(Instant.fromEpochMilliseconds(0))
        suspend fun StashDatabase.increment(firstRead: CompletableDeferred<Unit>?) = writeTransaction {
            val value = appStateDao().unreadZeroSince()!!.toEpochMilliseconds()
            firstRead?.complete(Unit)
            delay(200)
            appStateDao().setUnreadZeroSince(Instant.fromEpochMilliseconds(value + 1))
        }
        val firstRead = CompletableDeferred<Unit>()

        withContext(Dispatchers.Default) {
            val first = launch { database.increment(firstRead) }
            firstRead.await()
            other.increment(null)
            first.join()
        }

        assertEquals(2, appState.unreadZeroSince()!!.toEpochMilliseconds())
        other.close()
    }

    @Test
    fun aRepeatedOperationIdFails() = runTest {
        operations.insert(listOf(operation.toEntity()))

        assertFailsWith<SQLiteException> { operations.insert(listOf(operation.toEntity())) }
    }
}
