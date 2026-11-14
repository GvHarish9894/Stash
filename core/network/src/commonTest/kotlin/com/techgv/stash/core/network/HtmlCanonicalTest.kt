package com.techgv.stash.core.network

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlCanonicalTest {
    private val head = "<html><head><title>T</title></head><body>"
    private val open = "<b><i><u><s><em><strong><small><big><tt><font><code><strike>"
    private val close = "</strike></code></font></tt></big></small></strong></em></s></u></i></b>"

    /**
     * Every page the verifiers built to beat the element guard (rounds 2 and 3). Raw, each builds 196,005 to 2,487,017
     * elements from at most 5.2 MB; V* fooled the round-2 estimate, R* and X* the round-3 scanner.
     */
    private val hostilePages = linkedMapOf(
        "V0 12 unclosed formatting tags, <p>y x 99k" to "$head<p>${open}x" + "<p>y".repeat(99_000),
        "V1 closing tags in a comment" to "$head<p>${open}x<!--$close-->" + "<p>y".repeat(99_000),
        "V2 V1 plus 1.2 million </p>" to "$head<p>${open}x<!--$close-->" + "<p>y".repeat(99_000) + "</p>".repeat(1_200_000),
        "V3 V1 x 95k" to "$head<p>${open}x<!--$close-->" + "<p>y".repeat(95_000),
        "V4 closing tags in an attribute value" to "$head<p>${open}x<span title='$close'>z</span>" + "<p>y".repeat(95_000),
        "V5 closing tags in script text" to "$head<p>${open}x<script>var s = '$close'</script>" + "<p>y".repeat(95_000),
        "V6 closing tags ignored inside a table cell" to
            "$head<p>${open.repeat(3)}x<table><tr><td>${close.repeat(3)}</td></tr></table>" + "<p>y".repeat(92_000),
        "V7 stray </p> x 1.3 million" to head + "</p>".repeat(1_300_000),
        "V8 stray </br> x 1.04 million" to head + "</br>".repeat(1_040_000),
        "V9 200 <font> with different attributes" to
            head + (0 until 200).joinToString("") { "<font color=\"#${it.toString().padStart(6, '0')}\">" } + "<p>y".repeat(24_000),
        "V10 <table><td>x</table> x 49k" to head + "<table><td>x</table>".repeat(49_000),
        "R1 <span =\"> hides markup" to "$head<span =\"><p>${open}x" + "<p>y".repeat(99_000) + "\">z",
        "R2 <!--> empty comment" to "$head<!--><p>${open}x" + "<p>y".repeat(99_000) + "-->",
        "R3 --!> comment end" to "$head<!-- a --!><p>${open}x" + "<p>y".repeat(99_000) + "-->",
        "R4 <svg><style> foreign content" to "$head<svg><style><p>${open}x" + "<p>y".repeat(99_000) + "</style></svg>",
        "R5 <math><title> foreign content" to "$head<math><title><p>${open}x" + "<p>y".repeat(99_000) + "</title></math>",
        "R6 end tags ending in U+001F" to "$head<p>${open}x" + close.replace(">", "\u001F>") + "<p>y".repeat(99_000),
        "R7 <span a=U+000B\"> unquoted value" to "$head<span a=\u000B\"><p>${open}x" + "<p>y".repeat(99_000) + "\">z",
        "R8 <span a=\"x\"=\"> second quote" to "$head<span a=\"x\"=\"><p>${open}x" + "<p>y".repeat(99_000) + "\">z",
        "Q1 <col><td> x 49k (each <col> closes the row group)" to "$head<table>" + "<col><td>".repeat(49_000),
        "Q3 <caption><col> x 49k" to "$head<table>" + "<caption><col>".repeat(49_000),
        "X1 <meta =\"> in the head x 99k" to xPage(99_000),
        "X2 <meta =\"> in the head x 24k" to xPage(24_000),
        "H1 4.8 MB of <p><b> inside <head>" to
            "<html lang=en><head><title>T</title><meta name=description content=D>" + "<p><b>y".repeat(600_000) + "</head><body><p>x</body>",
        "H3 <body> hidden before the real one" to
            "<html lang=de><head><!-- <body> --><meta name=x content=\"<body>\"><script>var b='<body>'</script><style>/*<body>*/</style>" +
                "<noscript><body></noscript><title>Real</title><meta property=og:image content=/i.png></head><body>" + "<p>y".repeat(99_000),
    )

    @Test
    fun everyHostilePageTripsTheGuardOrBuildsFewerThan100kElementsFromTheCanonicalPage() {
        for ((name, page) in hostilePages) {
            val canonical = canonicalHtml(page)
            val estimate = canonical?.let { estimatedElementCount(it) }
            val document = parseGuarded(page, "https://example.com/")
            if (canonical == null || estimate!! > MAX_ELEMENTS) {
                // The decision is the canonical page's: nothing was parsed.
                assertNull(document, name)
            } else {
                val built = document!!.getAllElements().size
                assertTrue(built < MAX_ELEMENTS, "$name: estimate $estimate passed the guard but the parser built $built")
                assertTrue(built <= estimate + 4, "$name: built $built elements, more than the estimate $estimate")
            }
        }
    }

    private fun xPage(blocks: Int) = "<html lang=\"en\"><head><title>Meta</title><meta property=\"og:title\" content=\"OG\"><meta =\"><p>${open}x" +
        "<p>y".repeat(blocks) + "\"></head><body><p>hi</p></body></html>"

    @Test
    fun canonicalOutputStaysWithinTheCapInsideOneAttributeValue() {
        // N33: 5 MB of `&` in one attribute value would escape to 25 million chars.
        val page = "$head<p title=\"" + "&".repeat(5_000_000) + "\">"
        val out = StringBuilder()

        assertNull(canonicalHtml(page, out))
        assertTrue(out.length <= MAX_CANONICAL_CHARS + 8, "the buffer grew to ${out.length} chars")
        assertNull(parseGuarded(page, "https://example.com/"))
    }

    @Test
    fun canonicalOutputStaysWithinTheCapInsideOneTextRun() {
        val out = StringBuilder()

        assertNull(canonicalHtml(head + "<".repeat(5_000_000), out))
        assertTrue(out.length <= MAX_CANONICAL_CHARS + 8, "the buffer grew to ${out.length} chars")
    }

    @Test
    fun headOfXPagesIsAHandfulOfElements() {
        for (blocks in listOf(24_000, 99_000, 1_200_000)) {
            val canonical = canonicalHtml(xPage(blocks))!!
            val document = Ksoup.parse(headHtml(canonical).html)

            assertTrue(document.getAllElements().size <= 8, "built ${document.getAllElements().size}")
            assertEquals("OG", meta(document, "og:title"))
        }
    }

    @Test
    fun canonicalFormDropsCommentsDoctypesAndProcessingInstructions() {
        assertEquals("<p>a</p>b", canonicalHtml("<!DOCTYPE html><?xml x?><!-- c --><p>a</p><!-->b"))
        assertEquals("<p>after</p>", canonicalHtml("<!-- a --!><p>after</p>"))
        assertEquals("", canonicalHtml("<!-- never closed <p>x"))
    }

    @Test
    fun canonicalAttributesAreQuotedEscapedAndSafelyNamed() {
        assertEquals(
            "<a href=\"/x?a=1&amp;b=2\" title=\"&lt;b&gt; &quot;q&quot;\" data-x=\"y\">t</a>",
            canonicalHtml("<A HREF=/x?a=1&amp;b=2 title='<b> \"q\"' data-x=y =junk \"odd=1>t</a>"),
        )
        // An `=` that starts an attribute name keeps the following quote as part of the name; the tag ends at `>`.
        assertEquals("<span><p>x</p>\"&gt;z", canonicalHtml("<span =\"><p>x</p>\">z"))
        // U+000B is not HTML whitespace, so it is part of the unquoted value.
        assertEquals("<span a=\"\u000B&quot;\"><p>", canonicalHtml("<span a=\u000B\"><p>"))
        // The first of duplicate attributes wins.
        assertEquals("<p id=\"a\">", canonicalHtml("<p id=a id=b>"))
    }

    @Test
    fun rawTextAndForeignContentNeverBecomeMarkup() {
        assertEquals("<title>&lt;p&gt;T</title><p>", canonicalHtml("<title><p>T</title><p>"))
        assertEquals("<p>", canonicalHtml("<script>document.write('<p><p>')</script><style><p></style><textarea><p></textarea><p>"))
        assertEquals(
            "<script type=\"application/ld+json\">{\"a\":\"&lt;b&gt;\"}</script>",
            canonicalHtml("<script type=\"application/ld+json\">{\"a\":\"<b>\"}</script>"),
        )
        assertEquals("<p>a</p><p>b", canonicalHtml("<p>a</p><svg><style><p>x</style><svg><p></svg></svg><math><title><p></title></math><p>b"))
        assertEquals("<p>", canonicalHtml("<p><plaintext><b>rest is text"))
    }

    @Test
    fun strayAngleBracketsAndCutOffTagsAreText() {
        assertEquals("a &lt; b &gt; c &lt;3", canonicalHtml("a < b > c <3"))
        assertEquals("<p>x", canonicalHtml("<p>x<a href=\"never closed"))
        assertEquals("x", canonicalHtml("x</ >"))
    }

    @Test
    fun aRealArticleSurvivesCanonicalisation() {
        val canonical = canonicalHtml(SyntheticPages.newsArticle)!!
        val article = ReaderExtractor.extract(canonical, "https://news.example.com/a")!!

        assertEquals(SyntheticPages.bodyText.length, article.textLength)
        assertEquals(canonical, canonicalHtml(canonical), "canonical form is a fixed point")
    }
}
