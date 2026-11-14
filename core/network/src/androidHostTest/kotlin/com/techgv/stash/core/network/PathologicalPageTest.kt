package com.techgv.stash.core.network

import com.techgv.stash.core.model.ReaderBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
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

    // Before the fix ReaderBlocks.parse took 4.1 s and 2.0 s on the larger of these (verifier, desktop JVM), growing as n^1.9.
    // The checks compare a page with one four times its size, so they hold on a slow machine: linear work grows about
    // 4 times, the old behaviour about 14 times.
    private val linearGrowth = 8.0

    private fun brAfter(tag: String, count: Int) = "<p>" + "<$tag>x</$tag><br>".repeat(count)

    @Test
    fun readerBlocksStayLinearWithABreakAfterEveryStyledWord() = runTest {
        ReaderBlocks.parse("<p><i>x</i><br>".repeat(1_000)) // class loading and JIT are not what this measures
        for ((tag, count) in listOf("i" to 49_000, "b" to 33_000)) {
            val small = brAfter(tag, count / 4)
            val big = brAfter(tag, count)
            val quick = (1..3).minOf { measureTime { ReaderBlocks.parse(small) } }
            val (blocks, slow) = (1..3).map { measureTimedValue { ReaderBlocks.parse(big) } }.minBy { it.duration }

            val paragraph = assertIs<ReaderBlock.Paragraph>(blocks.single())
            assertTrue(paragraph.text.spans.size > 30_000)
            assertTrue(slow / quick < linearGrowth, "4 times the page took $slow against $quick")
        }
    }

    @Test
    fun extractorCleanStepStaysLinearOnManyEmptySections() = runTest {
        // Ksoup.clean pretty-printed its output, quadratic on blank text nodes: 32,000 empty sections took 2.1 s.
        fun page(sections: Int) = "<html><head><title>T</title></head><body><article>" +
            SyntheticPages.bodyParagraphs.joinToString("") { "<p>$it</p>" }.repeat(2) + "<section></section>".repeat(sections) + "</article></body></html>"
        ReaderExtractor.extract(SyntheticPages.newsArticle, "https://example.com/") // class loading and JIT are not measured
        // Larger pages and more runs than the reader-block check: these timings are short, and a busy machine once
        // pushed three runs of a 40 ms page past the bound.
        val small = page(20_000)
        val big = page(80_000) // under the 100,000-element cap

        val quick = (1..5).minOf { measureTime { ReaderExtractor.extract(small, "https://example.com/") } }
        val (article, slow) = (1..5).map { measureTimedValue { ReaderExtractor.extract(big, "https://example.com/") } }.minBy { it.duration }

        assertEquals(SyntheticPages.bodyText.length * 2 + 1, article!!.textLength)
        assertTrue(slow / quick < linearGrowth, "4 times the sections took $slow against $quick")
    }
}
