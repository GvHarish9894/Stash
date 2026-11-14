package com.techgv.stash.core.network

import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.model.RichSpan
import com.techgv.stash.core.model.RichText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderBlocksTest {
    private fun paragraph(text: String, vararg spans: RichSpan) = ReaderBlock.Paragraph(RichText(text, spans.toList()))

    @Test
    fun headingsKeepTheirLevel() = runTest {
        assertEquals(
            listOf(
                ReaderBlock.Heading(2, RichText("Morning")),
                ReaderBlock.Heading(4, RichText("Evening watch")),
            ),
            ReaderBlocks.parse("<h2>Morning</h2><h4>Evening\n   watch</h4>"),
        )
    }

    @Test
    fun inlineStylesBecomeSpansAtTheRightOffsets() = runTest {
        val blocks = ReaderBlocks.parse(
            """<p>The <strong>lamp</strong> turns, <em>slowly</em>, see <a href="https://example.com/lamp">the log</a>.</p>""",
        )

        assertEquals(
            listOf(
                paragraph(
                    "The lamp turns, slowly, see the log.",
                    RichSpan.Bold(4, 8),
                    RichSpan.Italic(16, 22),
                    RichSpan.Link(28, 35, "https://example.com/lamp"),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun nestedInlineTagsStack() = runTest {
        val blocks = ReaderBlocks.parse("""<p><a href="https://example.com/"><b>Bold <i>both</i></b> link</a> end</p>""")

        assertEquals(
            listOf(
                paragraph(
                    "Bold both link end",
                    RichSpan.Link(0, 14, "https://example.com/"),
                    RichSpan.Bold(0, 9),
                    RichSpan.Italic(5, 9),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun unsafeLinksAreNotSpans() = runTest {
        assertEquals(listOf(paragraph("click")), ReaderBlocks.parse("""<p><a href="javascript:alert(1)">click</a></p>"""))
    }

    @Test
    fun listsCountOrderedItemsAndFlattenNesting() = runTest {
        val blocks = ReaderBlocks.parse(
            """<ol><li>First</li><li>Second<ul><li>Inner</li></ul></li><li><b>Third</b></li></ol><ul><li>Dot</li></ul>""",
        )

        assertEquals(
            listOf(
                ReaderBlock.ListItem(RichText("First"), ordered = true, index = 1),
                ReaderBlock.ListItem(RichText("Second"), ordered = true, index = 2),
                ReaderBlock.ListItem(RichText("Inner"), ordered = false, index = 1),
                ReaderBlock.ListItem(RichText("Third", listOf(RichSpan.Bold(0, 5))), ordered = true, index = 3),
                ReaderBlock.ListItem(RichText("Dot"), ordered = false, index = 1),
            ),
            blocks,
        )
    }

    @Test
    fun quoteCodeAndImage() = runTest {
        val blocks = ReaderBlocks.parse(
            """<blockquote><p>Keep the light.</p><p>Always.</p></blockquote>""" +
                "<pre><code>val lamp = on()\n  rotate()</code></pre>" +
                """<p>Before<img src="https://cdn.example.com/lamp.jpg" alt="The lamp"></p>""" +
                """<img src="http://cdn.example.com/insecure.jpg">""",
        )

        assertEquals(
            listOf(
                ReaderBlock.Quote(RichText("Keep the light.\nAlways.")),
                ReaderBlock.Code("val lamp = on()\n  rotate()"),
                paragraph("Before"),
                ReaderBlock.Image("https://cdn.example.com/lamp.jpg", "The lamp"),
            ),
            blocks,
        )
    }

    @Test
    fun unknownTagsAreFlattened() = runTest {
        val blocks = ReaderBlocks.parse(
            """<section><div><p>Inside <span class="x">a span</span></p></div></section>Loose <u>text</u><br>next line<h3>After</h3>""",
        )

        assertEquals(
            listOf(
                paragraph("Inside a span"),
                paragraph("Loose text\nnext line"),
                ReaderBlock.Heading(3, RichText("After")),
            ),
            blocks,
        )
    }

    @Test
    fun spansDoNotCoverTrimmedWhitespace() = runTest {
        assertEquals(
            listOf(paragraph("bold\nnext", RichSpan.Bold(0, 4))),
            ReaderBlocks.parse("<p><b>bold </b><br>next</p>"),
        )
    }

    @Test
    fun roundTripFromExtractorOutput() = runTest {
        val article = ReaderExtractor.extract(SyntheticPages.hostileArticle, "https://news.example.com/2026/11/keeper")!!

        val blocks = ReaderBlocks.parse(article.extractedHtml)

        assertEquals(4, blocks.size)
        val first = blocks[0] as ReaderBlock.Paragraph
        assertEquals(SyntheticPages.bodyParagraphs[0] + " bad link", first.text.text)
        assertEquals(emptyList(), first.text.spans)
        val second = blocks[1] as ReaderBlock.Paragraph
        val older = second.text.text.indexOf("older story")
        assertEquals(
            listOf(RichSpan.Link(older, older + "older story".length, "https://news.example.com/archive/older")),
            second.text.spans,
        )
        assertEquals(ReaderBlock.Image("https://news.example.com/images/keeper.jpg", "The keeper"), blocks[2])
        assertEquals(SyntheticPages.bodyParagraphs[2] + " next", (blocks[3] as ReaderBlock.Paragraph).text.text)
    }

    private fun richTexts(blocks: List<ReaderBlock>): List<RichText> = blocks.mapNotNull {
        when (it) {
            is ReaderBlock.Heading -> it.text
            is ReaderBlock.Paragraph -> it.text
            is ReaderBlock.Quote -> it.text
            is ReaderBlock.ListItem -> it.text
            else -> null
        }
    }

    @Test
    fun spansStayInsideTheirText() = runTest {
        // Em space, ideographic space and nbsp are trimmed at line ends; breaks and blocks inside spans trigger the trim.
        val awkward = listOf(
            "<p>a\u2003\u2003\u2003<b><br>x</b></p>",
            "<p>a\u3000\u3000<i><br>xy</i></p>",
            "<p>\u3000\u3000日本語<b>\u3000<br>\u3000太字</b>\u3000</p>",
            "<p>a&nbsp;&nbsp;<a href=\"https://e.com/\"><br>link</a></p>",
            "<p>x <b>bold <p>block</p> tail</b> end</p>",
            "<blockquote>q\u2003<em><p>inner</p>\u2003</em></blockquote>",
            "<ul><li>item\u3000<b><br></b></li><li><i>\u2003<br>\u2003</i>z</li></ul>",
            "<p><b><i><a href=\"https://e.com/\">\u2003<br>\u2003</a></i></b>tail</p>",
            "<h2>t&nbsp;<strong><div>d</div></strong></h2>",
            "<p><b>\u2003</b><i>\u3000<br>\u3000</i>z<b> <br> </b></p>",
        )
        for (html in awkward) {
            for (rich in richTexts(ReaderBlocks.parse(html))) {
                for (span in rich.spans) {
                    assertTrue(span.start in 0..span.end && span.end <= rich.text.length, "$span in \"${rich.text}\" from $html")
                }
            }
        }
    }

    @Test
    fun spanAfterTrimmedWideSpacesStartsOnItsText() = runTest {
        assertEquals(listOf(paragraph("a\nx", RichSpan.Bold(2, 3))), ReaderBlocks.parse("<p>a\u2003\u2003\u2003<b><br>x</b></p>"))
        assertEquals(listOf(paragraph("a\nxy", RichSpan.Italic(2, 4))), ReaderBlocks.parse("<p>a\u3000\u3000<i><br>xy</i></p>"))
    }

    @Test
    fun leadingWhitespaceOfALineIsDropped() = runTest {
        assertEquals(
            listOf(paragraph("Leading space"), paragraph("a\nb")),
            ReaderBlocks.parse("<p>\n   Leading space</p><p>a<br>   b</p>"),
        )
    }

    @Test
    fun schemesMatchInAnyCase() = runTest {
        assertEquals(
            listOf(
                paragraph("link", RichSpan.Link(0, 4, "HTTP://Example.com/x")),
                ReaderBlock.Image("HTTPS://Cdn.Example.com/a.jpg", null),
            ),
            ReaderBlocks.parse("""<p><a href="HTTP://Example.com/x">link</a></p><img src="HTTPS://Cdn.Example.com/a.jpg">"""),
        )
    }
}
