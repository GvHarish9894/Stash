package com.techgv.stash.core.platform

/** The system share sheet: an `ACTION_SEND` chooser on Android, `UIActivityViewController` on iOS. Call on the main thread. */
interface ShareSheet {
    /**
     * Offers [url] to other apps, with [title] as the subject or preview text. Returns once the sheet is shown.
     * On iPad the sheet is anchored to the centre of the key window. Never throws.
     */
    fun share(url: String, title: String)
}
