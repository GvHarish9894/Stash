package com.techgv.stash.core.database

import com.techgv.stash.core.testing.savedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager

class StashDatabaseBuilderIosTest {
    @Test
    fun opensTheDatabaseFileInApplicationSupport() = runTest {
        val path = applicationSupportPath() + "/$STASH_DATABASE_NAME"
        deleteDatabaseFiles(path)
        val database = stashDatabaseBuilder().buildStashDatabase()

        database.insertItems(listOf(savedItem("ios-file")))

        assertEquals("ios-file", database.savedItemDao().get("ios-file")?.id)
        database.close()
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
    }
}
