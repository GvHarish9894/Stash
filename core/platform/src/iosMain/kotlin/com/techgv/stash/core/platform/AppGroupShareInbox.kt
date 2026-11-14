package com.techgv.stash.core.platform

import com.techgv.stash.core.model.SharedPayload
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileModificationDate
import platform.Foundation.timeIntervalSinceNow
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import kotlin.time.Instant

/**
 * The Share Extension's inbox: the [directory] `inbox/` in the App Group, one file per share (decision D26, instead of
 * one appended `inbox.jsonl`: no file coordination and no lock held by a process iOS may suspend).
 *
 * The extension (`iosApp/ShareExtension/InboxWriter.swift`) writes each payload to a dot-prefixed temp name and renames
 * it to `<epoch millis, 15 digits>-<UUID>.json`, so a file with a final name is always complete and names sort in the
 * order shared. Each file holds `{"text": String?, "subject": String?, "sourceApp": String?, "at": "ISO-8601 UTC"}`.
 *
 * [drain] hands the files over in name order and deletes each only after [save] returns; a throwing [save] leaves that
 * file and the later ones. It lists the directory again after every file, so files shared during the drain are
 * delivered now when they sort after the current one, otherwise by the next drain. A file that is not a JSON object
 * with a valid `at` is deleted without a save; a file that cannot be read is left for the next drain. Each drain first
 * deletes what never becomes a share: temp files older than [STALE_TEMP_SECONDS] (an extension killed between write
 * and rename) and names that are not `.json`.
 */
class AppGroupShareInbox(private val directory: String) : ShareInbox {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun drain(save: suspend (SharedPayload) -> Unit) {
        withContext(Dispatchers.IO) { deleteLeftovers() }
        var last = ""
        while (true) {
            val name = withContext(Dispatchers.IO) { entryNames().firstOrNull { it > last } } ?: return
            last = name
            val path = "$directory/$name"
            val bytes = withContext(Dispatchers.IO) { NSData.dataWithContentsOfFile(path) }?.toByteArray() ?: continue
            parse(bytes.decodeToString())?.let { save(it) }
            // A file that could not be deleted is delivered again by the next drain; the save path dedupes.
            withContext(Dispatchers.IO) { NSFileManager.defaultManager.removeItemAtPath(path, null) }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun deleteLeftovers() {
        val files = NSFileManager.defaultManager
        files.contentsOfDirectoryAtPath(directory, null).orEmpty().filterIsInstance<String>().forEach { name ->
            val path = "$directory/$name"
            val leftover = if (name.startsWith(".")) {
                val modified = files.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate
                modified != null && -modified.timeIntervalSinceNow > STALE_TEMP_SECONDS
            } else {
                !name.endsWith(".json")
            }
            if (leftover) files.removeItemAtPath(path, null)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun entryNames(): List<String> =
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, null).orEmpty()
            .filterIsInstance<String>()
            .filter { !it.startsWith(".") && it.endsWith(".json") }
            .sorted()

    companion object {
        /** A temp file this old belongs to an extension that died before renaming it. */
        const val STALE_TEMP_SECONDS = 60.0 * 60

        private fun parse(text: String): SharedPayload? {
            val json = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
            fun field(name: String) = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val at = field("at")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
            return SharedPayload(field("text"), field("subject"), field("sourceApp"), at)
        }

        @OptIn(ExperimentalForeignApi::class)
        private fun NSData.toByteArray(): ByteArray = bytes?.readBytes(length.toInt()) ?: ByteArray(0)
    }
}
