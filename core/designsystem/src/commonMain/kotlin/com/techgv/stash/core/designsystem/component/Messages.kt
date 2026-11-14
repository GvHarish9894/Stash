package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.common_dismiss
import com.techgv.stash.core.resources.common_later
import com.techgv.stash.core.resources.common_read_now
import com.techgv.stash.core.resources.common_undo
import com.techgv.stash.core.resources.notification_preview_header
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Dark pill toast, announced politely. */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier, width: Dp = 220.dp) {
    val c = Stash.colors
    Box(
        modifier.width(width).heightIn(min = 44.dp).clip(CircleShape).background(c.toast).padding(horizontal = Stash.spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) { Txt(text, Stash.type.bodySmallStrong, c.onToast, align = TextAlign.Center) }
}

/** Toast slot at the bottom: slides up and fades, keeping the last text while it leaves. Empty [text] hides it. */
@Composable
fun BoxScope.ToastHost(text: String, bottom: Dp, width: Dp = 220.dp) {
    val last = rememberLast(text.ifEmpty { null })
    AnimatedVisibility(
        text.isNotEmpty(),
        Modifier.align(Alignment.BottomCenter).navBarsPad().padding(bottom = bottom),
        enter = enter(slideInVertically(StashMotion.enter(StashMotion.TOAST)) { it / 2 } + fadeIn(tween(StashMotion.TOAST))),
        exit = exit(fadeOut(StashMotion.exit(StashMotion.TOAST))),
    ) { Toast(last.orEmpty(), width = width) }
}

/** The handoff's default undo time; pass the platform's recommended timeout, or [Duration.INFINITE] for a screen reader. */
val DefaultUndoTimeout: Duration = 10.seconds

/**
 * Undo bar: min 56 dp, toast colours, Undo and × buttons, and a 3 dp line counting down [timeout]. The bar owns the
 * countdown: it calls [onTimeout] when it runs out and pauses while it is pressed or holds focus. [Duration.INFINITE]
 * keeps it up until Undo or × (no line). Under Reduce motion the line stays still while the time runs.
 * Show a new message with a new `key { }` so the countdown restarts.
 */
@Composable
fun UndoSnackbar(
    message: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    timeout: Duration = DefaultUndoTimeout,
) {
    val c = Stash.colors
    val reduceMotion = Stash.reduceMotion
    val finite = timeout.isFinite()
    val total = if (finite) timeout.inWholeMilliseconds.coerceAtLeast(1) else Long.MAX_VALUE
    var remaining by remember(timeout) { mutableLongStateOf(total) }
    var pressed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val timeoutCallback by rememberUpdatedState(onTimeout)
    LaunchedEffect(timeout, pressed, focused) {
        if (!finite || pressed || focused) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (remaining > 0) {
            val now = withFrameMillis { it }
            remaining -= now - last
            last = now
        }
        timeoutCallback()
    }
    val dismiss = stringResource(Res.string.common_dismiss)
    val radius = Stash.radius.card
    val track = c.flame.copy(alpha = 0.25f)
    Box(
        modifier.heightIn(min = 56.dp)
            .graphicsLayer { shadowElevation = 20f; shape = RoundedCornerShape(radius); clip = true }
            .background(c.toast)
            // Pause while any finger is down on the bar; the event still reaches the buttons.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pressed = event.changes.any { it.pressed }
                    }
                }
            }
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .drawBehind {
                if (!finite) return@drawBehind
                val h = 3.dp.toPx()
                val top = size.height - h
                drawRect(track, Offset(0f, top), Size(size.width, h))
                val fraction = if (reduceMotion) 1f else (remaining.toFloat() / total).coerceIn(0f, 1f)
                drawRect(c.toastAction, Offset(0f, top), Size(size.width * fraction, h))
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = Stash.spacing.xs, top = Stash.spacing.xs, bottom = Stash.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(message, Stash.type.callout, c.onToast, Modifier.weight(1f))
            Box(
                Modifier.heightIn(min = 44.dp).focusRing(RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).press(onUndo).padding(horizontal = Stash.spacing.md, vertical = Stash.spacing.xxs),
                contentAlignment = Alignment.Center,
            ) { Txt(stringResource(Res.string.common_undo), Stash.type.calloutStrong, c.toastAction) }
            Box(
                Modifier.size(36.dp).focusRing(CircleShape).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = dismiss },
                contentAlignment = Alignment.Center,
            ) { Icon(StashIcons.close(), null, Modifier.size(12.dp), tint = c.onToast) }
        }
    }
}

/** In-app preview of a reminder, drawn like a system notification and announced assertively. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderPreviewBanner(
    time: String,
    title: String,
    body: String,
    appIcon: AppIcon,
    onLater: () -> Unit,
    onReadNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Stash.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.graphicsLayer { shadowElevation = 30f; this.shape = shape; clip = true }
            .background(c.surface).border(1.dp, c.outline, shape)
            .semantics { liveRegion = LiveRegionMode.Assertive }
            .padding(start = Stash.spacing.sm, end = Stash.spacing.sm, top = Stash.spacing.sm, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Stash.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xs)) {
            AppIconTile(appIcon, 20.dp, 6.dp, shadow = false)
            Txt(stringResource(Res.string.notification_preview_header, time), Stash.type.meta, c.inkFaded)
        }
        Txt(title, Stash.type.emphasis, c.ink)
        Txt(body, Stash.type.bodySmall, c.inkFaded)
        // Wraps instead of squeezing a label until it breaks inside a word (very large text).
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xs, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Stash.spacing.xs),
        ) {
            Box(
                Modifier.heightIn(min = 36.dp).focusRing(CircleShape).clip(CircleShape).clickable(role = Role.Button, onClick = onLater).padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(stringResource(Res.string.common_later), Stash.type.label, c.inkFaded) }
            PrimaryButton(stringResource(Res.string.common_read_now), onReadNow, height = 36.dp, radius = 18.dp, style = Stash.type.label)
        }
    }
}
