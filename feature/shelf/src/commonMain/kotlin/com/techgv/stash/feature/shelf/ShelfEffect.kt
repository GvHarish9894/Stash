package com.techgv.stash.feature.shelf

import com.techgv.stash.core.designsystem.HapticKind
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.ItemId

/** One-off actions. [Haptic] is played by [ShelfRoute]; every other effect is a callback that `shared` implements. */
sealed interface ShelfEffect {
    data class OpenReader(val id: ItemId, val markedRead: Boolean) : ShelfEffect

    /** Shared answers with [ShelfIntent.BrowserOpened]. */
    data class OpenInBrowser(val url: String) : ShelfEffect
    data class Share(val url: String, val title: String) : ShelfEffect
    data class Copy(val text: String) : ShelfEffect
    data object OpenSearch : ShelfEffect
    data object OpenArchive : ShelfEffect
    data object OpenSettings : ShelfEffect
    data object OpenStreak : ShelfEffect

    /** Shared answers with [ShelfIntent.ClipboardPeeked]; it must not trigger a paste prompt. */
    data object PeekClipboard : ShelfEffect

    /** The user tapped Use on a hidden clipboard link; shared answers with [ShelfIntent.ClipboardRead]. */
    data object ReadClipboard : ShelfEffect

    /** The first read of the day happened here; the streak feature shows it. */
    data class Celebrate(val celebration: Celebration) : ShelfEffect

    /** A confirm or reject haptic that depends on a command result. */
    data class Haptic(val kind: HapticKind) : ShelfEffect
}
