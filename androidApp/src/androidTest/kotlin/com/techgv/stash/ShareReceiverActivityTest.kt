package com.techgv.stash

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.model.SavedItem
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Shares into the installed app the way another app does, and reads the result from the app's real database. */
class ShareReceiverActivityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val items = GlobalContext.get().get<ItemRepository>()

    /** [newTask] adds Chrome's always-a-new-task flag; without it a share reaches an open sheet's task. */
    private fun share(text: String, newTask: Boolean = false) = context.startActivity(
        Intent(Intent.ACTION_SEND)
            .setClassName(context, "com.techgv.stash.ShareReceiverActivity")
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (newTask) Intent.FLAG_ACTIVITY_MULTIPLE_TASK else 0),
    )

    private suspend fun shelfWith(url: String): List<SavedItem> = items.newestUnread(500).filter { it.url == url }

    private fun <T> waitFor(timeoutMs: Long, read: () -> T, done: (T) -> Boolean): T {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val value = read()
            if (done(value)) return value
            if (SystemClock.elapsedRealtime() > end) fail("timed out; last value $value")
            SystemClock.sleep(5)
        }
    }

    @Test
    fun aSharedLinkIsSavedOnceAndASecondShareKeepsOneItem() = runBlocking {
        val url = "https://example.com/share-activity-${System.currentTimeMillis()}"
        // Measure the share alone, not the main thread finishing an earlier test's work (a language change recreates UI).
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val started = SystemClock.elapsedRealtime()
        share("Look $url?utm_source=x")
        val saved = waitFor(5_000, { runBlocking { shelfWith(url) } }) { it.isNotEmpty() }.single()
        val elapsed = SystemClock.elapsedRealtime() - started
        println("Share: startActivity to committed save in $elapsed ms (warm process)")
        assertTrue(elapsed < 500, "saved in $elapsed ms")

        share("Again: $url")
        SystemClock.sleep(1_000)
        assertEquals(listOf(saved.id), shelfWith(url).map { it.id }, "no second item")

        GlobalContext.get().get<CommandBus>().dispatch(Command.Remove(saved.id))
        Unit
    }

    @Test
    fun aSecondShareWhileTheSheetIsOpenIsSavedToo() = runBlocking {
        val stamp = System.currentTimeMillis()
        val urls = listOf("https://example.com/first-$stamp", "https://example.com/second-$stamp")
        share(urls[0])
        waitFor(5_000, { runBlocking { shelfWith(urls[0]) } }) { it.isNotEmpty() }
        SystemClock.sleep(1_000)
        share(urls[1])
        val saved = urls.map { url -> waitFor(5_000, { runBlocking { shelfWith(url) } }) { it.isNotEmpty() }.single() }
        removeAll(saved)
    }

    @Test
    fun everyShareInABurstIsSavedExactlyOnce() = runBlocking {
        val stamp = System.currentTimeMillis()
        val urls = List(31) { "https://example.com/burst-$stamp-$it" }
        urls.forEachIndexed { i, url ->
            share(url, newTask = i % 2 == 0)
            SystemClock.sleep(100)
        }
        val saved = waitFor(15_000, { runBlocking { urls.flatMap { shelfWith(it) } } }) { it.size >= urls.size }
        assertEquals(urls.toSet(), saved.map { it.url }.toSet())
        SystemClock.sleep(1_000)
        assertEquals(urls.size, urls.flatMap { shelfWith(it) }.size, "each share saved once")
        removeAll(saved)
    }

    private suspend fun removeAll(saved: List<SavedItem>) {
        val bus = GlobalContext.get().get<CommandBus>()
        saved.forEach { bus.dispatch(Command.Remove(it.id)) }
    }

    @Test
    fun textWithoutALinkSavesNothing() = runBlocking {
        val before = items.newestUnread(500).map { it.id }
        share("Nothing to see here ${System.currentTimeMillis()}")
        SystemClock.sleep(1_000)
        assertEquals(before, items.newestUnread(500).map { it.id })
    }
}
