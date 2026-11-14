package com.techgv.stash.core.data

import com.techgv.stash.core.database.ArticleContentEntity
import com.techgv.stash.core.domain.ReaderResult
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.network.ReaderExtractor
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class StashReaderRepositoryTest {
    private fun article(words: String) = TestData.html(
        "<article><h1>Headline</h1>" + List(6) {
            "<p>$words paragraph $it. It is long enough to read like an article, with sentences, commas and enough words " +
                "to pass the extractor's threshold, and not like a navigation bar, a footer or other page chrome.</p>"
        }.joinToString("") + "</article>",
    )

    private val offline: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { throw IOException("offline") }

    private suspend fun TestData.article(id: String) = reader.article(ItemId(id))

    private suspend fun TestData.withItems(vararg ids: String) = insert(*ids.map { savedItem(it, title = "Title $it", readingMinutes = 4) }.toTypedArray())

    @Test
    fun aFetchedArticleIsCachedAndOpensAgainWithoutTheNetwork() = runTest {
        val data = TestData(this, network = article("Fresh"))
        data.withItems("a")

        val first = assertIs<ReaderResult.Ready>(data.article("a")).article
        assertEquals(1, data.requests.size)
        assertEquals("Title a", first.title)
        assertEquals("example.com", first.siteName)
        assertEquals(4, first.readingMinutes)
        assertIs<ReaderBlock.Paragraph>(first.blocks.first { it !is ReaderBlock.Heading })

        data.network = offline
        assertEquals(first, assertIs<ReaderResult.Ready>(data.article("a")).article, "offline from the cache")
        assertEquals(1, data.requests.size, "no second request")
    }

    @Test
    fun offlineWithoutACacheIsOffline() = runTest {
        val data = TestData(this, network = offline)
        data.withItems("a")

        assertEquals(ReaderResult.Offline, data.article("a"))
    }

    @Test
    fun aPageWithoutArticleTextOrNotHtmlIsUnavailable() = runTest {
        val data = TestData(this, network = TestData.html("<p>Short.</p>"))
        data.withItems("a", "pdf")
        assertEquals(ReaderResult.Unavailable, data.article("a"))

        data.network = { respond("%PDF", HttpStatusCode.OK, headersOf("Content-Type", "application/pdf")) }
        assertEquals(ReaderResult.Unavailable, data.article("pdf"))
        assertEquals(ReaderResult.Unavailable, data.reader.article(ItemId("missing")))
    }

    @Test
    fun anOlderExtractionIsRedoneOnlineAndStillServedOffline() = runTest {
        val data = TestData(this, network = offline)
        data.withItems("a")
        val old = "<p>Old extraction of this page.</p>"
        data.database.articleContentDao().upsert(
            ArticleContentEntity("a", old, "Old standfirst", 30, FIXTURE_NOW, ReaderExtractor.EXTRACTOR_VERSION - 1, old.length.toLong(), FIXTURE_NOW),
        )

        assertEquals("Old standfirst", assertIs<ReaderResult.Ready>(data.article("a")).article.standfirst, "offline: the old cache")

        data.network = article("New")
        assertIs<ReaderResult.Ready>(data.article("a"))
        val cached = assertNotNull(data.database.articleContentDao().get("a"))
        assertEquals(ReaderExtractor.EXTRACTOR_VERSION, cached.extractorVersion)
        assertEquals(true, "New paragraph" in cached.extractedHtml)
    }

    @Test
    fun theCacheEvictsTheLeastRecentlyOpenedBeyondItsBudget() = runTest {
        val data = TestData(this, network = article("Long ".repeat(50))) // ~3.2 KB each; TestData's budget is 10,000 bytes
        data.withItems("a", "b", "c", "d")
        for (id in listOf("a", "b", "c")) {
            data.article(id)
            data.clock.advanceBy(1.minutes)
        }
        data.article("a") // now b is the least recently opened
        data.clock.advanceBy(1.minutes)

        data.article("d")

        assertNull(data.database.articleContentDao().get("b"))
        listOf("a", "c", "d").forEach { assertNotNull(data.database.articleContentDao().get(it), it) }
        assertEquals(true, data.strings("SELECT SUM(html_bytes) FROM article_content").single().toLong() <= 10_000)
    }

    @Test
    fun aTimeoutWithoutACacheIsOffline() = runTest {
        val data = TestData(this, network = { throw HttpRequestTimeoutException("https://example.com/a", 15_000) })
        data.withItems("a")

        assertEquals(ReaderResult.Offline, data.article("a"))
    }

    @Test
    fun aFreshExtractionCachesItsStandfirstAndItsSizeInUtf8Bytes() = runTest {
        val tamil = "தமிழ் ".repeat(40)
        val data = TestData(
            this,
            network = TestData.html(
                "<article>" + List(6) { "<p>$tamil paragraph $it, with sentences, commas and enough words to read like an article.</p>" }.joinToString("") + "</article>",
                head = """<meta name="description" content="What this page is about">""",
            ),
        )
        data.withItems("a")

        val article = assertIs<ReaderResult.Ready>(data.article("a")).article

        assertEquals("What this page is about", article.standfirst)
        val cached = assertNotNull(data.database.articleContentDao().get("a"))
        assertEquals("What this page is about", cached.standfirst)
        assertEquals(cached.extractedHtml.encodeToByteArray().size.toLong(), cached.htmlBytes)
        assertEquals(true, cached.htmlBytes > cached.extractedHtml.length, "Tamil letters take more than one byte each")
    }
}
