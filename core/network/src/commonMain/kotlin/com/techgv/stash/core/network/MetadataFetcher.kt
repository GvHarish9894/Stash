package com.techgv.stash.core.network

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser
import com.techgv.stash.core.model.ContentType
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ceil

/**
 * What a page says about itself. Every URL is absolute https (http is upgraded) and at most 2,048 chars; text is
 * entity-decoded, whitespace-collapsed and capped (title 500, description 300 chars). A null [title] means the page gave
 * none: the caller keeps the URL as the title.
 */
data class PageMetadata(
    /**
     * The URL after redirects; for oEmbed results the unwrapped page URL. The data layer derives domain, site name,
     * source key and the host's content type from it.
     */
    val finalUrl: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    /** The page's declared icon, otherwise `/favicon.ico` on the final origin. */
    val faviconUrl: String,
    /** [ContentType.Video] when oEmbed or `og:type` says so; null when the page adds nothing to what the host implies. */
    val contentType: ContentType?,
    /**
     * Articles: from JSON-LD `wordCount`, otherwise from the body's visible words (counted without a DOM, outside
     * links and page chrome) when there are enough for an article. Videos: the duration, when known.
     */
    val readingMinutes: Int?,
    /** `link[rel=canonical]`, then `og:url`. */
    val canonicalUrl: String?,
    /** `html[lang]`, for example `en` or `ta-IN`; at most 35 chars. */
    val language: String?,
)

sealed interface MetadataResult {
    data class Success(val metadata: PageMetadata) : MetadataResult
}

/**
 * Fetches link metadata: oEmbed for known video providers, then Open Graph → Twitter card → plain HTML. All work runs
 * on [dispatcher]; tests may pass their own.
 */
class MetadataFetcher(client: HttpClient, private val dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    private val pages = PageLoader(client)

    suspend fun fetch(url: String): MetadataResult = boundedFetch(dispatcher, { it }) {
        val capture = captureLink(url) ?: return@boundedFetch FetchFailure(FetchFailureKind.InvalidUrl, retryable = false)
        val metadata = oEmbedEndpoints[capture.siteName]?.let { fromOEmbed(it, capture.url) }
            ?: pages.loadHead(capture.url).let {
                fromHtml(it.document, it.finalUrl, hostIsVideo = capture.contentType == ContentType.Video, bodyWords = it.bodyWords)
            }
        MetadataResult.Success(metadata)
    }

    /** Null when the provider has nothing usable, so the caller falls back to the page HTML. */
    private suspend fun fromOEmbed(endpoint: String, pageUrl: String): PageMetadata? = failuresAsValues({ null }) {
        val request = URLBuilder(endpoint).apply {
            parameters.append("url", pageUrl)
            parameters.append("format", "json")
        }.buildString()
        val json = Json.parseToJsonElement(pages.loadJson(request).bytes.decodeToString()).jsonObject
        fun string(key: String) = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val title = string("title")?.let { cleanText(it, MAX_TITLE) } ?: return@failuresAsValues null
        val isVideo = string("type") == "video"
        val finalUrl = upgradeToHttps(pageUrl)
        PageMetadata(
            finalUrl = finalUrl,
            title = title,
            description = string("description")?.let { cleanText(it, MAX_DESCRIPTION) },
            imageUrl = string("thumbnail_url")?.let(::outputUrl),
            faviconUrl = StringUtil.resolve(finalUrl, "/favicon.ico"),
            contentType = if (isVideo) ContentType.Video else null,
            readingMinutes = json["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?.takeIf { isVideo && it > 0 }
                ?.let { ceil(it / 60).toInt() },
            canonicalUrl = null,
            language = null,
        )
    }

    /** [document] holds only the page's head (see [headHtml]); [bodyWords] counts the body's visible words. */
    private fun fromHtml(document: Document, finalUrl: String, hostIsVideo: Boolean, bodyWords: Int): PageMetadata {
        val pageIsVideo = meta(document, "og:type")?.startsWith("video", ignoreCase = true) == true
        return PageMetadata(
            finalUrl = finalUrl,
            title = (meta(document, "og:title") ?: meta(document, "twitter:title") ?: document.title())
                .let { cleanText(it, MAX_TITLE) },
            description = (meta(document, "og:description") ?: meta(document, "twitter:description") ?: meta(document, "description"))
                ?.let { cleanText(it, MAX_DESCRIPTION) },
            imageUrl = metaUrl(document, "og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src"),
            faviconUrl = document.select("link[rel][href]")
                .firstOrNull { "icon" in it.attr("rel").lowercase().split(' ') }
                ?.absUrl("href")
                ?.let(::outputUrl)
                ?: StringUtil.resolve(finalUrl, "/favicon.ico"),
            contentType = if (pageIsVideo) ContentType.Video else null,
            readingMinutes = if (pageIsVideo || hostIsVideo) videoMinutes(document) else articleMinutes(document, bodyWords),
            canonicalUrl = document.selectFirst("link[rel=canonical][href]")?.absUrl("href")?.let(::outputUrl)
                ?: metaUrl(document, "og:url"),
            language = document.selectFirst("html[lang]")?.attr("lang")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LANGUAGE },
        )
    }

    private fun videoMinutes(document: Document): Int? {
        val seconds = (jsonLdValue(document, "duration") ?: document.selectFirst("meta[itemprop=duration]")?.attr("content"))
            ?.let(::isoDurationSeconds)
            ?: meta(document, "og:video:duration", "video:duration")?.toDoubleOrNull()
        return seconds?.takeIf { it > 0 }?.let { ceil(it / 60).toInt() }
    }

    private fun articleMinutes(document: Document, bodyWords: Int): Int? {
        val words = jsonLdValue(document, "wordCount")?.toIntOrNull()?.takeIf { it > 0 }
            ?: bodyWords.takeIf { it >= MIN_ARTICLE_WORDS }
        return words?.let { maxOf(1, (it + WORDS_PER_MINUTE / 2) / WORDS_PER_MINUTE) }
    }

    private companion object {
        const val MAX_TITLE = 500
        const val MAX_DESCRIPTION = 300
        const val MAX_LANGUAGE = 35

        /**
         * Fewer body words than this and the page is not an article (a stub, a wall, a hub); about the 500 characters
         * the reader extractor requires, at 5 characters a word.
         */
        const val MIN_ARTICLE_WORDS = 100

        /** Silent reading of non-fiction, from Brysbaert (2019), "How many words do we read per minute?". */
        const val WORDS_PER_MINUTE = 238

        /** Keyed by registrable domain. Each endpoint was checked live with curl (format=json returns 200 + JSON). */
        val oEmbedEndpoints = mapOf(
            "youtube.com" to "https://www.youtube.com/oembed",
            "youtu.be" to "https://www.youtube.com/oembed",
            "vimeo.com" to "https://vimeo.com/api/oembed.json",
            "dailymotion.com" to "https://www.dailymotion.com/services/oembed",
            "dai.ly" to "https://www.dailymotion.com/services/oembed",
        )
    }
}

private const val MAX_URL_LENGTH = 2_048

/** An absolute http(s) URL upgraded to https, or null when it is not one or is longer than [MAX_URL_LENGTH]. */
private fun outputUrl(url: String): String? =
    url.takeIf(::isHttpUrl)?.let(::upgradeToHttps)?.takeIf { it.length <= MAX_URL_LENGTH }

private fun metaElement(document: Document, keys: Array<out String>): Element? = keys.firstNotNullOfOrNull { key ->
    document.select("""meta[property="$key"], meta[name="$key"]""").firstOrNull { it.attr("content").isNotBlank() }
}

/** The first non-blank `content` of `meta[property=key]` or `meta[name=key]`, trying [keys] in order. */
internal fun meta(document: Document, vararg keys: String): String? = metaElement(document, keys)?.attr("content")?.trim()

private fun metaUrl(document: Document, vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> metaElement(document, arrayOf(key))?.absUrl("content")?.let(::outputUrl) }

/** The first [key] anywhere in the page's JSON-LD blocks (including `@graph`), as text. */
private fun jsonLdValue(document: Document, key: String): String? =
    document.select("""script[type="application/ld+json"]""").firstNotNullOfOrNull { script ->
        runCatching { Json.parseToJsonElement(script.data()) }.getOrNull()?.find(key)
    }

private fun JsonElement.find(key: String): String? = when (this) {
    is JsonObject -> (this[key] as? JsonPrimitive)?.content ?: values.firstNotNullOfOrNull { it.find(key) }
    is JsonArray -> firstNotNullOfOrNull { it.find(key) }
    else -> null
}

private val isoDuration = Regex("""^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$""")

/** `PT1H2M3S` → 3723. Null when the text is not an ISO 8601 duration. */
private fun isoDurationSeconds(text: String): Double? {
    val match = isoDuration.matchEntire(text.trim()) ?: return null
    val (days, hours, minutes, seconds) = match.destructured
    return (days.toDoubleOrNull() ?: 0.0) * 86_400 + (hours.toDoubleOrNull() ?: 0.0) * 3_600 +
        (minutes.toDoubleOrNull() ?: 0.0) * 60 + (seconds.toDoubleOrNull() ?: 0.0)
}

private val whitespace = Regex("""[\s\u00A0]+""")

/**
 * Decodes leftover entities (pages often double-encode meta content), collapses whitespace and caps at [max] chars.
 * Known limit: the cap counts UTF-16 units, so a combining mark, a Tamil or Hindi vowel sign or half of a flag can be
 * cut at the boundary; only surrogate pairs are kept whole.
 */
internal fun cleanText(raw: String, max: Int): String? {
    val text = Parser.unescapeEntities(raw, false).replace(whitespace, " ").trim()
    if (text.isEmpty()) return null
    if (text.length <= max) return text
    val end = if (text[max - 1].isHighSurrogate()) max - 1 else max
    return text.substring(0, end).trimEnd()
}
