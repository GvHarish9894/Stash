package com.techgv.stash.core.domain

/**
 * Search matching (decision D11): every query token must be a prefix of a word in the title or the site name.
 *
 * Words are maximal runs of letters, digits and [WORD_MARKS] (decision D16), compared lowercase with Greek final sigma
 * folded to σ, like SQLite FTS4's `unicode61` tokenizer. Query tokens are the words of the query by the same rule, so
 * `medium.com` is the two tokens `medium` and `com`.
 *
 * SQL equivalent: FTS4 over `title` and `site_name` with
 * `tokenize=unicode61 "remove_diacritics=0" "tokenchars=<WORD_MARKS>"`, queried as `MATCH '"tok1*" "tok2*"'` built
 * from [tokens], with the star inside the quotes (`'"tok"*'` matches whole words only). A query with no tokens must not
 * issue a `MATCH`: an empty expression matches nothing (SQLite 3.54) or raises an error (some versions), while no tokens
 * means no text filter (search shows no results, the archive shows everything). FTS operators are uppercase and tokens
 * are lowercased, so a token cannot become an operator; the quotes are kept anyway so no token character is read as
 * query syntax.
 *
 * Known limits: no Unicode normalization (an NFC query does not match NFD text), and a word whose lowercase form has a
 * different length (for example `İ`) is highlighted whole.
 *
 * Known parity limits with the FTS4 index (measured; the matching rule is deliberately unchanged):
 * - `İ` (U+0130): words containing it cannot be found through the index (Kotlin lowercases it to two chars, `i̇`).
 * - Letters outside the Basic Multilingual Plane (for example CJK Extension B) are surrogate pairs, which are separators
 *   here but word characters in FTS.
 * - Other numbers and letter numbers (categories No and Nl: superscripts, fractions, Roman numerals) are word
 *   characters in FTS only, so `x²` is one word in the index and two here.
 * - NFD text: decomposed accents are combining marks, separators here, so NFD titles split differently on both sides.
 */
object SearchMatcher {
    /**
     * The combining marks (Unicode categories Mn and Mc) of the Devanagari (U+0900–U+097F) and Tamil (U+0B80–U+0BFF)
     * blocks: vowel signs, virama, anusvara, visarga, nukta and the like. They belong to the word they follow, so Hindi
     * and Tamil words are not split at vowel signs. The database passes exactly these characters as `tokenchars=`.
     */
    const val WORD_MARKS: String =
        "\u0900\u0901\u0902\u0903\u093A\u093B\u093C\u093E\u093F\u0940\u0941\u0942\u0943\u0944\u0945\u0946\u0947\u0948" +
            "\u0949\u094A\u094B\u094C\u094D\u094E\u094F\u0951\u0952\u0953\u0954\u0955\u0956\u0957\u0962\u0963" +
            "\u0B82\u0BBE\u0BBF\u0BC0\u0BC1\u0BC2\u0BC6\u0BC7\u0BC8\u0BCA\u0BCB\u0BCC\u0BCD\u0BD7"

    private val wordMarks = WORD_MARKS.toSet()

    fun tokens(query: String): List<String> = words(query).map { (start, end) -> fold(query.substring(start, end)) }

    /** Null when some token matches no word in either field; otherwise the highlight ranges for each field. */
    fun match(tokens: List<String>, title: String, site: String): Pair<List<IntRange>, List<IntRange>>? {
        if (tokens.isEmpty()) return null
        val titleWords = foldedWords(title)
        val siteWords = foldedWords(site)
        val allMatch = tokens.all { token -> (titleWords + siteWords).any { it.text.startsWith(token) } }
        return if (allMatch) highlights(title, titleWords, tokens) to highlights(site, siteWords, tokens) else null
    }

    private class Word(val start: Int, val end: Int, val text: String)

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c in wordMarks

    // SQLite folds final sigma to σ; Kotlin's lowercase may produce either form depending on the platform.
    private fun fold(text: String) = text.lowercase().replace('ς', 'σ')

    private fun foldedWords(text: String) = words(text).map { (start, end) -> Word(start, end, fold(text.substring(start, end))) }

    /**
     * Ranges of the matched word prefixes, in order; each word contributes its longest matching token. A range is
     * extended over following [WORD_MARKS] so it never ends inside a Hindi or Tamil letter cluster.
     */
    private fun highlights(original: String, words: List<Word>, tokens: List<String>): List<IntRange> = words.mapNotNull { word ->
        val longest = tokens.filter { word.text.startsWith(it) }.maxOfOrNull { it.length } ?: return@mapNotNull null
        var end = if (word.text.length == word.end - word.start) word.start + longest else word.end
        while (end < word.end && original[end] in wordMarks) end++
        word.start until end
    }

    private fun words(text: String): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < text.length) {
            if (!isWordChar(text[i])) {
                i++
                continue
            }
            val start = i
            while (i < text.length && isWordChar(text[i])) i++
            result += start to i
        }
        return result
    }
}
