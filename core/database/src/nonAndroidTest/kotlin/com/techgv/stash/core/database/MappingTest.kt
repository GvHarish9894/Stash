package com.techgv.stash.core.database

import androidx.room.useWriterConnection
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.LifecycleStamps
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.OperationPayload
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class MappingTest {
    private val database = inMemoryDatabase()

    @AfterTest
    fun close() = database.close()

    private val full = SavedItem(
        id = ItemId("0190f6a2-7c1e-7cc3-8a6f-3c1d2e4f5a6b"),
        url = "https://www.youtube.com/watch?v=abc&utm_source=x",
        normalizedUrl = "https://youtube.com/watch?v=abc",
        title = "Ünïcødé — हिंदी, தமிழ், 東京 \"quoted\"",
        description = "A description",
        imageUrl = "https://i.ytimg.com/abc.jpg",
        faviconUrl = "https://youtube.com/favicon.ico",
        siteName = "youtube.com",
        domain = "www.youtube.com",
        sourceKey = "YouTube",
        contentType = ContentType.Video,
        sourceApp = "com.google.android.youtube",
        readingMinutes = 12,
        addedAt = FIXTURE_NOW,
        readAt = FIXTURE_NOW + 1.days,
        archivedAt = FIXTURE_NOW + 2.days,
        deletedAt = FIXTURE_NOW + 3.days,
        fetchStatus = FetchStatus.Failed,
        updatedAt = FIXTURE_NOW + 3.days,
    )

    private val minimal = savedItem("minimal").copy(sourceApp = null, readingMinutes = null, fetchStatus = FetchStatus.Pending)

    @Test
    fun fullAndMinimalItemsSurviveTheDatabase() = runTest {
        database.insertItems(listOf(full, minimal))

        assertEquals(full, database.savedItemDao().get(full.id.value)?.toModel())
        assertEquals(minimal, database.savedItemDao().get("minimal")?.toModel())
        assertEquals(minimal, database.savedItemDao().getByNormalizedUrl(minimal.normalizedUrl)?.toModel())
    }

    @Test
    fun instantsKeepMillisecondPrecision() = runTest {
        val precise = FIXTURE_NOW + 123_456_789.nanoseconds
        database.insertItems(listOf(minimal.copy(addedAt = precise, updatedAt = precise)))

        val stored = database.savedItemDao().get("minimal")!!.toModel()

        assertEquals(Instant.fromEpochMilliseconds(precise.toEpochMilliseconds()), stored.addedAt)
        assertEquals(FIXTURE_NOW.toEpochMilliseconds() + 123, stored.addedAt.toEpochMilliseconds())
    }

    @Test
    fun everyPayloadVariantSurvivesTheJsonColumn() = runTest {
        val stamps = LifecycleStamps(FIXTURE_NOW, FIXTURE_NOW + 1.days, null, null)
        val archived = LifecycleStamps(FIXTURE_NOW, FIXTURE_NOW + 1.days, FIXTURE_NOW + 2.days, FIXTURE_NOW + 3.days)
        val operations = listOf(
            Operation("op-save", ItemId("a"), OperationType.Save, OperationPayload.Lifecycle(null, stamps), FIXTURE_NOW),
            Operation("op-archive", ItemId("a"), OperationType.Archive, OperationPayload.Lifecycle(stamps, archived), FIXTURE_NOW),
            Operation("op-retry", ItemId("a"), OperationType.RetryMetadata, OperationPayload.Metadata(FetchStatus.Failed, FetchStatus.Pending), FIXTURE_NOW),
            Operation("op-undo", ItemId("a"), OperationType.Undo, OperationPayload.Undo("op-archive", archived, stamps), FIXTURE_NOW + 1.days),
        )
        database.operationDao().insert(operations.map { it.toEntity() })

        for (operation in operations) {
            assertEquals(operation, database.operationDao().get(operation.opId)?.toModel())
        }
        assertEquals(
            """{"type":"undo","undone_op_id":"op-archive","before":{"added_at":${FIXTURE_NOW.toEpochMilliseconds()},"read_at":${(FIXTURE_NOW + 1.days).toEpochMilliseconds()},"archived_at":${(FIXTURE_NOW + 2.days).toEpochMilliseconds()},"deleted_at":${(FIXTURE_NOW + 3.days).toEpochMilliseconds()}},"after":{"added_at":${FIXTURE_NOW.toEpochMilliseconds()},"read_at":${(FIXTURE_NOW + 1.days).toEpochMilliseconds()},"archived_at":null,"deleted_at":null}}""",
            database.operationDao().get("op-undo")?.payloadJson,
            "the stored JSON shape is part of the schema",
        )
    }

    @Test
    fun aPayloadWithKeysANewerVersionAddedStillReads() = runTest {
        val json = """{"type":"metadata","before":"Failed","after":"Pending","note":1}"""
        database.operationDao().insert(listOf(OperationEntity("op-new", "a", OperationType.RetryMetadata, json, FIXTURE_NOW)))

        assertEquals(OperationPayload.Metadata(FetchStatus.Failed, FetchStatus.Pending), database.operationDao().get("op-new")?.toModel()?.payload)
    }

    @Test
    fun aRowWithEnumNamesThisVersionDoesNotKnowReadsWithDefaults() = runTest {
        val stored = minimal.copy(contentType = ContentType.Video, fetchStatus = FetchStatus.Fetched)
        database.insertItems(listOf(stored))
        database.useWriterConnection { connection ->
            connection.usePrepared("UPDATE saved_item SET content_type = 'Podcast', fetch_status = 'Queued' WHERE id = 'minimal'") { it.step() }
        }

        val read = database.savedItemDao().get("minimal")?.toModel()

        assertEquals(stored.copy(contentType = ContentType.Article, fetchStatus = FetchStatus.Pending), read)
        assertEquals(listOf("minimal"), database.savedItemDao().shelf(ShelfFilter.All, ShelfSort.Newest, null).loadAllIds(), "the page still loads")
    }
}
