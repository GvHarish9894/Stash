package com.techgv.stash.feature.streak

import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.ItemId

sealed interface StreakSheetEffect {
    data class OpenReader(val id: ItemId, val markedRead: Boolean) : StreakSheetEffect

    /** "Open links in" is Browser; shared answers with [StreakSheetIntent.BrowserOpened]. */
    data class OpenInBrowser(val url: String) : StreakSheetEffect

    /** Settings, scrolled to the "Streak and reminders" group. */
    data object OpenReminders : StreakSheetEffect

    /** The read from "Read something short" was the first of the day. */
    data class Celebrate(val celebration: Celebration) : StreakSheetEffect
}
