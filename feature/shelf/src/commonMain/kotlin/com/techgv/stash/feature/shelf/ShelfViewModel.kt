package com.techgv.stash.feature.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.techgv.stash.core.designsystem.HapticKind
import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.domain.ManualLink
import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.ShelfQuerySpec
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.domain.StreakRepository
import com.techgv.stash.core.domain.SwipeActionResolver
import com.techgv.stash.core.domain.SwipeDirection
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Home: the shelf list, its header, chips, nudge, gesture tip, item menu, undo and the add-link sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModel(
    private val items: ItemRepository,
    private val bus: CommandBus,
    private val settings: SettingsRepository,
    streaks: StreakRepository,
    private val clock: Clock,
    private val zones: ZoneProvider,
) : ViewModel() {
    private val effectChannel = Channel<ShelfEffect>(Channel.BUFFERED)
    val effects: Flow<ShelfEffect> = effectChannel.receiveAsFlow()

    // What this screen owns. Its `filter` is the user's selection; [state] reports the filter actually applied.
    private val local = MutableStateFlow(ShelfState(clock.now(), zones.zone()))
    private val timers = mutableMapOf<String, Job>()

    /** The torn item whose Remove has not gone out yet: the card's `onTorn` and the fallback timer send it once. */
    private var tearPending: ItemId? = null

    private val chipCounts = items.observeChipCounts().shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), 1)

    // Relative times and the nudge level move with the clock; the streak flow covers midnight and 21:30 on its own.
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(1.minutes)
        }
    }

    private val stored = combine(settings.preferences, chipCounts, streaks.observeStreak(), items.observeArchiveCount(), ticks) {
            prefs, chips, streak, archived, _ ->
        val now = clock.now()
        val zone = zones.zone()
        val level = ReminderPlanner.nudgeLevel(prefs, streak.count, streak.readToday, chips.unread, now, zone)
        val nudge = level?.let {
            items.shortestUnread()?.let { item ->
                ShelfNudge(level, streak.count, item.id, ReminderPlanner.copyTitle(item), item.readingMinutes)
            }
        }
        ShelfState(now, zone, prefs, chips, streak = streak, nudge = nudge, archiveCount = archived)
    }

    val state: StateFlow<ShelfState> = combine(local, stored) { mine, from ->
        mine.copy(
            now = from.now,
            zone = from.zone,
            preferences = from.preferences,
            chips = from.chips,
            filter = ShelfQuery.effectiveFilter(mine.filter, from.chips),
            streak = from.streak,
            nudge = from.nudge,
            archiveCount = from.archiveCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), local.value)

    private data class RowsSpec(val query: ShelfQuerySpec, val start: SwipeAction, val end: SwipeAction)

    /** The shelf for the user's sort, grouping and the applied filter, each card with its resolved swipe outcomes. */
    val rows: Flow<PagingData<ShelfListRow>> =
        combine(settings.preferences, local.map { it.filter }, chipCounts) { prefs, filter, chips ->
            RowsSpec(ShelfQuerySpec(ShelfQuery.effectiveFilter(filter, chips), prefs.sort, prefs.group), prefs.swipeStartAction, prefs.swipeEndAction)
        }.distinctUntilChanged().flatMapLatest { spec ->
            val swipes = UserPreferences(swipeStartAction = spec.start, swipeEndAction = spec.end)
            items.observeShelf(spec.query).map { page ->
                page.map { row ->
                    when (row) {
                        is ShelfRow.Header -> ShelfListRow.Header(row.label)
                        is ShelfRow.Card -> ShelfListRow.Card(
                            row.item,
                            SwipeActionResolver.resolve(row.item, SwipeDirection.Start, swipes),
                            SwipeActionResolver.resolve(row.item, SwipeDirection.End, swipes),
                        )
                    }
                }
            }
        }.cachedIn(viewModelScope)

    init {
        later("firstLoad", FIRST_LOAD_MILLIS) { copy(firstLoad = false) }
    }

    fun onIntent(intent: ShelfIntent) {
        viewModelScope.launch { handle(intent) }
    }

    private suspend fun handle(intent: ShelfIntent) {
        when (intent) {
            ShelfIntent.OpenSearch -> send(ShelfEffect.OpenSearch)
            ShelfIntent.OpenArchive -> send(ShelfEffect.OpenArchive)
            ShelfIntent.OpenSettings -> send(ShelfEffect.OpenSettings)
            ShelfIntent.OpenStreak -> send(ShelfEffect.OpenStreak)
            is ShelfIntent.SelectFilter -> local.update { it.copy(filter = intent.filter, moreSourcesOpen = false) }
            ShelfIntent.OpenMoreSources -> local.update { it.copy(moreSourcesOpen = true) }
            ShelfIntent.CloseMoreSources -> local.update { it.copy(moreSourcesOpen = false) }
            ShelfIntent.ReadNudge -> items.shortestUnread()?.let { open(it.id) }
            ShelfIntent.DismissGestureTip -> dismissGestureTip()
            is ShelfIntent.Open -> open(intent.id)
            is ShelfIntent.Act -> {
                dismissGestureTip()
                act(intent.id, intent.outcome)
            }
            is ShelfIntent.Retry -> dispatch(Command.RetryMetadata(intent.id))
            is ShelfIntent.OpenMenu -> {
                dismissGestureTip()
                val item = items.get(intent.id) ?: return
                local.update { it.copy(menuItem = item) }
            }
            is ShelfIntent.MenuAction -> menuAction(intent.action)
            is ShelfIntent.Torn -> torn(intent.id)
            ShelfIntent.CloseMenu -> local.update { it.copy(menuItem = null) }
            is ShelfIntent.BrowserOpened -> toast(if (intent.opened) ShelfToast.OpenedInBrowser else ShelfToast.BrowserFailed)
            ShelfIntent.Undo -> {
                val offer = local.value.undo ?: return
                local.update { it.copy(undo = null) }
                dispatch(Command.Undo(offer.opId)) ?: return
                // The card comes back with the fly-back (handoff); a torn card must not tear again.
                local.update { it.copy(flyBackItemId = offer.itemId, tearingItemId = it.tearingItemId.takeUnless { id -> id == offer.itemId }) }
                later("flyBack", FLY_BACK_MILLIS) { copy(flyBackItemId = null) }
            }
            is ShelfIntent.DismissUndo -> local.update { if (it.undo?.opId == intent.opId) it.copy(undo = null) else it }
            ShelfIntent.OpenAddLink -> {
                local.update { it.copy(addLink = AddLinkState()) }
                send(ShelfEffect.PeekClipboard)
            }
            ShelfIntent.CloseAddLink -> local.update { it.copy(addLink = null) }
            is ShelfIntent.ClipboardPeeked -> local.update { it.copy(addLink = it.addLink?.copy(clipboard = intent.suggestion)) }
            is ShelfIntent.AddLinkUrlChanged -> local.update { it.copy(addLink = it.addLink?.copy(url = intent.url, error = null)) }
            ShelfIntent.UseClipboard -> when (val clip = local.value.addLink?.clipboard) {
                is ClipboardSuggestion.Visible -> saveLink(clip.url)
                ClipboardSuggestion.Hidden -> send(ShelfEffect.ReadClipboard)
                null -> Unit
            }
            is ShelfIntent.ClipboardRead -> {
                if (local.value.addLink == null) return
                val url = intent.url
                if (url == null) local.update { it.copy(addLink = it.addLink?.copy(clipboard = null)) } else saveLink(url)
            }
            ShelfIntent.SaveLink -> saveLink(local.value.addLink?.url ?: return)
        }
    }

    /** Reading is a command (decision D21); the page opens even when recording the read failed. */
    private suspend fun open(id: ItemId) {
        val item = items.get(id) ?: return
        val result = bus.dispatch(Command.OpenItem(id)) as? DispatchResult.Done
        if (settings.preferences.first().openLinksIn == OpenLinksIn.Browser) {
            if (result != null && result.affected > 0) strike(id)
            send(ShelfEffect.OpenInBrowser(item.url))
        } else {
            send(ShelfEffect.OpenReader(id, markedRead = result?.affected == 1))
        }
        result?.celebration?.let { send(ShelfEffect.Celebrate(it)) }
    }

    private suspend fun act(id: ItemId, outcome: SwipeOutcome) {
        val (command, type) = when (outcome) {
            SwipeOutcome.MarkRead -> Command.MarkRead(id) to null
            SwipeOutcome.MarkUnread -> Command.MarkUnread(id) to null
            SwipeOutcome.Archive -> Command.Archive(id) to OperationType.Archive
            SwipeOutcome.Remove -> Command.Remove(id) to OperationType.Remove
            SwipeOutcome.Restore -> Command.Restore(id) to OperationType.Restore
            SwipeOutcome.Delete -> Command.Delete(id) to OperationType.Delete
            SwipeOutcome.Share -> {
                items.get(id)?.let { send(ShelfEffect.Share(it.url, it.title)) }
                return
            }
            SwipeOutcome.RubberBand -> return
        }
        val done = dispatch(command) ?: return
        if (outcome == SwipeOutcome.MarkRead && done.affected > 0) strike(id)
        val opId = done.undoOpId
        if (opId != null && type != null) local.update { it.copy(undo = UndoOffer(opId, type, id)) }
    }

    private suspend fun menuAction(action: ItemMenuAction) {
        val item = local.value.menuItem ?: return
        local.update { it.copy(menuItem = null) }
        when (action) {
            ItemMenuAction.MarkRead -> act(item.id, SwipeOutcome.MarkRead)
            ItemMenuAction.MarkUnread -> act(item.id, SwipeOutcome.MarkUnread)
            ItemMenuAction.Open -> open(item.id)
            ItemMenuAction.Share -> act(item.id, SwipeOutcome.Share)
            ItemMenuAction.CopyLink -> {
                send(ShelfEffect.Copy(item.url))
                toast(ShelfToast.LinkCopied)
            }
            ItemMenuAction.Archive -> act(item.id, SwipeOutcome.Archive)
            ItemMenuAction.Remove -> tear(item.id)
            ItemMenuAction.Restore -> act(item.id, SwipeOutcome.Restore)
            ItemMenuAction.Delete -> act(item.id, SwipeOutcome.Delete)
        }
    }

    /** Remove from the menu: the card tears first (handoff) and reports [ShelfIntent.Torn]; the timer covers a card that left the screen. */
    private fun tear(id: ItemId) {
        tearPending = id
        local.update { it.copy(tearingItemId = id) }
        viewModelScope.launch {
            delay(TEAR_FALLBACK_MILLIS)
            torn(id)
        }
    }

    /** Sends the Remove of a torn card once; a refused one shows the card in place again. */
    private suspend fun torn(id: ItemId) {
        if (tearPending != id) return
        tearPending = null
        val done = dispatch(Command.Remove(id))
        if (done == null) {
            local.update { if (it.tearingItemId == id) it.copy(tearingItemId = null) else it }
            return
        }
        done.undoOpId?.let { opId -> local.update { it.copy(undo = UndoOffer(opId, OperationType.Remove, id)) } }
        // The row has left the list by then; an item that comes back some other way must not tear again.
        later("tear", TEAR_CLEAR_MILLIS) { if (tearingItemId == id) copy(tearingItemId = null) else this }
    }

    private suspend fun saveLink(input: String) {
        val error = when (val link = LinkCapturePolicy.validateManual(input)) {
            ManualLink.Empty -> AddLinkError.Empty
            ManualLink.NotALink -> AddLinkError.NotALink
            is ManualLink.Valid -> when (val result = bus.dispatch(Command.SaveLink(link.capture, sourceApp = null))) {
                is DispatchResult.Done -> {
                    local.update { it.copy(addLink = null, filter = ShelfFilter.All, newItemId = result.itemId) }
                    later("newItem", NEW_ITEM_MILLIS) { copy(newItemId = null) }
                    if (result.reshared) toast(ShelfToast.AlreadyOnShelf)
                    send(ShelfEffect.Haptic(HapticKind.Confirm))
                    return
                }
                is DispatchResult.Rejected -> {
                    toast(ShelfToast.SaveFailed)
                    send(ShelfEffect.Haptic(HapticKind.Reject))
                    return
                }
            }
        }
        local.update { it.copy(addLink = it.addLink?.copy(url = input, error = error)) }
        send(ShelfEffect.Haptic(HapticKind.Reject))
    }

    /** Hides the tip for good (persisted); the first swipe or long-press does it too. */
    private suspend fun dismissGestureTip() {
        if (settings.preferences.first().gestureTipDismissed) return
        bus.dispatch(Command.ChangeSetting { it.copy(gestureTipDismissed = true) })
    }

    /** Dispatches [command]; a celebration goes out as an effect, a rejection shows a toast and returns null. */
    private suspend fun dispatch(command: Command): DispatchResult.Done? = when (val result = bus.dispatch(command)) {
        is DispatchResult.Done -> result.also { done -> done.celebration?.let { send(ShelfEffect.Celebrate(it)) } }
        is DispatchResult.Rejected -> {
            toast(ShelfToast.ActionFailed)
            null
        }
    }

    private fun strike(id: ItemId) {
        local.update { it.copy(struckItemId = id) }
        later("strike", STRIKE_MILLIS) { copy(struckItemId = null) }
    }

    private fun toast(toast: ShelfToast) {
        local.update { it.copy(toast = toast) }
        later("toast", TOAST_MILLIS) { copy(toast = null) }
    }

    /** After [millis], applies [change] to the local state; a newer call with the same [key] replaces a pending one. */
    private fun later(key: String, millis: Long, change: ShelfState.() -> ShelfState) {
        timers[key]?.cancel()
        timers[key] = viewModelScope.launch {
            delay(millis)
            local.update(change)
        }
    }

    private suspend fun send(effect: ShelfEffect) = effectChannel.send(effect)

    internal companion object {
        const val STOP_MILLIS = 5_000L
        const val FIRST_LOAD_MILLIS = 1_500L
        const val NEW_ITEM_MILLIS = 800L
        const val STRIKE_MILLIS = 900L
        const val TOAST_MILLIS = 1_600L

        /** How long a card brought back by Undo keeps its fly-back (prototype: 600 ms, longer than the 500 ms motion). */
        const val FLY_BACK_MILLIS = 600L

        /** Remove goes out by then even if the tearing card left the screen before it called back. */
        const val TEAR_FALLBACK_MILLIS = 1_000L
        const val TEAR_CLEAR_MILLIS = 1_000L
    }
}
