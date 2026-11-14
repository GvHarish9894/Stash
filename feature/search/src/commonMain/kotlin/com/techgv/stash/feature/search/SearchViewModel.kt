package com.techgv.stash.feature.search

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
import com.techgv.stash.core.domain.RecentSearchRepository
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.SearchChips
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.SearchHit
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.OperationType
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val items: ItemRepository,
    private val bus: CommandBus,
    private val settings: SettingsRepository,
    private val recentSearches: RecentSearchRepository,
    private val clock: Clock,
    private val zones: ZoneProvider,
) : ViewModel() {
    private val effectChannel = Channel<SearchEffect>(Channel.BUFFERED)
    val effects: Flow<SearchEffect> = effectChannel.receiveAsFlow()

    /** The field text. Compose state, set synchronously, so the text field never waits on a flow while the user types. */
    var query by mutableStateOf("")
        private set

    private val typed = MutableStateFlow("")
    private val filter = MutableStateFlow<SearchFilter>(SearchFilter.All)
    private val overlay = MutableStateFlow(Overlay())

    private data class Overlay(
        val menu: SavedItemCardUi? = null,
        val undo: PendingUndo? = null,
        val toast: UiText? = null,
        val flyBack: String? = null,
    )

    /** The trimmed query once typing pauses; clearing the field applies at once. */
    private val searchText: StateFlow<String> = typed.map { it.trim() }.distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MILLIS }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val chips: Flow<Pair<String, SearchChips>?> = searchText.flatMapLatest { text ->
        if (text.isEmpty()) flowOf(null) else items.observeSearchChips(text).map { text to it }
    }

    val state: StateFlow<SearchState> =
        combine(recentSearches.observe(), chips, filter, overlay) { recents, found, selected, o ->
            SearchState(
                recents = recents.map { it.query },
                results = found?.let { (text, chips) -> results(text, chips, selected) },
                menu = o.menu,
                undo = o.undo,
                toast = o.toast,
                flyBackItemId = o.flyBack,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    /** Shelf hits, newest first, for the applied chip. */
    val shelfResults: Flow<PagingData<SearchResultUi>> = pagedHits(items::observeShelfSearch)

    /** Archive hits, shown after the shelf hits in the same list. */
    val archiveResults: Flow<PagingData<SearchResultUi>> = pagedHits(items::observeArchiveSearch)

    private fun pagedHits(source: (String, SearchFilter) -> Flow<PagingData<SearchHit>>): Flow<PagingData<SearchResultUi>> =
        combine(searchText, filter, ::Pair).flatMapLatest { (text, selected) ->
            if (text.isEmpty()) flowOf(PagingData.empty()) else source(text, selected).map { page -> page.map(::resultUi) }
        }.cachedIn(viewModelScope)

    private fun resultUi(hit: SearchHit): SearchResultUi {
        val card = hit.item.toCardUi(clock.now(), zones.zone())
        // A failed or untitled item shows its URL; the match ranges belong to the stored title and site name.
        return SearchResultUi(
            card,
            hit.titleMatches.takeIf { card.title == hit.item.title }.orEmpty(),
            hit.siteMatches.takeIf { card.siteName == hit.item.siteName }.orEmpty(),
        )
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                query = intent.query
                typed.value = intent.query
                filter.value = SearchFilter.All
            }
            is SearchIntent.SelectFilter -> filter.value = intent.filter
            SearchIntent.DismissMenu -> overlay.update { it.copy(menu = null) }
            is SearchIntent.UndoClosed -> overlay.update { if (it.undo?.opId == intent.opId) it.copy(undo = null) else it }
            else -> viewModelScope.launch { handle(intent) }
        }
    }

    private suspend fun handle(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Open -> open(intent.id)
            is SearchIntent.CardAction -> act(intent.id, intent.outcome)
            is SearchIntent.RetryMetadata -> dispatch(Command.RetryMetadata(intent.id))
            is SearchIntent.ShowMenu -> items.get(intent.id)?.let { item ->
                overlay.update { it.copy(menu = item.toCardUi(clock.now(), zones.zone())) }
            }
            is SearchIntent.MenuAction -> menuAction(intent.action)
            SearchIntent.Undo -> {
                val pending = overlay.value.undo ?: return
                overlay.update { it.copy(undo = null) }
                dispatch(Command.Undo(pending.opId)) ?: return
                // The result comes back with the fly-back (handoff).
                val id = pending.itemId.value
                overlay.update { it.copy(flyBack = id) }
                delay(FLY_BACK_MILLIS)
                overlay.update { if (it.flyBack == id) it.copy(flyBack = null) else it }
            }
            is SearchIntent.BrowserOpened ->
                toast(text(if (intent.opened) Res.string.toast_opened_in_browser else Res.string.search_open_link_failed))
            SearchIntent.Back -> {
                saveRecent()
                effectChannel.send(SearchEffect.Back)
            }
            is SearchIntent.QueryChanged, is SearchIntent.SelectFilter, SearchIntent.DismissMenu, is SearchIntent.UndoClosed -> Unit
        }
    }

    private suspend fun menuAction(action: ItemMenuAction) {
        val card = overlay.value.menu ?: return
        overlay.update { it.copy(menu = null) }
        val id = ItemId(card.id)
        when (action) {
            ItemMenuAction.Open -> open(id)
            ItemMenuAction.CopyLink -> items.get(id)?.let { item ->
                effectChannel.send(SearchEffect.Copy(item.url))
                toast(text(Res.string.toast_link_copied))
            }
            ItemMenuAction.MarkRead -> act(id, SwipeOutcome.MarkRead)
            ItemMenuAction.MarkUnread -> act(id, SwipeOutcome.MarkUnread)
            ItemMenuAction.Share -> act(id, SwipeOutcome.Share)
            ItemMenuAction.Archive -> act(id, SwipeOutcome.Archive)
            ItemMenuAction.Remove -> act(id, SwipeOutcome.Remove)
            ItemMenuAction.Restore -> act(id, SwipeOutcome.Restore)
            ItemMenuAction.Delete -> act(id, SwipeOutcome.Delete)
        }
    }

    private suspend fun act(id: ItemId, outcome: SwipeOutcome) {
        when (outcome) {
            SwipeOutcome.MarkRead -> dispatch(Command.MarkRead(id))?.celebration?.let { effectChannel.send(SearchEffect.Celebrate(it)) }
            SwipeOutcome.MarkUnread -> dispatch(Command.MarkUnread(id))
            SwipeOutcome.Archive -> undoable(Command.Archive(id), OperationType.Archive, id)
            SwipeOutcome.Remove -> undoable(Command.Remove(id), OperationType.Remove, id)
            SwipeOutcome.Restore -> undoable(Command.Restore(id), OperationType.Restore, id)
            SwipeOutcome.Delete -> undoable(Command.Delete(id), OperationType.Delete, id)
            SwipeOutcome.Share -> items.get(id)?.let { effectChannel.send(SearchEffect.Share(it.url, it.title)) }
            SwipeOutcome.RubberBand -> Unit
        }
    }

    /** Saves the query to recents, dispatches [Command.OpenItem], then opens the reader or the browser per the setting. */
    private suspend fun open(id: ItemId) {
        saveRecent()
        val item = items.get(id) ?: return
        val result = bus.dispatch(Command.OpenItem(id))
        // A storage failure only lost the read mark, so the page still opens; any other refusal means the item is gone.
        if (result is DispatchResult.Rejected && result.rejection != Rejection.StorageFailure) return
        val inBrowser = settings.preferences.first().openLinksIn == OpenLinksIn.Browser
        val markedRead = (result as? DispatchResult.Done)?.affected == 1
        effectChannel.send(if (inBrowser) SearchEffect.OpenInBrowser(item.url) else SearchEffect.OpenReader(id, markedRead))
        (result as? DispatchResult.Done)?.celebration?.let { effectChannel.send(SearchEffect.Celebrate(it)) }
    }

    private suspend fun saveRecent() {
        if (query.isNotBlank()) recentSearches.add(query)
    }

    private suspend fun undoable(command: Command, type: OperationType, id: ItemId) {
        dispatch(command)?.undoOpId?.let { opId -> overlay.update { it.copy(undo = PendingUndo(opId, type, id)) } }
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

    private fun results(text: String, chips: SearchChips, selected: SearchFilter) = SearchResults(
        text = text,
        total = chips.all,
        chips = listOf(SearchChipUi(SearchFilter.All, chips.all)) +
            chips.sources.map { SearchChipUi(SearchFilter.Source(it.key), it.count) } +
            listOfNotNull(chips.archived?.let { SearchChipUi(SearchFilter.Archived, it) }),
        filter = ShelfQuery.effectiveSearchFilter(selected, chips),
        archived = chips.archived,
    )

    internal companion object {
        /**
         * Typing faster than one key per 300 ms runs one query for the word, not one per letter: a one-letter prefix
         * matches much of a 50,000-item library, and a running SQLite query is not cancelled with its flow.
         */
        const val DEBOUNCE_MILLIS = 300L
        const val TOAST_MILLIS = 1_600L

        /** How long a result brought back by Undo keeps its fly-back (prototype: 600 ms, longer than the 500 ms motion). */
        const val FLY_BACK_MILLIS = 600L
    }
}
