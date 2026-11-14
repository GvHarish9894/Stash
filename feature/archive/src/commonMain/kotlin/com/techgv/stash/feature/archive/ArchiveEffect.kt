package com.techgv.stash.feature.archive

import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.ItemId

/** One-off actions `shared` performs for the archive screen. */
sealed interface ArchiveEffect {
    /** Open the reader on [id]; the reader returns to the Archive. */
    data class OpenReader(val id: ItemId, val markedRead: Boolean) : ArchiveEffect

    /** "Open links in: Browser": `BrowserLauncher.open`, then report the result with [ArchiveIntent.BrowserOpened]. */
    data class OpenInBrowser(val url: String) : ArchiveEffect

    data class Share(val url: String, val title: String) : ArchiveEffect

    data class Copy(val text: String) : ArchiveEffect

    /** The first read of the day happened here; show the celebration. */
    data class Celebrate(val celebration: Celebration) : ArchiveEffect

    /** Leave the archive. */
    data object Back : ArchiveEffect
}
