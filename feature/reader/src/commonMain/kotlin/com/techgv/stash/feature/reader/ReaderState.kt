package com.techgv.stash.feature.reader

import androidx.compose.runtime.Immutable
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.SavedItem

/** Where the reader was opened from; Close returns there (handoff: Home, Search or Archive; a link opens over Home). */
enum class ReaderOrigin { Home, Search, Archive, Link }

/** Reader-mode text for the open item. */
sealed interface ArticleState {
    /** Not asked for yet: reader mode has not been on. */
    data object Idle : ArticleState
    data object Loading : ArticleState
    data class Ready(val article: ReaderArticle) : ArticleState

    /** Reader mode cannot extract this page; the screen stays in web mode. */
    data object Unavailable : ArticleState
    data object Offline : ArticleState
}

/** Short messages at the bottom of the reader. */
enum class ReaderToast { MarkedRead, BackOnUnread, ReaderUnavailable, ActionFailed }

@Immutable
data class ReaderState(
    /** Null while loading, and when [missing]. */
    val item: SavedItem? = null,
    /** The item does not exist (any more): a widget row or notification outlived it. */
    val missing: Boolean = false,
    /** Null until the "Open in reader mode" setting is read, so the web view never starts for a reader-mode open. */
    val readerMode: Boolean? = null,
    val article: ArticleState = ArticleState.Idle,
    /** Reader-mode text step, 0..3, from the settings. */
    val textStep: Int = 1,
    /** Web page load progress, 0..1. */
    val webProgress: Float = 0f,
    /** The web page failed to load (offline, or the https page does not answer). */
    val webFailed: Boolean = false,
    val toast: ReaderToast? = null,
) {
    /** "Mark unread" only makes sense for an item that is read (also when archived). */
    val canMarkUnread: Boolean get() = item?.readAt != null

    /** Load progress for the brass bar, 0..1: the web page's, or the article's in reader mode. */
    val loadProgress: Float
        get() = when {
            readerMode == true -> if (article == ArticleState.Loading || article == ArticleState.Idle) 0f else 1f
            webFailed -> 1f
            else -> webProgress
        }
}
