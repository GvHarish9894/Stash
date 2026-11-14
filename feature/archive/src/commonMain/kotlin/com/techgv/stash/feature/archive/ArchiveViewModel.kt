package com.techgv.stash.feature.archive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.domain.SwipeActionResolver
import com.techgv.stash.core.domain.SwipeDirection
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.search_change_failed
import com.techgv.stash.core.resources.search_open_link_failed
import com.techgv.stash.core.resources.toast_link_copied
import com.techgv.stash.core.resources.toast_opened_in_browser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ArchiveViewModel(
    private val items: ItemRepository,
    private val bus: CommandBus,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zones: ZoneProvider,
) : ViewModel() {
    private val effectChannel = Channel<ArchiveEffect>(Channel.BUFFERED)
    val effects: Flow<ArchiveEffect> = effectChannel.receiveAsFlow()

    /** The search field text. Compose state, set synchronously, so the field never waits on a flow while the user types. */
    var query by mutableStateOf("")
        private set

    private val typed = MutableStateFlow("")
    private val overlay = MutableStateFlow(Overlay())

    private data class Overlay(
        val menu: SavedItemCardUi? = null,
        val undo: PendingUndo? = null,
        val toast: UiText? = null,
        val tearing: String? = null,
        val flyBack: String? = null,
    )

    /** The torn item whose Delete has not gone out yet: the card's `onTorn` and the fallback timer send it once. */
    private var tearPending: ItemId? = null

    /** The trimmed search text once typing pauses; clearing the field applies at once. */
    private val searchText: Flow<String> = typed.map { it.trim() }.distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MILLIS }

    val state: StateFlow<ArchiveState> = combine(items.observeArchiveCount(), settings.preferences, overlay) { count, prefs, o ->
        ArchiveState(count, prefs.autoArchive, prefs.cardLayout, o.menu, o.undo, o.toast, o.tearing, o.flyBack)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveState())

    /**
     * Archived items, newest archived first, under "This month" / "Earlier", filtered by the search text. No setting
     * changes these rows, so the list reloads only for the search text (and for item changes).
     */
    val rows: Flow<PagingData<ArchiveRowUi>> = searchText.flatMapLatest { text ->
        val tokens = SearchMatcher.tokens(text)
        items.observeArchive(text).map { page -> page.map { rowUi(it, tokens) } }
    }.cachedIn(viewModelScope)

    private fun rowUi(row: ShelfRow, tokens: List<String>): ArchiveRowUi = when (row) {
        is ShelfRow.Header -> ArchiveRowUi.Header(row.label)
        is ShelfRow.Card -> {
            val item = row.item
            val card = item.toCardUi(clock.now(), zones.zone())
            ArchiveRowUi.Card(
                card,
                // Fixed for archived items whatever the user's shelf swipe settings: start deletes, end restores.
                startAction = SwipeActionResolver.resolve(item, SwipeDirection.Start, UserPreferences()),
                endAction = SwipeActionResolver.resolve(item, SwipeDirection.End, UserPreferences()),
                // A failed or untitled item shows its URL; the match ranges belong to the stored title.
                titleHighlights = SearchMatcher.match(tokens, item.title, item.siteName)?.first
                    ?.takeIf { card.title == item.title }.orEmpty(),
            )
        }
    }

    fun onIntent(intent: ArchiveIntent) {
        when (intent) {
            is ArchiveIntent.QueryChanged -> {
                query = intent.query
                typed.value = intent.query
            }
            ArchiveIntent.DismissMenu -> overlay.update { it.copy(menu = null) }
            is ArchiveIntent.UndoClosed -> overlay.update { if (it.undo?.opId == intent.opId) it.copy(undo = null) else it }
            else -> viewModelScope.launch { handle(intent) }
        }
    }

    private suspend fun handle(intent: ArchiveIntent) {
        when (intent) {
            is ArchiveIntent.Open -> open(intent.id)
            is ArchiveIntent.CardAction -> act(intent.id, intent.outcome)
            is ArchiveIntent.RetryMetadata -> dispatch(Command.RetryMetadata(intent.id))
            is ArchiveIntent.ShowMenu -> items.get(intent.id)?.let { item ->
                overlay.update { it.copy(menu = item.toCardUi(clock.now(), zones.zone())) }
            }
            is ArchiveIntent.MenuAction -> menuAction(intent.action)
            is ArchiveIntent.Torn -> torn(intent.id)
            ArchiveIntent.Undo -> {
                val pending = overlay.value.undo ?: return
                overlay.update { it.copy(undo = null) }
                dispatch(Command.Undo(pending.opId)) ?: return
                // The card comes back with the fly-back (handoff); a torn card must not tear again.
                val id = pending.itemId.value
                overlay.update { it.copy(flyBack = id, tearing = it.tearing.takeUnless { t -> t == id }) }
                delay(FLY_BACK_MILLIS)
                overlay.update { if (it.flyBack == id) it.copy(flyBack = null) else it }
            }
            is ArchiveIntent.BrowserOpened ->
                toast(text(if (intent.opened) Res.string.toast_opened_in_browser else Res.string.search_open_link_failed))
            ArchiveIntent.Back -> effectChannel.send(ArchiveEffect.Back)
            is ArchiveIntent.QueryChanged, ArchiveIntent.DismissMenu, is ArchiveIntent.UndoClosed -> Unit
        }
    }

    private suspend fun menuAction(action: ItemMenuAction) {
        val card = overlay.value.menu ?: return
        overlay.update { it.copy(menu = null) }
        val id = ItemId(card.id)
        when (action) {
            ItemMenuAction.Open -> open(id)
            ItemMenuAction.CopyLink -> items.get(id)?.let { item ->
                effectChannel.send(ArchiveEffect.Copy(item.url))
                toast(text(Res.string.toast_link_copied))
            }
            ItemMenuAction.Share -> act(id, SwipeOutcome.Share)
            ItemMenuAction.Restore -> act(id, SwipeOutcome.Restore)
            ItemMenuAction.Delete -> tear(id)
            // Shelf-only rows: the archived menu never offers them.
            ItemMenuAction.MarkRead, ItemMenuAction.MarkUnread, ItemMenuAction.Archive, ItemMenuAction.Remove -> Unit
        }
    }

    private suspend fun act(id: ItemId, outcome: SwipeOutcome) {
        when (outcome) {
            SwipeOutcome.Restore -> undoable(Command.Restore(id), OperationType.Restore, id)
            SwipeOutcome.Delete -> undoable(Command.Delete(id), OperationType.Delete, id)
            SwipeOutcome.Share -> items.get(id)?.let { effectChannel.send(ArchiveEffect.Share(it.url, it.title)) }
            // Shelf-only outcomes: archived cards never offer them.
            SwipeOutcome.MarkRead, SwipeOutcome.MarkUnread, SwipeOutcome.Archive, SwipeOutcome.Remove, SwipeOutcome.RubberBand -> Unit
        }
    }

    /** Dispatches [Command.OpenItem] (an archived item can be opened, D21), then opens the reader or the browser. */
    private suspend fun open(id: ItemId) {
        val item = items.get(id) ?: return
        val result = bus.dispatch(Command.OpenItem(id))
        // A storage failure only lost the read mark, so the page still opens; any other refusal means the item is gone.
        if (result is DispatchResult.Rejected && result.rejection != Rejection.StorageFailure) return
        val inBrowser = settings.preferences.first().openLinksIn == OpenLinksIn.Browser
        val markedRead = (result as? DispatchResult.Done)?.affected == 1
        effectChannel.send(if (inBrowser) ArchiveEffect.OpenInBrowser(item.url) else ArchiveEffect.OpenReader(id, markedRead))
        (result as? DispatchResult.Done)?.celebration?.let { effectChannel.send(ArchiveEffect.Celebrate(it)) }
    }

    private suspend fun undoable(command: Command, type: OperationType, id: ItemId): Boolean {
        val done = dispatch(command) ?: return false
        done.undoOpId?.let { opId -> overlay.update { it.copy(undo = PendingUndo(opId, type, id)) } }
        return true
    }

    /** Delete from the menu: the card tears first (handoff) and reports [ArchiveIntent.Torn]; the timer covers a card that left the screen. */
    private fun tear(id: ItemId) {
        tearPending = id
        overlay.update { it.copy(tearing = id.value) }
        viewModelScope.launch {
            delay(TEAR_FALLBACK_MILLIS)
            torn(id)
        }
    }

    /** Sends the Delete of a torn card once; a refused one shows the card in place again. */
    private suspend fun torn(id: ItemId) {
        if (tearPending != id) return
        tearPending = null
        val deleted = undoable(Command.Delete(id), OperationType.Delete, id)
        if (deleted) delay(TEAR_CLEAR_MILLIS)
        // Refused: the card shows in place again. Done: the row has left the list by now, and an item that comes back
        // some other way must not tear again.
        overlay.update { if (it.tearing == id.value) it.copy(tearing = null) else it }
    }

    /** The result when it went through; a storage failure tells the user, other refusals leave the list as it is. */
    private suspend fun dispatch(command: Command): DispatchResult.Done? = when (val result = bus.dispatch(command)) {
        is DispatchResult.Done -> result
        is DispatchResult.Rejected -> {
            if (result.rejection == Rejection.StorageFailure) toast(text(Res.string.search_change_failed))
            null
        }
    }

    private var toastJob: Job? = null

    private fun toast(message: UiText) {
        overlay.update { it.copy(toast = message) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MILLIS)
            overlay.update { it.copy(toast = null) }
        }
    }

    internal companion object {
        /**
         * Typing faster than one key per 300 ms runs one query for the word, not one per letter: a one-letter prefix
         * matches much of a 50,000-item library, and a running SQLite query is not cancelled with its flow.
         */
        const val DEBOUNCE_MILLIS = 300L
        const val TOAST_MILLIS = 1_600L

        /** How long a card brought back by Undo keeps its fly-back (prototype: 600 ms, longer than the 500 ms motion). */
        const val FLY_BACK_MILLIS = 600L

        /** Delete goes out by then even if the tearing card left the screen before it called back. */
        const val TEAR_FALLBACK_MILLIS = 1_000L
        const val TEAR_CLEAR_MILLIS = 1_000L
    }
}
