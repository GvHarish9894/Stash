package com.techgv.stash.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.dataUsingEncoding
import platform.Foundation.stringWithContentsOfFile

/** A fresh directory under the test process's temporary directory. */
fun tempDirectory(): String = (NSTemporaryDirectory() + "stash-test-" + NSUUID().UUIDString).also(::createDirectory)

@OptIn(ExperimentalForeignApi::class)
fun createDirectory(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
}

@OptIn(ExperimentalForeignApi::class)
fun readText(path: String): String? = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

@Suppress("CAST_NEVER_SUCCEEDS")
fun writeText(path: String, text: String) {
    NSFileManager.defaultManager.createFileAtPath(path, contents = (text as NSString).dataUsingEncoding(NSUTF8StringEncoding), attributes = null)
}

/** A fixture in iosApp/StashTests/Fixtures, which the Swift tests also use (path set by shared/build.gradle.kts). */
fun swiftFixture(name: String): String =
    checkNotNull(NSProcessInfo.processInfo.environment["STASH_FIXTURES"] as? String) { "STASH_FIXTURES not set" } + "/" + name
