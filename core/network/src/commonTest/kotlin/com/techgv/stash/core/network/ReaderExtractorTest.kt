package com.techgv.stash.core.network

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderExtractorTest {
    private val base = "https://news.example.com/2026/11/keeper"

    @Test
    fun articlePageKeepsOnlyTheBody() {
        val article = assertNotNull(ReaderExtractor.extract(SyntheticPages.newsArticle, base))

        for (paragraph in SyntheticPages.bodyParagraphs) assertTrue(paragraph in article.extractedHtml, paragraph.take(30))
        for (chrome in listOf("Sport", "Share this story", "Sidebar story", "Commenter", "Footer", "pageview", "display: block")) {
            assertFalse(chrome in article.extractedHtml, chrome)
        }
        assertEquals("The last lighthouse keeper", article.title)
        assertEquals("Eleven years of dawns on the headland.", article.standfirst)
        assertFalse("<h1>" in article.extractedHtml, "the headline is shown by the reader, not repeated in the body")
    }

    @Test
    fun textLengthAndWordCountMatchTheVisibleText() {
        val article = assertNotNull(ReaderExtractor.extract(SyntheticPages.newsArticle, base))
        val visible = Ksoup.parseBodyFragment(article.extractedHtml).text()

        assertEquals(visible.length, article.textLength)
        assertEquals(SyntheticPages.bodyText.length, article.textLength)
        assertEquals(SyntheticPages.bodyText.split(" ").size, article.wordCount)
    }

    @Test
    fun linkFarmIsNotAnArticle() {
        assertNull(ReaderExtractor.extract(SyntheticPages.linkFarm, base))
    }

    @Test
    fun sectionFrontOfShortBlurbsIsNotAnArticle() {
        assertNull(ReaderExtractor.extract(SyntheticPages.sectionFront, base))
    }

    @Test
    fun stubAndCookieWallAreNotArticles() {
        assertNull(ReaderExtractor.extract(SyntheticPages.stub, base))
        assertNull(ReaderExtractor.extract(SyntheticPages.cookieWall, base))
    }

    @Test
    fun outputIsSanitized() {
        val html = assertNotNull(ReaderExtractor.extract(SyntheticPages.hostileArticle, base)).extractedHtml

        for (banned in listOf("<script", "injected", "<style", "color", "<iframe", "onclick", "onerror", "javascript:", "data:", "pixel.example", "http://insecure")) {
            assertFalse(banned in html, "$banned in $html")
        }
        assertTrue("""<img src="https://news.example.com/images/keeper.jpg" alt="The keeper">""" in html, html)
    }

    @Test
    fun relativeLinksAndImagesAreAbsolute() {
        val html = assertNotNull(ReaderExtractor.extract(SyntheticPages.hostileArticle, base)).extractedHtml

        assertTrue("""href="https://news.example.com/archive/older"""" in html, html)
        assertTrue("""href="https://news.example.com/2026/related/next"""" in html, html)
        assertTrue("""src="https://news.example.com/images/keeper.jpg"""" in html, html)
    }

    @Test
    fun articleWithoutSemanticTagsIsFoundByScoring() {
        val page = """
            <html><head><title>Plain blog</title></head><body>
              <div id="top-menu"><a href="/">Home</a> <a href="/about">About</a></div>
              <div class="wrapper"><div class="post-body">
                ${SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }}
              </div></div>
              <div class="links"><a href="/1">One</a> <a href="/2">Two</a> <a href="/3">Three</a></div>
            </body></html>
        """.trimIndent()

        val article = assertNotNull(ReaderExtractor.extract(page, base))

        assertEquals(SyntheticPages.bodyText.length, article.textLength)
        assertEquals("Plain blog", article.title)
    }

    @Test
    fun italicLeadBecomesTheStandfirstWhenThereIsNoDescription() {
        val page = """
            <html><head><title>Lead</title></head><body><article>
              <p><em>A keeper, a lamp and eleven years of weather.</em></p>
              ${SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }}
            </article></body></html>
        """.trimIndent()

        val article = assertNotNull(ReaderExtractor.extract(page, base))

        assertEquals("A keeper, a lamp and eleven years of weather.", article.standfirst)
        assertFalse("eleven years of weather" in article.extractedHtml, "the lead is not repeated in the body")
    }

    @Test
    fun divParagraphsKeepTheirBreaks() {
        val page = """
            <html><body><article>${SyntheticPages.bodyParagraphs.joinToString("") { "<div>$it</div>" }}</article></body></html>
        """.trimIndent()

        val html = assertNotNull(ReaderExtractor.extract(page, base)).extractedHtml

        assertEquals(SyntheticPages.bodyParagraphs.size, Ksoup.parseBodyFragment(html).select("p").size, html)
    }

    // Threshold edges. Three paragraphs of 185 chars score 3·√45 ≈ 20.1, just over 20; at 184 they score 19.9.

    @Test
    fun paragraphScoreJustAboveTheThresholdIsAnArticle() {
        assertNotNull(ReaderExtractor.extract(SyntheticPages.articleOf(185, 185, 185), base))
    }

    @Test
    fun paragraphScoreJustBelowTheThresholdIsNot() {
        assertNull(ReaderExtractor.extract(SyntheticPages.articleOf(184, 184, 184), base))
    }

    @Test
    fun bodyNeedsFiveHundredCharacters() {
        // Two 241-char paragraphs score 2·√101 ≈ 20.1 but hold 482 chars; a 20-char heading lifts the body past 500.
        assertNull(ReaderExtractor.extract(SyntheticPages.articleOf(241, 241), base))
        assertNotNull(ReaderExtractor.extract(SyntheticPages.articleOf(241, 241, extra = "<h2>${SyntheticPages.prose(20)}</h2>"), base))
    }

    private fun linkedArticle(linkPercent: Int): String {
        val paragraph = "<p><a href=\"/x\">${SyntheticPages.prose(2 * linkPercent)}</a> ${SyntheticPages.prose(199 - 2 * linkPercent)}</p>"
        return "<html><body><article>${paragraph.repeat(4)}</article></body></html>"
    }

    @Test
    fun linkHeavyBodyIsNotAnArticle() {
        assertNotNull(ReaderExtractor.extract(linkedArticle(linkPercent = 40), base))
        assertNull(ReaderExtractor.extract(linkedArticle(linkPercent = 60), base))
    }

    @Test
    fun chromeBoxesInsideTheArticleAreDropped() {
        val boxes = listOf("newsletter-box", "share-tools", "related-links").joinToString("") {
            "<div class=\"$it\"><p>Box $it: ${SyntheticPages.prose(300)}</p></div>"
        }
        val html = assertNotNull(ReaderExtractor.extract(SyntheticPages.articleOf(300, 300, 300, extra = boxes), base)).extractedHtml

        for (box in listOf("newsletter-box", "share-tools", "related-links")) assertFalse("Box $box" in html, box)
    }

    @Test
    fun articleInsideAChromeNamedWrapperIsKept() {
        val body = SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }
        val pages = listOf(
            """<html><body><div class="layout has-sidebar"><article>$body</article></div></body></html>""",
            """<html><body><div class="page nav-collapsed"><main>$body</main></div></body></html>""",
        )

        for (page in pages) assertEquals(SyntheticPages.bodyText.length, assertNotNull(ReaderExtractor.extract(page, base), page).textLength)
    }

    @Test
    fun italicLeadStaysInTheBodyWhenTheDescriptionIsTheStandfirst() {
        val page = """
            <html><head><meta name="description" content="A summary."></head><body><article>
              <p><em>Editor's note: this story was updated.</em></p>
              ${SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }}
            </article></body></html>
        """.trimIndent()

        val article = assertNotNull(ReaderExtractor.extract(page, base))

        assertEquals("A summary.", article.standfirst)
        assertTrue("Editor's note" in article.extractedHtml)
    }

    @Test
    fun lazyLoadedImagesUseTheirRealSource() {
        val images = """
            <img src="data:image/gif;base64,R0lGOD" data-src="/img/lazy.jpg" alt="lazy">
            <img srcset="https://cdn.example.com/small.jpg 1x, https://cdn.example.com/large.jpg 2x" alt="srcset">
            <img data-original="https://cdn.example.com/original.jpg" alt="original">
            <img data-src="http://insecure.example/x.jpg" alt="insecure">
        """
        val html = assertNotNull(ReaderExtractor.extract(SyntheticPages.articleOf(300, 300, 300, extra = images), base)).extractedHtml

        assertTrue("""src="https://news.example.com/img/lazy.jpg"""" in html, html)
        assertTrue("""src="https://cdn.example.com/small.jpg"""" in html, html)
        assertTrue("""src="https://cdn.example.com/original.jpg"""" in html, html)
        assertFalse("insecure" in html, html)
    }

    @Test
    fun linksWithoutASafeTargetAreUnwrapped() {
        val html = assertNotNull(ReaderExtractor.extract(SyntheticPages.hostileArticle, base)).extractedHtml

        assertFalse("<a>" in html, html)
        assertTrue("bad link" in html)
    }

    @Test
    fun hostileInputNeverThrows() {
        assertNull(ReaderExtractor.extract("<ul><li>x".repeat(200_000), base))
        assertNull(ReaderExtractor.extract("", base))
    }

    @Test
    fun lazySourceBeatsAPlaceholderSrc() {
        val images = """
            <img src="/pixel.gif" data-src="//cdn.example.com/a.jpg" alt="relative placeholder">
            <img src="https://cdn.example.com/placeholder.gif" data-original="https://cdn.example.com/b.jpg" alt="https placeholder">
            <img src="https://cdn.example.com/c.jpg" srcset="https://cdn.example.com/d.jpg 2x" alt="real src">
        """
        val html = assertNotNull(ReaderExtractor.extract(SyntheticPages.articleOf(300, 300, 300, extra = images), base)).extractedHtml

        assertTrue("""src="https://cdn.example.com/a.jpg"""" in html, html)
        assertTrue("""src="https://cdn.example.com/b.jpg"""" in html, html)
        assertTrue("""src="https://cdn.example.com/c.jpg"""" in html, html)
        for (unwanted in listOf("pixel.gif", "placeholder.gif", "d.jpg")) assertFalse(unwanted in html, unwanted)
    }

    private fun wrapped(inner: String) = """<html><body><div class="layout has-sidebar">$inner</div></body></html>"""

    @Test
    fun plainArticleInsideAChromeNamedWrapperIsFoundOnTheRetry() {
        val post = """<div class="post">${SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }}</div>"""

        assertEquals(SyntheticPages.bodyText.length, assertNotNull(ReaderExtractor.extract(wrapped(post), base)).textLength)
    }

    @Test
    fun retryWithoutTheFilterStillRejectsNonArticles() {
        for (page in listOf(SyntheticPages.linkFarm, SyntheticPages.stub, SyntheticPages.cookieWall, SyntheticPages.sectionFront)) {
            val body = page.substringAfter("<body>").substringBefore("</body>")
            assertNull(ReaderExtractor.extract(wrapped(body), base), body.take(60))
        }
    }

    // The retry must not turn page chrome into an article (verifier round 2, finding B).

    private val long = List(5) { SyntheticPages.prose(180 + it) }
    private fun paragraphs(prefix: String) = long.joinToString("") { "<p>$prefix $it</p>" }

    private val chromePages = mapOf(
        "consent wall" to """<div class="consent-wall"><h1>We value your privacy</h1>${paragraphs("Consent:")}</div>""",
        "cookie banner in a wrapper" to """<div class="layout has-sidebar"><div class="cookie-banner">${paragraphs("Cookies:")}</div></div>""",
        "GDPR pop-up" to """<div class="gdpr-modal popup">${paragraphs("GDPR:")}</div>""",
        "age gate" to """<div class="agegate">${paragraphs("Age:")}</div>""",
        "consent wall under a plain wrapper" to """<div class="consent"><div class="inner">${paragraphs("Consent:")}</div></div>""",
        "stub with a comment thread" to """<article><p>Short stub.</p></article><section id="comments">${paragraphs("Comment:")}</section>""",
        "stub with Disqus" to """<article><p>Short stub.</p></article><div id="disqus_thread">${paragraphs("Comment:")}</div>""",
        "stub with replies" to """<article><p>Short stub.</p></article><div class="replies">${paragraphs("Reply:")}</div>""",
        "modal" to """<div class="modal"><div class="body-text">${paragraphs("Modal:")}</div></div>""",
        "homepage with a long sidebar" to """<div class="teasers"><a href="/1">One</a></div><div class="sidebar">${paragraphs("Sidebar:")}</div>""",
        "homepage with a newsletter block" to """<div class="newsletter-signup">${paragraphs("Newsletter:")}</div>""",
        "homepage with a footer about block" to """<div class="site-footer-about">${paragraphs("About:")}</div>""",
        "category page with related posts" to """<div class="related-posts">${paragraphs("Related:")}</div>""",
    )

    @Test
    fun chromeIsNotAnArticleEvenOnTheRetry() {
        val fixtures = SyntheticPages.run { listOf(linkFarm, stub, cookieWall) }.map { it.substringAfter("<body>").substringBefore("</body>") }
        val pages = chromePages.values + fixtures
        assertEquals(16, pages.size)
        for (body in pages) assertNull(ReaderExtractor.extract("<html><body>$body</body></html>", base), body.take(80))
    }

    @Test
    fun articleRootNamedLikeChromeStillExtracts() {
        val page = """<html><body><div itemprop="articleBody" class="has-comments">${paragraphs("Body:")}</div></body></html>"""

        assertNotNull(ReaderExtractor.extract(page, base))
    }

    private val essay = SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }.repeat(2)

    private fun page(body: String, bodyAttrs: String = "", htmlAttrs: String = "") =
        "<html$htmlAttrs><head><title>Page</title></head><body$bodyAttrs>$body</body></html>"

    @Test
    fun pageStateClassesOnBodyAndHtmlDoNotHideTheArticle() {
        val wrapped = "<div class='site has-sidebar'><div class=entry-content>$essay</div></div>"
        val pages = mapOf(
            "P1 body.comments-open" to page(wrapped, bodyAttrs = " class='single single-post comments-open'"),
            "P6 html.cookie-consent-pending" to page(wrapped, htmlAttrs = " class='js cookie-consent-pending'"),
            "P7 body.modal-open" to page(wrapped, bodyAttrs = " class=modal-open"),
        )
        for ((name, html) in pages) assertNotNull(ReaderExtractor.extract(html, base), name)
    }

    @Test
    fun consentPlatformsAndAgeGatesAreChrome() {
        val pages = mapOf(
            "N1 Quantcast" to page("<div id=qc-cmp2-container><div class=qc-cmp2-summary-section><h2>We value your privacy</h2>${paragraphs("We and our partners:")}</div></div>"),
            "N2 age-gate" to page("<div class=age-gate><h2>Are you of legal drinking age?</h2>${paragraphs("Age verification:")}</div>"),
            "OneTrust" to page("<div id=onetrust-banner-sdk>${paragraphs("OneTrust:")}</div>"),
            "Didomi" to page("<div class=didomi-popup-container>${paragraphs("Didomi:")}</div>"),
            "Sourcepoint" to page("<div id=sp_message_container_123>${paragraphs("Sourcepoint:")}</div>"),
            "age verification" to page("<div class=age-verification>${paragraphs("Age:")}</div>"),
        )
        for ((name, html) in pages) assertNull(ReaderExtractor.extract(html, base), name)
    }

    @Test
    fun searchResultsAreAListingNotAnArticle() {
        val results = (1..10).joinToString("") {
            "<div class=result><h3><a href=/r$it>Result $it</a></h3><p class=snippet>${SyntheticPages.prose(160)}</p></div>"
        }

        assertNull(ReaderExtractor.extract(page("<div id=results>$results</div>"), base))
    }

    @Test
    fun articleWithLinkedSubheadingsIsStillAnArticle() {
        // A listicle: linked headings among many paragraphs are not a listing.
        val items = (1..8).joinToString("") { "<h2><a href=/tool$it>Tool $it</a></h2><p>${SyntheticPages.prose(200)}</p><p>${SyntheticPages.prose(160)}</p>" }

        assertNotNull(ReaderExtractor.extract(page("<article>$items</article>"), base))
    }
}
