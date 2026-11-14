package com.techgv.stash

import android.os.SystemClock
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.model.SharedPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

/** What `ShareReceiverActivity` runs, against the real database. */
class ShareSaveDeviceTest {
    private fun payload(text: String?) = SharedPayload(text, subject = null, sourceApp = "com.android.chrome", at = Clock.System.now())

    @Test
    fun aLinkIsSavedOnceAndASecondShareMovesItToTheTop() = runTest {
        withStashKoin { koin ->
            val bus = koin.get<CommandBus>()
            val items = koin.get<StashDatabase>().savedItemDao()
            val path = "share-test-${System.currentTimeMillis()}"
            val shared = payload("Look https://example.com/$path?utm_source=x")
            val key = assertNotNull(LinkCapturePolicy.normalize("https://example.com/$path"))
            val unreadBefore = items.unreadCount()

            val started = SystemClock.elapsedRealtime()
            assertEquals(ShareOutcome.Saved, saveShared(shared, bus))
            println("Share: intent text to committed save in ${SystemClock.elapsedRealtime() - started} ms")
            val saved = assertNotNull(items.getByNormalizedUrl(key))
            assertEquals("https://example.com/$path", saved.url)
            assertEquals(unreadBefore + 1, items.unreadCount())

            assertEquals(ShareOutcome.AlreadyOnShelf, saveShared(shared, bus))
            assertEquals(saved.id, items.getByNormalizedUrl(key)?.id)
            assertEquals(unreadBefore + 1, items.unreadCount(), "no second item")
        }
    }

    @Test
    fun textWithoutALinkSavesNothing() = runTest {
        withStashKoin { koin ->
            val items = koin.get<StashDatabase>().savedItemDao()
            val unreadBefore = items.unreadCount()
            assertEquals(ShareOutcome.NothingToSave, saveShared(payload("Just some words, no link"), koin.get()))
            assertEquals(ShareOutcome.NothingToSave, saveShared(payload(null), koin.get()))
            assertEquals(unreadBefore, items.unreadCount())
        }
    }
}
