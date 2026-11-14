package com.techgv.stash.core.network

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlScanTest {
    @Test
    fun estimateOfARealArticleIsCloseToWhatTheParserBuilds() {
        val built = Ksoup.parse(SyntheticPages.newsArticle).getAllElements().size
        val estimate = estimatedElementCount(SyntheticPages.newsArticle)

        assertTrue(estimate >= built - 4 && estimate <= built * 2, "estimate $estimate, built $built")
    }

    @Test
    fun estimateFollowsTheTokenizer() {
        // Tags inside comments, <!…>, <?…>, attribute values and raw text are not tags.
        assertEquals(1, estimatedElementCount("<!-- <p><p> --><!DOCTYPE html><?xml x?><p title='<b><i>'>"))
        assertEquals(2, estimatedElementCount("<script>if (a<b) document.write('<p><p>')</script><p>"))
        assertEquals(1, estimatedElementCount("<plaintext><p><p><p>"))
        // Formatting end tags hidden in a comment do not close anything.
        assertEquals(1 + 2 + 2, estimatedElementCount("<b>x<!--</b>--><p>y<p>z"))
        // Stray end tags the parser turns into elements.
        assertEquals(2, estimatedElementCount("</p></br></div>"))
        // Implied tbody and tr.
        assertEquals(4, estimatedElementCount("<table><td>x</table>"))
        // A bare <col> adds a colgroup and closes the row group, so the next cell gets a new tbody and tr.
        assertEquals(1 + 2 + 3 + 2 + 3, estimatedElementCount("<table><col><td>a<col><td>b"))
        // A caption closes the row group too.
        assertEquals(1 + 3 + 1 + 3, estimatedElementCount("<table><td>a<caption><td>b"))
        assertEquals(4, estimatedElementCount("<table><tbody><tr><td>x</table>"))
        // A formatting end tag inside a cell does not reach the <b> opened outside the table, so <p> still re-creates it:
        // b 1, table 2, tr 2 + implied tbody 1, td 2, p 2.
        assertEquals(1 + 2 + 3 + 2 + 2, estimatedElementCount("<b><table><tr><td></b></td></tr></table><p>y"))
        // Formatting tags differing only by attributes are all re-created, not just three of them.
        assertEquals(1 + 2 + 3 + 4 + 5 + 6, estimatedElementCount((1..5).joinToString("") { "<font size=$it>" } + "<p>"))
    }

    @Test
    fun headHtmlKeepsOnlyHeadTagsWhateverTheBodyHolds() {
        val page = """<html lang="ta"><head><title>Tide &amp; time</title><meta property="og:title" content="T">
            <link rel="icon" href="/i.png"><script>var x = '<p>'.repeat(9)</script><style>p{}</style>
            <script type="application/ld+json">{"wordCount": 2380}</script><noscript><img src="x"></noscript>
            </head><body>""" + "<p><b><i>y".repeat(50_000)

        val head = headHtml(canonicalHtml(page)!!)
        val document = Ksoup.parse(head.html)

        assertEquals(setOf("#root", "html", "head", "title", "meta", "link", "script", "body"), document.getAllElements().map { it.normalName() }.toSet())
        assertEquals(8, document.getAllElements().size)
        assertEquals("Tide & time", document.title())
        assertEquals("ta", document.selectFirst("html")?.attr("lang"))
        assertTrue("2380" in document.selectFirst("script")!!.data())
        assertEquals(canonicalHtml(page)!!.indexOf("</head>"), head.bodyStart)
    }

    @Test
    fun headHtmlOfAPageWithoutAHeadEndReadsOnlyAPrefix() {
        val page = "<title>T</title>" + "<meta name=\"x\" content=\"y\">".repeat(3) + "<span>".repeat(MAX_HEAD_SCAN / 6) + "<meta name=\"late\">"

        val document = Ksoup.parse(headHtml(canonicalHtml(page)!!).html)

        assertEquals(3, document.select("meta").size)
        assertEquals(0, headHtml(canonicalHtml(page)!!).bodyStart)
    }

    @Test
    fun bodyWordsSkipMarkupScriptsLinksAndChrome() {
        val page = """<p>One two <b>three</b></p><script>not counted at all</script><!-- nor this -->
            <nav>Home About</nav><p>four <a href="/x">linked words</a> five</p><footer>© Site</footer>
            <style>p { color: red }</style><noscript>enable js</noscript><p>six</p>"""

        assertEquals(6, bodyWordCount(canonicalHtml(page)!!, 0))
    }

    @Test
    fun bodyWordsSurviveSelfClosingAndUnclosedMarkup() {
        // A self-closing `<button/>` never gets an end tag (martinfowler.com's search form has one), and ASP.NET pages
        // wrap everything in a form: neither may hide the article text. (A `<footer/>`, like any non-void element,
        // stays open in HTML, so text after it is footer text.)
        val page = """<form id="aspnetForm"><header><button type="submit" value=" "/></header><button/>
            <p>one two three</p><a name="top">four</a><p>five six</p></form>"""

        assertEquals(3 + 2, bodyWordCount(canonicalHtml(page)!!, 0))
    }

    // The verifier's round-3 reading-time fixtures (synthetic): the word count read 43% high on the first two.

    private val sentences = listOf(
        "The council voted on Tuesday to extend the harbour wall by another forty metres, after two winters of storms.",
        "Engineers say the new section will be built from recycled granite and should be finished before the autumn tides.",
        "Residents, many of whom have lived on the seafront for decades, were divided about the cost and the noise.",
        "Funding comes partly from a national resilience grant and partly from a rise in local mooring fees.",
    )

    private fun prose(words: Int): String {
        val text = StringBuilder()
        var count = 0
        var i = 0
        while (count < words) {
            val sentence = sentences[i++ % sentences.size]
            text.append(sentence).append(' ')
            count += sentence.split(' ').size
        }
        return text.toString().trim()
    }

    private fun paragraphs(total: Int, per: Int = 60) = (0 until total / per).joinToString("\n") { "<p>${prose(per)}</p>" }

    private val nav = "<header class=site-header><a href=/>Logo</a><nav><ul>" +
        (1..25).joinToString("") { "<li><a href=/s$it>Section $it</a></li>" } + "</ul></nav></header>"

    private val readingFixtures = listOf(
        // News: link ticker, 900-word article, related blurbs, comments, footer.
        """<html lang="en-GB"><head><title>Harbour</title></head><body>$nav
            <div class="ticker">${(1..30).joinToString("") { "<a href=/t$it>Breaking item $it here</a> " }}</div>
            <main><article><h1>Harbour wall to be extended</h1><figure><img src=/a.jpg><figcaption>The wall in March.</figcaption></figure>
            ${paragraphs(900)}</article>
            <div class="related">${(1..6).joinToString("") { "<div><a href=/r$it>Related $it</a><p>${prose(20)}</p></div>" }}</div>
            <section id=comments>${(1..5).joinToString("") { "<div class=comment><p>${prose(40)}</p></div>" }}</section></main>
            <footer><p>${prose(50)}</p></footer></body></html>""",
        // WordPress: div widgets (no aside), 1,500 words, 10 comments and a comment form.
        """<html lang="en-US"><head><title>Post</title></head><body class="single-post">
            <div id=page class=site>$nav<div id=content class=site-content><div id=primary><article class=post><h1 class=entry-title>Title</h1>
            <div class=entry-content>${paragraphs(1500, 75)}</div></article>
            <div id=comments class=comments-area><ol class=comment-list>${(1..10).joinToString("") { "<li class=comment><p>${prose(50)}</p></li>" }}</ol>
            <form id=commentform><label>Name</label><input><label>Comment</label><textarea>Write here</textarea><button>Post Comment</button></form></div></div>
            <div id=secondary class=widget-area><div class=widget><h2>About me</h2><p>${prose(80)}</p></div>
            <div class=widget><h2>Tags</h2>${(1..40).joinToString(" ") { "<a href=/tag$it>tag$it</a>" }}</div></div></div></div></body></html>""",
        // Substack-like: svg icon, 2,500 words, subscribe box, footer.
        """<html lang="en"><head><title>Essay</title></head><body><div id=main><div class=post><svg><path d="M0 0L10 10"/></svg>
            <h1>Title</h1><h3 class=subtitle>Subtitle here</h3><div class=available-content><div class=body>${paragraphs(2500, 100)}</div></div>
            <div class=subscribe-widget><p>${prose(30)}</p><input><button>Subscribe</button></div>
            <div class=post-footer><a href=/p>Share</a> <a href=/l>Like</a></div></div></div>
            <footer><p>© 2026 Writer · Privacy · Terms</p></footer></body></html>""",
    )

    @Test
    fun bodyWordsStayWithin15PercentOfTheArticle() {
        for (page in readingFixtures) {
            val article = Ksoup.parse(page).selectFirst("article, .available-content")!!.text().split(Regex("\\s+")).count { it.isNotEmpty() }
            val canonical = canonicalHtml(page)!!
            val estimate = bodyWordCount(canonical, headHtml(canonical).bodyStart)

            val error = (estimate - article).toDouble() / article
            assertTrue(error in -0.15..0.15, "estimate $estimate for $article article words (${(error * 100).toInt()}%)")
        }
    }

    @Test
    fun jsonLdInTheBodyIsKept() {
        val page = """<html><head><title>T</title></head><body><p>x</p><script type="application/ld+json">{"wordCount": 2380}</script></body></html>"""
        val document = Ksoup.parse(headHtml(canonicalHtml(page)!!).html)

        assertTrue("2380" in document.selectFirst("script")!!.data())
    }
}
