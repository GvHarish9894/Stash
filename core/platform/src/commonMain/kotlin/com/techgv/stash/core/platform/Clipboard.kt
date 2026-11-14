package com.techgv.stash.core.platform

/** The system clipboard: `ClipboardManager` on Android, `UIPasteboard` on iOS. */
interface Clipboard {
    /**
     * What the Add-link sheet may suggest, WITHOUT triggering a paste prompt or toast: [ClipboardLink.Url] when the
     * platform lets the app see the text silently, [ClipboardLink.Hidden] when it only reveals that a link is there
     * (iOS `detectPatterns`), null when the clipboard holds no http(s) link. Safe to call from any thread; never throws.
     */
    suspend fun peekLink(): ClipboardLink?

    /**
     * The clipboard's text when it is an http(s) URL (trimmed), otherwise null. Never throws.
     *
     * On iOS this may show the system paste-permission prompt and on Android 12+ a "pasted" toast, so call it only
     * when the user taps "Use" on the suggestion. Returns null when the user declines. The caller still validates the
     * result like typed input. Safe to call from any thread.
     */
    suspend fun readUrl(): String?

    /** Puts [text] on the clipboard as plain text. Main thread. */
    fun copy(text: String)
}

/** What [Clipboard.peekLink] can tell about a link on the clipboard. */
sealed interface ClipboardLink {
    /** The link itself; the suggestion row shows it. */
    data class Url(val url: String) : ClipboardLink

    /** A link is there but reading it would prompt; the suggestion row offers to paste it without showing it. */
    data object Hidden : ClipboardLink
}
