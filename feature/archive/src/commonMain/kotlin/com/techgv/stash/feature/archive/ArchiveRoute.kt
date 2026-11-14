package com.techgv.stash.feature.archive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.ItemId
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration

/**
 * The archive destination.
 * - [onOpenReader]: open the reader on the item; the reader returns to the Archive.
 * - [onOpenInBrowser]: `BrowserLauncher.open(url, …)`; return its result (false shows "Couldn't open this link").
 * - [onShare]: `ShareSheet.share(url, title)`. [onCopy]: `Clipboard.copy(text)`.
 * - [onCelebrate]: show the celebration for the first read of the day.
 * - [onBack]: leave the Archive (the back button; system back is the navigation host's unless a sheet is open).
 * - [undoTimeout]: asked each time an undo bar appears, e.g. `{ a11yPrefs.recommendedTimeout(DefaultUndoTimeout) }`.
 */
@Composable
fun ArchiveRoute(
    widthClass: WidthClass,
    undoTimeout: () -> Duration,
    onOpenReader: (id: ItemId, markedRead: Boolean) -> Unit,
    onOpenInBrowser: (url: String) -> Boolean,
    onShare: (url: String, title: String) -> Unit,
    onCopy: (text: String) -> Unit,
    onCelebrate: (Celebration) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ArchiveViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val openReader by rememberUpdatedState(onOpenReader)
    val openInBrowser by rememberUpdatedState(onOpenInBrowser)
    val share by rememberUpdatedState(onShare)
    val copy by rememberUpdatedState(onCopy)
    val celebrate by rememberUpdatedState(onCelebrate)
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ArchiveEffect.OpenReader -> openReader(effect.id, effect.markedRead)
                is ArchiveEffect.OpenInBrowser -> viewModel.onIntent(ArchiveIntent.BrowserOpened(openInBrowser(effect.url)))
                is ArchiveEffect.Share -> share(effect.url, effect.title)
                is ArchiveEffect.Copy -> copy(effect.text)
                is ArchiveEffect.Celebrate -> celebrate(effect.celebration)
                ArchiveEffect.Back -> back()
            }
        }
    }
    ArchiveScreen(state, viewModel.query, rows, widthClass, undoTimeout, viewModel::onIntent)
}
