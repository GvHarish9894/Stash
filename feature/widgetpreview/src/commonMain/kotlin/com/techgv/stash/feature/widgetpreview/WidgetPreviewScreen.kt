package com.techgv.stash.feature.widgetpreview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.PrimaryButton
import com.techgv.stash.core.designsystem.component.ScreenHeader
import com.techgv.stash.core.designsystem.component.SegmentedControl
import com.techgv.stash.core.designsystem.component.ToastHost
import com.techgv.stash.core.designsystem.component.Txt
import com.techgv.stash.core.designsystem.component.WidgetPreview
import com.techgv.stash.core.designsystem.component.contentWidth
import com.techgv.stash.core.designsystem.component.enter
import com.techgv.stash.core.designsystem.component.navBarsPad
import com.techgv.stash.core.designsystem.component.statusBarsPad
import com.techgv.stash.core.designsystem.presentation.label
import com.techgv.stash.core.designsystem.presentation.resolve
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.model.WidgetSize
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.common_back_to_settings
import com.techgv.stash.core.resources.settings_widget
import com.techgv.stash.core.resources.widget_add
import com.techgv.stash.core.resources.widget_steps_body
import com.techgv.stash.core.resources.widget_steps_title
import org.jetbrains.compose.resources.stringResource

/**
 * The live widget on a wallpaper, the size control and "Add to home screen". When [canPinWidget] is false (iOS, or a
 * launcher that cannot pin) the button shows how to add the widget from the home screen instead. Expanded width puts
 * the controls beside the preview.
 */
@Composable
fun WidgetPreviewScreen(
    state: WidgetPreviewState,
    onIntent: (WidgetPreviewIntent) -> Unit,
    widthClass: WidthClass,
    canPinWidget: Boolean,
    modifier: Modifier = Modifier,
) {
    val expanded = widthClass == WidthClass.Expanded
    // Opening the screen moves focus to its back button (the first focusable child of the header).
    val headerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { headerFocus.requestFocus() }
    Box(modifier.fillMaxSize().background(Stash.colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPad().verticalScroll(rememberScrollState()).navBarsPad()) {
            ScreenHeader(
                stringResource(Res.string.settings_widget), stringResource(Res.string.common_back_to_settings),
                { onIntent(WidgetPreviewIntent.Back) }, (if (expanded) Modifier else Modifier.contentWidth()).focusRequester(headerFocus),
            )
            if (state.loaded) {
                val preview = @Composable { m: Modifier ->
                    // A preview only: tapping a row does nothing here, the real widget opens the reader.
                    WidgetPreview(state.size, state.appIcon, state.streak, state.streakStatus, state.unreadCount, state.items, onOpen = {}, modifier = m)
                }
                if (expanded) {
                    Row(Modifier.padding(horizontal = Stash.spacing.xl), horizontalArrangement = Arrangement.spacedBy(Stash.spacing.lg)) {
                        preview(Modifier.weight(1f).padding(top = Stash.spacing.xs))
                        Controls(state, onIntent, canPinWidget, Modifier.width(ControlsWidth).padding(top = Stash.spacing.xs))
                    }
                } else {
                    Column(Modifier.contentWidth()) {
                        preview(Modifier.padding(start = Stash.spacing.md, end = Stash.spacing.md, top = Stash.spacing.xs))
                        Controls(
                            state, onIntent, canPinWidget,
                            Modifier.padding(start = Stash.spacing.xl, end = Stash.spacing.xl, top = Stash.spacing.md, bottom = 24.dp),
                        )
                    }
                }
            }
        }
        ToastHost(state.toast?.resolve().orEmpty(), 32.dp, width = 240.dp)
    }
}

private val ControlsWidth = 360.dp

@Composable
private fun Controls(state: WidgetPreviewState, onIntent: (WidgetPreviewIntent) -> Unit, canPinWidget: Boolean, modifier: Modifier) {
    val c = Stash.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Stash.spacing.sm)) {
        SegmentedControl(
            WidgetSize.entries, state.size, { stringResource(it.label) }, { onIntent(WidgetPreviewIntent.SetSize(it)) },
            Modifier.fillMaxWidth(), fill = true,
        )
        PrimaryButton(stringResource(Res.string.widget_add), { onIntent(WidgetPreviewIntent.AddToHomeScreen(canPinWidget)) }, Modifier.fillMaxWidth())
        AnimatedVisibility(state.showSteps, enter = enter(fadeIn(tween(StashMotion.TOAST)))) {
            Column(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(Stash.spacing.xxs),
                horizontalAlignment = Alignment.Start,
            ) {
                Txt(stringResource(Res.string.widget_steps_title), Stash.type.bodyStrong, c.ink, Modifier.semantics { heading() })
                Txt(stringResource(Res.string.widget_steps_body), Stash.type.bodySmall, c.inkFaded)
            }
        }
    }
}
