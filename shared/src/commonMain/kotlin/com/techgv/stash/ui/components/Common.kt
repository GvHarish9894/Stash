package com.techgv.stash.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.model.PLAY
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora

/** Content column width on tablets and desktop windows; phones use the full width. */
val MaxContentWidth = 640.dp

fun Modifier.contentWidth() = fillMaxWidth().wrapContentWidth().widthIn(max = MaxContentWidth).fillMaxWidth()

@Composable
fun Modifier.statusBarsPad() = windowInsetsPadding(WindowInsets.statusBars)
@Composable
fun Modifier.navBarsPad() = windowInsetsPadding(WindowInsets.navigationBars)

/** The animation spec, or an instant one when Reduce motion is on. */
@Composable
fun <T> motion(spec: AnimationSpec<T>): AnimationSpec<T> = if (Stash.calm) snap() else spec

/** The design's "press" feel: no ripple, a short springy shrink. */
@Composable
fun Modifier.press(
    onClick: () -> Unit,
    scale: Float = 0.97f,
    role: Role = Role.Button,
    label: String? = null,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, motion(spring(0.45f, Spring.StiffnessMedium)))
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(source, indication = null, role = role, onClickLabel = label, onClick = onClick)
}

@Composable
fun Txt(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Stash.colors.ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) = Text(
    text, modifier, color = color, style = style, maxLines = maxLines,
    overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    textAlign = align,
)

/** 40dp round outlined icon button from the shelf header. */
@Composable
fun CircleIconButton(icon: ImageVector, label: String, onClick: () -> Unit, iconSize: Dp = 18.dp) {
    val c = Stash.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape)
            .press(onClick, 0.94f, label = label).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(iconSize), tint = c.ink) }
}

/** Bare 44dp back/close button. */
@Composable
fun BackButton(label: String, onClick: () -> Unit, tint: Color = Stash.colors.ink, icon: ImageVector = StashIcons.back()) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick, onClickLabel = label)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = tint) }
}

/** Filter / option pill, 36dp tall. */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    dashed: Boolean = false,
    weight: Int = 600,
) {
    val c = Stash.colors
    val bg by animateColorAsState(if (selected) c.primary else if (dashed) Color.Transparent else c.surface, motion(tween(200)))
    val fg by animateColorAsState(if (selected) c.onPrimary else c.ink, motion(tween(200)))
    val bd = if (selected) c.primary else c.outline
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(bg)
            .then(if (dashed) Modifier.dashedBorder(bd, 18.dp) else Modifier.border(1.dp, bd, RoundedCornerShape(18.dp)))
            .selectable(selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Txt(label, inter(13, weight), fg, maxLines = 1)
    }
}

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = size.copy(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** Segmented radio group on a variant track. [fill] spreads options evenly across the width. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val c = Stash.colors
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.variant).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            val bg by animateColorAsState(if (on) c.primary else Color.Transparent, motion(tween(200)))
            val fg by animateColorAsState(if (on) c.onPrimary else c.faded, motion(tween(200)))
            Box(
                (if (fill) Modifier.weight(1f) else Modifier.widthIn(min = 44.dp)).height(32.dp)
                    .clip(RoundedCornerShape(16.dp)).background(bg)
                    .selectable(on, onClick = { onPick(o) }, role = Role.RadioButton)
                    .padding(horizontal = if (fill) 4.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(label(o), inter(12, 600), fg, maxLines = 1) }
        }
    }
}

/** 52×32 switch with a springy knob, as in the design. */
@Composable
fun StashSwitch(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = Stash.colors
    val track by animateColorAsState(if (checked) c.primary else c.switchOff, motion(tween(200)))
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, motion(spring(0.5f, Spring.StiffnessMediumLow)))
    Box(
        Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(track)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier.padding(4.dp).offset(x = x).size(24.dp)
                .graphicsLayer { shadowElevation = 2f; shape = CircleShape; clip = true }
                .background(c.surface),
        )
    }
}

/** Uppercase Lora header followed by a hairline, used between list groups. */
@Composable
fun GroupHeader(label: String, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val c = Stash.colors
    Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        leading?.let { Icon(it, null, Modifier.size(16.dp), tint = c.primary) }
        Txt(label.uppercase(), lora(13, 600).copy(letterSpacing = 1.5.sp), c.primary, Modifier.semantics { heading() })
        Box(Modifier.weight(1f).height(1.dp).background(c.outline))
    }
}

/** An item's tile letter; the video mark is drawn as a triangle so both platforms match. */
@Composable
fun Mono(mono: String, fontSize: Int, color: Color) {
    if (mono != PLAY) return Txt(mono, lora(fontSize, 700), color)
    Canvas(Modifier.size((fontSize * 0.62f).dp)) {
        drawPath(
            Path().apply {
                moveTo(size.width * .12f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .12f, size.height); close()
            },
            color,
        )
    }
}

/** Candle with a flame; [flicker] wobbles the flame like the design's CSS animation. */
@Composable
fun Candle(
    size: Dp,
    candle: Color,
    flame: Color,
    modifier: Modifier = Modifier,
    flicker: Boolean = false,
    wick: Color = candle,
    core: Color? = null,
    band: Color? = null,
    flameScale: Float = 1f,
) {
    val flamePath = remember { PathParser().parsePathString(StashIcons.FLAME).toPath() }
    val corePath = remember { PathParser().parsePathString(StashIcons.FLAME_CORE).toPath() }
    val calm = Stash.calm
    val anim = remember { Animatable(0f) }
    LaunchedEffect(flicker, calm) {
        anim.snapTo(0f)
        if (flicker && !calm) repeat(3) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
        }
        anim.snapTo(0f)
    }
    val wobble = anim.value
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        scale(k, Offset.Zero) {
            drawRoundRect(candle, Offset(9f, 11f), androidx.compose.ui.geometry.Size(6f, 11f), CornerRadius(1.2f))
            band?.let { drawRoundRect(it, Offset(9f, 11f), androidx.compose.ui.geometry.Size(6f, 2f), CornerRadius(1f)) }
            drawLine(wick, Offset(12f, 9.6f), Offset(12f, 11.2f), 1.2f, androidx.compose.ui.graphics.StrokeCap.Round)
            // Keyframes 1 → (1.07, .93) → (.94, 1.06) → (1.03, .98) → 1, pivot at flame base.
            val p = wobble * 4
            val sx = when { p < 1 -> 1 + .07f * p; p < 2 -> 1.07f - .13f * (p - 1); p < 3 -> .94f + .09f * (p - 2); else -> 1.03f - .03f * (p - 3) }
            val sy = when { p < 1 -> 1 - .07f * p; p < 2 -> .93f + .13f * (p - 1); p < 3 -> 1.06f - .08f * (p - 2); else -> .98f + .02f * (p - 3) }
            scale(sx * flameScale, sy * flameScale, Offset(12f, 10f)) {
                drawPath(flamePath, flame)
                core?.let { drawPath(corePath, it) }
            }
        }
    }
}

/** Dark pill toast. */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier, width: Dp = 220.dp) {
    val c = Stash.colors
    Box(
        modifier.width(width).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(c.toast).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, inter(13, 500), c.onToast, align = TextAlign.Center) }
}

/** Toast slot that slides up and fades, showing the last non-empty text while leaving. */
@Composable
fun BoxScope.ToastHost(text: String, bottom: Dp, width: Dp = 220.dp) {
    val last = remember { mutableLast<String>() }
    if (text.isNotEmpty()) last.value = text
    AnimatedVisibility(
        text.isNotEmpty(),
        Modifier.align(Alignment.BottomCenter).navBarsPad().padding(bottom = bottom),
        enter = enter(slideInVertically { it / 2 } + fadeIn()),
        exit = exit(fadeOut()),
    ) { Toast(last.value ?: "", width = width) }
}

class Last<T>(var value: T? = null)
fun <T> mutableLast() = Last<T>()

@Composable
fun enter(t: EnterTransition) = if (Stash.calm) EnterTransition.None else t

@Composable
fun exit(t: ExitTransition) = if (Stash.calm) ExitTransition.None else t

/** Scrim + bottom sheet with the design's 28dp top corners and drag-handle close button. */
@Composable
fun BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    closeLabel: String = "Close",
    background: Color = Stash.colors.surface,
    padding: PaddingValues = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 32.dp),
    gap: Dp = 16.dp,
    horizontal: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = enter(fadeIn(tween(250))), exit = exit(fadeOut(tween(240)))) {
            Box(
                Modifier.fillMaxSize().background(Color(0x6B1E1812))
                    .clickable(remember { MutableInteractionSource() }, null, onClickLabel = closeLabel, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible, Modifier.align(Alignment.BottomCenter),
            enter = enter(slideInVertically(tween(360, easing = EaseOutQuint)) { it }),
            exit = exit(slideOutVertically(tween(240)) { it }),
        ) {
            Column(
                Modifier.contentWidth()
                    .graphicsLayer { shadowElevation = 30f; shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp); clip = true }
                    .background(background)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = horizontal,
            ) {
                SheetHandle(onDismiss, closeLabel, Modifier.align(Alignment.CenterHorizontally))
                content()
            }
        }
    }
}

val EaseOutQuint = androidx.compose.animation.core.CubicBezierEasing(.2f, .8f, .2f, 1f)

@Composable
fun SheetHandle(onClick: () -> Unit, label: String = "Close", modifier: Modifier = Modifier) {
    Box(
        modifier.size(64.dp, 20.dp).clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
    ) { Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(Stash.colors.outline)) }
}

/** Filled primary action button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    radius: Dp = 14.dp,
    bg: Color = Stash.colors.primary,
    fg: Color = Stash.colors.onPrimary,
    size: Int = 15,
) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(radius)).background(bg).press(onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(text, inter(size, 600), fg, Modifier.padding(horizontal = 16.dp), maxLines = 1) }
}

/** Outlined secondary button. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    radius: Dp = 14.dp,
    border: Color = Stash.colors.outline,
    size: Int = 15,
) {
    val c = Stash.colors
    Box(
        modifier.height(height).clip(RoundedCornerShape(radius)).border(1.dp, border, RoundedCornerShape(radius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(text, inter(size, 600), c.ink, maxLines = 1) }
}

/** Little coloured badge, e.g. "Phase 2". */
@Composable
fun Badge(text: String, border: Color?, bg: Color = Color.Transparent, fg: Color = Stash.colors.ink) {
    Box(
        Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(11.dp)) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, inter(11, 600), fg) }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
