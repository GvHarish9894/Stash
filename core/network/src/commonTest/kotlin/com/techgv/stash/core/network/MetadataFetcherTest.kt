package com.techgv.stash.core.network

import com.techgv.stash.core.model.ContentType
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataFetcherTest {
    private suspend fun fetchPage(url: String = "https://www.example.com/post", page: String): PageMetadata {
        val result = MetadataFetcher(mockClient { html(page) }).fetch(url)
        return assertIs<MetadataResult.Success>(result).metadata
    }

    private suspend fun failureOf(url: String = "https://example.com/post", respond: suspend MockRequestHandleScope.() -> HttpResponseData): FetchFailure =
        assertIs<FetchFailure>(MetadataFetcher(mockClient { respond() }).fetch(url))

    // Precedence

    private val allLevels = """
        <html lang="en"><head>
          <title>Plain title</title>
          <meta name="description" content="Plain description">
          <meta name="twitter:title" content="Twitter title">
          <meta name="twitter:description" content="Twitter description">
          <meta name="twitter:image" content="https://cdn.example.com/twitter.jpg">
          <meta property="og:title" content="OG title">
          <meta property="og:description" content="OG description">
          <meta property="og:image" content="https://cdn.example.com/og.jpg">
          <meta property="og:site_name" content="Example Times">
          <link rel="icon" href="/static/icon.png">
          <link rel="canonical" href="https://www.example.com/canonical-post">
        </head><body></body></html>
    """.trimIndent()

    @Test
    fun openGraphBeatsTwitterBeatsPlainHtml() = runTest {
        val metadata = fetchPage(page = allLevels)

        assertEquals("OG title", metadata.title)
        assertEquals("OG description", metadata.description)
        assertEquals("https://cdn.example.com/og.jpg", metadata.imageUrl)
        assertEquals("https://www.example.com/post", metadata.finalUrl)
        assertEquals("https://www.example.com/static/icon.png", metadata.faviconUrl)
        assertEquals("https://www.example.com/canonical-post", metadata.canonicalUrl)
        assertEquals("en", metadata.language)
        assertNull(metadata.contentType, "an article page adds nothing to the host's type")
    }

    @Test
    fun twitterCardAloneIsUsed() = runTest {
        val metadata = fetchPage(page = allLevels.lines().filterNot { "og:" in it || "<title>" in it || "name=\"description\"" in it }.joinToString("\n"))

        assertEquals("Twitter title", metadata.title)
        assertEquals("Twitter description", metadata.description)
        assertEquals("https://cdn.example.com/twitter.jpg", metadata.imageUrl)
    }

    @Test
    fun twitterCardBeatsPlainHtml() = runTest {
        val metadata = fetchPage(page = allLevels.lines().filterNot { "og:" in it }.joinToString("\n"))

        assertEquals("Twitter title", metadata.title)
        assertEquals("Twitter description", metadata.description)
        assertEquals("https://cdn.example.com/twitter.jpg", metadata.imageUrl)
    }

    @Test
    fun plainHtmlAloneIsUsed() = runTest {
        val metadata = fetchPage(page = allLevels.lines().filterNot { "og:" in it || "twitter:" in it }.joinToString("\n"))

        assertEquals("Plain title", metadata.title)
        assertEquals("Plain description", metadata.description)
        assertNull(metadata.imageUrl)
    }

    @Test
    fun missingEverythingLeavesTitleNullAndFaviconOnOrigin() = runTest {
        val metadata = fetchPage(url = "https://blog.example.co.uk/a/b", page = "<html><head></head><body></body></html>")

        assertNull(metadata.title)
        assertNull(metadata.description)
        assertNull(metadata.imageUrl)
        assertNull(metadata.canonicalUrl)
        assertNull(metadata.language)
        assertNull(metadata.readingMinutes)
        assertEquals("https://blog.example.co.uk/favicon.ico", metadata.faviconUrl)
        assertEquals("https://blog.example.co.uk/a/b", metadata.finalUrl)
    }

    @Test
    fun relativeUrlsResolveAgainstThePostRedirectUrl() = runTest {
        val requested = mutableListOf<String>()
        val client = mockClient { request ->
            requested += request.url.toString()
            when (request.url.toString()) {
                "https://sho.rt/abc" -> redirect("https://www.example.com/blog/2026/post", HttpStatusCode.MovedPermanently)
                "https://www.example.com/blog/2026/post" -> redirect("/stories/keeper")
                "https://www.example.com/stories/keeper" -> html(
                    """<html><head><meta property="og:image" content="../img/cover.jpg">
                       <link rel="shortcut icon" href="favicon.png"><title>Keeper</title></head></html>""",
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val metadata = assertIs<MetadataResult.Success>(MetadataFetcher(client).fetch("https://sho.rt/abc")).metadata

        assertEquals(3, requested.size)
        assertEquals("https://www.example.com/stories/keeper", metadata.finalUrl)
        assertEquals("https://www.example.com/img/cover.jpg", metadata.imageUrl)
        assertEquals("https://www.example.com/stories/favicon.png", metadata.faviconUrl)
    }

    @Test
    fun nonHttpImageUrlsAreDropped() = runTest {
        val metadata = fetchPage(page = """<head><meta property="og:image" content="data:image/png;base64,AAAA"></head>""")

        assertNull(metadata.imageUrl)
    }

    @Test
    fun imageUrlsAreUpgradedToHttpsAndSchemesMatchInAnyCase() = runTest {
        assertEquals(
            "https://cdn.example.com/a.jpg",
            fetchPage(page = """<meta property="og:image" content="http://cdn.example.com/a.jpg">""").imageUrl,
        )
        assertEquals(
            "HTTPS://Cdn.Example.com/b.jpg",
            fetchPage(page = """<meta property="og:image" content="HTTPS://Cdn.Example.com/b.jpg">""").imageUrl,
        )
    }

    @Test
    fun overlongUrlsAndLanguageAreDropped() = runTest {
        val long = "https://cdn.example.com/" + "a".repeat(2_048)
        val metadata = fetchPage(
            page = """<html lang="${"x".repeat(36)}"><head><meta property="og:image" content="$long">
                <link rel="icon" href="$long"><link rel="canonical" href="$long"><meta property="og:url" content="$long">""",
        )

        assertNull(metadata.imageUrl)
        assertNull(metadata.canonicalUrl)
        assertNull(metadata.language)
        assertEquals("https://www.example.com/favicon.ico", metadata.faviconUrl)
        assertEquals("en-GB", fetchPage(page = """<html lang="en-GB">""").language)
    }

    @Test
    fun sendsBrowserLikeHeaders() = runTest {
        var userAgent: String? = null
        var language: String? = null
        val client = mockClient { request ->
            userAgent = request.headers[HttpHeaders.UserAgent]
            language = request.headers[HttpHeaders.AcceptLanguage]
            html("<title>t</title>")
        }

        MetadataFetcher(client).fetch("https://example.com/")

        assertTrue(userAgent!!.startsWith("Mozilla/5.0"))
        assertEquals(PageLoader.ACCEPT_LANGUAGE, language)
    }

    @Test
    fun htmlRequestDoesNotAskForJson() = runTest {
        var accept: List<String>? = null
        MetadataFetcher(mockClient { request -> accept = request.headers.getAll(HttpHeaders.Accept); html("<title>t</title>") })
            .fetch("https://example.com/")

        assertEquals(listOf("text/html,application/xhtml+xml;q=0.9,*/*;q=0.8"), accept)
    }

    // oEmbed

    private val youTubeOEmbed = """
        {"title":"Building a quiet shelf","author_name":"Stash","type":"video","provider_name":"YouTube",
         "thumbnail_url":"https://i.ytimg.com/vi/abc/hqdefault.jpg","html":"<iframe></iframe>"}
    """.trimIndent()

    @Test
    fun youTubeHostsUseOEmbed() = runTest {
        for (url in listOf("https://www.youtube.com/watch?v=abc", "https://youtu.be/abc", "https://m.youtube.com/watch?v=abc")) {
            val requested = mutableListOf<String>()
            val client = mockClient { request ->
                requested += request.url.toString()
                if (request.url.host == "www.youtube.com" && request.url.encodedPath == "/oembed") {
                    assertEquals(url, request.url.parameters["url"])
                    assertEquals("json", request.url.parameters["format"])
                    respond(youTubeOEmbed, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respondError(HttpStatusCode.InternalServerError)
                }
            }

            val metadata = assertIs<MetadataResult.Success>(MetadataFetcher(client).fetch(url)).metadata

            assertEquals(1, requested.size, "the page itself is not fetched for $url")
            assertEquals("Building a quiet shelf", metadata.title)
            assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", metadata.imageUrl)
            assertEquals(url, metadata.finalUrl)
            assertEquals(ContentType.Video, metadata.contentType)
            assertNull(metadata.readingMinutes)
        }
    }

    @Test
    fun vimeoOEmbedDurationBecomesMinutes() = runTest {
        val client = mockClient { request ->
            assertEquals("/api/oembed.json", request.url.encodedPath)
            respond(
                """{"type":"video","title":"Tide &amp; time","provider_name":"Vimeo","duration":62,"description":"A short film."}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val metadata = assertIs<MetadataResult.Success>(MetadataFetcher(client).fetch("https://vimeo.com/76979871")).metadata

        assertEquals("Tide & time", metadata.title)
        assertEquals("A short film.", metadata.description)
        assertEquals(2, metadata.readingMinutes)
        assertEquals("https://vimeo.com/favicon.ico", metadata.faviconUrl)
    }

    @Test
    fun oEmbedDurationCountsOnlyForVideos() = runTest {
        val client = mockClient {
            respond(
                """{"type":"rich","title":"A playlist","provider_name":"Vimeo","duration":620}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val metadata = assertIs<MetadataResult.Success>(MetadataFetcher(client).fetch("https://vimeo.com/showcase/1")).metadata

        assertNull(metadata.readingMinutes)
        assertNull(metadata.contentType)
    }

    @Test
    fun lookalikeHostsDoNotUseOEmbed() = runTest {
        val urls = listOf(
            "https://youtube.com.evil.example/watch?v=abc",
            "https://notyoutube.com/watch?v=abc",
            "https://www.youtube-nocookie.com/embed/abc",
            "https://evil.example/youtube.com/watch?v=abc",
            "https://vimeo.co/1",
        )
        for (url in urls) {
            val requested = mutableListOf<String>()
            val client = mockClient { request -> requested += request.url.toString(); html("<title>page</title>") }

            assertEquals("page", assertIs<MetadataResult.Success>(MetadataFetcher(client).fetch(url)).metadata.title, url)
            assertEquals(listOf(url), requested)
        }
    }

    @Test
    fun unwrappedLinkIsWhatGetsFetched() = runTest {
        val requested = mutableListOf<String>()
        val client = mockClient { request ->
            requested += request.url.toString()
            respond(youTubeOEmbed, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val metadata = assertIs<MetadataResult.Success>(
            MetadataFetcher(client).fetch("https://www.google.com/url?q=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dabc%26utm_source%3Dx"),
        ).metadata

        assertEquals(1, requested.size)
        assertTrue(requested.single().startsWith("https://www.youtube.com/oembed?"), requested.single())
        assertEquals("https://www.youtube.com/watch?v=abc", metadata.finalUrl)
    }

    @Test
    fun oEmbedFailureFallsBackToPageHtml() = runTest {
        val oEmbedFailures = listOf<suspend MockRequestHandleScope.() -> HttpResponseData>(
            { respondError(HttpStatusCode.NotFound) },
            { respond("not json", headers = headersOf(HttpHeaders.ContentType, "application/json")) },
            { respond("""{"type":"video"}""", headers = headersOf(HttpHeaders.ContentType, "application/json")) },
            { throw IOException("connection reset") },
        )
        for (oEmbedFailure in oEmbedFailures) {
            val client = mockClient { request ->
                if (request.url.encodedPath == "/oembed") {
                    oEmbedFailure()
                } else {
                    html("""<head><meta property="og:title" content="From the page"><meta property="og:type" content="video.other"></head>""")
                }
            }

            val metadata = assertIs<MetadataResult.Success>(MetadataFetcher(client).fetch("https://www.youtube.com/watch?v=abc")).metadata

            assertEquals("From the page", metadata.title)
            assertEquals(ContentType.Video, metadata.contentType)
        }
    }

    // Failures

    @Test
    fun httpStatusesMapToKinds() = runTest {
        val expected = mapOf(
            404 to FetchFailure(FetchFailureKind.HttpStatus, retryable = false, httpStatus = 404),
            410 to FetchFailure(FetchFailureKind.HttpStatus, retryable = false, httpStatus = 410),
            401 to FetchFailure(FetchFailureKind.Blocked, retryable = false, httpStatus = 401),
            403 to FetchFailure(FetchFailureKind.Blocked, retryable = false, httpStatus = 403),
            429 to FetchFailure(FetchFailureKind.Blocked, retryable = true, httpStatus = 429),
            408 to FetchFailure(FetchFailureKind.Timeout, retryable = true, httpStatus = 408),
            500 to FetchFailure(FetchFailureKind.HttpStatus, retryable = true, httpStatus = 500),
            502 to FetchFailure(FetchFailureKind.HttpStatus, retryable = true, httpStatus = 502),
            503 to FetchFailure(FetchFailureKind.HttpStatus, retryable = true, httpStatus = 503),
            504 to FetchFailure(FetchFailureKind.HttpStatus, retryable = true, httpStatus = 504),
        )
        for ((status, failure) in expected) {
            assertEquals(failure, failureOf { html("<title>error</title>", status = HttpStatusCode.fromValue(status)) }, "status $status")
        }
    }

    @Test
    fun nonHtmlIsNotParsed() = runTest {
        assertEquals(
            FetchFailure(FetchFailureKind.NotHtml, retryable = false),
            failureOf { respond("%PDF-1.7", headers = headersOf(HttpHeaders.ContentType, "application/pdf")) },
        )
        assertEquals(
            FetchFailure(FetchFailureKind.NotHtml, retryable = false),
            failureOf { respond("<title>no type</title>") },
        )
    }

    @Test
    fun xhtmlIsParsed() = runTest {
        val result = MetadataFetcher(mockClient { html("<title>XHTML page</title>", contentType = "application/xhtml+xml") })
            .fetch("https://example.com/")

        assertEquals("XHTML page", assertIs<MetadataResult.Success>(result).metadata.title)
    }

    /** The producer ends only because the fetch cancelled the body; a fetch that kept reading would hang here. */
    private suspend fun WriterJob.stopsWithoutUs() = withContext(Dispatchers.Default) { withTimeout(10_000) { job.join() } }

    @Test
    fun endlessBodyStopsAtTheCap() = runTest {
        var produced = 0L
        val writer = CoroutineScope(Dispatchers.Default).writer {
            val chunk = ByteArray(64 * 1024) { 'a'.code.toByte() }
            while (true) {
                channel.writeFully(chunk)
                channel.flush()
                produced += chunk.size
            }
        }

        val failure = failureOf { respond(writer.channel, headers = headersOf(HttpHeaders.ContentType, "text/html")) }
        writer.stopsWithoutUs()

        assertEquals(FetchFailure(FetchFailureKind.TooLarge, retryable = false), failure)
        // Up to a few MB sit in channel buffers between the mock engine and the reader.
        assertTrue(produced < PageLoader.MAX_HTML_BYTES + 4 * 1024 * 1024, "read $produced bytes")
    }

    @Test
    fun declaredOversizedBodyIsNotRead() = runTest {
        var produced = 0L
        val writer = CoroutineScope(Dispatchers.Default).writer {
            val chunk = ByteArray(64 * 1024)
            while (true) {
                channel.writeFully(chunk)
                channel.flush()
                produced += chunk.size
            }
        }
        val headers = headersOf(
            HttpHeaders.ContentType to listOf("text/html"),
            HttpHeaders.ContentLength to listOf("209715200"),
        )

        val failure = failureOf { respond(writer.channel, headers = headers) }
        writer.stopsWithoutUs()

        assertEquals(FetchFailure(FetchFailureKind.TooLarge, retryable = false), failure)
        // A margin over Ktor's 1 MiB channel buffer; still proves the 200 MB page is not downloaded.
        assertTrue(produced < 8 * 1024 * 1024, "read $produced bytes of a 200 MB page")
    }

    @Test
    fun timeoutsAreRetryable() = runTest {
        val timeout = FetchFailure(FetchFailureKind.Timeout, retryable = true)
        assertEquals(timeout, failureOf { throw HttpRequestTimeoutException("https://example.com/post", 15_000) })
        assertEquals(timeout, failureOf { throw ConnectTimeoutException("connect timed out") })
    }

    @Test
    fun connectionErrorIsOffline() = runTest {
        assertEquals(
            FetchFailure(FetchFailureKind.Offline, retryable = true),
            failureOf { throw IOException("Unable to resolve host") },
        )
    }

    @Test
    fun invalidUrlsAreRejectedWithoutARequest() = runTest {
        var requests = 0
        val fetcher = MetadataFetcher(mockClient { requests++; html("") })
        for (url in listOf("not a url", "https://", "ftp://example.com/file", "javascript:alert(1)", "file:///etc/hosts", "")) {
            assertEquals(FetchFailure(FetchFailureKind.InvalidUrl, retryable = false), fetcher.fetch(url), url)
        }
        assertEquals(0, requests)
    }

    @Test
    fun redirectLoopIsBounded() = runTest {
        var requests = 0
        val failure = failureOf(url = "https://example.com/a") { requests++; redirect("https://example.com/a") }

        assertEquals(FetchFailure(FetchFailureKind.TooManyRedirects, retryable = false), failure)
        assertEquals(PageLoader.MAX_REDIRECTS + 1, requests)
    }

    @Test
    fun redirectToNonHttpSchemeIsInvalid() = runTest {
        assertEquals(
            FetchFailure(FetchFailureKind.InvalidUrl, retryable = false),
            failureOf { redirect("ftp://example.com/file") },
        )
    }

    @Test
    fun redirectWithoutLocationIsAnHttpStatus() = runTest {
        assertEquals(
            FetchFailure(FetchFailureKind.HttpStatus, retryable = false, httpStatus = 302),
            failureOf { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.ContentType, "text/html")) },
        )
    }

    @Test
    fun cancellationPropagates() = runTest {
        val started = CompletableDeferred<Unit>()
        val fetcher = MetadataFetcher(mockClient { started.complete(Unit); awaitCancellation() })
        var returned: MetadataResult? = null

        val job = launch { returned = fetcher.fetch("https://example.com/slow") }
        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertNull(returned, "a cancelled fetch must not return a Failure")
    }

    // Charsets

    private suspend fun titleOf(bytes: ByteArray, contentType: String): String? {
        val client = mockClient { respond(bytes, headers = headersOf(HttpHeaders.ContentType, contentType)) }
        return assertIs<MetadataResult.Success>(MetadataFetcher(client).fetch("https://example.com/")).metadata.title
    }

    /** "<title>Café “quoted”</title>" in windows-1252: é = 0xE9, “ = 0x93, ” = 0x94. */
    private val windows1252Title = "<title>Caf".encodeToByteArray() + byteArrayOf(0xE9.toByte(), ' '.code.toByte(), 0x93.toByte()) +
        "quoted".encodeToByteArray() + byteArrayOf(0x94.toByte()) + "</title>".encodeToByteArray()

    @Test
    fun charsetFromContentTypeHeader() = runTest {
        assertEquals("Café “quoted”", titleOf(windows1252Title, "text/html; charset=windows-1252"))
        // Browsers decode iso-8859-1 as windows-1252, so 0x93 and 0x94 are still curly quotes.
        assertEquals("Café “quoted”", titleOf(windows1252Title, "text/html; charset=ISO-8859-1"))
    }

    @Test
    fun charsetFromMetaWhenHeaderHasNone() = runTest {
        val page = """<html><head><meta charset="windows-1252">""".encodeToByteArray() + windows1252Title
        assertEquals("Café “quoted”", titleOf(page, "text/html"))

        val httpEquiv = """<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">""".encodeToByteArray() + windows1252Title
        assertEquals("Café “quoted”", titleOf(httpEquiv, "text/html"))
    }

    @Test
    fun utf8IsTheDefaultAndKeepsTamilAndHindi() = runTest {
        val title = "தமிழ் கட்டுரை — हिन्दी लेख"
        assertEquals(title, titleOf("<title>$title</title>".encodeToByteArray(), "text/html"))
        assertEquals(title, titleOf("<title>$title</title>".encodeToByteArray(), "text/html; charset=UTF-8"))
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "<title>$title</title>".encodeToByteArray()
        assertEquals(title, titleOf(withBom, "text/html"))
    }

    @Test
    fun unknownCharsetFallsBackToUtf8() = runTest {
        assertEquals("Plain", titleOf("<title>Plain</title>".encodeToByteArray(), "text/html; charset=x-made-up"))
    }

    // Limits

    @Test
    fun titleAndDescriptionAreCapped() = runTest {
        val metadata = fetchPage(page = """<title>${"t".repeat(2_000)}</title><meta name="description" content="${"d".repeat(1_000)}">""")

        assertEquals(500, metadata.title!!.length)
        assertEquals(300, metadata.description!!.length)
    }

    @Test
    fun capDoesNotSplitASurrogatePair() = runTest {
        val metadata = fetchPage(page = "<title>${"t".repeat(499)}😀tail</title>")

        assertEquals("t".repeat(499), metadata.title)
    }

    @Test
    fun entitiesAreDecodedAndWhitespaceCollapsed() = runTest {
        val metadata = fetchPage(
            page = """<head><meta property="og:title" content="Fish &amp;amp; Chips &mdash; caf&eacute;">
                <title>ignored</title><meta property="og:description" content="  A
                    quiet&nbsp;&nbsp; shelf	 ">""",
        )

        assertEquals("Fish & Chips — café", metadata.title)
        assertEquals("A quiet shelf", metadata.description)
    }

    // Reading time

    @Test
    fun articleMinutesComeFromJsonLdWordCount() = runTest {
        val metadata = fetchPage(
            page = """<head><script type="application/ld+json">
                {"@context":"https://schema.org","@graph":[{"@type":"WebPage"},{"@type":"NewsArticle","wordCount":"2380"}]}
                </script></head><body><p>short</p></body>""",
        )

        assertEquals(10, metadata.readingMinutes)
    }

    @Test
    fun articleMinutesComeFromBodyWordsOtherwise() = runTest {
        val metadata = fetchPage(page = SyntheticPages.newsArticle)

        assertEquals(1, metadata.readingMinutes)
        assertEquals("The last lighthouse keeper", metadata.title)
        assertEquals("en-GB", metadata.language)
    }

    @Test
    fun normalArticleGetsEverythingFromItsHead() = runTest {
        val body = SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }.repeat(4)
        val page = """<html lang="en"><head><title>Plain</title><meta property="og:title" content="Keeper">
            <meta name="description" content="Dawns on the headland."><meta property="og:image" content="/img/keeper.jpg">
            <link rel="icon" href="/favicon.png"></head><body><nav><a href="/">Home</a></nav><article>$body</article></body></html>"""

        val metadata = fetchPage(page = page)

        assertEquals("Keeper", metadata.title)
        assertEquals("Dawns on the headland.", metadata.description)
        assertEquals("https://www.example.com/img/keeper.jpg", metadata.imageUrl)
        assertEquals("https://www.example.com/favicon.png", metadata.faviconUrl)
        // 16 paragraphs of about 36 words: about 580 words, 2 minutes at 238 a minute.
        assertEquals(2, metadata.readingMinutes)
    }

    @Test
    fun shortPagesHaveNoReadingTime() = runTest {
        assertNull(fetchPage(page = SyntheticPages.stub).readingMinutes)
    }

    @Test
    fun videoMinutesComeFromDuration() = runTest {
        val openGraph = fetchPage(page = """<meta property="og:type" content="video.movie"><meta property="video:duration" content="330">""")
        assertEquals(ContentType.Video, openGraph.contentType)
        assertEquals(6, openGraph.readingMinutes)

        val jsonLd = fetchPage(
            page = """<meta property="og:type" content="video.other">
                <script type="application/ld+json">{"@type":"VideoObject","duration":"PT4M13S"}</script>""",
        )
        assertEquals(5, jsonLd.readingMinutes)

        val itemprop = fetchPage(page = """<meta property="og:type" content="video"><meta itemprop="duration" content="PT1H0M0S">""")
        assertEquals(60, itemprop.readingMinutes)
    }

    @Test
    fun hostTypeIsLeftToTheDataLayer() = runTest {
        val metadata = fetchPage(url = "https://github.com/techgv/stash", page = "<title>techgv/stash</title>")

        assertNull(metadata.contentType)
        assertEquals("https://github.com/techgv/stash", metadata.finalUrl)
    }
}
