package com.techgv.stash.feature.streak

sealed interface StreakSheetIntent {
    /** "Read something short": opens the shortest unread item. */
    data object ReadShort : StreakSheetIntent
    data object OpenReminders : StreakSheetIntent

    /** Shared's answer to [StreakSheetEffect.OpenInBrowser]: whether a browser showed the page. */
    data class BrowserOpened(val opened: Boolean) : StreakSheetIntent
}
