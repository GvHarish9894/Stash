package com.techgv.stash.core.network

import com.fleeksoft.ksoup.parser.Parser

/**
 * The single reader of raw HTML. It tokenizes the page by the HTML tokenizer's rules and writes out the page it
 * understood in a form no parser can read differently:
 * - comments, doctypes, processing instructions and other `<!…>` sections are dropped;
 * - tags keep only names made of ASCII letters, digits and `-`; attributes keep only safe names and are written as
 *   `name="value"` with the value's entities decoded and `&`, `<`, `>` and `"` escaped;
 * - `script` and `style` are dropped with their contents, except JSON-LD scripts, kept with `<` and `>` escaped;
 *   `title` keeps its text escaped; `textarea`, `xmp`, `iframe`, `noembed`, `noframes` and everything after
 *   `plaintext` are dropped;
 * - `svg` and `math` are dropped with their whole subtree (reader mode does not render them);
 * - a `<` that starts nothing is written as `&lt;`, a `>` in text as `&gt;`; a tag cut off by the end of the page is
 *   dropped, as the tokenizer does.
 *
 * Invariant: the parser only ever sees scanner output. Every element it builds comes from a tag written here (plus
 * what the tree builder adds around them, which [estimatedElementCount] models on this same string), so markup the
 * scanner did not see cannot reach the parser, whatever the page does to confuse a tokenizer.
 *
 * Returns null when the canonical page would be longer than [MAX_CANONICAL_CHARS] (only junk, such as megabytes of
 * bare `<` or `&`, grows that much). The cap is checked as each character is written, so [out] never grows past it by
 * more than one escape, even inside one long text run or attribute value.
 */
internal fun canonicalHtml(page: String, out: StringBuilder = StringBuilder(minOf(page.length + page.length / 8, MAX_CANONICAL_CHARS))): String? {
    var i = 0
    while (i < page.length) {
        if (out.length > MAX_CANONICAL_CHARS) return null
        val lt = page.indexOf('<', i)
        if (lt < 0) {
            appendText(out, page, i, page.length)
            break
        }
        appendText(out, page, i, lt)
        i = readMarkup(page, lt, out)
    }
    return if (out.length > MAX_CANONICAL_CHARS) null else out.toString()
}

/** Twice the fetch cap: canonical output is about as long as its input unless the input is junk. */
internal const val MAX_CANONICAL_CHARS = 2 * PageLoader.MAX_HTML_BYTES

private val droppedRawText = setOf("style", "textarea", "xmp", "iframe", "noembed", "noframes")
private val foreignRoots = setOf("svg", "math")
private val safeTagName = Regex("[a-z][a-z0-9-]*")
private val safeAttributeName = Regex("[a-z_:][a-z0-9_:.-]*")

private class Tag(val name: String, val attributes: List<Pair<String, String>>, val selfClosing: Boolean, val end: Int)

private fun Char.isHtmlWhitespace() = this == ' ' || this == '\t' || this == '\n' || this == '\r' || this == '\u000C'

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

private fun appendText(out: StringBuilder, page: String, from: Int, to: Int) {
    for (k in from until to) {
        if (out.length > MAX_CANONICAL_CHARS) return
        when (val c = page[k]) {
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '\u0000' -> Unit
            else -> out.append(c)
        }
    }
}

private fun appendAttributeValue(out: StringBuilder, raw: String) {
    val value = if ('&' in raw) Parser.unescapeEntities(raw, true) else raw
    for (c in value) {
        if (out.length > MAX_CANONICAL_CHARS) return
        when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\u0000' -> Unit
            else -> out.append(c)
        }
    }
}

/** Reads the construct starting at the `<` at [lt], writes its canonical form and returns the index after it. */
private fun readMarkup(page: String, lt: Int, out: StringBuilder): Int {
    val n = page.length
    if (lt + 1 >= n) {
        out.append("&lt;")
        return n
    }
    val next = page[lt + 1]
    return when {
        page.startsWith("<!--", lt) -> commentEnd(page, lt + 4)
        next == '!' || next == '?' -> bogusCommentEnd(page, lt + 2)
        next == '/' -> {
            val c = page.getOrNull(lt + 2) ?: return n
            when {
                c == '>' -> lt + 3
                !c.isAsciiLetter() -> bogusCommentEnd(page, lt + 2)
                else -> {
                    val tag = readTag(page, lt + 2) ?: return n
                    if (safeTagName.matches(tag.name)) out.append("</").append(tag.name).append('>')
                    tag.end
                }
            }
        }
        next.isAsciiLetter() -> {
            val tag = readTag(page, lt + 1) ?: return n
            writeStartTag(page, tag, out)
        }
        else -> {
            out.append("&lt;")
            lt + 1
        }
    }
}

/** Writes a start tag (and, for raw-text elements, its contents) and returns where reading continues. */
private fun writeStartTag(page: String, tag: Tag, out: StringBuilder): Int {
    val name = tag.name
    if (!safeTagName.matches(name)) return tag.end
    if (name in foreignRoots) return if (tag.selfClosing) tag.end else foreignSubtreeEnd(page, tag.end, name)
    if (name == "plaintext") return page.length
    if (name == "script" || name == "title" || name in droppedRawText) {
        val close = rawTextEnd(page, name, tag.end, page.length).let { if (it < 0) page.length else it }
        val jsonLd = name == "script" && tag.attributes.any { (key, value) -> key == "type" && value.contains("ld+json", ignoreCase = true) }
        if (jsonLd || name == "title") {
            out.append(if (jsonLd) "<script type=\"application/ld+json\">" else "<title>")
            appendText(out, page, tag.end, close)
            out.append("</").append(name).append('>')
        }
        return if (close >= page.length) page.length else readTag(page, close + 2)?.end ?: page.length
    }
    out.append('<').append(name)
    for ((key, value) in tag.attributes) {
        out.append(' ').append(key).append("=\"")
        appendAttributeValue(out, value)
        out.append('"')
    }
    out.append('>')
    return tag.end
}

/** After `<!--`: an empty comment (`<!-->`, `<!--->`), else up to `-->` or `--!>`, else the end of the page. */
private fun commentEnd(page: String, from: Int): Int {
    if (page.startsWith(">", from)) return from + 1
    if (page.startsWith("->", from)) return from + 2
    var k = from
    while (true) {
        val dashes = page.indexOf("--", k)
        if (dashes < 0) return page.length
        if (page.startsWith("-->", dashes)) return dashes + 3
        if (page.startsWith("--!>", dashes)) return dashes + 4
        k = dashes + 1
    }
}

private fun bogusCommentEnd(page: String, from: Int): Int = page.indexOf('>', from).let { if (it < 0) page.length else it + 1 }

/**
 * Skips an `svg` or `math` subtree up to its matching end tag. Known fidelity limit: an HTML tag that breaks out of
 * foreign content in a browser (`<p>` inside `<svg>`) is skipped with the subtree here; it never reaches the parser.
 */
private fun foreignSubtreeEnd(page: String, from: Int, root: String): Int {
    var depth = 1
    var i = from
    while (true) {
        val lt = page.indexOf('<', i)
        if (lt < 0 || lt + 1 >= page.length) return page.length
        val next = page[lt + 1]
        i = when {
            page.startsWith("<!--", lt) -> commentEnd(page, lt + 4)
            page.startsWith("<![CDATA[", lt) -> page.indexOf("]]>", lt + 9).let { if (it < 0) page.length else it + 3 }
            next == '!' || next == '?' -> bogusCommentEnd(page, lt + 2)
            next == '/' && page.getOrNull(lt + 2)?.isAsciiLetter() == true -> {
                val tag = readTag(page, lt + 2) ?: return page.length
                if (tag.name == root && --depth == 0) return tag.end
                tag.end
            }
            next.isAsciiLetter() -> {
                val tag = readTag(page, lt + 1) ?: return page.length
                if (tag.name == root && !tag.selfClosing) depth++
                tag.end
            }
            else -> lt + 1
        }
    }
}

/**
 * Reads a tag from its name at [from] by the HTML tokenizer's tag, attribute-name and attribute-value states. Null
 * when the page ends inside the tag (the tokenizer then drops it). Names are lowercased; the first of duplicate
 * attributes wins; attributes with unsafe names (for example one starting with `=`) are read but not kept.
 */
private fun readTag(page: String, from: Int): Tag? {
    val n = page.length
    var i = from
    while (i < n && !page[i].isHtmlWhitespace() && page[i] != '/' && page[i] != '>') i++
    val name = page.substring(from, i).lowercase()
    // Insertion-ordered; the first of duplicate names wins. A map keeps a tag with 100,000 attributes linear.
    val attributes = LinkedHashMap<String, String>()
    var selfClosing = false
    while (true) {
        while (i < n && page[i].isHtmlWhitespace()) i++
        if (i >= n) return null
        when (page[i]) {
            '>' -> return Tag(name, attributes.toList(), selfClosing, i + 1)
            '/' -> {
                i++
                selfClosing = i < n && page[i] == '>'
                continue
            }
        }
        selfClosing = false
        // Attribute name: a leading `=` belongs to it; then up to whitespace, `/`, `>` or `=`.
        val nameStart = i
        i++
        while (i < n && !page[i].isHtmlWhitespace() && page[i] != '/' && page[i] != '>' && page[i] != '=') i++
        val key = page.substring(nameStart, i).lowercase()
        while (i < n && page[i].isHtmlWhitespace()) i++
        var value = ""
        if (i < n && page[i] == '=') {
            i++
            while (i < n && page[i].isHtmlWhitespace()) i++
            if (i >= n) return null
            val quote = page[i]
            if (quote == '"' || quote == '\'') {
                val close = page.indexOf(quote, i + 1)
                if (close < 0) return null
                value = page.substring(i + 1, close)
                i = close + 1
                // After a quoted value anything but whitespace, `/` or `>` starts the next attribute name.
            } else if (quote != '>') {
                val start = i
                while (i < n && !page[i].isHtmlWhitespace() && page[i] != '>') i++
                value = page.substring(start, i)
            }
        }
        if (safeAttributeName.matches(key) && key !in attributes) attributes[key] = value
    }
}
