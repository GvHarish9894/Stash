package com.techgv.stash.core.network

import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Transport safeguards shared by both fetchers, exercised through [MetadataFetcher] and [ReaderFetcher]. */
class PageLoaderTest {
    private suspend fun fetch(url: String, client: io.ktor.client.HttpClient): MetadataResult = MetadataFetcher(client).fetch(url)

    @Test
    fun httpIsRequestedAsHttps() = runTest {
        val requested = mutableListOf<String>()
        val client = mockClient { request -> requested += request.url.toString(); html("<title>t</title>") }

        val metadata = assertIs<MetadataResult.Success>(fetch("http://example.com/post", client)).metadata
        fetch("HTTP://example.com:80/other?x=1", client)

        assertEquals(listOf("https://example.com/post", "https://example.com/other?x=1"), requested)
        assertEquals("https://example.com/post", metadata.finalUrl)
    }

    @Test
    fun redirectToHttpIsFollowedAsHttps() = runTest {
        val requested = mutableListOf<String>()
        val client = mockClient { request ->
            requested += request.url.toString()
            if (request.url.encodedPath == "/a") redirect("http://example.com/b") else html("<title>b</title>")
        }

        val result = ReaderFetcher(client).fetch("https://example.com/a")

        assertEquals(ReaderFetchResult.NotExtractable, result)
        assertEquals(listOf("https://example.com/a", "https://example.com/b"), requested)
    }

    private fun hops(redirects: Int) = mockClient { request ->
        val hop = request.url.encodedPath.removePrefix("/hop/").toInt()
        if (hop < redirects) redirect("/hop/${hop + 1}") else html("<title>arrived</title>")
    }

    @Test
    fun tenRedirectsSucceedAndElevenFail() = runTest {
        assertIs<MetadataResult.Success>(fetch("https://example.com/hop/0", hops(10)))
        assertEquals(FetchFailure(FetchFailureKind.TooManyRedirects, retryable = false), fetch("https://example.com/hop/0", hops(11)))
    }

    private fun pageOfSize(size: Int): ByteArray {
        val head = "<title>big</title>".encodeToByteArray()
        return head + ByteArray(size - head.size) { ' '.code.toByte() }
    }

    @Test
    fun bodyOfExactlyTheCapIsReadAndOneMoreByteIsTooLarge() = runTest {
        val cap = 5 * 1024 * 1024
        val tooLarge = FetchFailure(FetchFailureKind.TooLarge, retryable = false)
        val html = headersOf(HttpHeaders.ContentType, "text/html")

        fun declared(size: Int) = headersOf(HttpHeaders.ContentType to listOf("text/html"), HttpHeaders.ContentLength to listOf("$size"))

        // With a Content-Length header…
        assertIs<MetadataResult.Success>(fetch("https://example.com/", mockClient { respond(pageOfSize(cap), headers = declared(cap)) }))
        assertEquals(tooLarge, fetch("https://example.com/", mockClient { respond(pageOfSize(cap + 1), headers = declared(cap + 1)) }))
        // …and streamed without one.
        assertIs<MetadataResult.Success>(
            fetch("https://example.com/", mockClient { respond(ByteReadChannel(pageOfSize(cap)), headers = html) }),
        )
        assertEquals(tooLarge, fetch("https://example.com/", mockClient { respond(ByteReadChannel(pageOfSize(cap + 1)), headers = html) }))
    }

    @Test
    fun readTimeoutIsRetryable() = runTest {
        assertEquals(
            FetchFailure(FetchFailureKind.Timeout, retryable = true),
            fetch("https://example.com/", mockClient { throw SocketTimeoutException("read timed out") }),
        )
    }

    @Test
    fun unexpectedErrorsAreNotRetryable() = runTest {
        assertEquals(
            FetchFailure(FetchFailureKind.Unexpected, retryable = false),
            fetch("https://example.com/", mockClient { throw IllegalStateException("engine bug") }),
        )
    }

    @Test
    fun engineCancellationReportedAsIOExceptionStillCancels() = runTest {
        var returned: FetchFailure? = null

        val job = launch {
            returned = failuresAsValues({ it }) {
                // What an engine does when its call is cancelled: the coroutine is cancelled, the error is plain I/O.
                currentCoroutineContext().job.cancel()
                throw IOException("Canceled")
            }
        }
        job.join()

        assertTrue(job.isCancelled)
        assertNull(returned)
    }

    @OptIn(ExperimentalCoroutinesApi::class) // testScheduler.currentTime
    @Test
    fun wholeFetchIsBoundedByOneTimeout() = runTest {
        val fetcher = MetadataFetcher(mockClient { awaitCancellation() }, StandardTestDispatcher(testScheduler))

        assertEquals(FetchFailure(FetchFailureKind.Timeout, retryable = true), fetcher.fetch("https://example.com/slow"))
        assertEquals(FETCH_TIMEOUT_MILLIS, testScheduler.currentTime)
    }

    @Test
    fun trackingParamsAreDroppedBeforeFetching() = runTest {
        val requested = mutableListOf<String>()
        ReaderFetcher(mockClient { request -> requested += request.url.toString(); html("") })
            .fetch("https://example.com/post?utm_source=news&id=7&fbclid=abc")

        assertEquals(listOf("https://example.com/post?id=7"), requested)
    }

    @Test
    fun deepUnclosedNestingBeforeTheBodyEndTagStillParses() = runTest {
        // Ksoup 0.2.6 throws on `</body>` with this many elements open; the loader retries without the optional tag.
        val page = SyntheticPages.articleOf(300, 300, 300).replace("</article></body>", "</article>" + "<div>x".repeat(300) + "</body>")

        assertIs<ReaderFetchResult.Extracted>(ReaderFetcher(mockClient { html(page) }).fetch("https://example.com/"))
        assertEquals("Edge", assertIs<MetadataResult.Success>(fetch("https://example.com/", mockClient { html(page) })).metadata.title)
    }

    /** The verifier's 396 KB page: 99,000 tags, but the parser re-creates the 12 open formatting tags in every `<p>`. */
    private val reopenedFormatting = "<html><head><title>Formatting</title></head><body>" +
        "<p><b><i><u><s><em><strong><small><big><tt><font><code><strike>x" + "<p>y".repeat(99_000)

    @Test
    fun readerLoaderRefusesTheUnclosedFormattingPage() = runTest {
        assertNull(PageLoader(mockClient { html(reopenedFormatting) }).loadHtml("https://example.com/"))
    }

    /** The verifier's round-2 page: the closing tags sit in a comment, so they close nothing. */
    private val commentHidden = "<html><head><title>Hidden</title><meta property=\"og:description\" content=\"D\"></head><body>" +
        "<p><b><i><u><s><em><strong><small><big><tt><font><code><strike>x" +
        "<!--</strike></code></font></tt></big></small></strong></em></s></u></i></b>-->" + "<p>y".repeat(99_000)

    @Test
    fun metadataParsesOnlyTheHeadOfHostilePages() = runTest {
        for (page in listOf(commentHidden, commentHidden + "</p>".repeat(1_200_000), reopenedFormatting, metaHidden(99_000))) {
            val head = PageLoader(mockClient { html(page) }).loadHead("https://example.com/")

            assertTrue(head.document.getAllElements().size <= 8, "built ${head.document.getAllElements().size} elements")
            assertEquals(ReaderFetchResult.NotExtractable, ReaderFetcher(mockClient { html(page) }).fetch("https://example.com/"))
        }
        val metadata = assertIs<MetadataResult.Success>(fetch("https://example.com/", mockClient { html(commentHidden) })).metadata
        assertEquals("Hidden", metadata.title)
        assertEquals("D", metadata.description)
    }

    @Test
    fun unclosedFormattingPageGivesHeadMetadataAndNoReader() = runTest {
        val metadata = assertIs<MetadataResult.Success>(fetch("https://example.com/", mockClient { html(reopenedFormatting) })).metadata

        assertEquals("Formatting", metadata.title)
        assertEquals(ReaderFetchResult.NotExtractable, ReaderFetcher(mockClient { html(reopenedFormatting) }).fetch("https://example.com/"))
        assertNull(ReaderExtractor.extract(reopenedFormatting, "https://example.com/"))
        assertEquals(emptyList(), ReaderBlocks.parse(reopenedFormatting))
    }

    @Test
    fun elementEstimateCountsReopenedFormattingAndClosedTagsOnce() {
        // Four elements, plus one for each </p>: counting stray ones keeps the estimate on the safe side.
        assertEquals(4 + 2, estimatedElementCount("<p><b>x</b></p><p>y</p><div></div>"))
        // Each tag counts itself plus the formatting tags still open before it: b 1, i 2, u 3, then 4 for each <p>.
        assertEquals(1 + 2 + 3 + 4 * 4, estimatedElementCount("<b><i><u>x<p>a<p>b<p>c<p>d"))
        // The parser keeps at most three of one formatting tag: from the fourth <b> on, each tag adds 1 + 3.
        assertEquals(1 + 2 + 3 + 7 * 4 + 4 * 4, estimatedElementCount("<b>".repeat(10) + "<p>a<p>b<p>c<p>d"))
    }

    @Test
    fun redirectWithWhitespaceBeforeTheSchemeIsStillHttps() = runTest {
        for (space in listOf(" ", "\u00A0", "\u2003")) {
            val requested = mutableListOf<String>()
            val client = mockClient { request ->
                requested += request.url.toString()
                if (request.url.encodedPath == "/a") redirect("${space}http://example.com/b") else html("<title>b</title>")
            }

            assertEquals("b", assertIs<MetadataResult.Success>(fetch("https://example.com/a", client)).metadata.title, "space ${space.first().code}")
            assertEquals(listOf("https://example.com/a", "https://example.com/b"), requested)
        }
    }

    @Test
    fun noRequestIsSentUnlessItIsHttps() = runTest {
        var requests = 0
        val loader = PageLoader(mockClient { requests++; html("") })

        for (url in listOf("\u2003http://example.com/", " http://example.com/")) {
            assertEquals(
                FetchFailure(FetchFailureKind.InvalidUrl, retryable = false),
                failuresAsValues({ it }) { loader.loadHtml(url); error("loaded $url") },
            )
        }
        assertEquals(0, requests)
    }

    /** The verifier's round-3 page: `<meta =">` keeps the quote in an attribute name, so the payload is real markup. */
    private fun metaHidden(blocks: Int) =
        "<html lang=\"en\"><head><title>Meta</title><meta property=\"og:title\" content=\"OG\"><meta =\"><p>" +
            "<b><i><u><s><em><strong><small><big><tt><font><code><strike>x" + "<p>y".repeat(blocks) + "\"></head><body><p>hi</p></body></html>"

    @Test
    fun metadataOfTheLargestMetaHiddenPageReturnsTheHead() = runTest {
        // 4.8 MB: before canonicalisation this threw OutOfMemoryError out of fetch on a 512 MB JVM.
        val page = metaHidden(1_200_000)
        val head = PageLoader(mockClient { html(page) }).loadHead("https://example.com/")

        assertTrue(head.document.getAllElements().size <= 8, "built ${head.document.getAllElements().size} elements")
        val metadata = assertIs<MetadataResult.Success>(fetch("https://example.com/", mockClient { html(page) })).metadata
        assertEquals("OG", metadata.title)
        assertEquals("en", metadata.language)
    }
}
