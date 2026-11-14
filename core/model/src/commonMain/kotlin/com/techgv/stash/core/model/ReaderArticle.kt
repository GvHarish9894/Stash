package com.techgv.stash.core.model

/** Reader mode content as the shared UI renders it natively. */
data class ReaderArticle(
    val title: String,
    val siteName: String,
    val standfirst: String?,
    val readingMinutes: Int?,
    val blocks: List<ReaderBlock>,
)

sealed interface ReaderBlock {
    /** [level] is 1 to 6, as in `h1`…`h6`. */
    data class Heading(val level: Int, val text: RichText) : ReaderBlock
    data class Paragraph(val text: RichText) : ReaderBlock
    data class Quote(val text: RichText) : ReaderBlock

    /** Nested lists are flattened; [index] counts from 1 within the list the item belongs to. */
    data class ListItem(val text: RichText, val ordered: Boolean, val index: Int) : ReaderBlock
    data class Code(val text: String) : ReaderBlock
    data class Image(val url: String, val alt: String?) : ReaderBlock
}

/** Plain text plus styled ranges. Offsets are UTF-16 indices into [text], end exclusive. */
data class RichText(val text: String, val spans: List<RichSpan> = emptyList())

sealed interface RichSpan {
    val start: Int
    val end: Int

    data class Bold(override val start: Int, override val end: Int) : RichSpan
    data class Italic(override val start: Int, override val end: Int) : RichSpan
    data class Link(override val start: Int, override val end: Int, val url: String) : RichSpan
}
