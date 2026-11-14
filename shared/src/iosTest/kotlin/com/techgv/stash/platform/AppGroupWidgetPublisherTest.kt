package com.techgv.stash.platform

import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.WidgetItem
import com.techgv.stash.core.model.WidgetSnapshot
import com.techgv.stash.core.platform.AppGroupWidgetPublisher
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.NSJSONSerialization
import platform.Foundation.dataWithContentsOfFile
import kotlin.test.Test
import kotlin.test.assertEquals

class AppGroupWidgetPublisherTest {
    private val path = tempDirectory() + "/widget.json"

    @Test
    fun writesTheSnapshotTheWidgetFixtureDescribesThenReloads() = runTest {
        val events = mutableListOf<String>()
        val publisher = AppGroupWidgetPublisher(path) { events += if (readText(path) == null) "reload before write" else "reload" }
        publisher.publish(
            WidgetSnapshot(
                items = listOf(
                    WidgetItem(
                        ItemId("019a7b2c-5e10-7d3a-9c41-2f6e8b1d0a01"), "The quiet return of serif type on screens",
                        "example-blog.com", ContentType.Article, 5, "https://example-blog.com/serif.png",
                    ),
                    WidgetItem(
                        ItemId("019a7b2c-5e10-7d3a-9c41-2f6e8b1d0a02"), "Bookbinding by hand — “Coptic” stitch, ✂️ and all",
                        "youtube.com", ContentType.Video, null, null,
                    ),
                ),
                unreadCount = 7,
                streak = 12,
                readToday = true,
            ),
        )
        assertEquals(listOf("reload"), events)
        // The Swift widget tests decode the same fixture.
        assertEquals(json(swiftFixture("widget.json")), json(path))
        // Foundation compares true and 1 as equal, but Swift's JSONDecoder only reads a JSON boolean as Bool.
        assertEquals(true, readText(path)?.contains("\"readToday\":true"), readText(path))
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun json(file: String): Any? = NSJSONSerialization.JSONObjectWithData(checkNotNull(NSData.dataWithContentsOfFile(file)), 0u, null)
}
