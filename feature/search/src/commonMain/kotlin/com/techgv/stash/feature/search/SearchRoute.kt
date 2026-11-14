package com.techgv.stash.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.ItemId
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration

/**
 * The search destination.
 * - [onOpenReader]: open the reader on the item; the reader returns to Search.
 * - [onOpenInBrowser]: `BrowserLauncher.open(url, …)`; return its result (false shows "Couldn't open this link").
 * - [onShare]: `ShareSheet.share(url, title)`. [onCopy]: `Clipboard.copy(text)`.
 * - [onCelebrate]: show the celebration for the first read of the day.
 * - [onBack]: leave Search (the query is already saved to recents).
 * - [undoTimeout]: asked each time an undo bar appears, e.g. `{ a11yPrefs.recommendedTimeout(DefaultUndoTimeout) }`.
 */
@Composable
fun SearchRoute(
    widthClass: WidthClass,
    undoTimeout: () -> Duration,
    onOpenReader: (id: ItemId, markedRead: Boolean) -> Unit,
    onOpenInBrowser: (url: String) -> Boolean,
    onShare: (url: String, title: String) -> Unit,
    onCopy: (text: String) -> Unit,
    onCelebrate: (Celebration) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<SearchViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shelf = viewModel.shelfResults.collectAsLazyPagingItems()
    val archive = viewModel.archiveResults.collectAsLazyPagingItems()
    val openReader by rememberUpdatedState(onOpenReader)
    val openInBrowser by rememberUpdatedState(onOpenInBrowser)
    val share by rememberUpdatedState(onShare)
    val copy by rememberUpdatedState(onCopy)
    val celebrate by rememberUpdatedState(onCelebrate)
    val back by rememberUpdatedState(onBack)
    // The field takes focus on entry and after a recreation, but not on the way back from the reader: the keyboard it
    // would open again takes the first Back for itself, so leaving Search needed two (U-37).
    var leftForReader by rememberSaveable { mutableStateOf(false) }
    val autofocus = remember { !leftForReader }
    LaunchedEffect(Unit) { leftForReader = false }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.OpenReader -> {
                    leftForReader = true
                    openReader(effect.id, effect.markedRead)
                }
                is SearchEffect.OpenInBrowser -> viewModel.onIntent(SearchIntent.BrowserOpened(openInBrowser(effect.url)))
                is SearchEffect.Share -> share(effect.url, effect.title)
                is SearchEffect.Copy -> copy(effect.text)
                is SearchEffect.Celebrate -> celebrate(effect.celebration)
                SearchEffect.Back -> back()
            }
        }
    }
    SearchScreen(state, viewModel.query, shelf, archive, widthClass, undoTimeout, viewModel::onIntent, autofocus = autofocus)
}
