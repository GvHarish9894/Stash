package com.techgv.stash.feature.widgetpreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.model.WidgetSize
import org.koin.compose.viewmodel.koinViewModel

/**
 * The widget preview destination. [canPinWidget] is `WidgetPinner.canPin`; [pinWidget] is `WidgetPinner.requestPin` and
 * returns whether the system dialog was shown. [onBack] returns to Settings.
 */
@Composable
fun WidgetPreviewRoute(
    widthClass: WidthClass,
    canPinWidget: Boolean,
    onBack: () -> Unit,
    pinWidget: (WidgetSize) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<WidgetPreviewViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pin by rememberUpdatedState(pinWidget)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WidgetPreviewEffect.Back -> onBack()
                is WidgetPreviewEffect.PinWidget -> viewModel.onIntent(WidgetPreviewIntent.PinResult(pin(effect.size)))
            }
        }
    }
    WidgetPreviewScreen(state, viewModel::onIntent, widthClass, canPinWidget, modifier)
}
