package com.techgv.stash.feature.search

import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.ItemId

/** One-off actions `shared` performs for the search screen. */
sealed interface SearchEffect {
    /** Open the reader on [id]; the reader returns to Search. */
    data class OpenReader(val id: ItemId, val markedRead: Boolean) : SearchEffect

    /** "Open links in: Browser": `BrowserLauncher.open`, then report the result with [SearchIntent.BrowserOpened]. */
    data class OpenInBrowser(val url: String) : SearchEffect

    data class Share(val url: String, val title: String) : SearchEffect

    data class Copy(val text: String) : SearchEffect

    /** The first read of the day happened here; show the celebration. */
    data class Celebrate(val celebration: Celebration) : SearchEffect

    /** Leave the search screen. */
    data object Back : SearchEffect
}
