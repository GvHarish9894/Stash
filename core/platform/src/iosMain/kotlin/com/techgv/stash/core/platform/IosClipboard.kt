package com.techgv.stash.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardDetectionPatternProbableWebURL
import kotlin.coroutines.resume

/**
 * `UIPasteboard`. [peekLink] uses only `hasURLs`, `hasStrings` and `detectPatterns`, which never show the paste prompt,
 * so it can only say that a link is there ([ClipboardLink.Hidden]). [readUrl] reads the content and may prompt; it
 * runs off the main thread because the read waits for the prompt.
 */
class IosClipboard : Clipboard {
    override suspend fun peekLink(): ClipboardLink? {
        val board = UIPasteboard.generalPasteboard
        return when {
            board.hasURLs -> ClipboardLink.Hidden
            board.hasStrings && hasProbableWebUrl(board) -> ClipboardLink.Hidden
            else -> null
        }
    }

    private suspend fun hasProbableWebUrl(board: UIPasteboard): Boolean = suspendCancellableCoroutine { done ->
        board.detectPatternsForPatterns(setOf(UIPasteboardDetectionPatternProbableWebURL)) { found, _ ->
            done.resume(found?.contains(UIPasteboardDetectionPatternProbableWebURL) == true)
        }
    }

    override suspend fun readUrl(): String? = withContext(Dispatchers.IO) {
        val board = UIPasteboard.generalPasteboard
        val text = when {
            board.hasURLs -> board.URL?.absoluteString
            board.hasStrings -> board.string
            else -> null
        }?.trim()
        text?.takeIf { httpUrl(it) != null }
    }

    override fun copy(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
