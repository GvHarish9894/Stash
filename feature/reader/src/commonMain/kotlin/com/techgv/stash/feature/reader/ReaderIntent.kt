package com.techgv.stash.feature.reader

sealed interface ReaderIntent {
    data object Close : ReaderIntent
    data object ToggleReaderMode : ReaderIntent
    data object MarkUnread : ReaderIntent

    /** A− / A+: the new step; out-of-range values are clamped to 0..3. */
    data class ChangeTextStep(val step: Int) : ReaderIntent

    /** Retry after reader mode was offline. */
    data object RetryArticle : ReaderIntent

    /** Retry after the web page failed to load. */
    data object RetryWeb : ReaderIntent

    /** "Open in browser" under the web page's offline message. */
    data object OpenInBrowser : ReaderIntent

    /** A link tapped in the reader-mode text. */
    data class OpenLink(val url: String) : ReaderIntent

    data class Web(val event: WebEvent) : ReaderIntent
}

/** What the platform web view reports. */
sealed interface WebEvent {
    /** Main page load progress, 0..1. */
    data class Progress(val fraction: Float) : WebEvent

    /** The main page failed to load; cancelled navigations are not reported. */
    data object Failed : WebEvent
}
