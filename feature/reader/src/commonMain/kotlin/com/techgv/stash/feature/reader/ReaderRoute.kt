package com.techgv.stash.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.model.ItemId
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The reader destination. [itemId] null draws the empty detail pane (expanded windows before a pick). [markedRead] is
 * whether the opener's `OpenItem` marked the item read (`DispatchResult.Done.affected == 1`). [onClose] gets [from] so
 * the host returns to the opener; [onOpenInBrowser] opens an http(s) URL with `BrowserLauncher`. [readerMode] is the
 * mode to start in, null for the setting's default; [onReaderModeChange] reports the mode once known and on every
 * change, so a host that moves the item to another reader (a resize) can carry it over.
 */
@Composable
fun ReaderRoute(
    itemId: ItemId?,
    from: ReaderOrigin,
    markedRead: Boolean,
    widthClass: WidthClass,
    onClose: (ReaderOrigin) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
    readerMode: Boolean? = null,
    onReaderModeChange: (Boolean) -> Unit = {},
) {
    if (itemId == null) {
        ReaderEmptyPane(modifier)
        return
    }
    // Keyed by item: in the detail pane one back-stack entry shows one item after another.
    val viewModel = koinViewModel<ReaderViewModel>(key = "reader:${itemId.value}") { parametersOf(itemId, markedRead, readerMode) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modeChanged by rememberUpdatedState(onReaderModeChange)
    LaunchedEffect(state.readerMode) { state.readerMode?.let(modeChanged) }
    val close by rememberUpdatedState { onClose(from) }
    val openInBrowser by rememberUpdatedState(onOpenInBrowser)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ReaderEffect.Close -> close()
                is ReaderEffect.OpenInBrowser -> openInBrowser(effect.url)
            }
        }
    }
    ReaderScreen(state, widthClass, viewModel::onIntent, modifier)
}
