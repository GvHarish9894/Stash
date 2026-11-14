package com.techgv.stash.feature.streak

import androidx.compose.runtime.Immutable
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.model.ReminderTime

/** What the streak sheet and the celebration draw. */
@Immutable
data class StreakSheetState(
    /** Null until the first streak state arrives. */
    val streak: StreakState? = null,
    val hasUnread: Boolean = false,
    /** Null when the daily reminder is off. */
    val reminderTime: ReminderTime? = null,
    val streakSaver: Boolean = false,
    /** Shown after "Read something short" opened the browser; it outlives the sheet. */
    val toast: StreakToast? = null,
)

enum class StreakToast { OpenedInBrowser, BrowserFailed }
