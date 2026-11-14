package com.techgv.stash.feature.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.model.OpenLinksIn
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
import kotlinx.coroutines.launch

/** The streak sheet, and the week strip the celebration shows. */
class StreakSheetViewModel(
    streaks: StreakRepository,
    private val settings: SettingsRepository,
    private val items: ItemRepository,
    private val bus: CommandBus,
) : ViewModel() {
    private val effectChannel = Channel<StreakSheetEffect>(Channel.BUFFERED)
    val effects: Flow<StreakSheetEffect> = effectChannel.receiveAsFlow()

    private val toast = MutableStateFlow<StreakToast?>(null)
    private var toastJob: Job? = null

    val state: StateFlow<StreakSheetState> =
        combine(streaks.observeStreak(), settings.preferences, items.observeChipCounts(), toast) { streak, prefs, chips, toast ->
            StreakSheetState(streak, chips.unread > 0, prefs.reminderTime.takeIf { prefs.dailyReminder }, prefs.streakSaver, toast)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakSheetState())

    fun onIntent(intent: StreakSheetIntent) {
        viewModelScope.launch {
            when (intent) {
                StreakSheetIntent.OpenReminders -> effectChannel.send(StreakSheetEffect.OpenReminders)
                StreakSheetIntent.ReadShort -> readShort()
                is StreakSheetIntent.BrowserOpened -> showToast(if (intent.opened) StreakToast.OpenedInBrowser else StreakToast.BrowserFailed)
            }
        }
    }

    /**
     * Reading is a command (decision D21); the page opens even when recording the read failed, in the browser when
     * "Open links in" says so.
     */
    private suspend fun readShort() {
        val item = items.shortestUnread() ?: return
        val result = bus.dispatch(Command.OpenItem(item.id)) as? DispatchResult.Done
        val open = if (settings.preferences.first().openLinksIn == OpenLinksIn.Browser) {
            StreakSheetEffect.OpenInBrowser(item.url)
        } else {
            StreakSheetEffect.OpenReader(item.id, markedRead = result?.affected == 1)
        }
        effectChannel.send(open)
        result?.celebration?.let { effectChannel.send(StreakSheetEffect.Celebrate(it)) }
    }

    private fun showToast(shown: StreakToast) {
        toastJob?.cancel()
        toast.value = shown
        toastJob = viewModelScope.launch {
            delay(TOAST_MILLIS)
            toast.value = null
        }
    }

    companion object {
        /** As long as the shelf's toasts. */
        const val TOAST_MILLIS = 1_600L
    }
}
