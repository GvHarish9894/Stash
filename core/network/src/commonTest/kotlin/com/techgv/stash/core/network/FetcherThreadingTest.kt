package com.techgv.stash.core.network

import com.techgv.stash.core.model.ReaderBlock
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Decoding, parsing and extraction must not run on the caller's (main) thread. The caller here is a dispatcher the
 * test runs by hand, one slice at a time; work moved off it leaves the caller exactly two slices: the start, which ends
 * at the switch, and the resume with the finished result. Nothing the work needs can finish during the first slice
 * (the network waits for a gate the test opens after it), so the check does not depend on timing or scheduling.
 */
class FetcherThreadingTest {
    private class HandRunDispatcher : CoroutineDispatcher() {
        private val slices = Channel<Runnable>(Channel.UNLIMITED)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            slices.trySend(block)
        }

        /** Runs the next slice on the test's thread, waiting (in real time) for work elsewhere to hand one back. */
        suspend fun runNextSlice() {
            val slice = withContext(Dispatchers.Default) { withTimeout(30_000) { slices.receive() } }
            slice.run()
        }

        fun hasPendingSlice(): Boolean = !slices.isEmpty
    }

    private val url = "https://news.example.com/long"

    private val longArticle = "<html><head><title>Long</title></head><body><article>" +
        SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it <b>bold</b> <a href=\"/x\">link</a></p>" }.repeat(500) +
        "</article></body></html>"

    private val networkGate = CompletableDeferred<Unit>()

    /**
     * Answers only once the gate opens, then in three chunks 20 ms apart, so a fetch that read the body on the caller
     * would need extra slices there.
     */
    private fun gatedChunkedClient() = mockClient {
        networkGate.await()
        val bytes = longArticle.encodeToByteArray()
        val writer = CoroutineScope(Dispatchers.Default).writer {
            for (part in 0 until 3) {
                channel.writeFully(bytes, bytes.size * part / 3, bytes.size * (part + 1) / 3)
                channel.flush()
                delay(20)
            }
        }
        respond(writer.channel, headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
    }

    private suspend fun <T : Any> runOnHandRunCaller(afterFirstSlice: suspend () -> Unit, work: suspend () -> T): T {
        val caller = HandRunDispatcher()
        var result: T? = null
        val job = CoroutineScope(caller).launch { result = work() }

        caller.runNextSlice()
        assertNull(result, "the work finished inside the caller's first slice, so it ran on the caller")
        afterFirstSlice()
        caller.runNextSlice()
        assertNotNull(result, "the caller needed more slices: the work came back to the caller before it was done")
        assertTrue(job.isCompleted && !caller.hasPendingSlice())
        return result!!
    }

    @Test
    fun readerFetchStartedOnASingleThreadDoesNotParseOnIt() = runTest {
        val fetcher = ReaderFetcher(gatedChunkedClient())

        assertIs<ReaderFetchResult.Extracted>(runOnHandRunCaller({ networkGate.complete(Unit) }) { fetcher.fetch(url) })
    }

    @Test
    fun metadataFetchStartedOnASingleThreadDoesNotParseOnIt() = runTest {
        val fetcher = MetadataFetcher(gatedChunkedClient())

        assertIs<MetadataResult.Success>(runOnHandRunCaller({ networkGate.complete(Unit) }) { fetcher.fetch(url) })
    }

    @Test
    fun readerBlocksStartedOnASingleThreadDoNotParseOnIt() = runTest {
        val html = ReaderExtractor.extract(longArticle, url)!!.extractedHtml
        // The parsing side is hand-run too, so the parse provably happens in its slice, never in the caller's.
        val worker = HandRunDispatcher()

        val blocks: List<ReaderBlock> = runOnHandRunCaller({
            assertTrue(worker.hasPendingSlice(), "the parse was not handed to the work dispatcher")
            worker.runNextSlice()
        }) { ReaderBlocks.parse(html, worker) }

        assertTrue(blocks.size > 100)
    }
}
