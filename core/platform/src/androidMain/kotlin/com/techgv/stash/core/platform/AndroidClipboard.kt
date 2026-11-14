package com.techgv.stash.core.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.domain.ManualLink

/**
 * `ClipboardManager`. Android 12+ shows a "pasted from your clipboard" toast whenever another app's clip is read, so
 * there [peekLink] looks only at the clip's description (MIME types and the system's own URL classification), which
 * shows no toast, and the clip itself is read only in [readUrl], on the user's "Use". Below 12 reading is silent.
 */
class AndroidClipboard(context: Context) : Clipboard {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val appContext = context.applicationContext

    override suspend fun peekLink(): ClipboardLink? = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            readUrl()?.let(ClipboardLink::Url)
        } else {
            manager.primaryClipDescription?.takeIf(::likelyLink)?.let { ClipboardLink.Hidden }
        }
    } catch (e: RuntimeException) {
        null
    }

    override suspend fun readUrl(): String? = try {
        val text = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(appContext)?.toString()?.trim()
        // Valid as typed input and already an absolute http(s) URL: the Add-link sheet's rules, minus the scheme guess.
        text?.takeIf { LinkCapturePolicy.validateManual(it) is ManualLink.Valid && LinkCapturePolicy.normalize(it) != null }
    } catch (e: RuntimeException) {
        null
    }

    override fun copy(text: String) {
        manager.setPrimaryClip(ClipData.newPlainText("Link", text))
    }

    // Android 12+: the system classifies clip text in the background; until it has, only a URI-list MIME type tells.
    private fun likelyLink(description: ClipDescription): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
        ) {
            description.getConfidenceScore(TextClassifier.TYPE_URL) >= LINK_CONFIDENCE
        } else {
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
        }

    private companion object {
        const val LINK_CONFIDENCE = 0.5f
    }
}
