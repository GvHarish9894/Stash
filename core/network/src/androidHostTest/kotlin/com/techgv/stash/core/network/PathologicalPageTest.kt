package com.techgv.stash.core.network

import com.techgv.stash.core.model.ReaderBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/**
 * JVM regression for crafted pages under the size cap. Before the fix, this page took 13.5 s in ReaderFetcher, 13.8 s in
 * MetadataFetcher and 117.7 s in ReaderBlocks.parse; each must now finish an order of magnitude faster than the 13 s.
 */
class PathologicalPageTest {
    private val bound = 1_300.milliseconds

    /** 1.8 MB: 200,000 nested `<ul><li>` pairs. */
    private val nestedLists = "<html><head><title>Deep</title></head><body>" + "<ul><li>x".repeat(200_000) + "</body></html>"

    @Test
    fun nestedListPageIsNotExtractedAndFinishesQuickly() = runTest {
        val (result, elapsed) = measureTimedValue { ReaderFetcher(mockClient { html(nestedLists) }).fetch("https://example.com/") }

        assertEquals(ReaderFetchResult.NotExtractable, result)
        assertTrue(elapsed < bound, "took $elapsed")
    }

    @Test
    fun nestedListPageStillGivesHeadMetadataQuickly() = runTest {
        val (result, elapsed) = measureTimedValue { MetadataFetcher(mockClient { html(nestedLists) }).fetch("https://example.com/") }

        assertEquals("Deep", assertIs<MetadataResult.Success>(result).metadata.title)
        assertTrue(elapsed < bound, "took $elapsed")
    }

    @Test
    fun readerBlocksOnTheNestedListPageFinishQuickly() = runTest {
        val (blocks, elapsed) = measureTimedValue { ReaderBlocks.parse(nestedLists) }

        assertEquals(emptyList(), blocks)
        assertTrue(elapsed < bound, "took $elapsed")
    }

    // Before the fix ReaderBlocks.parse took 4.1 s and 2.0 s on these (verifier, desktop JVM), growing as n^1.9.
    private val brAfterItalic = "<p>" + "<i>x</i><br>".repeat(49_000)
    private val brAfterBold = "<p>" + "<b>x</b><br>".repeat(33_000)

    @Test
    fun readerBlocksStayLinearWithABreakAfterEveryStyledWord() = runTest {
        ReaderBlocks.parse("<p><i>x</i><br>".repeat(1_000)) // class loading and JIT are not what this measures
        for (html in listOf(brAfterItalic, brAfterBold)) {
            val (blocks, elapsed) = measureTimedValue { ReaderBlocks.parse(html) }

            val paragraph = assertIs<ReaderBlock.Paragraph>(blocks.single())
            assertTrue(paragraph.text.spans.size > 30_000)
            assertTrue(elapsed < styledBreaksBound, "took $elapsed")
        }
    }

    /** A tenth of the 4.1 s measured before the fix. */
    private val styledBreaksBound = 410.milliseconds

    @Test
    fun extractorCleanStepStaysLinearOnManyEmptySections() = runTest {
        // Ksoup.clean pretty-printed its output, quadratic on blank text nodes: 32,000 empty sections took 2.1 s.
        val page = "<html><head><title>T</title></head><body><article>" +
            SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }.repeat(2) + "<section></section>".repeat(32_000) + "</article></body></html>"
        ReaderExtractor.extract(SyntheticPages.newsArticle, "https://example.com/") // class loading and JIT are not measured

        val (article, elapsed) = measureTimedValue { ReaderExtractor.extract(page, "https://example.com/") }

        assertEquals(SyntheticPages.bodyText.length * 2 + 1, article!!.textLength)
        assertTrue(elapsed < 500.milliseconds, "took $elapsed")
    }
}
