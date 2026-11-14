package com.techgv.stash.core.network

import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderFetcherTest {
    @Test
    fun articleIsExtractedAgainstTheFinalUrl() = runTest {
        val client = mockClient { request ->
            if (request.url.host == "sho.rt") redirect("https://news.example.com/2026/keeper") else html(SyntheticPages.hostileArticle)
        }

        val result = ReaderFetcher(client).fetch("https://sho.rt/k")

        val html = assertIs<ReaderFetchResult.Extracted>(result).article.extractedHtml
        assertTrue("https://news.example.com/images/keeper.jpg" in html, html)
    }

    @Test
    fun pageWithoutArticleIsNotExtractable() = runTest {
        assertEquals(ReaderFetchResult.NotExtractable, ReaderFetcher(mockClient { html(SyntheticPages.linkFarm) }).fetch("https://example.com/"))
    }

    @Test
    fun failuresAreValues() = runTest {
        assertEquals(
            FetchFailure(FetchFailureKind.HttpStatus, retryable = false, httpStatus = 404),
            ReaderFetcher(mockClient { respondError(HttpStatusCode.NotFound) }).fetch("https://example.com/gone"),
        )
        assertEquals(
            FetchFailure(FetchFailureKind.InvalidUrl, retryable = false),
            ReaderFetcher(mockClient { html("") }).fetch("mailto:keeper@example.com"),
        )
    }

    @Test
    fun cancellationPropagates() = runTest {
        val started = CompletableDeferred<Unit>()
        val fetcher = ReaderFetcher(mockClient { started.complete(Unit); awaitCancellation() })
        var returned: ReaderFetchResult? = null

        val job = launch { returned = fetcher.fetch("https://example.com/slow") }
        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertNull(returned)
    }
}
