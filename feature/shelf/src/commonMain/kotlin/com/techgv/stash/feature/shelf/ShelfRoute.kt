package com.techgv.stash.feature.shelf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.designsystem.perform
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.ItemId
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration

/**
 * Home, as `shared` places it. Navigation and capability work arrive as callbacks:
 * - [onOpenInBrowser] opens the page and returns whether a browser showed it;
 * - [peekClipboard] says what the add-link sheet may suggest without a paste prompt; [readClipboard] reads the link
 *   after the user tapped Use on a hidden one (null when declined or not a link);
 * - [onCelebrate] hands over the first read of the day, for the streak feature's celebration;
 * - [undoTimeout] is asked each time an undo bar appears (10 s default, `Duration.INFINITE` under a screen reader);
 * - [refreshFailedMetadata] runs on pull-to-refresh;
 * - [detailPane], on expanded windows, is drawn beside the shelf pane, under the shelf's sheets; [selectedItemId] is the
 *   item open in it;
 * - [settleItemId] is the item whose reader just closed, for the card's settle.
 *
 * The callbacks are captured when the screen starts collecting effects; pass ones that stay valid (navigation, capabilities).
 */
@Composable
fun ShelfRoute(
    widthClass: WidthClass,
    undoTimeout: () -> Duration,
    refreshFailedMetadata: suspend () -> Unit,
    onOpenReader: (id: ItemId, markedRead: Boolean) -> Unit,
    onOpenInBrowser: (url: String) -> Boolean,
    onShare: (url: String, title: String) -> Unit,
    onCopy: (text: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStreak: () -> Unit,
    peekClipboard: suspend () -> ClipboardSuggestion?,
    readClipboard: suspend () -> String?,
    onCelebrate: (Celebration) -> Unit,
    modifier: Modifier = Modifier,
    detailPane: (@Composable () -> Unit)? = null,
    selectedItemId: ItemId? = null,
    settleItemId: ItemId? = null,
) {
    val viewModel = koinViewModel<ShelfViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ShelfEffect.OpenReader -> onOpenReader(effect.id, effect.markedRead)
                is ShelfEffect.OpenInBrowser -> viewModel.onIntent(ShelfIntent.BrowserOpened(onOpenInBrowser(effect.url)))
                is ShelfEffect.Share -> onShare(effect.url, effect.title)
                is ShelfEffect.Copy -> onCopy(effect.text)
                ShelfEffect.OpenSearch -> onOpenSearch()
                ShelfEffect.OpenArchive -> onOpenArchive()
                ShelfEffect.OpenSettings -> onOpenSettings()
                ShelfEffect.OpenStreak -> onOpenStreak()
                ShelfEffect.PeekClipboard -> viewModel.onIntent(ShelfIntent.ClipboardPeeked(peekClipboard()))
                ShelfEffect.ReadClipboard -> viewModel.onIntent(ShelfIntent.ClipboardRead(readClipboard()))
                is ShelfEffect.Celebrate -> onCelebrate(effect.celebration)
                is ShelfEffect.Haptic -> haptics.perform(effect.kind)
            }
        }
    }
    ShelfScreen(state, rows, widthClass, undoTimeout, refreshFailedMetadata, viewModel::onIntent, modifier, detailPane, selectedItemId, settleItemId)
}
