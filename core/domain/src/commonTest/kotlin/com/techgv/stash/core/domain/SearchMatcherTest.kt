package com.techgv.stash.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchMatcherTest {
    private fun match(query: String, title: String, site: String = "") = SearchMatcher.match(SearchMatcher.tokens(query), title, site)

    @Test
    fun tokensAreLowercaseWordRunsOfTheQuery() {
        assertEquals(listOf("swift", "ui"), SearchMatcher.tokens("  Swift   UI "))
        assertEquals(listOf("medium", "com"), SearchMatcher.tokens("medium.com"))
        assertEquals(listOf("c", "kotlin"), SearchMatcher.tokens("\"c++\" -kotlin"))
        assertEquals(emptyList(), SearchMatcher.tokens("  ...  "))
    }

    @Test
    fun prefixOfAWordNotAnInfix() {
        assertEquals(listOf(0..2) to emptyList<IntRange>(), match("swi", "Swift tips"))
        assertNull(match("ift", "Swift tips"))
        assertNull(match("", "Swift tips"))
    }

    @Test
    fun everyTokenMustMatchInEitherFieldInAnyOrder() {
        assertEquals(listOf(0..4, 6..9) to emptyList<IntRange>(), match("tips swift", "Swift tips"))
        assertEquals(listOf(0..4) to listOf(0..2), match("swift med", "Swift tips", "medium.com"))
        assertNull(match("swift kotlin", "Swift tips", "medium.com"))
    }

    @Test
    fun punctuationSeparatesWords() {
        assertEquals(emptyList<IntRange>() to listOf(0..2), match("med", "Title", "medium.com"))
        assertEquals(emptyList<IntRange>() to listOf(7..9), match("com", "Title", "medium.com"))
        assertEquals(listOf(0..3, 5..7) to emptyList<IntRange>(), match("room fts", "Room/FTS4 guide"))
    }

    @Test
    fun nonAsciiCaseFolding() {
        assertEquals(listOf(0..2) to emptyList<IntRange>(), match("éco", "ÉCOLE du web"))
        assertEquals(listOf(0..3) to emptyList<IntRange>(), match("ΚΑΛΗ", "καλημέρα"))
        assertEquals(listOf(6..8) to emptyList<IntRange>(), match("தம", "Learn தமிழ்"), "the range covers the whole cluster தமி")
        assertNull(match("ecole", "ÉCOLE"), "no diacritic folding, like unicode61 with remove_diacritics=0")
    }

    @Test
    fun hindiAndTamilWordsKeepTheirVowelSigns() {
        assertEquals(listOf("हिंदी", "समाचार"), SearchMatcher.tokens("हिंदी समाचार"))
        assertEquals(listOf("தமிழ்"), SearchMatcher.tokens("தமிழ்"))
        assertEquals(listOf(0..2) to emptyList<IntRange>(), match("हिं", "हिंदी समाचार"))
        assertNull(match("हिं", "हम दोनों"))
        assertNull(match("தமிழ்", "தமர் ழகரம்"))
        assertEquals(listOf(0..4) to emptyList<IntRange>(), match("தமிழ்", "தமிழ் நாடு"))
    }

    @Test
    fun highlightsNeverEndInsideALetterCluster() {
        // ह is followed by the vowel sign ि and anusvara ं; the highlight takes the whole cluster हिं.
        assertEquals(listOf(0..2) to emptyList<IntRange>(), match("ह", "हिंदी"))
        assertEquals(listOf(0..0) to emptyList<IntRange>(), match("த", "தமிழ்"))
    }

    @Test
    fun wordMarksAreTheDevanagariAndTamilCombiningMarks() {
        assertEquals(48, SearchMatcher.WORD_MARKS.length)
        val expected = (('\u0900'..'\u097F') + ('\u0B80'..'\u0BFF'))
            .filter { it.category == CharCategory.NON_SPACING_MARK || it.category == CharCategory.COMBINING_SPACING_MARK }
            .toSet()
        assertEquals(expected, SearchMatcher.WORD_MARKS.toSet())
        assertEquals(true, SearchMatcher.WORD_MARKS.all { it in '\u0900'..'\u097F' || it in '\u0B80'..'\u0BFF' })
        assertEquals(true, SearchMatcher.WORD_MARKS.none { it.isLetterOrDigit() })
        assertEquals(SearchMatcher.WORD_MARKS.length, SearchMatcher.WORD_MARKS.toSet().size)
    }

    @Test
    fun greekFinalSigmaFoldsToSigma() {
        assertEquals(listOf("οδοσ"), SearchMatcher.tokens("ΟΔΟΣ"))
        assertEquals(listOf("οδοσ"), SearchMatcher.tokens("οδος"))
        assertEquals(listOf(0..3) to emptyList<IntRange>(), match("οδοσ", "ΟΔΟΣ"))
        assertEquals(listOf(0..3) to emptyList<IntRange>(), match("ΟΔΟΣ", "ΟΔΟΣΗΜΑΝΣΗ"))
        assertEquals(listOf(0..3) to emptyList<IntRange>(), match("οδος", "Οδοσήμανση"))
    }

    @Test
    fun highlightsEveryMatchedWordInOrderWithTheLongestToken() {
        assertEquals(
            listOf(0..6, 12..18, 29..35) to emptyList<IntRange>(),
            match("compose", "Compose and Compose-like ui, composed"),
        )
        assertEquals(listOf(0..5) to emptyList<IntRange>(), match("kot kotlin", "Kotlin"))
    }
}
