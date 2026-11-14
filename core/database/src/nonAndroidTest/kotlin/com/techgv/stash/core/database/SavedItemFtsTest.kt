package com.techgv.stash.core.database

import androidx.room.useWriterConnection
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SavedItemFtsTest {
    private val database = inMemoryDatabase()
    private val dao = database.savedItemDao()

    @AfterTest
    fun close() = database.close()

    /** Ids the FTS index itself returns, so a stale index row would show even when the item row is gone. */
    private suspend fun indexed(query: String): List<String> = database.useWriterConnection { connection ->
        connection.usePrepared(
            "SELECT s.id FROM saved_item_fts f LEFT JOIN saved_item s ON s.rowid = f.docid " +
                "WHERE saved_item_fts MATCH ? ORDER BY f.docid",
        ) { statement ->
            statement.bindText(1, checkNotNull(ftsMatch(SearchMatcher.tokens(query))).query)
            buildList { while (statement.step()) add(if (statement.isNull(0)) "<stale>" else statement.getText(0)) }
        }
    }

    private suspend fun integrityCheck() = database.useWriterConnection { connection ->
        connection.usePrepared("INSERT INTO saved_item_fts(saved_item_fts) VALUES('integrity-check')") { it.step() }
    }

    @Test
    fun indexFollowsInsertUpdateAndDelete() = runTest {
        database.insertItems(listOf(savedItem("a", title = "Swift tips", siteName = "medium.com")))
        assertEquals(listOf("a"), indexed("swi"))
        assertEquals(listOf("a"), indexed("medium"))

        dao.upsert(listOf(savedItem("a", title = "Kotlin notes", siteName = "medium.com").toEntity()))
        assertEquals(emptyList(), indexed("swi"))
        assertEquals(listOf("a"), indexed("kot"))
        integrityCheck()

        // Items are tombstoned in normal use; a physical delete must still leave no index row behind.
        database.useWriterConnection { connection -> connection.usePrepared("DELETE FROM saved_item WHERE id = 'a'") { it.step() } }
        assertEquals(emptyList(), indexed("kot"))
        assertEquals(emptyList(), indexed("medium"))
        integrityCheck()
    }

    @Test
    fun hindiAndTamilPrefixesMatchWholeLetterClusters() = runTest {
        database.insertItems(
            listOf(
                savedItem("hindi-news", title = "हिंदी समाचार", addedAt = FIXTURE_NOW),
                savedItem("hindi-both", title = "हम दोनों", addedAt = FIXTURE_NOW),
                savedItem("tamil-nadu", title = "தமிழ் நாடு", addedAt = FIXTURE_NOW),
                savedItem("tamil-other", title = "தமர் ழகரம்", addedAt = FIXTURE_NOW),
            ),
        )
        assertEquals(listOf("hindi-news"), indexed("हिं"))
        assertEquals(listOf("hindi-news"), indexed("समा"))
        assertEquals(listOf("tamil-nadu"), indexed("தமிழ்"))
        assertEquals(listOf("tamil-nadu", "tamil-other"), indexed("தம").sorted())
        // Without the marks as token characters these would match the letters after a vowel sign or virama.
        assertEquals(emptyList(), indexed("दी"), "an infix of हिंदी")
        assertEquals(emptyList(), indexed("ழ்"), "an infix of தமிழ்")
        // The same through the paged list the data layer uses.
        val shelf = dao.shelf(ShelfFilter.All, ShelfSort.Newest, ftsMatch(SearchMatcher.tokens("हिं"))).loadAllIds()
        assertEquals(listOf("hindi-news"), shelf)
    }

    @Test
    fun operatorWordsAndPunctuationAreSearchedAsText() = runTest {
        database.insertItems(
            listOf(savedItem("oracle", title = "The Oracle of order"), savedItem("near", title = "Near and far"), savedItem("cpp", title = "C++ in 2026")),
        )
        assertEquals(listOf("oracle"), indexed("OR"))
        assertEquals(listOf("near"), indexed("NEAR"))
        assertEquals(listOf("cpp"), indexed("c++ 2026"))
        assertEquals(emptyList(), indexed("racle"))
    }

    @Test
    fun emptyTokenListIssuesNoMatch() {
        assertEquals(null, ftsMatch(SearchMatcher.tokens("  ...  ")))
        assertEquals("\"swift*\" \"ui*\"", ftsMatch(SearchMatcher.tokens("Swift UI"))?.query)
    }
}
