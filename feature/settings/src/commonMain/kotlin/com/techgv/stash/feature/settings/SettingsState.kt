package com.techgv.stash.feature.settings

import androidx.compose.runtime.Immutable
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.UserPreferences

/** The settings groups, in the handoff's order. Also the sections list of the two-pane (expanded) layout. */
enum class SettingsSection { Appearance, Reading, Streak, Swipe, Shelf, Home, Account, Data, About }

/** A reminder switch that waits for the notification permission before it turns on. */
enum class ReminderSwitch { DailyReminder, StreakSaver }

/** Choosing [period] would move [count] read items now: "Not yet / Move now". */
@Immutable
data class AutoArchiveConfirm(val period: AutoArchivePeriod, val count: Int)

/** The in-app preview of the reminder at [slot], with the text the real notification would have. */
@Immutable
data class ReminderPreview(val slot: ReminderLevel, val time: UiText, val title: String, val body: String, val itemId: ItemId)

@Immutable
data class SettingsState(
    /** False until the stored settings arrive; the screen draws no controls before that, so none animate from defaults. */
    val loaded: Boolean = false,
    val prefs: UserPreferences = UserPreferences(),
    val streak: Int = 0,
    val readToday: Boolean = false,
    val streakStatus: StreakStatus = StreakStatus.Unlit,
    val unreadCount: Int = 0,
    /** Read items on the shelf, what "Clear read items" would remove. */
    val readCount: Int = 0,
    val archivedCount: Int = 0,
    val autoArchiveConfirm: AutoArchiveConfirm? = null,
    /** "Clear N read items?" is showing for this count. */
    val clearReadConfirm: Int? = null,
    /** The last slot picked in "Preview a reminder". */
    val previewSlot: ReminderLevel? = null,
    val preview: ReminderPreview? = null,
    /** Turning this on waits for the permission answer. */
    val pendingPermission: ReminderSwitch? = null,
    /** Notifications are not permitted (the last request was refused, or the OS says so): offer the system settings. */
    val notificationsDenied: Boolean = false,
    val toast: UiText? = null,
) {
    /** Something system back closes before it leaves the screen (handoff: confirmation, then notification). */
    val hasTopLayer: Boolean get() = autoArchiveConfirm != null || clearReadConfirm != null || preview != null
}
