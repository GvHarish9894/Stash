package com.techgv.stash.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techgv.stash.core.designsystem.theme.READER_TEXT_STEPS
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.ReaderRepository
import com.techgv.stash.core.domain.ReaderResult
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The reader for item [id]. The opener has already dispatched `OpenItem`; [markedRead] says whether that open marked
 * the item read (`DispatchResult.Done.affected == 1`), which shows "Marked as read" once. [readerMode] is the mode to
 * start in when the item moves to another reader (a resize), null for the setting's default.
 */
class ReaderViewModel(
    private val id: ItemId,
    markedRead: Boolean,
    readerMode: Boolean?,
    items: ItemRepository,
    private val reader: ReaderRepository,
    private val bus: CommandBus,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ReaderEffect>(Channel.BUFFERED)
    val effects: Flow<ReaderEffect> = effectChannel.receiveAsFlow()

    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            items.observe(id).collect { item ->
                val gone = item == null || item.lifecycle == ItemLifecycle.Deleted
                mutableState.update { it.copy(item = item.takeUnless { gone }, missing = gone) }
            }
        }
        viewModelScope.launch {
            settings.preferences.map { it.readerTextStep }.distinctUntilChanged().collect { step ->
                mutableState.update { it.copy(textStep = step) }
            }
        }
        viewModelScope.launch {
            // Only the initial mode follows the setting; the Aa toggle then belongs to this screen.
            if (readerMode ?: settings.preferences.first().readerModeDefault) enterReaderMode() else mutableState.update { it.copy(readerMode = false) }
        }
        if (markedRead) showToast(ReaderToast.MarkedRead)
    }

    fun onIntent(intent: ReaderIntent) {
        when (intent) {
            ReaderIntent.Close -> send(ReaderEffect.Close)
            ReaderIntent.ToggleReaderMode ->
                if (state.value.readerMode == true) {
                    mutableState.update { it.copy(readerMode = false, webProgress = 0f, webFailed = false) }
                } else {
                    enterReaderMode()
                }
            ReaderIntent.MarkUnread -> dispatch(Command.MarkUnread(id), ReaderToast.BackOnUnread)
            is ReaderIntent.ChangeTextStep -> {
                val step = intent.step.coerceIn(0, READER_TEXT_STEPS - 1)
                if (step != state.value.textStep) dispatch(Command.ChangeSetting { it.copy(readerTextStep = step) }, null)
            }
            ReaderIntent.RetryArticle -> if (state.value.article == ArticleState.Offline) loadArticle()
            ReaderIntent.RetryWeb -> mutableState.update { it.copy(webFailed = false, webProgress = 0f) }
            ReaderIntent.OpenInBrowser -> state.value.item?.let { send(ReaderEffect.OpenInBrowser(it.url)) }
            is ReaderIntent.OpenLink -> browserLink(intent.url)?.let { send(ReaderEffect.OpenInBrowser(it)) }
            is ReaderIntent.Web -> when (val event = intent.event) {
                is WebEvent.Progress -> mutableState.update { if (it.webFailed) it else it.copy(webProgress = event.fraction) }
                WebEvent.Failed -> mutableState.update { it.copy(webFailed = true) }
            }
        }
    }

    private fun enterReaderMode() {
        when (state.value.article) {
            ArticleState.Unavailable -> {
                mutableState.update { it.copy(readerMode = false) }
                showToast(ReaderToast.ReaderUnavailable)
            }
            ArticleState.Idle, ArticleState.Offline -> {
                mutableState.update { it.copy(readerMode = true) }
                loadArticle()
            }
            ArticleState.Loading, is ArticleState.Ready -> mutableState.update { it.copy(readerMode = true) }
        }
    }

    private fun loadArticle() {
        mutableState.update { it.copy(article = ArticleState.Loading) }
        viewModelScope.launch {
            val article = when (val result = reader.article(id)) {
                is ReaderResult.Ready -> ArticleState.Ready(result.article)
                ReaderResult.Unavailable -> ArticleState.Unavailable
                ReaderResult.Offline -> ArticleState.Offline
            }
            val fallBack = article == ArticleState.Unavailable && state.value.readerMode == true
            mutableState.update { it.copy(article = article, readerMode = if (fallBack) false else it.readerMode) }
            if (fallBack) showToast(ReaderToast.ReaderUnavailable)
        }
    }

    /** Dispatches [command]; shows [done] when it applied and the failure toast when it was rejected. */
    private fun dispatch(command: Command, done: ReaderToast?) {
        viewModelScope.launch {
            when (bus.dispatch(command)) {
                is DispatchResult.Done -> done?.let(::showToast)
                is DispatchResult.Rejected -> showToast(ReaderToast.ActionFailed)
            }
        }
    }

    private fun showToast(toast: ReaderToast) {
        toastJob?.cancel()
        mutableState.update { it.copy(toast = toast) }
        toastJob = viewModelScope.launch {
            delay(toast.millis)
            mutableState.update { it.copy(toast = null) }
        }
    }

    private fun send(effect: ReaderEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }
}

/** How long each toast stays (prototype: 2.2 s after opening, 1.8 s after Mark unread). */
internal val ReaderToast.millis: Long
    get() = when (this) {
        ReaderToast.MarkedRead -> 2_200L
        ReaderToast.BackOnUnread -> 1_800L
        ReaderToast.ReaderUnavailable -> 3_000L
        ReaderToast.ActionFailed -> 2_200L
    }
