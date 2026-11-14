package com.techgv.stash.core.network

import com.techgv.stash.core.model.ReaderBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * JVM regression for crafted pages under the size cap. Before the fix, this page took 13.5 s in ReaderFetcher, 13.8 s in
 * MetadataFetcher and 117.7 s in ReaderBlocks.parse; each must now finish in under a third of the 13 s (the bound is wide because shared
 * CI runners are several times slower than a desktop).
 */
class PathologicalPageTest {
    private val bound = 4.seconds

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

    // Wall-clock bounds, so each sits well away from both sides: far above what the fixed code takes on a slow shared
    // CI runner, far below what the old code took on a fast desktop. The best of three runs keeps one pause out of it.

    // Before the fix ReaderBlocks.parse took 4.1 s on 49,000 of these (desktop JVM), growing as n^1.9. Fixed, it takes
    // about 0.1 s on a desktop and took 0.4 s on a CI runner. 49,000 is the most the 100,000-element cap allows.
    private val styledBreaksBound = 2.seconds

    @Test
    fun readerBlocksStayLinearWithABreakAfterEveryStyledWord() = runTest {
        ReaderBlocks.parse("<p><i>x</i><br>".repeat(1_000)) // class loading and JIT are not what this measures
        for (tag in listOf("i", "b")) {
            val html = "<p>" + "<$tag>x</$tag><br>".repeat(49_000)
            val (blocks, took) = (1..3).map { measureTimedValue { ReaderBlocks.parse(html) } }.minBy { it.duration }

            val paragraph = assertIs<ReaderBlock.Paragraph>(blocks.single())
            assertTrue(paragraph.text.spans.size > 30_000)
            assertTrue(took < styledBreaksBound, "took $took")
        }
    }

    @Test
    fun extractorCleanStepStaysLinearOnManyEmptySections() = runTest {
        // Ksoup.clean pretty-printed its output, quadratic on blank text nodes: 32,000 empty sections took 2.1 s, so
        // these 80,000 (under the 100,000-element cap) would take about 13 s. Fixed, they take about 0.2 s.
        val page = "<html><head><title>T</title></head><body><article>" +
            SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }.repeat(2) + "<section></section>".repeat(80_000) + "</article></body></html>"
        ReaderExtractor.extract(SyntheticPages.newsArticle, "https://example.com/") // class loading and JIT are not measured

        val (article, took) = (1..3).map { measureTimedValue { ReaderExtractor.extract(page, "https://example.com/") } }.minBy { it.duration }

        assertEquals(SyntheticPages.bodyText.length * 2 + 1, article!!.textLength)
        assertTrue(took < 3.seconds, "took $took")
    }
}
