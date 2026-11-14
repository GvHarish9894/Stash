package com.techgv.stash.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.designsystem.presentation.label
import com.techgv.stash.core.designsystem.presentation.plural
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.NoReminderReason
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.domain.ReminderPreviewResult
import com.techgv.stash.core.domain.ReminderTextRenderer
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.settings_app_icon_failed
import com.techgv.stash.core.resources.settings_preview_no_streak
import com.techgv.stash.core.resources.settings_save_failed
import com.techgv.stash.core.resources.time_11_00_pm
import com.techgv.stash.core.resources.time_7_00_pm
import com.techgv.stash.core.resources.time_9_00_am
import com.techgv.stash.core.resources.time_9_30_pm
import com.techgv.stash.core.resources.toast_app_icon_changed
import com.techgv.stash.core.resources.toast_archive_later
import com.techgv.stash.core.resources.toast_auto_archive_after
import com.techgv.stash.core.resources.toast_auto_archive_off
import com.techgv.stash.core.resources.toast_cleared
import com.techgv.stash.core.resources.toast_moved_to_archive
import com.techgv.stash.core.resources.toast_no_reminder_empty
import com.techgv.stash.core.resources.toast_no_reminder_read
import com.techgv.stash.core.resources.toast_nothing_to_clear
import com.techgv.stash.core.resources.toast_reminder_off
import com.techgv.stash.core.resources.toast_saver_off
import com.techgv.stash.core.resources.toast_tip_back
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val items: ItemRepository,
    private val streaks: StreakRepository,
    private val bus: CommandBus,
    private val reminderText: ReminderTextRenderer,
    private val clock: Clock,
    private val zones: ZoneProvider,
) : ViewModel() {
    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

    /** The screen's own state; the stored data is merged in by [state]. */
    private val ui = MutableStateFlow(SettingsState())
    private var toastJob: Job? = null
    private var previewJob: Job? = null

    val state: StateFlow<SettingsState> = combine(
        settings.preferences,
        streaks.observeStreak(),
        items.observeChipCounts(),
        items.observeArchiveCount(),
        ui,
    ) { prefs, streak, chips, archived, ui ->
        ui.copy(
            loaded = true,
            prefs = prefs,
            streak = streak.count,
            readToday = streak.readToday,
            streakStatus = streak.status,
            unreadCount = chips.unread,
            readCount = chips.all - chips.unread,
            archivedCount = archived,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch { handle(intent) }
    }

    private suspend fun handle(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetTheme -> change { it.copy(theme = intent.theme) }
            is SettingsIntent.SetAccent -> change { it.copy(accent = intent.accent) }
            is SettingsIntent.SetCardLayout -> change { it.copy(cardLayout = intent.layout) }
            is SettingsIntent.SetTextSize -> change { it.copy(textSize = intent.size) }
            is SettingsIntent.SetReduceMotion -> change { it.copy(reduceMotion = intent.on) }
            is SettingsIntent.SetMarkReadOnOpen -> change { it.copy(markReadOnOpen = intent.on) }
            is SettingsIntent.SetReaderModeDefault -> change { it.copy(readerModeDefault = intent.on) }
            is SettingsIntent.SetOpenLinksIn -> change { it.copy(openLinksIn = intent.target) }
            is SettingsIntent.SetDailyReminder ->
                if (intent.on) askPermission(ReminderSwitch.DailyReminder) else change { it.copy(dailyReminder = false) }
            is SettingsIntent.SetReminderTime -> change { it.copy(reminderTime = intent.time) }
            is SettingsIntent.SetStreakSaver ->
                if (intent.on) askPermission(ReminderSwitch.StreakSaver) else change { it.copy(streakSaver = false) }
            is SettingsIntent.NotificationPermissionResult -> onPermission(intent.granted)
            is SettingsIntent.NotificationsPermitted -> ui.update { it.copy(notificationsDenied = !intent.permitted) }
            SettingsIntent.OpenNotificationSettings -> effectChannel.send(SettingsEffect.OpenNotificationSettings)
            is SettingsIntent.PreviewReminder -> previewReminder(intent.slot, intent.timeout)
            SettingsIntent.DismissPreview -> hidePreview()
            SettingsIntent.ReadPreviewItem -> readPreviewItem()
            is SettingsIntent.SetSwipeStart -> change { it.copy(swipeStartAction = intent.action) }
            is SettingsIntent.SetSwipeEnd -> change { it.copy(swipeEndAction = intent.action) }
            is SettingsIntent.SetSort -> change { it.copy(sort = intent.sort) }
            // Sorting by source groups by source, so picking a grouping leaves that sort.
            is SettingsIntent.SetGroup ->
                change { it.copy(group = intent.group, sort = if (it.sort == ShelfSort.BySource) ShelfSort.Newest else it.sort) }
            is SettingsIntent.SetAutoArchive -> setAutoArchive(intent)
            SettingsIntent.MoveDueNow -> moveDueNow()
            SettingsIntent.MoveDueLater -> {
                ui.update { it.copy(autoArchiveConfirm = null) }
                toast(text(Res.string.toast_archive_later))
            }
            SettingsIntent.OpenArchive -> effectChannel.send(SettingsEffect.OpenArchive)
            SettingsIntent.ShowTipAgain -> if (change { it.copy(gestureTipDismissed = false) }) toast(text(Res.string.toast_tip_back))
            SettingsIntent.OpenWidgetPreview -> effectChannel.send(SettingsEffect.OpenWidgetPreview)
            is SettingsIntent.ChooseAppIcon ->
                if (intent.icon != settings.preferences.first().appIcon) effectChannel.send(SettingsEffect.SetAppIcon(intent.icon))
            is SettingsIntent.AppIconResult -> when {
                !intent.applied -> toast(text(Res.string.settings_app_icon_failed))
                change { it.copy(appIcon = intent.icon) } -> toast(text(Res.string.toast_app_icon_changed, text(intent.icon.label)))
            }
            is SettingsIntent.ChooseLanguage -> effectChannel.send(SettingsEffect.SetLanguage(intent.language.tag))
            SettingsIntent.OpenLanguageSettings -> effectChannel.send(SettingsEffect.OpenLanguageSettings)
            SettingsIntent.ClearRead -> {
                val count = items.observeChipCounts().first().let { it.all - it.unread }
                if (count == 0) toast(text(Res.string.toast_nothing_to_clear)) else ui.update { it.copy(clearReadConfirm = count) }
            }
            SettingsIntent.ConfirmClearRead -> confirmClearRead()
            SettingsIntent.CancelClearRead -> ui.update { it.copy(clearReadConfirm = null) }
            SettingsIntent.OpenSourceCode -> effectChannel.send(SettingsEffect.OpenExternally(SOURCE_CODE_URL))
            SettingsIntent.NavigateBack -> effectChannel.send(SettingsEffect.Back)
            SettingsIntent.DismissTopLayer -> {
                val s = ui.value
                when {
                    s.autoArchiveConfirm != null -> ui.update { it.copy(autoArchiveConfirm = null) }
                    s.clearReadConfirm != null -> ui.update { it.copy(clearReadConfirm = null) }
                    else -> hidePreview()
                }
            }
        }
    }

    /** Returns whether the change was stored; a refused change shows a toast and the controls keep the stored value. */
    private suspend fun change(transform: (UserPreferences) -> UserPreferences): Boolean {
        if (bus.dispatch(Command.ChangeSetting(transform)) is DispatchResult.Done) return true
        toast(text(Res.string.settings_save_failed))
        return false
    }

    private suspend fun askPermission(switch: ReminderSwitch) {
        ui.update { it.copy(pendingPermission = switch) }
        effectChannel.send(SettingsEffect.RequestNotificationPermission)
    }

    private suspend fun onPermission(granted: Boolean) {
        val pending = ui.value.pendingPermission ?: return
        ui.update { it.copy(pendingPermission = null, notificationsDenied = !granted) }
        // The user was asked: the app's own one-time request (decision D27) is not needed any more.
        change {
            when {
                !granted -> it.copy(notificationPermissionAsked = true)
                pending == ReminderSwitch.DailyReminder -> it.copy(notificationPermissionAsked = true, dailyReminder = true)
                else -> it.copy(notificationPermissionAsked = true, streakSaver = true)
            }
        }
    }

    /**
     * Shows today's reminder at [slot] as [ReminderPlanner] would plan it now, rendered by the same [ReminderTextRenderer]
     * as the real notification, or says why there would be none.
     */
    private suspend fun previewReminder(slot: ReminderLevel, timeout: Duration) {
        hidePreview()
        ui.update { it.copy(previewSlot = slot) }
        val prefs = settings.preferences.first()
        val streak = streaks.observeStreak().first()
        val unread = items.observeChipCounts().first().unread
        val zone = zones.zone()
        val result = ReminderPlanner.preview(slot, prefs, streak.count, streak.readToday, unread, items.shortestUnread(), clock.now(), zone)
        val planned = when (result) {
            is ReminderPreviewResult.Planned -> result.notification
            is ReminderPreviewResult.NoReminder -> return toast(text(result.reason.message))
        }
        val at = planned.fireAt.toLocalDateTime(zone).time
        val rendered = reminderText.render(planned)
        val preview = ReminderPreview(slot, slotLabel(at), rendered.title, rendered.body, planned.itemId)
        ui.update { it.copy(preview = preview) }
        if (timeout.isFinite()) {
            previewJob = viewModelScope.launch {
                delay(timeout)
                ui.update { if (it.preview == preview) it.copy(preview = null) else it }
            }
        }
    }

    private fun hidePreview() {
        previewJob?.cancel()
        ui.update { it.copy(preview = null) }
    }

    private suspend fun readPreviewItem() {
        val preview = ui.value.preview ?: return
        hidePreview()
        val result = bus.dispatch(Command.OpenItem(preview.itemId))
        if (result is DispatchResult.Rejected) {
            if (result.rejection == Rejection.StorageFailure) toast(text(Res.string.settings_save_failed))
            return
        }
        val url = items.get(preview.itemId)?.url
        val browser = settings.preferences.first().openLinksIn == OpenLinksIn.Browser
        effectChannel.send(if (browser && url != null) SettingsEffect.OpenExternally(url) else SettingsEffect.OpenReader(preview.itemId, (result as DispatchResult.Done).affected == 1))
        (result as DispatchResult.Done).celebration?.let { effectChannel.send(SettingsEffect.Celebrate(it)) }
    }

    /** The period is stored first; when it would move items now, "Not yet" leaves them for the next daily check. */
    private suspend fun setAutoArchive(intent: SettingsIntent.SetAutoArchive) {
        ui.update { it.copy(autoArchiveConfirm = null) }
        if (!change { it.copy(autoArchive = intent.period) }) return
        val days = intent.period.days ?: return toast(text(Res.string.toast_auto_archive_off))
        val due = items.countAutoArchiveDue(intent.period)
        if (due > 0) {
            ui.update { it.copy(autoArchiveConfirm = AutoArchiveConfirm(intent.period, due)) }
        } else {
            toast(plural(Res.plurals.toast_auto_archive_after, days))
        }
    }

    private suspend fun moveDueNow() {
        val confirm = ui.value.autoArchiveConfirm ?: return
        ui.update { it.copy(autoArchiveConfirm = null) }
        when (val result = bus.dispatch(Command.ApplyAutoArchive(confirm.period))) {
            is DispatchResult.Done -> toast(plural(Res.plurals.toast_moved_to_archive, result.affected))
            is DispatchResult.Rejected -> toast(
                if (result.rejection == Rejection.NothingToApply) {
                    plural(Res.plurals.toast_auto_archive_after, confirm.period.days ?: 0)
                } else {
                    text(Res.string.settings_save_failed)
                },
            )
        }
    }

    private suspend fun confirmClearRead() {
        ui.update { it.copy(clearReadConfirm = null) }
        when (val result = bus.dispatch(Command.ClearRead)) {
            is DispatchResult.Done -> toast(plural(Res.plurals.toast_cleared, result.affected))
            is DispatchResult.Rejected -> toast(
                text(if (result.rejection == Rejection.NothingToApply) Res.string.toast_nothing_to_clear else Res.string.settings_save_failed),
            )
        }
    }

    private fun toast(message: UiText) {
        toastJob?.cancel()
        ui.update { it.copy(toast = message) }
        toastJob = viewModelScope.launch {
            delay(TOAST_DURATION)
            ui.update { it.copy(toast = null) }
        }
    }

    companion object {
        const val SOURCE_CODE_URL = "https://github.com/GvHarish9894"

        /** How long a settings toast stays (the prototype's 1.8 s). */
        val TOAST_DURATION: Duration = 1_800.milliseconds

        private val NoReminderReason.message
            get() = when (this) {
                NoReminderReason.ReminderOff -> Res.string.toast_reminder_off
                NoReminderReason.SaverOff -> Res.string.toast_saver_off
                NoReminderReason.ReadToday -> Res.string.toast_no_reminder_read
                NoReminderReason.NothingUnread -> Res.string.toast_no_reminder_empty
                NoReminderReason.NoStreak -> Res.string.settings_preview_no_streak
            }

        private fun slotLabel(time: LocalTime): UiText = text(
            when (time) {
                LocalTime(9, 0) -> Res.string.time_9_00_am
                LocalTime(19, 0) -> Res.string.time_7_00_pm
                LocalTime(21, 30) -> Res.string.time_9_30_pm
                else -> Res.string.time_11_00_pm
            },
        )
    }
}
