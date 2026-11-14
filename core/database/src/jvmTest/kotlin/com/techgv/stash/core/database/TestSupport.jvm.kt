package com.techgv.stash.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.io.path.Path

actual fun temporaryDirectory(): String = System.getProperty("java.io.tmpdir")

actual fun deleteFile(path: String) {
    File(path).delete()
}

// Gradle runs JVM tests in the project directory.
actual fun schemaDirectory(): String = "schemas"

actual fun migrationTestHelper(databasePath: String): MigrationTestHelper = MigrationTestHelper(
    schemaDirectoryPath = Path(schemaDirectory()),
    databasePath = Path(databasePath),
    driver = BundledSQLiteDriver(),
    databaseClass = StashDatabase::class,
    databaseFactory = { StashDatabaseConstructor.initialize() },
)
