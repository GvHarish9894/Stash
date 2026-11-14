package com.techgv.stash.feature.settings

import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.AppLanguage
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import kotlin.time.Duration

sealed interface SettingsIntent {
    // Appearance
    data class SetTheme(val theme: ThemeMode) : SettingsIntent
    data class SetAccent(val accent: Accent) : SettingsIntent
    data class SetCardLayout(val layout: CardLayout) : SettingsIntent
    data class SetTextSize(val size: TextSize) : SettingsIntent
    data class SetReduceMotion(val on: Boolean) : SettingsIntent

    // Reading
    data class SetMarkReadOnOpen(val on: Boolean) : SettingsIntent
    data class SetReaderModeDefault(val on: Boolean) : SettingsIntent
    data class SetOpenLinksIn(val target: OpenLinksIn) : SettingsIntent

    // Streak and reminders. Turning a reminder on asks for the notification permission first.
    data class SetDailyReminder(val on: Boolean) : SettingsIntent
    data class SetReminderTime(val time: ReminderTime) : SettingsIntent
    data class SetStreakSaver(val on: Boolean) : SettingsIntent

    /** Shared's answer to [SettingsEffect.RequestNotificationPermission]. */
    data class NotificationPermissionResult(val granted: Boolean) : SettingsIntent

    /** Whether the OS permits notifications now, read by shared when the screen comes to the front. */
    data class NotificationsPermitted(val permitted: Boolean) : SettingsIntent
    data object OpenNotificationSettings : SettingsIntent

    /** Shows the reminder at [slot] for [timeout] ([Duration.INFINITE]: until dismissed). */
    data class PreviewReminder(val slot: ReminderLevel, val timeout: Duration) : SettingsIntent
    data object DismissPreview : SettingsIntent

    /** "Read now" on the preview: opens the item the reminder names. */
    data object ReadPreviewItem : SettingsIntent

    // Swipe gestures (decision D6: stored as start / end)
    data class SetSwipeStart(val action: SwipeAction) : SettingsIntent
    data class SetSwipeEnd(val action: SwipeAction) : SettingsIntent

    // Shelf
    data class SetSort(val sort: ShelfSort) : SettingsIntent
    data class SetGroup(val group: ShelfGrouping) : SettingsIntent
    data class SetAutoArchive(val period: AutoArchivePeriod) : SettingsIntent
    data object MoveDueNow : SettingsIntent
    data object MoveDueLater : SettingsIntent
    data object OpenArchive : SettingsIntent
    data object ShowTipAgain : SettingsIntent

    // Home screen and language
    data object OpenWidgetPreview : SettingsIntent
    data class ChooseAppIcon(val icon: AppIcon) : SettingsIntent

    /** Shared's answer to [SettingsEffect.SetAppIcon]. */
    data class AppIconResult(val icon: AppIcon, val applied: Boolean) : SettingsIntent
    data class ChooseLanguage(val language: AppLanguage) : SettingsIntent
    data object OpenLanguageSettings : SettingsIntent

    // Your data
    data object ClearRead : SettingsIntent
    data object ConfirmClearRead : SettingsIntent
    data object CancelClearRead : SettingsIntent

    // About
    data object OpenSourceCode : SettingsIntent

    /** The header's back button. */
    data object NavigateBack : SettingsIntent

    /** System back while [SettingsState.hasTopLayer]: closes the confirmation, else the preview. */
    data object DismissTopLayer : SettingsIntent
}
