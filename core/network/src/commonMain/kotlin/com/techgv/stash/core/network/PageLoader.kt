package com.techgv.stash.core.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document
import com.techgv.stash.core.domain.LinkCapture
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.model.SharedPayload
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

enum class FetchFailureKind {
    /** Not an absolute http(s) URL, or a redirect to one that is not. */
    InvalidUrl,
    /**
     * No connection, DNS failure or connection reset. Known limit: TLS failures (bad certificate, old protocol) land
     * here too, because the engines report them as plain I/O errors; they are retried like any network error.
     */
    Offline,
    /** A connect, read or request timeout, a 408, or the whole fetch running past [FETCH_TIMEOUT_MILLIS]. */
    Timeout,
    /** Any other non-2xx status; see [FetchFailure.httpStatus]. */
    HttpStatus,
    /** 401, 403 or 429: the site refuses automated requests. */
    Blocked,
    /** The response is not HTML (for example a PDF). */
    NotHtml,
    /** The body is larger than the cap; reading stopped at the cap. */
    TooLarge,
    /** More redirects than the bound, including redirect loops. */
    TooManyRedirects,
    /** A bug or an unparseable response; retrying the same request will not help. */
    Unexpected,
}

/**
 * Why a fetch failed. [retryable] is true when the same request may succeed later (offline, timeout, 408, 429, 5xx);
 * the caller owns the retry policy.
 */
data class FetchFailure(
    val kind: FetchFailureKind,
    val retryable: Boolean,
    val httpStatus: Int? = null,
) : MetadataResult, ReaderFetchResult

/** One fetch, all redirect hops and parsing included, never takes longer than this. */
const val FETCH_TIMEOUT_MILLIS = 30_000L

/** Carries a [FetchFailure] out of nested fetch code; [failuresAsValues] turns it back into a value. */
internal class FetchFailed(val failure: FetchFailure) : Exception()

private fun fail(kind: FetchFailureKind, retryable: Boolean = false, httpStatus: Int? = null): Nothing =
    throw FetchFailed(FetchFailure(kind, retryable, httpStatus))

/** Runs [block] and maps every exception except cancellation to a [FetchFailure]. */
internal suspend fun <R> failuresAsValues(onFailure: (FetchFailure) -> R, block: suspend () -> R): R =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // An engine may report its own cancellation as an IOException; a cancelled caller must still see cancellation.
        currentCoroutineContext().ensureActive()
        onFailure(
            when (e) {
                is FetchFailed -> e.failure
                is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException ->
                    FetchFailure(FetchFailureKind.Timeout, retryable = true)
                is IOException -> FetchFailure(FetchFailureKind.Offline, retryable = true)
                else -> FetchFailure(FetchFailureKind.Unexpected, retryable = false)
            },
        )
    } catch (e: Throwable) {
        // Last resort, JVM targets only: running out of memory must not escape fetch (Kotlin/Native cannot recover).
        if (!isOutOfMemory(e)) throw e
        onFailure(FetchFailure(FetchFailureKind.Unexpected, retryable = false))
    }

/**
 * Runs a whole fetch on [dispatcher] (decoding and parsing are CPU work that must stay off the caller's thread) under
 * [FETCH_TIMEOUT_MILLIS], with every failure returned as a value.
 */
internal suspend fun <R : Any> boundedFetch(
    dispatcher: CoroutineDispatcher,
    onFailure: (FetchFailure) -> R,
    block: suspend () -> R,
): R = withContext(dispatcher) {
    withTimeoutOrNull(FETCH_TIMEOUT_MILLIS) { failuresAsValues(onFailure, block) }
        ?: onFailure(FetchFailure(FetchFailureKind.Timeout, retryable = true))
}

/** The link as the domain policy sees it: unwrapped from redirect services and without tracking params. */
internal fun captureLink(url: String): LinkCapture.Captured? {
    if (LinkCapturePolicy.normalize(url) == null) return null
    // capture() is the policy's widest URL parser (it accepts any host shape); the payload time is unused.
    return LinkCapturePolicy.capture(SharedPayload(url, null, null, Instant.DISTANT_PAST)) as? LinkCapture.Captured
}

internal fun isHttpUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

/** Decision D17: never make a cleartext request; `http://host:80/x` becomes `https://host/x`. */
internal fun upgradeToHttps(url: String): String {
    if (!url.startsWith("http://", ignoreCase = true)) return url
    val rest = url.substring("http://".length)
    val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
    return "https://" + rest.substring(0, authorityEnd).removeSuffix(":80") + rest.substring(authorityEnd)
}

internal class LoadedBody(val bytes: ByteArray, val charset: String?, val finalUrl: String)

/** An HTML page parsed with the post-redirect URL as its base, so relative links resolve the way the browser would. */
internal class LoadedPage(val document: Document, val finalUrl: String)

/** A page's head, parsed alone (see [headHtml]), plus the word count of its body text, counted without a DOM. */
internal class LoadedHead(val document: Document, val finalUrl: String, val bodyWords: Int)

/**
 * Real pages, even the longest Wikipedia lists (about 51,000 estimated elements), stay well below this; crafted pages
 * do not.
 */
internal const val MAX_ELEMENTS = 100_000

private val optionalEndTags = Regex("""</(body|html)\s*>""", RegexOption.IGNORE_CASE)

/**
 * Ksoup 0.2.6 throws on a `</body>` end tag when about 256 elements are still open (pages with unclosed tags reach
 * that). Both end tags are optional in HTML, so the retry parses the page without them.
 */
/**
 * The page as a DOM, or null when it would build more than [MAX_ELEMENTS] elements, cannot be parsed, or (JVM only)
 * runs the parse out of memory. Only [canonicalHtml]'s output is estimated and parsed: the parser only ever sees
 * scanner output.
 */
internal fun parseGuarded(html: String, baseUri: String): Document? {
    val canonical = canonicalHtml(html) ?: return null
    if (estimatedElementCount(canonical) > MAX_ELEMENTS) return null
    return try {
        parseHtml(canonical, baseUri)
    } catch (e: Throwable) {
        if (e is Exception || isOutOfMemory(e)) null else throw e
    }
}

private fun parseHtml(html: String, baseUri: String): Document =
    try {
        Ksoup.parse(html, baseUri)
    } catch (e: Exception) {
        Ksoup.parse(html.replace(optionalEndTags, ""), baseUri)
    }

/**
 * GETs a URL over https with bounded redirects, a status check, a content-type check and a size cap enforced while
 * reading. Known limits: no cookie jar, so a site that sets a cookie and redirects back to itself ends in
 * [FetchFailureKind.TooManyRedirects]; loopback and LAN hosts, and redirects to them, are fetched like any other host
 * (accepted: a client app issuing GETs, without credentials, for links its own user saved).
 */
internal class PageLoader(private val client: HttpClient) {
    /**
     * The whole page as a DOM, for the reader (opened by the user). Null when the page would build more than
     * [MAX_ELEMENTS] elements, when Ksoup cannot parse it, or (JVM only) when parsing runs out of memory.
     */
    suspend fun loadHtml(url: String): LoadedPage? {
        val body = loadPage(url)
        return parseGuarded(decodeHtml(body.bytes, body.charset), body.finalUrl)?.let { LoadedPage(it, body.finalUrl) }
    }

    /**
     * Only the head as a DOM, for metadata (run on every save): built from the canonical page's head tags, so its size
     * is bounded by construction, not by an estimate.
     */
    suspend fun loadHead(url: String): LoadedHead {
        val body = loadPage(url)
        val canonical = canonicalHtml(decodeHtml(body.bytes, body.charset)).orEmpty()
        val head = headHtml(canonical)
        return LoadedHead(Ksoup.parse(head.html, body.finalUrl), body.finalUrl, bodyWordCount(canonical, head.bodyStart))
    }

    private suspend fun loadPage(url: String): LoadedBody =
        load(url, HTML_ACCEPT, MAX_HTML_BYTES) { it.match(ContentType.Text.Html) || it.match(XHTML) }

    suspend fun loadJson(url: String): LoadedBody =
        load(url, ContentType.Application.Json.toString(), MAX_JSON_BYTES) { it.match(ContentType.Application.Json) }

    /** The client does not follow redirects; this loop does, so each hop is upgraded to https, checked and counted. */
    private suspend fun load(url: String, accept: String, maxBytes: Int, isWanted: (ContentType) -> Boolean): LoadedBody {
        var current = upgradeToHttps(url)
        repeat(MAX_REDIRECTS + 1) {
            if (LinkCapturePolicy.normalize(current) == null) fail(FetchFailureKind.InvalidUrl)
            var location: String? = null
            val body = client.prepareGet(current) {
                // Last line of D17: whatever the URL text looked like, the request about to be sent must be https.
                if (this.url.protocol != URLProtocol.HTTPS) fail(FetchFailureKind.InvalidUrl)
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, accept)
                header(HttpHeaders.AcceptLanguage, ACCEPT_LANGUAGE)
            }.execute { response ->
                val redirect = response.headers[HttpHeaders.Location]?.takeIf { response.status.value in 300..399 }
                if (redirect != null) {
                    // Whitespace (including nbsp and em space) before the scheme would hide `http://` from the upgrade.
                    location = StringUtil.resolve(current, redirect.trim())
                    null
                } else {
                    read(response, current, maxBytes, isWanted)
                }
            }
            body?.let { return it }
            current = upgradeToHttps(location!!)
        }
        fail(FetchFailureKind.TooManyRedirects)
    }

    private suspend fun read(
        response: HttpResponse,
        url: String,
        maxBytes: Int,
        isWanted: (ContentType) -> Boolean,
    ): LoadedBody {
        val status = response.status.value
        when {
            status == 401 || status == 403 -> fail(FetchFailureKind.Blocked, httpStatus = status)
            status == 429 -> fail(FetchFailureKind.Blocked, retryable = true, httpStatus = status)
            status == 408 -> fail(FetchFailureKind.Timeout, retryable = true, httpStatus = status)
            status >= 500 -> fail(FetchFailureKind.HttpStatus, retryable = true, httpStatus = status)
            status !in 200..299 -> fail(FetchFailureKind.HttpStatus, httpStatus = status)
        }
        val type = runCatching { response.contentType() }.getOrNull()
        if (type == null || !isWanted(type.withoutParameters())) fail(FetchFailureKind.NotHtml)
        if ((response.contentLength() ?: 0) > maxBytes) fail(FetchFailureKind.TooLarge)
        // One byte past the cap tells a body of exactly maxBytes from a larger one without reading further.
        val bytes = response.bodyAsChannel().readBuffer(maxBytes + 1L).readByteArray()
        if (bytes.size > maxBytes) fail(FetchFailureKind.TooLarge)
        return LoadedBody(bytes, type.parameter("charset"), url)
    }

    companion object {
        /** Large article pages (long Wikipedia entries, news pages with inline scripts) stay under 5 MB of HTML. */
        const val MAX_HTML_BYTES = 5 * 1024 * 1024
        const val MAX_JSON_BYTES = 256 * 1024

        /** Browsers allow 20; real chains (short link → tracker → canonical → locale) stay well under 10. */
        const val MAX_REDIRECTS = 10

        private val XHTML = ContentType("application", "xhtml+xml")
        private const val HTML_ACCEPT = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8"

        // Several sites serve a bot wall or a stripped page to non-browser user agents.
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/142.0.0.0 Safari/537.36"
        const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
    }
}

/**
 * True for an out-of-memory error the runtime can recover from. JVM and Android: `OutOfMemoryError`, a last resort
 * after the element guard. Kotlin/Native cannot recover from running out of memory, so iOS always says false.
 */
internal expect fun isOutOfMemory(error: Throwable): Boolean

/** Decodes [bytes] with the charset [name], or returns null when this platform cannot decode it. */
internal expect fun decodeWithCharset(bytes: ByteArray, name: String): String?

private val metaCharset = Regex("""<meta[^>]+charset\s*=\s*["']?\s*([A-Za-z0-9._:-]+)""", RegexOption.IGNORE_CASE)

/**
 * WHATWG Encoding labels mapped to the decoder browsers actually use, for the encodings where the platform's own
 * decoder of that name is narrower or missing: latin1 and ascii labels are windows-1252 (so `“` sent as 0x93 is not a
 * control char), Shift_JIS labels windows-31j (cp932: ①, ㈱), GBK / GB2312 labels gb18030, EUC-KR labels windows-949
 * (UHC: 똠), Big5 labels Big5-HKSCS (嘅), TIS-620 / ISO-8859-11 labels windows-874 (–), ISO-8859-9 labels windows-1254
 * (€), ISO-8859-8-I labels ISO-8859-8, and x-mac-ukrainian x-mac-cyrillic. Each target name was checked to decode its
 * distinguishing character on Android/JVM (java.nio) and iOS (Foundation). Other labels go to the platform unchanged.
 * Known limit: iOS decodes Big5-HKSCS extension characters to the private-use code points of the older HKSCS mapping
 * (0x8840 gives U+F303 where browsers give U+31C0); the common characters are the same on both.
 */
private val whatwgLabels: Map<String, String> = buildMap {
    val windows1252 = listOf(
        "ansi_x3.4-1968", "ascii", "cp1252", "cp819", "csisolatin1", "ibm819", "iso-8859-1", "iso-ir-100", "iso8859-1",
        "iso88591", "iso_8859-1", "iso_8859-1:1987", "l1", "latin1", "us-ascii", "windows-1252", "x-cp1252",
    )
    val shiftJis = listOf("csshiftjis", "ms932", "ms_kanji", "shift-jis", "shift_jis", "sjis", "windows-31j", "x-sjis")
    val gbk = listOf(
        "chinese", "csgb2312", "csiso58gb231280", "gb2312", "gb_2312", "gb_2312-80", "gbk", "iso-ir-58", "x-gbk", "gb18030",
    )
    val utf8 = listOf("unicode-1-1-utf-8", "unicode11utf8", "unicode20utf8", "utf-8", "utf8", "x-unicode20utf8")
    val eucKr = listOf(
        "cseuckr", "csksc56011987", "euc-kr", "iso-ir-149", "korean", "ks_c_5601-1987", "ks_c_5601-1989", "ksc5601",
        "ksc_5601", "windows-949",
    )
    val big5 = listOf("big5", "big5-hkscs", "cn-big5", "csbig5", "x-x-big5")
    val windows874 = listOf("dos-874", "iso-8859-11", "iso8859-11", "iso885911", "tis-620", "windows-874")
    val windows1254 = listOf(
        "cp1254", "csisolatin5", "iso-8859-9", "iso-ir-148", "iso8859-9", "iso88599", "iso_8859-9", "iso_8859-9:1989",
        "l5", "latin5", "windows-1254", "x-cp1254",
    )
    val iso88598 = listOf(
        "csiso88598e", "csisolatinhebrew", "hebrew", "iso-8859-8", "iso-8859-8-e", "iso-ir-138", "iso8859-8", "iso88598",
        "iso_8859-8", "iso_8859-8:1988", "visual", "csiso88598i", "iso-8859-8-i", "logical",
    )
    val macCyrillic = listOf("x-mac-cyrillic", "x-mac-ukrainian")
    windows1252.forEach { put(it, "windows-1252") }
    shiftJis.forEach { put(it, "windows-31j") }
    gbk.forEach { put(it, "gb18030") }
    utf8.forEach { put(it, "utf-8") }
    eucKr.forEach { put(it, "windows-949") }
    big5.forEach { put(it, "big5-hkscs") }
    windows874.forEach { put(it, "windows-874") }
    windows1254.forEach { put(it, "windows-1254") }
    iso88598.forEach { put(it, "iso-8859-8") }
    // Foundation decodes this name per WHATWG; the JVM actuals map it to Java's closest table.
    macCyrillic.forEach { put(it, "x-mac-cyrillic") }
}

/**
 * A UTF-8 BOM wins (as in browsers), then the Content-Type charset, then `<meta charset>` in the first 4 KB, then UTF-8.
 * A label this platform cannot decode is skipped, so an unknown header label falls through to the meta tag.
 */
internal fun decodeHtml(bytes: ByteArray, headerCharset: String?): String {
    val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    if (hasBom) return bytes.decodeToString(3, bytes.size)
    val labels = sequence {
        headerCharset?.let { yield(it) }
        metaCharset.find(bytes.decodeToString(0, minOf(bytes.size, 4096)))?.let { yield(it.groupValues[1]) }
    }
    for (label in labels) {
        val name = label.trim('"', '\'', ' ').lowercase().let { whatwgLabels[it] ?: it }
        if (name == "utf-8") return bytes.decodeToString()
        decodeWithCharset(bytes, name)?.let { return it }
    }
    return bytes.decodeToString()
}
