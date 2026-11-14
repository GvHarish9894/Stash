package com.techgv.stash.widget

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.WidgetItem
import com.techgv.stash.core.model.WidgetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StashWidgetDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun item(n: Int, minutes: Int? = 6, image: String? = null) =
        WidgetItem(ItemId("id-$n"), "Title $n", "site$n.com", ContentType.Article, minutes, image)

    private fun snapshot(vararg items: WidgetItem) = WidgetSnapshot(items.toList(), unreadCount = 5, streak = 12, readToday = true)

    private fun readerLink(id: String) = Intent(Intent.ACTION_VIEW, Uri.parse("stash://reader/$id")).setPackage(context.packageName)

    @Test
    fun publishStoresTheSnapshotTheWidgetReads() = runTest {
        val published = snapshot(item(1), item(2, minutes = null, image = "https://example.com/a.png"))
        GlanceWidgetPublisher(context).publish(published)
        assertEquals(published, WidgetSnapshotStore.read(context))

        val empty = WidgetSnapshot(emptyList(), unreadCount = 0, streak = 0, readToday = false)
        GlanceWidgetPublisher(context).publish(empty)
        assertEquals(empty, WidgetSnapshotStore.read(context))
    }

    @Test
    fun mediumEmptyShowsAllCaughtUp() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(StashWidget.MEDIUM)
        val shown = WidgetSnapshot(emptyList(), 0, 3, readToday = false)
        val text = runBlocking { widgetText(shown) }
        provideComposable { StashWidgetContent(shown, emptyMap(), text) }
        onNode(hasTextEqualTo("All caught up. Nothing waiting on your shelf.")).assertExists()
        onNode(hasTextEqualTo("· 0 unread")).assertExists()
        onNode(hasTextEqualTo("3")).assertExists()
    }

    @Test
    fun mediumWithOneItemShowsItsRowOpeningTheReader() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(StashWidget.MEDIUM)
        val shown = snapshot(item(1))
        val text = runBlocking { widgetText(shown) }
        provideComposable { StashWidgetContent(shown, emptyMap(), text) }
        onNode(hasTextEqualTo("Stash")).assertExists()
        onNode(hasTextEqualTo("12")).assertExists()
        onNode(hasTextEqualTo("· 5 unread")).assertExists()
        onNode(hasTextEqualTo("Title 1")).assertExists()
        onNode(hasTextEqualTo("site1.com · 6 min")).assertExists()
        onAllNodes(hasTextEqualTo("S")).assertCountEquals(2) // the app tile and the row's monogram (no thumbnail)
        onNode(hasStartActivityClickAction(readerLink("id-1"))).assertExists()
        // The header and background open MainActivity by class, never a launcher alias an icon change can disable.
        val mainActivity = Intent(Intent.ACTION_MAIN).setClassName(context.packageName, "com.techgv.stash.MainActivity")
        onNode(hasStartActivityClickAction(mainActivity)).assertExists()
        onNode(hasTextEqualTo("All caught up. Nothing waiting on your shelf.")).assertDoesNotExist()
    }

    @Test
    fun mediumShowsAtMostThreeRows() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(StashWidget.MEDIUM)
        val shown = snapshot(item(1), item(2, minutes = null), item(3), item(4))
        val text = runBlocking { widgetText(shown) }
        provideComposable { StashWidgetContent(shown, emptyMap(), text) }
        listOf("Title 1", "Title 2", "Title 3").forEach { onNode(hasTextEqualTo(it)).assertExists() }
        onNode(hasTextEqualTo("site2.com")).assertExists()
        onNode(hasTextEqualTo("Title 4")).assertDoesNotExist()
        listOf("id-1", "id-2", "id-3").forEach { onNode(hasStartActivityClickAction(readerLink(it))).assertExists() }
    }

    @Test
    fun smallShowsTheCountStreakAndNextTitle() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(StashWidget.SMALL)
        val shown = snapshot(item(1), item(2))
        val text = runBlocking { widgetText(shown) }
        provideComposable { StashWidgetContent(shown, emptyMap(), text) }
        onNode(hasTextEqualTo("5")).assertExists()
        onNode(hasTextEqualTo("unread")).assertExists()
        onNode(hasTextEqualTo("12")).assertExists()
        onNode(hasTextEqualTo("Title 1")).assertExists()
        onNode(hasTextEqualTo("Title 2")).assertDoesNotExist()
        onNode(hasStartActivityClickAction(readerLink("id-1"))).assertExists()
    }

    @Test
    fun smallEmptySaysAllCaughtUp() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(StashWidget.SMALL)
        val shown = WidgetSnapshot(emptyList(), 0, 4, readToday = false)
        val text = runBlocking { widgetText(shown) }
        provideComposable { StashWidgetContent(shown, emptyMap(), text) }
        onNode(hasTextEqualTo("0")).assertExists()
        onNode(hasTextEqualTo("4")).assertExists()
        onNode(hasTextEqualTo("All caught up")).assertExists()
    }

    @Test
    fun theWidgetTextFollowsTheAppLanguage() = runBlocking {
        val system = Locale.getDefault()
        try {
            // What the per-app language does to the process (see ReminderLanguageTest).
            Locale.setDefault(Locale.forLanguageTag("es"))
            val text = widgetText(snapshot())
            assertEquals("· 5 sin leer", text.unreadCount)
            assertEquals("sin leer", text.unreadLabel)
        } finally {
            Locale.setDefault(system)
        }
        assertEquals("· 5 unread", widgetText(snapshot()).unreadCount)
    }

    @Test
    fun aLanguageChangeRedrawsTheWidgetWithTheSameSnapshot() = runBlocking {
        val system = Locale.getDefault()
        GlanceWidgetPublisher(context).publish(snapshot(item(1)))
        // What a placed widget's session does: recompute its text on each emission (see StashWidget.provideGlance).
        val texts = Channel<String>(Channel.UNLIMITED)
        val session = launch(Dispatchers.Default) { WidgetSnapshotStore.updates(context).collect { texts.send(widgetText(it).unreadCount) } }
        try {
            assertEquals("· 5 unread", withTimeout(5_000) { texts.receive() })
            Locale.setDefault(Locale.forLanguageTag("es"))
            WidgetSnapshotStore.languageChanged(context)
            assertEquals("· 5 sin leer", withTimeout(5_000) { texts.receive() })
        } finally {
            session.cancel()
            Locale.setDefault(system)
            WidgetSnapshotStore.languageChanged(context)
        }
    }

    @Test
    fun aVeryLongThinImageIsDownsampledByItsLongerSide() {
        // 60000 × 200 decoded at full size is ~48 MB; by the longer side it is 234 × 1.
        assertEquals(256, WidgetThumbnails.sampleSize(60_000, 200))
        assertEquals(1, WidgetThumbnails.sampleSize(200, 200))
        val strip = Bitmap.createBitmap(20_000, 100, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        strip.recycle()
        val thumbnail = assertNotNull(WidgetThumbnails.decode(png))
        assertEquals(128 to 128, thumbnail.width to thumbnail.height)
    }
}
