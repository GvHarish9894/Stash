package com.techgv.stash.core.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.model.RichSpan
import com.techgv.stash.core.model.RichText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns [ExtractedArticle.extractedHtml] into the blocks the reader renders. Unknown tags are flattened into their text.
 * Known limits: tables become plain text, inline `<code>` gets no span, and nested lists lose their depth.
 */
object ReaderBlocks {
    /**
     * Parses on [Dispatchers.Default]; safe to call from the main thread. Empty when the HTML is larger than a fetched
     * page may be, would build too many elements, or cannot be parsed (none of which happens to [ReaderExtractor]
     * output); the UI then falls back to the web view. These guards bound the CPU work, which no timeout can stop.
     */
    suspend fun parse(extractedHtml: String): List<ReaderBlock> = parse(extractedHtml, Dispatchers.Default)

    /** [parse] with the dispatcher the work runs on; tests pass their own. */
    internal suspend fun parse(extractedHtml: String, dispatcher: CoroutineDispatcher): List<ReaderBlock> = withContext(dispatcher) {
        if (extractedHtml.length > PageLoader.MAX_HTML_BYTES) return@withContext emptyList()
        // The parser only ever sees scanner output, even for HTML this module wrote itself.
        val canonical = canonicalHtml(extractedHtml) ?: return@withContext emptyList()
        if (estimatedElementCount(canonical) > MAX_ELEMENTS) return@withContext emptyList()
        try {
            val body = Ksoup.parseBodyFragment(canonical).body()
            BlockCollector(blockHolders(body)).apply { collect(body) }.finish()
        } catch (e: Throwable) {
            if (e is Exception || isOutOfMemory(e)) emptyList() else throw e
        }
    }
}

private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val listTags = setOf("ul", "ol")
private val blockTags = headingTags + listTags + setOf("p", "div", "li", "blockquote", "pre")
private val blockOrImageTags = headingTags + listTags + setOf("p", "blockquote", "pre", "img")

/** Elements that are or contain a block or image, found in one pass so walking wrappers stays linear. */
private fun blockHolders(root: Element): Set<Element> {
    val holders = HashSet<Element>()
    // Recursion depth is bounded: Ksoup nests elements at most 512 deep.
    fun visit(element: Element): Boolean {
        var holds = element.normalName() in blockOrImageTags
        for (child in element.children()) if (visit(child)) holds = true
        if (holds) holders.add(element)
        return holds
    }
    visit(root)
    return holders
}

private class BlockCollector(private val blockHolders: Set<Element>) {
    private val blocks = mutableListOf<ReaderBlock>()
    private var loose = InlineText()

    fun collect(parent: Element) {
        for (node in parent.childNodes()) {
            if (node !is Element) {
                loose.append(node)
                continue
            }
            when (val tag = node.normalName()) {
                in headingTags -> block(node) { ReaderBlock.Heading(tag[1].digitToInt(), it) }
                "p" -> block(node, ReaderBlock::Paragraph)
                "blockquote" -> block(node, ReaderBlock::Quote)
                "pre" -> {
                    flush()
                    node.wholeText().trim('\n').takeIf { it.isNotBlank() }?.let { blocks += ReaderBlock.Code(it) }
                }
                in listTags -> {
                    flush()
                    list(node)
                }
                "img" -> {
                    flush()
                    image(node)?.let { blocks += it }
                }
                // A wrapper such as div or figure: walk into it when it holds blocks, otherwise it is inline text.
                else -> if (node in blockHolders) collect(node) else loose.append(node)
            }
        }
    }

    fun finish(): List<ReaderBlock> {
        flush()
        return blocks
    }

    /** Text between blocks becomes its own paragraph. */
    private fun flush() {
        loose.build()?.let { blocks += ReaderBlock.Paragraph(it) }
        blocks += loose.images
        loose = InlineText()
    }

    private fun block(element: Element, make: (RichText) -> ReaderBlock) {
        flush()
        val text = InlineText().apply { append(element) }
        text.build()?.let { blocks += make(it) }
        blocks += text.images
    }

    private fun list(list: Element) {
        val ordered = list.normalName() == "ol"
        var index = 0
        for (item in list.children()) {
            if (item.normalName() in listTags) {
                list(item)
                continue
            }
            val text = InlineText().apply { append(item, skip = listTags) }
            text.build()?.let { blocks += ReaderBlock.ListItem(it, ordered, ++index) }
            blocks += text.images
            nestedLists(item).forEach(::list)
        }
    }

    /** Lists inside [element] that are not inside another nested list, so each list is visited once. */
    private fun nestedLists(element: Element): List<Element> =
        element.children().flatMap { if (it.normalName() in listTags) listOf(it) else nestedLists(it) }
}

private fun image(element: Element): ReaderBlock.Image? = element.attr("src").trim()
    .takeIf { it.startsWith("https://", ignoreCase = true) }
    ?.let { ReaderBlock.Image(it, element.attr("alt").trim().ifEmpty { null }) }

/**
 * Builds one [RichText], collapsing whitespace the way a browser renders it; images found inside are kept aside. Every
 * span satisfies `0 <= start < end <= text.length` and starts on visible text.
 */
private class InlineText {
    private val text = StringBuilder()
    private val spans = mutableListOf<RichSpan>()

    /** Starts of elements still being appended; trimming whitespace can move the text end below them. */
    private val openStarts = ArrayList<Int>()
    val images = mutableListOf<ReaderBlock.Image>()

    fun append(node: Node, skip: Set<String> = emptySet()) {
        if (node is TextNode) return appendText(node.text())
        if (node !is Element) return
        val tag = node.normalName()
        when {
            tag in skip -> return
            tag == "br" -> newline()
            tag == "img" -> image(node)?.let { images += it }
            else -> {
                if (tag in blockTags) newline()
                openStarts += text.length
                node.childNodes().forEach { append(it, skip) }
                val start = openStarts.removeAt(openStarts.lastIndex)
                span(tag, node, start, text.length)?.let { spans += it }
            }
        }
    }

    private fun span(tag: String, element: Element, start: Int, end: Int): RichSpan? = when {
        start >= end -> null
        tag == "b" || tag == "strong" -> RichSpan.Bold(start, end)
        tag == "em" || tag == "i" -> RichSpan.Italic(start, end)
        tag == "a" -> element.attr("href").trim().takeIf(::isHttpUrl)?.let { RichSpan.Link(start, end, it) }
        else -> null
    }

    private fun appendText(raw: String) {
        val atLineStart = text.isEmpty() || text.last() == ' ' || text.last() == '\n'
        text.append(if (atLineStart) raw.trimStart() else raw)
    }

    private fun newline() {
        trimEnd()
        if (text.isNotEmpty()) text.append('\n')
    }

    /**
     * Drops trailing whitespace and pulls back spans and open starts that reached past it. Both lists are ordered by
     * their end (open starts) or by when they closed (spans, whose ends never decrease), so only a tail can reach past
     * the text; walking back from the end keeps a `<br>` after every styled word linear rather than quadratic.
     */
    private fun trimEnd() {
        while (text.isNotEmpty() && text.last().isWhitespace()) text.setLength(text.length - 1)
        val length = text.length
        var open = openStarts.lastIndex
        while (open >= 0 && openStarts[open] > length) openStarts[open--] = length
        var last = spans.lastIndex
        while (last >= 0 && spans[last].end > length) last--
        if (last == spans.lastIndex) return
        val tail = spans.subList(last + 1, spans.size)
        val clamped = tail.mapNotNull { span -> if (span.start >= length) null else span.withRange(span.start, length) }
        tail.clear()
        spans += clamped
    }

    fun build(): RichText? {
        trimEnd()
        if (text.isEmpty()) return null
        val visible = spans.mapNotNull { span ->
            // A span that begins at a line break or space starts at its first visible character.
            var start = span.start
            while (start < span.end && text[start].isWhitespace()) start++
            if (start < span.end) span.withRange(start, span.end) else null
        }
        return RichText(text.toString(), visible.sortedWith(compareBy({ it.start }, { -it.end })))
    }
}

private fun RichSpan.withRange(start: Int, end: Int): RichSpan = when (this) {
    is RichSpan.Bold -> copy(start = start, end = end)
    is RichSpan.Italic -> copy(start = start, end = end)
    is RichSpan.Link -> copy(start = start, end = end)
}
