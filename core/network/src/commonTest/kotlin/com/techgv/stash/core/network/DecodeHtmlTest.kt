package com.techgv.stash.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs on both targets: iOS decodes through its own tables and Foundation, Android through java.nio. */
class DecodeHtmlTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun title(vararg body: Int) = "<title>".encodeToByteArray() + bytes(*body) + "</title>".encodeToByteArray()

    /** "Café" in windows-1252: é is 0xE9, an invalid lone byte in UTF-8. */
    private val cafe1252 = title('C'.code, 'a'.code, 'f'.code, 0xE9)

    @Test
    fun shiftJisDecodes() {
        assertEquals("<title>日本</title>", decodeHtml(title(0x93, 0xFA, 0x96, 0x7B), "Shift_JIS"))
        assertEquals("<meta charset=\"shift_jis\"><title>日本</title>", decodeHtml("<meta charset=\"shift_jis\">".encodeToByteArray() + title(0x93, 0xFA, 0x96, 0x7B), null))
    }

    @Test
    fun gbkDecodes() {
        assertEquals("<title>中文</title>", decodeHtml(title(0xD6, 0xD0, 0xCE, 0xC4), "GBK"))
    }

    @Test
    fun headerCharsetBeatsMetaCharset() {
        val page = "<meta charset=\"utf-8\">".encodeToByteArray() + cafe1252

        assertEquals("<meta charset=\"utf-8\"><title>Café</title>", decodeHtml(page, "windows-1252"))
    }

    @Test
    fun unknownHeaderLabelFallsThroughToMeta() {
        val page = "<meta charset=\"windows-1252\">".encodeToByteArray() + cafe1252

        assertEquals("<meta charset=\"windows-1252\"><title>Café</title>", decodeHtml(page, "x-made-up"))
    }

    @Test
    fun noUsableLabelMeansUtf8() {
        assertEquals("<title>தமிழ்</title>", decodeHtml("<title>தமிழ்</title>".encodeToByteArray(), "x-made-up"))
    }

    @Test
    fun shiftJisLabelDecodesCp932Characters() {
        // Browsers decode Shift_JIS as windows-31j, where ① is 0x8740 and ㈱ is 0x878A.
        val company = title(0x89, 0xEF, 0x8E, 0xD0, 0x87, 0x40, 0x87, 0x8A)

        for (label in listOf("Shift_JIS", "sjis", "ms_kanji", "csshiftjis", "x-sjis", "ms932", "windows-31j")) {
            assertEquals("<title>会社①㈱</title>", decodeHtml(company, label), label)
        }
        assertEquals("<meta charset=\"shift_jis\"><title>会社①㈱</title>", decodeHtml("<meta charset=\"shift_jis\">".encodeToByteArray() + company, null))
    }

    @Test
    fun gbkLabelsDecodeAsGb18030() {
        for (label in listOf("GBK", "gb2312", "x-gbk", "chinese", "GB_2312-80", "csgb2312", "gb18030")) {
            assertEquals("<title>中文</title>", decodeHtml(title(0xD6, 0xD0, 0xCE, 0xC4), label), label)
        }
    }

    @Test
    fun oneBadByteReplacesOnlyItself() {
        // 日本, then 0x81 0x20 (a lead byte without a valid trail byte), then 数.
        val shiftJis = decodeHtml(title(0x93, 0xFA, 0x96, 0x7B, 0x81, 0x20, 0x90, 0x94), "Shift_JIS")
        assertTrue(shiftJis.startsWith("<title>日本\uFFFD") && shiftJis.endsWith("数</title>"), shiftJis)

        val gbk = decodeHtml(title(0xD6, 0xD0, 0xCE, 0xC4, 0xFF), "GBK")
        assertTrue(gbk.startsWith("<title>中文\uFFFD") && gbk.endsWith("</title>"), gbk)
    }

    @Test
    fun moreWhatwgLabelsUseTheBrowsersDecoder() {
        val cases = listOf(
            Triple(listOf("euc-kr", "ks_c_5601-1987", "korean", "windows-949"), bytes(0x8C, 0x63), "똠"), // UHC only
            Triple(listOf("big5", "x-x-big5", "cn-big5", "big5-hkscs"), bytes(0x9D, 0xEF), "嘅"), // HKSCS only
            Triple(listOf("tis-620", "iso-8859-11", "windows-874"), bytes(0x96), "–"), // windows-874 only
            Triple(listOf("iso-8859-9", "latin5", "windows-1254"), bytes(0x80, 0xD0), "€Ğ"), // € is windows-1254 only
            Triple(listOf("iso-8859-8-i", "logical", "iso-8859-8"), bytes(0xE0), "א"),
            Triple(listOf("x-mac-cyrillic", "x-mac-ukrainian"), bytes(0x80, 0xA2, 0xB6, 0xFF), "АҐґ€"), // WHATWG table
        )
        for ((labels, body, text) in cases) {
            for (label in labels) assertEquals("<title>$text</title>", decodeHtml(title(*body.map { it.toInt() and 0xFF }.toIntArray()), label), label)
        }
    }
}
