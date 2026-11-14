package com.techgv.stash.core.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.safety.Cleaner
import com.fleeksoft.ksoup.safety.Safelist
import kotlin.math.sqrt

/**
 * Reader-mode text of a page. [extractedHtml] holds only headings, paragraphs, lists, quotes, pre/code, https images,
 * http(s) links, emphasis and line breaks, with every URL absolute. [textLength] and [wordCount] count its visible text.
 */
data class ExtractedArticle(
    val title: String?,
    val standfirst: String?,
    val extractedHtml: String,
    val textLength: Int,
    val wordCount: Int,
)

/**
 * Readability-style extraction: pick the article body, drop page chrome, sanitize what is left. Every step is linear in
 * the page size. Known limit: an article made only of list items (no paragraphs) is not extracted, because the
 * "reads like an article" score counts paragraph text.
 */
object ReaderExtractor {
    /** Stored with each cached article; bump it when extraction changes so old caches are re-extracted. */
    const val EXTRACTOR_VERSION = 1

    /** Readability's default: a candidate body needs 500 characters of text. */
    private const val MIN_TEXT_LENGTH = 500

    /**
     * Firefox's isProbablyReaderable: each paragraph of 140+ characters adds √(length − 140), and the page needs more
     * than 20. Stubs, cookie walls and section fronts (many short teaser blurbs) stay below it.
     */
    private const val MIN_PARAGRAPH_LENGTH = 140
    private const val MIN_READERABLE_SCORE = 20.0
    private const val MAX_LINK_DENSITY = 0.5
    private const val MAX_TITLE = 500
    private const val MAX_STANDFIRST = 300

    private const val JUNK = "script, style, noscript, iframe, frame, object, embed, svg, canvas, video, audio, template, " +
        "form, button, input, select, textarea, dialog, nav, header, footer, aside, [hidden], [aria-hidden=true], " +
        "[role=navigation], [role=banner], [role=contentinfo], [role=complementary], [role=dialog]"
    private const val ARTICLE_ROOTS = "[itemprop=articleBody], article, main, [role=main]"

    // From Mozilla Readability's unlikelyCandidates / okMaybeItsACandidate, plus cookie and consent walls.
    private val unlikely = Regex(
        "-ad-|ad-break|advert|age-?gate|age-verif|banner|breadcrumb|cmp|combx|comment|community|consent|cookie|" +
            "cover-wrap|didomi|disqus|footer|gdpr|header|legends|menu|modal|more-from|newsletter|nav|onetrust|pager|" +
            "pagination|popup|post-actions|promo|related|remark|replies|responses|rss|share|shoutbox|sidebar|" +
            "skyscraper|social|sp_message|sponsor|subscribe|supplemental",
        RegexOption.IGNORE_CASE,
    )
    private val maybe = Regex("and|article|body|column|content|main|shadow", RegexOption.IGNORE_CASE)

    /** Never the article, even on the retry: consent and age walls, comment threads, pop-ups. */
    private val neverChrome = Regex(
        "cookie|consent|gdpr|age-?gate|age-verif|cmp|onetrust|didomi|sp_message|comment|disqus|replies|modal|popup",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Whether a class-and-id string names page chrome, as the extractor judges it; the body word count skips such
     * containers too.
     */
    internal fun namesChrome(names: String): Boolean =
        (unlikely.containsMatchIn(names) && !maybe.containsMatchIn(names)) || neverChrome.containsMatchIn(names)
    private val neverUnlikely = setOf("html", "body", "article", "main")
    private val blockTags = setOf(
        "p", "div", "section", "article", "ul", "ol", "table", "blockquote", "pre", "figure",
        "h1", "h2", "h3", "h4", "h5", "h6",
    )
    private val whitespace = Regex("""\s+""")

    private val safelist = Safelist()
        .addTags("h1", "h2", "h3", "h4", "h5", "h6", "p", "br", "ul", "ol", "li", "blockquote", "pre", "code")
        .addTags("a", "img", "em", "i", "strong", "b")
        .addAttributes("a", "href")
        .addAttributes("img", "src", "alt")
        .addProtocols("a", "href", "http", "https")
        .addProtocols("img", "src", "https")

    /** Null when the page has no extractable article (the UI then says reader mode isn't available). Never throws. */
    fun extract(html: String, baseUrl: String): ExtractedArticle? =
        try {
            parseGuarded(html, baseUrl)?.let { extract(it) }
        } catch (e: Throwable) {
            // Ksoup can still fail on a hostile page (or, on the JVM, run out of memory); it has no article to show.
            if (e is Exception || isOutOfMemory(e)) null else throw e
        }

    /** Strips [document] in place. Never throws. */
    internal fun extract(document: Document): ExtractedArticle? =
        try {
            extractOrNull(document)
        } catch (e: Throwable) {
            if (e is Exception || isOutOfMemory(e)) null else throw e
        }

    private fun extractOrNull(document: Document): ExtractedArticle? {
        if (document.getAllElements().size > MAX_ELEMENTS) return null
        val title = meta(document, "og:title", "twitter:title") ?: document.title()
        val description = meta(document, "og:description", "twitter:description", "description")

        document.select(JUNK).remove()
        val unlikely = unlikelyElements(document)
        var lengths = TextLengths(document, skip = unlikely)
        var root = findRoot(document, lengths)
        if (root != null) {
            unlikely.forEach { it.remove() }
        } else {
            // As Readability does: a chrome-like class on a plain wrapper (`has-sidebar` around a `div.post`) hid the
            // article, so look again with nothing filtered; but what was filtered must not become the article itself.
            lengths = TextLengths(document)
            root = findRoot(document, lengths)?.takeUnless { isChromeOnRetry(it, unlikely) } ?: return null
        }
        if (isTeaserListing(root)) return null

        root.select("img").forEach { useLazySource(it, document.baseUri()) }
        root.select("img").filter(::isTrackingPixel).forEach { it.remove() }
        root.select("ul, ol, div, section, table")
            .filter { it !== root && lengths.linkDensity(it) > MAX_LINK_DENSITY }
            .forEach { it.remove() }
        // Divs used as paragraphs would otherwise run their text together once the sanitizer unwraps them.
        root.select("div").filter { div -> div.children().none { it.normalName() in blockTags } }.forEach { it.tagName("p") }
        val cleanTitle = cleanText(title, MAX_TITLE)
        root.select("h1, h2").filter { cleanText(it.text(), MAX_TITLE) == cleanTitle }.forEach { it.remove() }
        val italicLead = root.selectFirst("p")?.takeIf { p ->
            p.childrenSize() == 1 && p.child(0).normalName() in setOf("em", "i") && p.text() == p.child(0).text()
        }
        // The lead leaves the body only when it becomes the standfirst; otherwise its text would be lost.
        val standfirst = description ?: italicLead?.text()?.also { italicLead.remove() }

        // Cleaner on a parsed fragment, not Ksoup.clean: that pretty-prints its output, which is quadratic on many blank
        // text nodes (an article followed by 96,000 empty sections took 16 s).
        val fragment = Cleaner(safelist).clean(Ksoup.parseBodyFragment(root.html(), document.baseUri()))
        fragment.outputSettings().prettyPrint(false)
        val clean = fragment.body()
        clean.select("img:not([src])").remove()
        clean.select("a:not([href])").unwrap()
        val cleanLengths = TextLengths(clean)
        if (readerableScore(clean, cleanLengths) <= MIN_READERABLE_SCORE) return null
        if (cleanLengths.linkDensity(clean) > MAX_LINK_DENSITY) return null
        val text = clean.text()
        return ExtractedArticle(
            title = cleanTitle,
            standfirst = standfirst?.let { cleanText(it, MAX_STANDFIRST) },
            extractedHtml = clean.html(),
            textLength = text.length,
            // shortcut: whitespace-separated words undercount Chinese, Japanese and Thai; count characters there if needed.
            wordCount = text.split(whitespace).count { it.isNotEmpty() },
        )
    }

    /**
     * A root the retry must not use: an element the filter removed, or anything that is or sits inside a consent wall,
     * comment thread or pop-up. The walk stops below `<body>`: classes there and on `<html>` describe page state
     * (`comments-open`, `modal-open`, `cookie-consent-pending`), not the content. Article roots (`<article>`,
     * `has-comments` on an articleBody…) never reach the retry: the filter does not remove them.
     *
     * Known limit: a root named only like a newsletter (`div.newsletter-issue`) is rejected too. Its class cannot tell
     * an issue from a sign-up block with long copy, and a missed article only falls back to the web view.
     */
    private fun isChromeOnRetry(root: Element, unlikely: Set<Element>): Boolean =
        root in unlikely ||
            (sequenceOf(root) + root.parents().asSequence().takeWhile { it.normalName() != "body" && it.normalName() != "html" })
                .any { neverChrome.containsMatchIn(chromeName(it)) }

    /**
     * A listing rather than an article: five or more of the root's children are teasers (a heading holding a link),
     * and they make up at least half of its children. Search results, archives and section fronts look like this; an
     * article's children are mostly paragraphs.
     */
    private fun isTeaserListing(root: Element): Boolean {
        val children = root.children()
        val teasers = children.count { child -> child.select("h1, h2, h3, h4, h5, h6").any { it.selectFirst("a[href]") != null } }
        return teasers >= 5 && teasers * 2 >= children.size
    }

    private fun chromeName(element: Element) = "${element.className()} ${element.id()}"

    private fun findRoot(document: Document, lengths: TextLengths): Element? =
        preferredRoot(document, lengths) ?: scoredRoot(document, lengths)

    /**
     * Elements whose class or id looks like page chrome, except wrappers of an article root: a layout div called
     * `has-sidebar` around the `<article>` must stay.
     */
    private fun unlikelyElements(document: Document): Set<Element> {
        val holdsArticle = HashSet<Element>()
        for (articleRoot in document.select(ARTICLE_ROOTS)) {
            var parent = articleRoot.parent()
            // Stops at the first wrapper already marked, so marking stays linear in the page size.
            while (parent != null && holdsArticle.add(parent)) parent = parent.parent()
        }
        return document.select("[class], [id]")
            .filter { it.normalName() !in neverUnlikely && it !in holdsArticle && !it.`is`(ARTICLE_ROOTS) }
            .filter { "${it.className()} ${it.id()}".let { name -> unlikely.containsMatchIn(name) && !maybe.containsMatchIn(name) } }
            .toSet()
    }

    /** `[itemprop=articleBody]`, then `article`, then `main`: the longest match that reads like an article. */
    private fun preferredRoot(document: Document, lengths: TextLengths): Element? =
        listOf("[itemprop=articleBody]", "article", "main, [role=main]").firstNotNullOfOrNull { selector ->
            document.select(selector).filter { looksLikeArticle(it, lengths) }.maxByOrNull { lengths.text(it) }
        }

    /** Scores parents of text paragraphs by length and commas, as Readability does, then penalizes link-heavy ones. */
    private fun scoredRoot(document: Document, lengths: TextLengths): Element? {
        val scores = HashMap<Element, Double>()
        for (paragraph in document.select("p, pre, td, blockquote")) {
            val length = lengths.text(paragraph)
            if (length < 25) continue
            val score = 1.0 + lengths.commas(paragraph) + minOf(length / 100, 3)
            paragraph.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score }
            paragraph.parent()?.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score / 2 }
        }
        return scores.entries
            .maxByOrNull { (element, score) -> score * (1 - lengths.linkDensity(element)) }
            ?.key
            ?.takeIf { looksLikeArticle(it, lengths) }
    }

    private fun looksLikeArticle(element: Element, lengths: TextLengths): Boolean =
        lengths.text(element) >= MIN_TEXT_LENGTH && lengths.linkDensity(element) <= MAX_LINK_DENSITY

    private fun readerableScore(body: Element, lengths: TextLengths): Double =
        body.select("p, pre").sumOf { sqrt(maxOf(0, lengths.text(it) - MIN_PARAGRAPH_LENGTH).toDouble()) }

    /**
     * Lazy-loading pages keep the real image in `data-src` or `data-original` and put a placeholder (often a relative or
     * https pixel) in `src`, so an https lazy source always wins; the first `srcset` entry is used only when `src` is
     * not https.
     */
    private fun useLazySource(image: Element, baseUri: String) {
        fun https(value: String) = value.trim().substringBefore(' ').takeIf { it.isNotEmpty() }
            ?.let { StringUtil.resolve(baseUri, it) }
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
        val lazy = https(image.attr("data-src")) ?: https(image.attr("data-original"))
            ?: https(image.attr("srcset").substringBefore(',')).takeUnless { https(image.attr("src")) != null }
        lazy?.let { image.attr("src", it) }
    }

    private fun isTrackingPixel(image: Element): Boolean =
        image.attr("width").trim().toIntOrNull()?.let { it <= 1 } == true ||
            image.attr("height").trim().toIntOrNull()?.let { it <= 1 } == true
}

/**
 * Text length, link-text length and comma count of every element under a root, measured in one pass so that scoring
 * every candidate stays linear. Text nodes count with their whitespace collapsed, as `Element.text()` shows them.
 * Subtrees in [skip] count as absent: their elements measure zero and add nothing to their ancestors.
 */
private class TextLengths(root: Element, private val skip: Set<Element> = emptySet()) {
    private class Measure(val text: Int, val linkText: Int, val commas: Int)

    private val measures = HashMap<Element, Measure>()

    init {
        measure(root, inLink = false)
    }

    // Recursion depth is bounded: Ksoup nests elements at most 512 deep.
    private fun measure(element: Element, inLink: Boolean): Measure {
        val link = inLink || element.normalName() == "a"
        var text = 0
        var linkText = 0
        var commas = 0
        for (child in element.childNodes()) {
            if (child is TextNode) {
                val length = child.text().length
                text += length
                if (link) linkText += length
                commas += child.getWholeText().count { it == ',' }
            } else if (child is Element && child !in skip) {
                val inner = measure(child, link)
                text += inner.text
                linkText += inner.linkText
                commas += inner.commas
            }
        }
        return Measure(text, linkText, commas).also { measures[element] = it }
    }

    fun text(element: Element): Int = measures[element]?.text ?: 0

    fun commas(element: Element): Int = measures[element]?.commas ?: 0

    fun linkDensity(element: Element): Double {
        val measure = measures[element] ?: return 0.0
        return if (measure.text == 0) 0.0 else measure.linkText.toDouble() / measure.text
    }
}
