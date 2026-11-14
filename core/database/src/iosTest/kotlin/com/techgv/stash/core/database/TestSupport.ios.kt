package com.techgv.stash.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.getenv

actual fun temporaryDirectory(): String = NSTemporaryDirectory()

@OptIn(ExperimentalForeignApi::class)
actual fun deleteFile(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, null)
}

// Set by the iosSimulatorArm64Test task (SIMCTL_CHILD_ prefix); the simulator reads the host file system.
@OptIn(ExperimentalForeignApi::class)
actual fun schemaDirectory(): String = checkNotNull(getenv("ROOM_SCHEMA_DIR")?.toKString()) { "ROOM_SCHEMA_DIR is not set" }

actual fun migrationTestHelper(databasePath: String): MigrationTestHelper = MigrationTestHelper(
    schemaDirectoryPath = schemaDirectory(),
    fileName = databasePath,
    driver = BundledSQLiteDriver(),
    databaseClass = StashDatabase::class,
    databaseFactory = { StashDatabaseConstructor.initialize() },
)
