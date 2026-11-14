package com.techgv.stash.feature.reader

sealed interface ReaderEffect {
    /** Back to the screen that opened the reader. */
    data object Close : ReaderEffect

    /** Open an http(s) [url] in the browser (`BrowserLauncher` in `shared`). */
    data class OpenInBrowser(val url: String) : ReaderEffect
}
