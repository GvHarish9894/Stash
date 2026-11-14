package com.techgv.stash.feature.widgetpreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.domain.WidgetSnapshotPolicy
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.settings_save_failed
import com.techgv.stash.core.resources.toast_pin_requested
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class WidgetPreviewViewModel(
    private val settings: SettingsRepository,
    private val items: ItemRepository,
    streaks: StreakRepository,
    private val bus: CommandBus,
    private val clock: Clock,
    private val zones: ZoneProvider,
) : ViewModel() {
    private val effectChannel = Channel<WidgetPreviewEffect>(Channel.BUFFERED)
    val effects: Flow<WidgetPreviewEffect> = effectChannel.receiveAsFlow()

    private val ui = MutableStateFlow(WidgetPreviewState())
    private var toastJob: Job? = null

    // The chip counts change with every item change, so they also tell when to fetch the next unread items again.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val unread = items.observeChipCounts().mapLatest { it.unread to items.newestUnread(WidgetSnapshotPolicy.MAX_ITEMS) }

    val state: StateFlow<WidgetPreviewState> = combine(settings.preferences, streaks.observeStreak(), unread, ui) { prefs, streak, (count, newest), ui ->
        val snapshot = WidgetSnapshotPolicy.snapshot(newest, count, streak)
        val byId = newest.associateBy { it.id }
        val now = clock.now()
        val zone = zones.zone()
        ui.copy(
            loaded = true,
            size = prefs.widgetSize,
            appIcon = prefs.appIcon,
            streak = snapshot.streak,
            streakStatus = streak.status,
            unreadCount = snapshot.unreadCount,
            items = snapshot.items.mapNotNull { byId[it.id]?.toCardUi(now, zone) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetPreviewState())

    fun onIntent(intent: WidgetPreviewIntent) {
        viewModelScope.launch {
            when (intent) {
                is WidgetPreviewIntent.SetSize -> {
                    val result = bus.dispatch(Command.ChangeSetting { it.copy(widgetSize = intent.size) })
                    if (result is DispatchResult.Rejected) toast(text(Res.string.settings_save_failed))
                }
                is WidgetPreviewIntent.AddToHomeScreen ->
                    if (intent.canPin) effectChannel.send(WidgetPreviewEffect.PinWidget(settings.preferences.first().widgetSize)) else ui.update { it.copy(showSteps = true) }
                is WidgetPreviewIntent.PinResult ->
                    if (intent.dialogShown) toast(text(Res.string.toast_pin_requested)) else ui.update { it.copy(showSteps = true) }
                WidgetPreviewIntent.Back -> effectChannel.send(WidgetPreviewEffect.Back)
            }
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
        val TOAST_DURATION: Duration = 1_800.milliseconds
    }
}
