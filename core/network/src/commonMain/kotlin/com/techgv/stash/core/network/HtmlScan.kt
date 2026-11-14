package com.techgv.stash.core.network

/**
 * A forward-only scan of HTML that builds no DOM: it walks tags the way the HTML tokenizer splits them, skipping
 * comments, `<!…>` and `<?…>` sections, quoted attribute values (so a `>` or a tag inside one is not a tag), and the
 * contents of the [rawText] elements, which it reports through [onText] with the element's name. Linear in the length.
 *
 * [onTag] gets the lowercase name, whether it is an end tag, and the tag's attribute text range; returning false stops
 * the scan. [onText] gets each text range and, for raw-text contents, the element's name.
 */
internal inline fun scanHtml(
    html: String,
    from: Int,
    to: Int,
    rawText: Set<String>,
    onText: (start: Int, end: Int, rawIn: String?) -> Unit,
    onTag: (name: String, isEnd: Boolean, attrStart: Int, attrEnd: Int, tagStart: Int) -> Boolean,
) {
    var i = from
    var textStart = from
    while (i < to) {
        val lt = html.indexOf('<', i)
        if (lt < 0 || lt + 1 >= to) break
        val next = html[lt + 1]
        val skipTo = when {
            html.startsWith("<!--", lt) -> html.indexOf("-->", lt + 4).let { if (it < 0) to else it + 3 }
            next == '!' || next == '?' -> html.indexOf('>', lt + 2).let { if (it < 0) to else it + 1 }
            else -> -1
        }
        if (skipTo >= 0) {
            if (lt > textStart) onText(textStart, lt, null)
            i = minOf(skipTo, to)
            textStart = i
            continue
        }
        val isEnd = next == '/'
        val nameStart = if (isEnd) lt + 2 else lt + 1
        if (nameStart >= to || !html[nameStart].isLetter()) {
            i = lt + 1 // a `<` that starts no tag is text
            continue
        }
        var nameEnd = nameStart
        while (nameEnd < to && html[nameEnd].let { !it.isWhitespace() && it != '/' && it != '>' }) nameEnd++
        var k = nameEnd
        var lastSignificant = ' '
        while (k < to && html[k] != '>') {
            val c = html[k]
            if ((c == '"' || c == '\'') && lastSignificant == '=') {
                k = html.indexOf(c, k + 1).let { if (it < 0) to else it }
                lastSignificant = c
            } else if (!c.isWhitespace()) {
                lastSignificant = c
            }
            k++
        }
        if (lt > textStart) onText(textStart, lt, null)
        val name = html.substring(nameStart, nameEnd).lowercase()
        if (!onTag(name, isEnd, nameEnd, minOf(k, to), lt)) return
        i = minOf(k + 1, to)
        textStart = i
        if (!isEnd && name in rawText) {
            val close = if (name == "plaintext") -1 else rawTextEnd(html, name, i, to)
            val end = if (close < 0) to else close
            if (end > i) onText(i, end, name)
            i = end
            textStart = end
        }
    }
    if (to > textStart) onText(textStart, to, null)
}

/** Where `</name` (followed by a non-name character) next starts, or -1. */
internal fun rawTextEnd(html: String, name: String, from: Int, to: Int): Int {
    var at = from
    while (true) {
        val found = html.indexOf("</$name", at, ignoreCase = true)
        if (found < 0 || found >= to) return -1
        val after = found + 2 + name.length
        if (after >= html.length || html[after].let { it.isWhitespace() || it == '/' || it == '>' }) return found
        at = found + 1
    }
}

/** Contents the parser keeps as text, never as elements. `noscript` is absent: Ksoup parses it as markup in the body. */
private val parserRawText = setOf("script", "style", "textarea", "title", "xmp", "iframe", "noembed", "noframes", "plaintext")

// The HTML parser's "formatting" elements: left unclosed, the parser re-creates them inside every later element.
private val formattingTags = setOf("a", "b", "big", "code", "em", "font", "i", "nobr", "s", "small", "strike", "strong", "tt", "u")

// A formatting end tag does not reach past these while one opened after it is still open.
private val formattingBarriers = setOf("table", "td", "th", "caption", "object", "marquee", "applet", "template")

/**
 * Elements the parser will build from [html], estimated without parsing it; stops counting once past [limit].
 *
 * This is a best-effort bound, not a proof: it models the parser quirks that multiply elements (unclosed formatting
 * elements re-created inside every later element, at most 3 per name and attribute set; stray `</p>` and `</br>`
 * creating elements; implied `tbody` / `tr`; formatting end tags ignored behind a table cell, caption, object, marquee,
 * applet or template) and skips what creates none (comments, attribute values, raw text). Every case it errs on counts
 * more, not fewer. Real pages land within 1.4× their tag count.
 */
internal fun estimatedElementCount(html: String, limit: Int = MAX_ELEMENTS): Int {
    var count = 0
    // Open formatting elements per name, each with the barrier depth it was opened at.
    val openByName = HashMap<String, ArrayList<OpenFormatting>>()
    val sameKey = HashMap<String, Int>()
    var reopened = 0
    var barrierDepth = 0
    // Per open table: whether a row group and a row are open, to count the parser's implied tbody and tr.
    val tables = ArrayList<BooleanArray>()
    scanHtml(html, 0, html.length, parserRawText, onText = { _, _, _ -> }) { name, isEnd, attrStart, attrEnd, _ ->
        if (isEnd) {
            if (name == "p" || name == "br") count++
            if (name in formattingBarriers && barrierDepth > 0) {
                // Closing a cell, caption, object… clears the formatting elements opened inside it, as the parser does.
                for (stack in openByName.values) {
                    while (stack.lastOrNull()?.barrierDepth == barrierDepth) {
                        val key = stack.removeAt(stack.lastIndex).key
                        val same = sameKey.getValue(key)
                        if (same <= 3) reopened--
                        sameKey[key] = same - 1
                    }
                }
                barrierDepth--
            }
            when (name) {
                "table" -> tables.removeLastOrNull()
                "tr" -> tables.lastOrNull()?.set(1, false)
                "tbody", "thead", "tfoot" -> tables.lastOrNull()?.fill(false)
            }
            val stack = openByName[name]
            val last = stack?.lastOrNull()
            if (last != null && last.barrierDepth == barrierDepth) {
                stack.removeAt(stack.lastIndex)
                val same = sameKey.getValue(last.key)
                if (same <= 3) reopened--
                sameKey[last.key] = same - 1
            }
        } else {
            count += 1 + reopened
            val table = tables.lastOrNull()
            when (name) {
                "table" -> tables += BooleanArray(2)
                "tbody", "thead", "tfoot" -> table?.let { it[0] = true; it[1] = false }
                // A bare <col> makes the parser add a colgroup and close the row group; a caption or colgroup closes it too,
                // so the next cell gets a new tbody and tr.
                "col" -> table?.let { count++; it.fill(false) }
                "caption", "colgroup" -> table?.fill(false)
                "tr" -> table?.let {
                    if (!it[0]) count++
                    it[0] = true
                    it[1] = true
                }
                "td", "th" -> table?.let {
                    if (!it[1]) count += if (it[0]) 1 else 2
                    it[0] = true
                    it[1] = true
                }
            }
            if (name in formattingBarriers) barrierDepth++
            if (name in formattingTags) {
                val key = name + html.substring(attrStart, attrEnd).trim().removeSuffix("/")
                val same = sameKey[key] ?: 0
                if (same < 3) reopened++
                sameKey[key] = same + 1
                openByName.getOrPut(name) { ArrayList() } += OpenFormatting(key, barrierDepth)
            }
        }
        count <= limit
    }
    return count
}

private class OpenFormatting(val key: String, val barrierDepth: Int)

/** Contents skipped when looking for the end of the head or counting words: none of it is visible text. */
private val invisibleText = parserRawText + setOf("noscript", "template")

/** Containers whose text is page chrome rather than the article, skipped when counting words, whatever their class. */
private val chromeTags = setOf("nav", "header", "footer", "aside")

/**
 * Containers skipped when their class or id names page chrome (comments, related links, sidebars…). Only elements
 * whose end tag is required: an unclosed `<p class="share">` would otherwise hide the rest of the page.
 */
private val chromeNamedContainers = chromeTags + setOf("div", "section", "ul", "ol", "form", "figure", "table", "span")

private val articleRootAttribute = Regex("""\b(?:itemprop="articleBody"|role="main")""")

private val classOrId = Regex("""\b(?:class|id)="([^"]*)"""")

/** If a page has no `</head>` or `<body>`, only this much of it is searched for head tags. */
internal const val MAX_HEAD_SCAN = 256 * 1024
private const val MAX_HEAD_TAGS = 2_000
private const val MAX_JSON_LD_CHARS = 512 * 1024

/**
 * The head of a page written by [canonicalHtml], reduced to what metadata reads: the `<html>` tag (for `lang`), the
 * first `<title>`, every `<meta>`, `<link>` and `<base>` up to the first `</head>` or `<body>` (or, when the page has
 * neither, its first [MAX_HEAD_SCAN] chars), and JSON-LD scripts from anywhere in the page (articles often put theirs
 * in the body). The kept tags' attributes are canonical (quoted and escaped), so parsing the result builds one element
 * per kept tag (at most [MAX_HEAD_TAGS] plus the scripts) whatever the page contains: metadata never builds the body
 * DOM (decision D23). [bodyStart] is where body text begins: the end of the head, or 0 when the page has none.
 */
internal class HeadHtml(val html: String, val bodyStart: Int)

internal fun headHtml(page: String): HeadHtml {
    var headEnd = -1
    scanHtml(page, 0, page.length, invisibleText, onText = { _, _, _ -> }) { name, isEnd, _, _, tagStart ->
        val ends = (isEnd && name == "head") || (!isEnd && name == "body")
        if (ends) headEnd = tagStart
        !ends
    }
    val kept = StringBuilder()
    var htmlTag = "<html>"
    var title: String? = null
    var tags = 0
    var jsonLdChars = 0
    var jsonLdOpen = false
    val end = if (headEnd >= 0) headEnd else minOf(page.length, MAX_HEAD_SCAN)
    scanHtml(
        page, 0, page.length, invisibleText,
        onText = { start, stop, rawIn ->
            if (rawIn == "title" && title == null && start < end) title = page.substring(start, stop)
            if (rawIn == "script" && jsonLdOpen && jsonLdChars + (stop - start) <= MAX_JSON_LD_CHARS && tags++ < MAX_HEAD_TAGS) {
                kept.append("<script type=\"application/ld+json\">").append(page, start, stop).append("</script>")
                jsonLdChars += stop - start
            }
        },
    ) { name, isEnd, attrStart, attrEnd, tagStart ->
        if (!isEnd) {
            val attrs = page.substring(attrStart, attrEnd)
            jsonLdOpen = name == "script" && attrs.contains("ld+json", ignoreCase = true)
            if (tagStart < end) {
                when (name) {
                    "html" -> htmlTag = "<html$attrs>"
                    "meta", "link", "base" -> if (tags++ < MAX_HEAD_TAGS) kept.append('<').append(name).append(attrs).append('>')
                }
            }
        }
        true
    }
    val head = "$htmlTag<head>${title?.let { "<title>$it</title>" }.orEmpty()}$kept</head><body></body></html>"
    return HeadHtml(head, maxOf(headEnd, 0))
}

/**
 * Words of visible body text in a page written by [canonicalHtml], from [from] on, counted without building a DOM.
 * Skipped: text inside tags, scripts, styles and templates; text in links; and whole containers that are page chrome,
 * either by tag (nav, header, footer, aside) or by a class or id the reader extractor treats as chrome (comments,
 * related posts, sidebars, share bars…). An unclosed chrome container makes it count less, never more.
 */
internal fun bodyWordCount(page: String, from: Int): Int {
    var words = 0
    var inLink = false
    var skipping: String? = null
    var skipDepth = 0
    scanHtml(
        page, from, page.length, invisibleText,
        onText = { start, end, rawIn ->
            if (rawIn == null && skipping == null && !inLink) {
                var inWord = false
                for (i in start until end) {
                    val letter = !page[i].isWhitespace()
                    if (letter && !inWord) words++
                    inWord = letter
                }
            }
        },
    ) { name, isEnd, attrStart, attrEnd, _ ->
        val skipped = skipping
        if (skipped != null) {
            if (name == skipped) {
                if (!isEnd) skipDepth++ else if (--skipDepth == 0) skipping = null
            }
        } else if (name == "a") {
            // Links do not nest, so a flag (not a depth) keeps one unclosed `<a>` from hiding the rest of the page.
            inLink = !isEnd
        } else if (!isEnd && name in chromeNamedContainers) {
            // Only this tag's own attribute text: searching the page from here would be quadratic on attribute-less tags.
            val attrs = page.substring(attrStart, attrEnd)
            val names = classOrId.findAll(attrs).joinToString(" ") { it.groupValues[1] }
            // Article roots are never chrome, even when named `has-comments` (as for the extractor).
            val chromeNamed = names.isNotEmpty() && ReaderExtractor.namesChrome(names) && !articleRootAttribute.containsMatchIn(attrs)
            if (name in chromeTags || chromeNamed) {
                skipping = name
                skipDepth = 1
            }
        }
        true
    }
    return words
}
