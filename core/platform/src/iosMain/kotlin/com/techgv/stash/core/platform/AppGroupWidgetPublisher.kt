package com.techgv.stash.core.platform

import com.techgv.stash.core.model.WidgetSnapshot
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * Writes the snapshot to `widget.json` in the App Group, then calls [reloadWidgets] (`WidgetCenter.reloadAllTimelines`,
 * handed in by the Swift app because WidgetKit has no Objective-C API).
 *
 * `widget.json` (mirrored by `WidgetSnapshotFile` in `iosApp/WidgetExtension/WidgetSnapshot.swift`):
 * `{"items": [{"id": String, "title": String, "siteName": String, "contentType": "Article" | "Video" | "Repository",
 * "readingMinutes": Int | null, "imageUrl": String | null}], "unreadCount": Int, "streak": Int, "readToday": Bool}`.
 * `items` holds at most the next 3 unread items; empty means "all caught up".
 */
class AppGroupWidgetPublisher(private val path: String, private val reloadWidgets: () -> Unit) : WidgetPublisher {

    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    override suspend fun publish(snapshot: WidgetSnapshot) {
        val json = buildJsonObject {
            putJsonArray("items") {
                snapshot.items.forEach {
                    addJsonObject {
                        put("id", it.id.value)
                        put("title", it.title)
                        put("siteName", it.siteName)
                        put("contentType", it.contentType.name)
                        put("readingMinutes", it.readingMinutes)
                        put("imageUrl", it.imageUrl)
                    }
                }
            }
            put("unreadCount", snapshot.unreadCount)
            put("streak", snapshot.streak)
            put("readToday", snapshot.readToday)
        }
        withContext(Dispatchers.IO) {
            // Atomic (temp file, then rename): the widget process may read while the app writes.
            val written = NSString.create(string = json.toString()).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            check(written) { "Could not write the widget snapshot" }
        }
        reloadWidgets()
    }
}
