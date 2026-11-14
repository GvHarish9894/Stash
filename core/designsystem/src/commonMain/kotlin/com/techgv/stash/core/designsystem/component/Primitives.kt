package com.techgv.stash.core.designsystem.component

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.presentation.PLAY_MONOGRAM
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.designsystem.theme.motionSpec

/** Content column width on tablets and desktop windows; phones use the full width. */
val MaxContentWidth = 640.dp

fun Modifier.contentWidth() = fillMaxWidth().wrapContentWidth().widthIn(max = MaxContentWidth).fillMaxWidth()

@Composable
fun Modifier.statusBarsPad() = windowInsetsPadding(WindowInsets.statusBars)

@Composable
fun Modifier.navBarsPad() = windowInsetsPadding(WindowInsets.navigationBars)

/** The design's "press" feel: no ripple, a short springy shrink. */
@Composable
fun Modifier.press(
    onClick: () -> Unit,
    scale: Float = 0.97f,
    role: Role = Role.Button,
    label: String? = null,
    enabled: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, motionSpec(StashMotion.press()))
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(source, indication = null, enabled = enabled, role = role, onClickLabel = label, onClick = onClick)
}

/**
 * The keyboard focus ring (handoff): a 2 dp `color-primary` line 2 dp outside [shape], or 2 dp inside it with [inset]
 * (cards). Drawn only while the next focus target in the chain has keyboard focus, so put it before `clip` (which would
 * cut it off) and before the clickable, selectable or field it rings.
 */
@Composable
fun Modifier.focusRing(shape: Shape = RectangleShape, inset: Boolean = false): Modifier {
    val color = Stash.colors.primary
    val inputMode = LocalInputModeManager.current
    var focused by remember { mutableStateOf(false) }
    val memory = LocalFocusMemory.current
    val requester = remember { FocusRequester() }
    return focusRequester(requester).onFocusChanged {
        focused = it.isFocused
        if (it.isFocused) memory.focused = requester else if (memory.focused === requester) memory.focused = null
    }.drawWithContent {
        drawContent()
        if (!focused || inputMode.inputMode != InputMode.Keyboard) return@drawWithContent
        val stroke = 2.dp.toPx()
        // The centre of the line sits the 2 dp gap plus half the stroke from the edge.
        val d = (2.dp.toPx() + stroke / 2) * if (inset) -1 else 1
        val outline = shape.createOutline(Size(size.width + 2 * d, size.height + 2 * d), layoutDirection, this)
        translate(-d, -d) { drawOutline(outline, color, style = Stroke(stroke)) }
    }
}

/** The control with a [focusRing] that has focus now, so [coveredByModal] can return focus to it. */
private class FocusMemory {
    var focused: FocusRequester? = null
}

private val LocalFocusMemory = staticCompositionLocalOf { FocusMemory() }

/**
 * For the screen content under a sheet or dialog (handoff): while [covered] it is out of the accessibility tree and
 * keyboard focus cannot enter it; when it is uncovered, focus goes back to the element that had it (the opener).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.coveredByModal(covered: Boolean): Modifier {
    val group = remember { FocusRequester() }
    var saved by remember { mutableStateOf(false) }
    // The opener, when it is a control with a focus ring: restoreFocusedChild only reaches the group's direct child, which
    // inside a lazy list or another focus group is not the control itself.
    val memory = LocalFocusMemory.current
    val opener = remember { arrayOfNulls<FocusRequester>(1) }
    if (covered && opener[0] == null) opener[0] = memory.focused
    LaunchedEffect(covered) {
        if (covered) return@LaunchedEffect
        val focused = opener[0]
        opener[0] = null
        if (!saved) return@LaunchedEffect
        // A frame later the sheet no longer holds on to focus and this group can be entered again.
        withFrameNanos { }
        if (focused == null || !focused.requestFocus()) group.restoreFocusedChild()
        saved = false
    }
    return focusRequester(group)
        .focusProperties {
            if (covered) {
                // Focus leaving for the sheet: remember which child had it.
                onExit = { saved = group.saveFocusedChild() }
                onEnter = { cancelFocusChange() }
            }
        }
        .focusGroup()
        .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)
}

/**
 * Lays its children out side by side, widths in proportion to [weights] and all as tall as the tallest (handoff: two
 * side-by-side buttons keep equal heights). When a child's longest word does not fit its share (very large text), the
 * children stack full-width instead, so labels wrap between words rather than breaking inside one ("Canc-el").
 */
@Composable
fun RowOrStack(weights: List<Float>, modifier: Modifier = Modifier, gap: Dp = Stash.spacing.sm, content: @Composable () -> Unit) {
    Layout(content, modifier) { children, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val free = width - gapPx * (children.size - 1)
        val shares = children.indices.map { (free * weights[it] / weights.sum()).toInt() }
            .let { it.dropLast(1) + (free - it.dropLast(1).sum()) }
        val sideBySide = children.indices.all { children[it].minIntrinsicWidth(Constraints.Infinity) <= shares[it] }
        if (sideBySide) {
            val height = children.indices.maxOf { children[it].minIntrinsicHeight(shares[it]) }
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            val placeables = children.indices.map { children[it].measure(Constraints.fixed(shares[it], height)) }
            layout(width, height) {
                var x = 0
                placeables.forEach { it.placeRelative(x, 0); x += it.width + gapPx }
            }
        } else {
            val placeables = children.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            layout(width, placeables.sumOf { it.height } + gapPx * (placeables.size - 1)) {
                var y = 0
                placeables.forEach { it.place(0, y); y += it.height + gapPx }
            }
        }
    }
}

/**
 * `Modifier.animateItem()` with the Stash specs, or no item animation when Reduce motion is on. Use it on every lazy
 * list item: `Modifier.stashAnimateItem(this)` inside `item { }`.
 */
@Composable
fun Modifier.stashAnimateItem(scope: LazyItemScope): Modifier =
    if (Stash.reduceMotion) {
        this
    } else {
        with(scope) {
            animateItem(
                fadeInSpec = tween(StashMotion.LIST_ITEM, easing = StashMotion.EaseOutEmphasized),
                // Rows below a removed one close the gap with the swipe-out's row collapse (handoff: 260 ms, ease).
                placementSpec = tween(StashMotion.ROW_COLLAPSE, easing = StashMotion.Ease),
                fadeOutSpec = StashMotion.exit(StashMotion.ROW_COLLAPSE),
            )
        }
    }

/** Fades content out over the last [width] at the end edge, e.g. a horizontally scrolling chip row (handoff). */
fun Modifier.endEdgeFade(width: Dp = 24.dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width)
        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        val from = if (rtl) w else size.width - w
        val to = if (rtl) 0f else size.width
        drawRect(
            Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = from, endX = to),
            topLeft = Offset(if (rtl) 0f else size.width - w, 0f), size = Size(w, size.height), blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color = Stash.colors.ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) = Text(
    text, modifier, color = color, style = style, maxLines = maxLines,
    overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    textAlign = align,
)

/** 40 dp round outlined icon button from the shelf header (48 dp touch target). */
@Composable
fun CircleIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: Dp = 18.dp) {
    val c = Stash.colors
    Box(
        modifier.size(40.dp).focusRing(CircleShape).clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape)
            .press(onClick, 0.94f, label = label).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(iconSize), tint = c.ink) }
}

/** Bare 44 dp back / close button. */
@Composable
fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Stash.colors.ink, icon: ImageVector = StashIcons.back()) {
    Box(
        modifier.size(44.dp).focusRing(CircleShape).clip(CircleShape).clickable(onClick = onClick, onClickLabel = label, role = Role.Button)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = tint) }
}

/** Round × button, e.g. clearing a search field (32 dp visual, 48 dp touch). */
@Composable
fun ClearButton(label: String, background: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).focusRing(CircleShape).clip(CircleShape).background(background).clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(StashIcons.close(), null, Modifier.size(12.dp), tint = Stash.colors.ink) }
}

/**
 * Screen header: back button, Lora screen title (a heading) and an optional [subtitle] under it (the archive's item
 * count), with optional [actions] at the end.
 */
@Composable
fun ScreenHeader(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Stash.colors
    val s = Stash.spacing
    Row(
        modifier.fillMaxWidth().padding(start = s.sm, end = s.sm, top = s.xl, bottom = if (subtitle == null) s.xs else s.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.xxs),
    ) {
        BackButton(backLabel, onBack)
        Column(Modifier.weight(1f)) {
            Txt(title, Stash.type.screenTitle, c.ink, Modifier.semantics { heading() })
            if (subtitle != null) Txt(subtitle, Stash.type.meta, c.inkFaded)
        }
        actions?.invoke(this)
    }
}

/** The extended "Add link" FAB: at least 56 dp tall (grows with the font scale), `radius-lg`, `elevation-fab`. Place it 20 dp from the end, 28 dp above the nav bar. */
@Composable
fun StashFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Stash.colors
    val e = Stash.elevation
    val shape = RoundedCornerShape(Stash.radius.large)
    Row(
        modifier.heightIn(min = 56.dp).focusRing(shape)
            .graphicsLayer {
                shadowElevation = e.fab.toPx(); this.shape = shape; clip = true
                ambientShadowColor = e.fabShadow; spotShadowColor = e.fabShadow
            }
            .background(c.primary).press(onClick).padding(start = Stash.spacing.md, end = Stash.spacing.xl, top = Stash.spacing.xs, bottom = Stash.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xs),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = c.onPrimary)
        Txt(text, Stash.type.emphasis, c.onPrimary)
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

/** Opacity of disabled controls (handoff: 55 %). */
const val DisabledAlpha = 0.55f

/**
 * Filled primary action button. [height] is its minimum height: with large text the label wraps and the button grows
 * (handoff: buttons grow in height, never clip text). [style] is `Stash.type.emphasis` (15), `calloutStrong` (14) or
 * `label` (13).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    radius: Dp = Stash.radius.card,
    bg: Color = Stash.colors.primary,
    fg: Color = Stash.colors.onPrimary,
    style: TextStyle = Stash.type.emphasis,
) {
    Box(
        modifier.heightIn(min = height).focusRing(RoundedCornerShape(radius)).graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .clip(RoundedCornerShape(radius)).background(bg).press(onClick, enabled = enabled),
        contentAlignment = Alignment.Center,
    ) { Txt(text, style, fg, Modifier.padding(horizontal = Stash.spacing.md, vertical = Stash.spacing.xs), align = TextAlign.Center) }
}

/** Outlined secondary button; [height] is its minimum height, like [PrimaryButton]. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    radius: Dp = Stash.radius.card,
    border: Color = Stash.colors.outline,
    style: TextStyle = Stash.type.emphasis,
) {
    Box(
        modifier.heightIn(min = height).focusRing(RoundedCornerShape(radius)).graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .clip(RoundedCornerShape(radius)).border(1.dp, border, RoundedCornerShape(radius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(text, style, Stash.colors.ink, Modifier.padding(horizontal = Stash.spacing.md, vertical = Stash.spacing.xs), align = TextAlign.Center) }
}

/** Small badge, e.g. "Phase 2" (brass border) or "Soon" (variant fill). */
@Composable
fun Badge(text: String, border: Color?, bg: Color = Color.Transparent, fg: Color = Stash.colors.ink) {
    Box(
        Modifier.heightIn(min = 22.dp).clip(RoundedCornerShape(11.dp)).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(11.dp)) else Modifier)
            .padding(horizontal = Stash.spacing.xs, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, Stash.type.captionStrong, fg) }
}

private class LastValue<T>(var value: T?)

/** The last non-null [current], so content that is animating out can keep drawing it. */
@Composable
fun <T : Any> rememberLast(current: T?): T? {
    val box = remember { LastValue<T>(null) }
    if (current != null) box.value = current
    return box.value
}

@Composable
fun enter(t: EnterTransition) = if (Stash.reduceMotion) EnterTransition.None else t

@Composable
fun exit(t: ExitTransition) = if (Stash.reduceMotion) ExitTransition.None else t

/**
 * An item's tile letter at [fontSize] (at 100 % text; it is drawn to the size of its tile and does not grow with the
 * font scale). The video mark is drawn as a triangle so both platforms match.
 */
@Composable
fun Monogram(monogram: String, fontSize: TextUnit, color: Color) {
    val glyph = with(LocalDensity.current) { fontSize.value.dp.toSp() }
    if (monogram != PLAY_MONOGRAM) return Txt(monogram, Stash.type.monogram.copy(fontSize = glyph, lineHeight = glyph), color)
    Canvas(Modifier.size((fontSize.value * 0.62f).dp)) {
        drawPath(
            Path().apply {
                moveTo(size.width * .12f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .12f, size.height); close()
            },
            color,
        )
    }
}

/**
 * Candle with a flame; [flicker] wobbles the flame for three cycles, like the design's CSS animation. Decorative.
 * [flameScale] is read while drawing, so an animated value does not recompose.
 */
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
    flameScale: () -> Float = { 1f },
) {
    val flamePath = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(StashIcons.FLAME).toPath() }
    val corePath = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString(StashIcons.FLAME_CORE).toPath() }
    val reduceMotion = Stash.reduceMotion
    val anim = remember { Animatable(0f) }
    LaunchedEffect(flicker, reduceMotion) {
        anim.snapTo(0f)
        if (flicker && !reduceMotion) {
            repeat(StashMotion.FLAME_FLICKER_CYCLES) {
                anim.snapTo(0f)
                anim.animateTo(1f, tween(StashMotion.FLAME_FLICKER_CYCLE, easing = StashMotion.EaseInOut))
            }
        }
        anim.snapTo(0f)
    }
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        val fs = flameScale()
        scale(k, Offset.Zero) {
            drawRoundRect(candle, Offset(9f, 11f), Size(6f, 11f), CornerRadius(1.2f))
            band?.let { drawRoundRect(it, Offset(9f, 11f), Size(6f, 2f), CornerRadius(1f)) }
            drawLine(wick, Offset(12f, 9.6f), Offset(12f, 11.2f), 1.2f, StrokeCap.Round)
            // Keyframes 1 → (1.07, .93) → (.94, 1.06) → (1.03, .98) → 1, pivot at the flame base.
            val p = anim.value * 4
            val sx = when { p < 1 -> 1 + .07f * p; p < 2 -> 1.07f - .13f * (p - 1); p < 3 -> .94f + .09f * (p - 2); else -> 1.03f - .03f * (p - 3) }
            val sy = when { p < 1 -> 1 - .07f * p; p < 2 -> .93f + .13f * (p - 1); p < 3 -> 1.06f - .08f * (p - 2); else -> .98f + .02f * (p - 3) }
            scale(sx * fs, sy * fs, Offset(12f, 10f)) {
                drawPath(flamePath, flame)
                core?.let { drawPath(corePath, it) }
            }
        }
    }
}
