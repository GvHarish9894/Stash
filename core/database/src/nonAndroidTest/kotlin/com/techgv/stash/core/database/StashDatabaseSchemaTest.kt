package com.techgv.stash.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * Schema version 1 is the only version, so there is no migration to test yet. These tests cover what applies today:
 * the database opens from an empty file and from a version-1 file that already holds data.
 */
class StashDatabaseSchemaTest {
    private val path = temporaryDatabasePath()

    @AfterTest
    fun deleteFiles() = deleteDatabaseFiles(path)

    @Test
    fun opensFromAnEmptyFile() = runTest {
        BundledSQLiteDriver().open(path).close() // creates a zero-byte file

        val database = fileDatabase(path)
        database.insertItems(listOf(savedItem("a")))

        assertEquals("a", database.savedItemDao().get("a")?.id)
        database.close()
    }

    @Test
    fun opensAVersion1FileWithDataBuiltFromTheExportedSchema() = runTest {
        val helper = migrationTestHelper(path)
        val connection = helper.createDatabase(1)
        connection.execSQL(
            "INSERT INTO saved_item (id, url, normalized_url, title, site_name, domain, source_key, content_type, " +
                "added_at, fetch_status, updated_at) VALUES ('a', 'https://example.com/a', 'https://example.com/a', " +
                "'हिंदी समाचार', 'example.com', 'example.com', 'example.com', 'Article', ${FIXTURE_NOW.toEpochMilliseconds()}, " +
                "'Fetched', ${FIXTURE_NOW.toEpochMilliseconds()})",
        )
        connection.execSQL("INSERT INTO app_state (id, unread_zero_since) VALUES (0, ${FIXTURE_NOW.toEpochMilliseconds()})")
        connection.close()

        val database = fileDatabase(path)

        assertEquals(savedItem("a", title = "हिंदी समाचार", readingMinutes = null), database.savedItemDao().get("a")?.toModel())
        assertEquals(FIXTURE_NOW, database.appStateDao().unreadZeroSince())
        val hits = database.savedItemDao().shelf(ShelfFilter.All, ShelfSort.Newest, ftsMatch(SearchMatcher.tokens("हिं"))).loadAllIds()
        assertEquals(listOf("a"), hits, "the exported schema creates the FTS table and its sync triggers")
        database.close()
    }

    @Test
    fun reopensAFileItWroteEarlier() = runTest {
        fileDatabase(path).apply {
            insertItems(listOf(savedItem("a")))
            close()
        }

        val reopened = fileDatabase(path)

        assertEquals(savedItem("a"), reopened.savedItemDao().get("a")?.toModel())
        reopened.close()
    }
}
