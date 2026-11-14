package com.techgv.stash.core.network

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

sealed interface ReaderFetchResult {
    data class Extracted(val article: ExtractedArticle) : ReaderFetchResult

    /** The page loaded but has no article text; the UI falls back to the web view. */
    data object NotExtractable : ReaderFetchResult
}

/**
 * Downloads a page with the same safeguards as [MetadataFetcher] and extracts its reader-mode text. All work runs on
 * [dispatcher]; tests may pass their own.
 */
class ReaderFetcher(client: HttpClient, private val dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    private val pages = PageLoader(client)

    suspend fun fetch(url: String): ReaderFetchResult = boundedFetch(dispatcher, { it }) {
        val capture = captureLink(url) ?: return@boundedFetch FetchFailure(FetchFailureKind.InvalidUrl, retryable = false)
        pages.loadHtml(capture.url)
            ?.let { ReaderExtractor.extract(it.document) }
            ?.let { ReaderFetchResult.Extracted(it) }
            ?: ReaderFetchResult.NotExtractable
    }
}
