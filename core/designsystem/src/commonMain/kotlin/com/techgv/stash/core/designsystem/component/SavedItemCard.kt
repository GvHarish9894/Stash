package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import com.techgv.stash.core.resources.card_options
import com.techgv.stash.core.resources.card_options_for
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.techgv.stash.core.designsystem.HapticKind
import com.techgv.stash.core.designsystem.perform
import com.techgv.stash.core.designsystem.presentation.CardLifecycle
import com.techgv.stash.core.designsystem.presentation.MetadataState
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.cardAccessibilityLabel
import com.techgv.stash.core.designsystem.presentation.cardActionLabel
import com.techgv.stash.core.designsystem.presentation.cardActions
import com.techgv.stash.core.designsystem.presentation.middleEllipsis
import com.techgv.stash.core.designsystem.presentation.resolve
import com.techgv.stash.core.designsystem.presentation.swipeOutcomeLabel
import com.techgv.stash.core.designsystem.presentation.text
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashColors
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.designsystem.theme.motionSpec
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.card_failed_meta
import com.techgv.stash.core.resources.card_meta_three
import com.techgv.stash.core.resources.card_meta_two
import com.techgv.stash.core.resources.card_more_options
import com.techgv.stash.core.resources.common_list_separator
import com.techgv.stash.core.resources.common_retry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How a card arrives or comes back: none, the first-load rise, the new-item paper flutter, the settle of the card whose
 * reader just closed, or the fly-back of a card brought back by Undo. It plays when the card first shows with it or
 * when it changes to it; keep it set for the whole animation ([CardMotion.SETTLE], [CardMotion.FLY_BACK]).
 */
enum class CardEntrance { None, Rise, Flutter, Settle, FlyBack }

/** The distance a card must travel for a swipe to act (handoff: release at ≥ 110 dp). */
val SwipeThreshold = 110.dp

/** Follow factor when the swipe direction has no action. */
const val RubberBandFactor = 0.22f

/** Handoff long-press duration, whatever the platform default. */
private const val LONG_PRESS_MILLIS = 480L

/** Where the card sits for a finger travel of [dragPx]: 1:1 when that direction has an action, else rubber-banded. */
fun swipeDisplacement(dragPx: Float, hasAction: Boolean): Float = if (hasAction) dragPx else dragPx * RubberBandFactor

/** The action a release triggers: [action] once the travel reaches [thresholdPx], else none. */
fun swipeOutcomeOnRelease(dragPx: Float, thresholdPx: Float, action: SwipeOutcome?): SwipeOutcome? =
    action?.takeIf { abs(dragPx) >= thresholdPx && it != SwipeOutcome.RubberBand }

/** Outcomes that fly the card off screen; the row then collapses. The others spring back. */
fun SwipeOutcome.fliesOff() =
    this == SwipeOutcome.Remove || this == SwipeOutcome.Delete || this == SwipeOutcome.Archive || this == SwipeOutcome.Restore

/** Background colour and icon for a swipe action. */
fun swipeVisual(outcome: SwipeOutcome, c: StashColors): Pair<Color, ImageVector> = when (outcome) {
    SwipeOutcome.MarkRead -> c.good to StashIcons.check()
    SwipeOutcome.MarkUnread -> c.good to StashIcons.dot()
    SwipeOutcome.Remove -> c.danger to StashIcons.torn()
    SwipeOutcome.Archive -> c.primary to StashIcons.archive(2f)
    SwipeOutcome.Share -> c.share to StashIcons.share(2f)
    SwipeOutcome.Restore -> c.good to StashIcons.restore()
    SwipeOutcome.Delete -> c.danger to StashIcons.trash()
    SwipeOutcome.RubberBand -> Color.Transparent to StashIcons.dot()
}

/** [text] with each of [ranges] (end inclusive, as `SearchHit` gives them) on a [mark] background. */
fun highlighted(text: String, ranges: List<IntRange>, mark: Color): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (r in ranges.sortedBy { it.first }) {
        val start = r.first.coerceIn(at, text.length)
        val end = (r.last + 1).coerceIn(start, text.length)
        append(text.substring(at, start))
        withStyle(SpanStyle(background = mark)) { append(text.substring(start, end)) }
        at = end
    }
    append(text.substring(at))
}

/** Gap between cards for a card layout. */
fun cardListGap(layout: CardLayout): Dp = when (layout) {
    CardLayout.Compact -> 6.dp
    CardLayout.LargeImages -> 14.dp
    CardLayout.Comfortable -> 10.dp
}

/**
 * Fills the card shape behind a swiping card: colour, 24 dp icon and label. [progress] (0..1 of the 110 dp threshold,
 * read while drawing) scales the icon 0.6 → 1.15. [towardEnd] puts the content at the start edge, where the card
 * uncovers it.
 */
@Composable
fun SwipeActionBackground(outcome: SwipeOutcome, progress: () -> Float, towardEnd: Boolean, modifier: Modifier = Modifier) {
    val label = swipeOutcomeLabel(outcome) ?: return
    val c = Stash.colors
    val (color, icon) = swipeVisual(outcome, c)
    Row(
        modifier.clip(RoundedCornerShape(Stash.radius.card)).background(color).padding(horizontal = Stash.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xs, if (towardEnd) Alignment.Start else Alignment.End),
    ) {
        val iconView = @Composable {
            Icon(
                icon, null,
                Modifier.size(24.dp).graphicsLayer {
                    val k = 0.6f + progress().coerceIn(0f, 1f) * 0.55f
                    scaleX = k; scaleY = k
                },
                tint = c.onPrimary,
            )
        }
        if (towardEnd) iconView()
        Txt(stringResource(label), Stash.type.label, c.onPrimary)
        if (!towardEnd) iconView()
    }
}

/**
 * Per-layout measurements; [large] is 0 for the row layouts and 1 for large images, [padStart]…[padBottom] pad the card,
 * [textStart] / [textEnd] the text column.
 */
private data class Lay(
    val large: Float, val gap: Dp, val padStart: Dp, val padTop: Dp, val padEnd: Dp, val padBottom: Dp,
    val tile: Dp, val tileH: Dp, val tileRadius: Dp, val mono: Float, val clamp: Int, val textGap: Dp,
    val textStart: Dp, val textEnd: Dp, val titleFs: Float, val titleLh: Float,
)

/** [from] → [to] at [f]; the line clamp switches at once. */
private fun lerpLay(from: Lay, to: Lay, f: Float) = to.copy(
    large = lerp(from.large, to.large, f), gap = lerp(from.gap, to.gap, f), padStart = lerp(from.padStart, to.padStart, f),
    padTop = lerp(from.padTop, to.padTop, f), padEnd = lerp(from.padEnd, to.padEnd, f), padBottom = lerp(from.padBottom, to.padBottom, f),
    tile = lerp(from.tile, to.tile, f), tileH = lerp(from.tileH, to.tileH, f), tileRadius = lerp(from.tileRadius, to.tileRadius, f),
    mono = lerp(from.mono, to.mono, f), textGap = lerp(from.textGap, to.textGap, f), textStart = lerp(from.textStart, to.textStart, f),
    textEnd = lerp(from.textEnd, to.textEnd, f), titleFs = lerp(from.titleFs, to.titleFs, f), titleLh = lerp(from.titleLh, to.titleLh, f),
)

/** The [Lay] being shown and, only while a change animates, the animation from [from] to [to]. */
private class LayChange(var from: Lay, var to: Lay, var progress: Animatable<Float, AnimationVector1D>? = null) {
    fun shown() = progress?.let { lerpLay(from, to, it.value) } ?: to
}

/**
 * The card's semantics, shared by [SavedItemCard] and [SearchResultCard]: one merged node with the spoken label, tap and
 * long-press, and the custom actions (the lifecycle's actions, Retry for failed details, More options).
 */
@Composable
private fun Modifier.cardSemantics(
    item: SavedItemCardUi,
    onOpen: () -> Unit,
    onAction: (SwipeOutcome) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    displaced: Boolean = false,
    selected: Boolean = false,
): Modifier {
    val label = cardAccessibilityLabel(item, text(Res.string.common_list_separator)).resolve()
    val actionLabels = cardActions(item).map { it to stringResource(cardActionLabel(it)!!) }
    val moreLabel = stringResource(Res.string.card_more_options)
    val retryLabel = stringResource(Res.string.common_retry)
    return clearAndSetSemantics {
        testTag = "item-${item.id}" // stable selector for UI automation; titles are user data
        cardDisplaced = displaced
        if (selected) this.selected = true
        contentDescription = label
        onClick { onOpen(); true }
        onLongClick { onMore(); true }
        customActions = actionLabels.map { (outcome, name) -> CustomAccessibilityAction(name) { onAction(outcome); true } } +
            listOfNotNull(
                CustomAccessibilityAction(retryLabel) { onRetry(); true }.takeIf { item.metadata == MetadataState.Failed },
                CustomAccessibilityAction(moreLabel) { onMore(); true },
            )
    }
}

/** Whether a card sits away from its resting place (mid-swipe or flown off); lets UI tests see it. */
internal val CardDisplacedKey = SemanticsPropertyKey<Boolean>("CardDisplaced")
private var SemanticsPropertyReceiver.cardDisplaced by CardDisplacedKey

/** Provides the handoff's 480 ms long-press to [content]. */
@Composable
private fun LongPress480(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val config = remember(base) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis: Long get() = LONG_PRESS_MILLIS
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides config, content = content)
}

/**
 * The shimmer phase while [pending] (and motion is allowed), read only while drawing; null otherwise. It sweeps
 * [StashMotion.SHIMMER_SWEEPS] times and then stops (handoff: no loop longer than about 5 s).
 */
@Composable
private fun shimmerPhase(pending: Boolean): State<Float>? {
    if (!pending || Stash.reduceMotion) return null
    val phase = remember { Animatable(-1f) }
    var sweeping by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        repeat(StashMotion.SHIMMER_SWEEPS) {
            phase.snapTo(-1f)
            phase.animateTo(2f, tween(StashMotion.SHIMMER, easing = LinearEasing))
        }
        sweeping = false
    }
    return if (sweeping) phase.asState() else null
}

/**
 * [target], animated over 250 ms (ease) from what was shown when the card layout or text size changes. One animation
 * for every measurement, created only when a change starts, so resting cards hold no animation state.
 */
@Composable
private fun animatedLay(target: Lay): Lay {
    val change = remember { LayChange(target, target) }
    if (target != change.to) {
        change.from = change.shown()
        change.to = target
        change.progress = if (Stash.reduceMotion) null else Animatable(0f)
    }
    change.progress?.let { progress ->
        LaunchedEffect(progress) {
            progress.animateTo(1f, tween(StashMotion.LAYOUT_CHANGE, easing = StashMotion.Ease))
            change.progress = null
        }
    }
    return change.shown()
}

/** The progress (0 → 1) of [entrance] after [delayMillis]; null while none plays and under Reduce motion. */
@Composable
private fun entranceProgress(entrance: CardEntrance, delayMillis: Int): Animatable<Float, AnimationVector1D>? {
    if (entrance == CardEntrance.None || Stash.reduceMotion) return null
    val anim = remember(entrance) { Animatable(0f) }
    LaunchedEffect(anim) {
        delay(delayMillis.toLong())
        anim.animateTo(
            1f,
            when (entrance) {
                CardEntrance.Flutter -> StashMotion.enter(StashMotion.NEW_ITEM)
                // Linear time: the keyframes ease each segment.
                CardEntrance.Settle -> tween(CardMotion.SETTLE, easing = LinearEasing)
                CardEntrance.FlyBack -> tween(CardMotion.FLY_BACK, easing = LinearEasing)
                else -> StashMotion.enter(StashMotion.LIST_RISE)
            },
        )
    }
    return anim
}

/** Draws [entrance] at progress [p]; the fly-back comes from the start edge ([towardStart] 1 in LTR, −1 in RTL). */
private fun GraphicsLayerScope.applyEntrance(entrance: CardEntrance, p: Float, towardStart: Float) {
    when (entrance) {
        CardEntrance.Flutter -> {
            translationY = (1 - p) * -46.dp.toPx()
            // 4° → −1.5° → 0°.
            rotationZ = if (p < 0.6f) 4f - 5.5f * (p / 0.6f) else -1.5f + 1.5f * ((p - 0.6f) / 0.4f)
            alpha = min(1f, p * 1.6f)
        }
        CardEntrance.Settle -> {
            scaleX = settleScale(p)
            scaleY = scaleX
        }
        CardEntrance.FlyBack -> {
            translationX = towardStart * flyBackX(p).dp.toPx()
            rotationZ = towardStart * flyBackTurn(p)
            alpha = flyBackAlpha(p)
        }
        else -> {
            translationY = (1 - p) * 14.dp.toPx()
            alpha = min(1f, p * 1.6f)
        }
    }
}

/**
 * The card's Options action for keyboard and switch users (handoff, Focus order: "each card, then its Options action").
 * Out of sight until it has focus, then a 40 dp primary pill in the card's bottom end corner, as in the prototype.
 */
@Composable
private fun CardOptionsButton(title: String, onMore: () -> Unit, modifier: Modifier) {
    val c = Stash.colors
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.card_options_for, title)
    Box(
        modifier.onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.heightIn(min = 40.dp) else Modifier.size(1.dp))
            .focusRing(CircleShape).clip(CircleShape).background(if (focused) c.primary else Color.Transparent)
            .clickable(role = Role.Button, onClick = onMore)
            .semantics { contentDescription = label }
            .then(if (focused) Modifier.padding(horizontal = 14.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (focused) Txt(stringResource(Res.string.card_options), Stash.type.labelSmall, c.onPrimary, Modifier.clearAndSetSemantics {})
    }
}

/** The Menu key on a focused card (or its Options action) opens the item sheet (handoff, Keyboard and switch access). */
private fun Modifier.menuKeyOpens(onMore: () -> Unit) = onKeyEvent {
    if (it.key == Key.Menu && it.type == KeyEventType.KeyDown) { onMore(); true } else false
}

/**
 * The saved-item card in its three layouts. Tap opens, long-press (480 ms) opens the item menu, a horizontal drag swipes.
 *
 * - [startAction] / [endAction]: what a swipe toward the start / end edge does (resolve them with `SwipeActionResolver`;
 *   `RubberBand` = the card follows at 22 % and nothing happens).
 * - [onAction] receives the outcome after a swipe that reached 110 dp (after the fly-off for remove, archive, restore
 *   and delete) and from the accessibility actions. A card that flew off slides back when it is still shown shortly
 *   after (the command was refused, or the row keeps its key) and whenever its lifecycle changes.
 * - Lifecycle drives the read styling and actions; pending details shimmer, failed details show "Couldn't load details ·
 *   Retry" ([onRetry]).
 * - The card plays the long-press and threshold haptics itself.
 * - [fixedLeftRight] makes [startAction] the LEFT swipe and [endAction] the RIGHT swipe in every layout direction. Use
 *   it for the archive, whose swipes are fixed (right restores, left deletes). Shelf cards leave it false, so the
 *   user's start / end actions follow the reading direction.
 * - [entrance] plays an arrival ([CardEntrance]); [entranceDelayMillis] delays it (the first-load stagger).
 * - [selected]: the item open in the list-detail reader pane; a 2 dp primary outline (the handoff defines no selected
 *   card, so it borrows the focus ring's colour and the option cards' 2 dp selected border) and `selected` semantics.
 * - Keyboard and switch access: an Options action follows the card in focus order, and the Menu key opens [onMore].
 * - [tearing] plays the page tear of Remove from the item menu (toward the start edge, then the card's height
 *   collapses; [CardMotion.TEAR]) and then calls [onTorn] once: remove the item there. Setting it back to false shows
 *   the card in place again. Under Reduce motion the card is gone at once and [onTorn] is called straight away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedItemCard(
    item: SavedItemCardUi,
    layout: CardLayout,
    startAction: SwipeOutcome,
    endAction: SwipeOutcome,
    onOpen: () -> Unit,
    onAction: (SwipeOutcome) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    titleHighlights: List<IntRange> = emptyList(),
    onRetry: () -> Unit = {},
    animateStrike: Boolean = false,
    entrance: CardEntrance = CardEntrance.None,
    entranceDelayMillis: Int = 0,
    fixedLeftRight: Boolean = false,
    tearing: Boolean = false,
    onTorn: () -> Unit = {},
    selected: Boolean = false,
) {
    val c = Stash.colors
    val s = Stash.spacing
    val r = Stash.radius
    val e = Stash.elevation
    val reduceMotion = Stash.reduceMotion
    val density = LocalDensity.current
    val haptics: HapticFeedback = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val titleStyle = Stash.type.cardTitle
    val fs = titleStyle.fontSize.value
    // Handoff: compact titles get a second line at large font scales.
    val compactLines = if (density.fontScale > 1.3f) 2 else 1
    val target = when (layout) {
        CardLayout.Compact -> Lay(0f, 10.dp, 10.dp, 9.dp, 28.dp, 9.dp, 40.dp, 40.dp, r.thumbnailCompact, 17f,
            compactLines, 2.dp, 0.dp, 0.dp, fs - 1, ((fs - 1) * 1.35f).toInt().toFloat())
        CardLayout.LargeImages -> Lay(1f, s.sm, 0.dp, 0.dp, 0.dp, 14.dp, 0.dp, 156.dp, r.card - 1.dp, 52f,
            3, 6.dp, 14.dp, 30.dp, fs + 2, ((fs + 2) * 1.35f).toInt().toFloat())
        CardLayout.Comfortable -> Lay(0f, s.sm, s.sm, s.sm, 26.dp, s.sm, 60.dp, 60.dp, r.thumbnail, 24f, 3, 6.dp,
            0.dp, 0.dp, fs, titleStyle.lineHeight.value)
    }
    // Card layout and text size changes animate (handoff: 250 ms, ease), including the switch between the row layouts
    // and large images (`large` 0 → 1: the thumbnail grows to the full width and the text moves below it).
    val lay = animatedLay(target)
    val tileShape = RoundedCornerShape(lay.tileRadius, lay.tileRadius, lay.tileRadius * (1 - lay.large), lay.tileRadius * (1 - lay.large))
    val pad = PaddingValues(lay.padStart, lay.padTop, lay.padEnd, lay.padBottom)
    val read = item.lifecycle == CardLifecycle.Read

    // Animation state exists only while a card arrives or tears; a resting card allocates none.
    val enterAnim = entranceProgress(entrance, entranceDelayMillis)
    val tear = if (tearing) {
        val anim = remember { Animatable(0f) }
        val torn by rememberUpdatedState(onTorn)
        LaunchedEffect(anim) {
            if (reduceMotion) anim.snapTo(1f) else anim.animateTo(1f, tween(CardMotion.TEAR, easing = LinearEasing))
            torn()
        }
        anim
    } else {
        null
    }
    // Tear and fly-back travel toward the start edge.
    val towardStart = if (rtl) -1f else 1f

    val dx = remember { Animatable(0f) }
    // A new lifecycle (restored, archived, …) always shows the card in place.
    LaunchedEffect(item.lifecycle) { dx.snapTo(0f) }
    // Raw finger distance, tracked synchronously so fast drags are not lost.
    val raw = remember { floatArrayOf(0f) }
    val crossed = remember { booleanArrayOf(false) }
    val actionCallback by rememberUpdatedState(onAction)
    val threshold = with(density) { SwipeThreshold.toPx() }
    val flyDistance = with(density) { 460.dp.toPx() }
    // A positive drag moves toward the right edge: the end edge in LTR, the start edge in RTL (unless fixedLeftRight).
    val mirrored = rtl && !fixedLeftRight
    fun actionFor(x: Float) = (if ((x > 0) != mirrored) endAction else startAction).takeIf { it != SwipeOutcome.RubberBand }
    // Recomposes only when the swipe starts, ends or changes direction.
    val revealed by remember(startAction, endAction, rtl, fixedLeftRight) {
        derivedStateOf { dx.value.let { x -> if (x == 0f) null else actionFor(x)?.let { it to ((x > 0) != rtl) } } }
    }
    val dragging by remember { derivedStateOf { dx.value != 0f } }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && !dragging) 0.97f else 1f, motionSpec(StashMotion.press()))

    val strike = if (animateStrike && !reduceMotion) {
        val anim = remember { Animatable(0f) }
        LaunchedEffect(anim) {
            delay(50)
            anim.animateTo(1f, tween(StashMotion.STRIKE, easing = StashMotion.Ease))
        }
        anim
    } else {
        null
    }
    val titleColor by animateColorAsState(if (read) c.inkFaded else c.ink, motionSpec(tween(StashMotion.READ_STYLE)))
    val tileAlpha by animateFloatAsState(if (read) 0.55f else 1f, motionSpec(tween(StashMotion.READ_STYLE)))
    val shimmer = shimmerPhase(item.metadata == MetadataState.Pending)
    val cardShape = RoundedCornerShape(r.card)

    LongPress480 {
        Box(
            modifier
                .then(
                    if (tear == null) {
                        Modifier
                    } else {
                        // The torn card's row collapses.
                        Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, (placeable.height * tearHeight(tear.value)).roundToInt()) { placeable.place(0, 0) }
                        }
                    },
                )
                .then(
                    if (enterAnim == null && tear == null) {
                        Modifier
                    } else {
                        Modifier.graphicsLayer {
                            enterAnim?.value?.let { p -> applyEntrance(entrance, p, towardStart) }
                            tear?.value?.let { t ->
                                translationX = towardStart * tearX(t, 24.dp.toPx(), size.width)
                                rotationZ = towardStart * tearTurn(t)
                                alpha = tearAlpha(t)
                            }
                        }
                    },
                )
                .menuKeyOpens(onMore),
        ) {
          Box(Modifier.cardSemantics(item, onOpen, onAction, onMore, onRetry, displaced = dragging, selected = selected)) {
            revealed?.let { (act, towardEnd) ->
                SwipeActionBackground(act, { abs(dx.value) / threshold }, towardEnd, Modifier.matchParentSize())
            }
            Box(
                Modifier
                    .graphicsLayer {
                        val x = dx.value
                        translationX = x
                        rotationZ = x / density.density * 0.012f
                        scaleX = pressScale
                        scaleY = pressScale
                        val moving = x != 0f
                        // Dragging lifts the card; pressing tightens its shadow.
                        shadowElevation = (if (moving) e.cardDrag else if (pressed) e.card / 2 else e.card).toPx()
                        ambientShadowColor = if (moving) e.cardDragShadow else e.cardShadow
                        spotShadowColor = ambientShadowColor
                        shape = cardShape
                        clip = true
                    }
                    .focusRing(cardShape, inset = true)
                    .background(c.surface)
                    .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outline, cardShape)
                    .pointerInput(startAction, endAction, rtl, fixedLeftRight, reduceMotion) {
                        detectHorizontalDragGestures(
                            onDragStart = { raw[0] = 0f; crossed[0] = false },
                            onHorizontalDrag = { change, drag ->
                                change.consume()
                                raw[0] += drag
                                val target = swipeDisplacement(raw[0], hasAction = actionFor(raw[0]) != null)
                                val over = swipeOutcomeOnRelease(raw[0], threshold, actionFor(raw[0])) != null
                                if (over && !crossed[0]) haptics.perform(HapticKind.SwipeThreshold)
                                crossed[0] = over
                                scope.launch { dx.snapTo(target) }
                            },
                            onDragEnd = {
                                val a = swipeOutcomeOnRelease(raw[0], threshold, actionFor(raw[0]))
                                raw[0] = 0f
                                scope.launch {
                                    if (a != null && a.fliesOff()) {
                                        val dir = if (dx.value > 0) 1 else -1
                                        if (!reduceMotion) dx.animateTo(dir * flyDistance, StashMotion.exit(StashMotion.SWIPE_OUT))
                                        actionCallback(a)
                                        // Still here after the row would have collapsed: the action was refused or the row
                                        // kept its key, so bring the card back.
                                        delay(2L * StashMotion.ROW_COLLAPSE)
                                    } else if (a != null) {
                                        actionCallback(a)
                                    }
                                    if (reduceMotion) dx.snapTo(0f) else dx.animateTo(0f, StashMotion.swipeBack())
                                }
                            },
                            onDragCancel = { scope.launch { if (reduceMotion) dx.snapTo(0f) else dx.animateTo(0f, StashMotion.swipeBack()) } },
                        )
                    }
                    .combinedClickable(source, indication = null, onClick = onOpen, onLongClick = onMore),
            ) {
                CardBody(lay, pad) {
                    ItemThumbnail(
                        item, lay.mono.sp,
                        Modifier.graphicsLayer { alpha = tileAlpha }.clip(tileShape).shimmer(shimmer, c.shimmer),
                    )
                    Column(Modifier.padding(start = lay.textStart, end = lay.textEnd), verticalArrangement = Arrangement.spacedBy(lay.textGap)) {
                        Text(
                            highlighted(item.title, titleHighlights, c.highlight), Modifier.shimmer(shimmer, c.shimmer), color = titleColor,
                            maxLines = lay.clamp, overflow = TextOverflow.Ellipsis,
                            style = titleStyle.copy(fontSize = lay.titleFs.sp, lineHeight = lay.titleLh.sp),
                        )
                        if (item.metadata == MetadataState.Failed) {
                            FailedMeta(item.siteName, onRetry)
                        } else {
                            MetaLine(
                                item,
                                Modifier.drawWithContent {
                                    drawContent()
                                    if (read) {
                                        val y = size.height * 0.52f
                                        drawLine(c.inkFaded, Offset(0f, y), Offset(size.width * (strike?.value ?: 1f), y), 1.5.dp.toPx())
                                    }
                                },
                            )
                        }
                    }
                }
                UnreadDot(item.unread, Modifier.align(Alignment.TopEnd).padding(top = s.sm, end = 10.dp))
            }
          }
            CardOptionsButton(item.title, onMore, Modifier.align(Alignment.BottomEnd).padding(end = s.xs, bottom = s.xs))
        }
    }
}

/**
 * The card's thumbnail and text column, [pad]ded: side by side and vertically centred at [Lay.large] 0 (the thumbnail
 * [Lay.tile] wide), the thumbnail full width with the text below at 1, interpolated between.
 */
@Composable
private fun CardBody(lay: Lay, pad: PaddingValues, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth().padding(pad)) { measurables, constraints ->
        val (thumbnail, text) = measurables
        val large = lay.large
        val width = constraints.maxWidth
        val gap = lay.gap.roundToPx()
        val tileH = lay.tileH.roundToPx()
        val tile = thumbnail.measure(Constraints.fixed(lerp(lay.tile.roundToPx(), width, large), tileH))
        val textX = lerp(tile.width + gap, 0, large)
        val body = text.measure(Constraints(maxWidth = (width - textX).coerceAtLeast(0)))
        val rowH = maxOf(tileH, body.height)
        layout(width, lerp(rowH, tileH + gap + body.height, large)) {
            tile.placeRelative(0, lerp((rowH - tileH) / 2, 0, large))
            body.placeRelative(textX, lerp((rowH - body.height) / 2, tileH + gap, large))
        }
    }
}

/**
 * A search result: 52 dp thumbnail, the title and the site with their match ranges highlighted, and "site · time"
 * (handoff: search results keep the prototype's fixed style, not the card layout setting). Tap opens; long-press and the
 * accessibility actions and the Menu key are the same as on [SavedItemCard]. No swipe. [entrance] plays an arrival (the
 * Undo fly-back).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultCard(
    item: SavedItemCardUi,
    onOpen: () -> Unit,
    onAction: (SwipeOutcome) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    titleHighlights: List<IntRange> = emptyList(),
    siteHighlights: List<IntRange> = emptyList(),
    onRetry: () -> Unit = {},
    entrance: CardEntrance = CardEntrance.None,
) {
    val c = Stash.colors
    val read = item.lifecycle == CardLifecycle.Read
    val shimmer = shimmerPhase(item.metadata == MetadataState.Pending)
    val shape = RoundedCornerShape(Stash.radius.card)
    val enterAnim = entranceProgress(entrance, 0)
    val towardStart = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    LongPress480 {
        Row(
            modifier.fillMaxWidth()
                .then(if (enterAnim == null) Modifier else Modifier.graphicsLayer { applyEntrance(entrance, enterAnim.value, towardStart) })
                .menuKeyOpens(onMore).cardSemantics(item, onOpen, onAction, onMore, onRetry)
                .focusRing(shape, inset = true).clip(shape).background(c.surface).border(1.dp, c.outline, shape)
                .combinedClickable(onClick = onOpen, onLongClick = onMore)
                .padding(Stash.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
        ) {
            val tileAlpha = when {
                item.lifecycle == CardLifecycle.Archived -> 0.7f
                read -> 0.55f
                else -> 1f
            }
            ItemThumbnail(
                item, 22.sp,
                Modifier.size(52.dp).graphicsLayer { alpha = tileAlpha }.clip(RoundedCornerShape(Stash.radius.thumbnail))
                    .shimmer(shimmer, c.shimmer),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    highlighted(item.title, titleHighlights, c.highlight), Modifier.shimmer(shimmer, c.shimmer),
                    color = if (read) c.inkFaded else c.ink, style = Stash.type.cardTitle,
                )
                if (item.metadata == MetadataState.Failed) {
                    FailedMeta(item.siteName, onRetry)
                } else {
                    val time = item.time?.resolve()
                    val site = highlighted(item.siteName, siteHighlights, c.highlight)
                    val line = if (time == null) site else {
                        // Keep the site's highlight inside the localized "%1$s · %2$s" pattern.
                        val pattern = stringResource(Res.string.card_meta_two, SITE_MARK, time)
                        AnnotatedString(pattern.substringBefore(SITE_MARK)) + site + AnnotatedString(pattern.substringAfter(SITE_MARK))
                    }
                    Text(line, color = c.inkFaded, style = Stash.type.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private const val SITE_MARK = "\u0000"

/** Test tag of the image inside [ItemThumbnail]. */
internal const val THUMBNAIL_IMAGE_TAG = "thumbnail-image"

/**
 * The item's thumbnail in a [modifier]-sized tile: the monogram on the item's tint, covered by the item's image
 * (centre-cropped) once it loads. The monogram stays while loading, on error, without an image, and while the details
 * are pending or failed. The image is decorative: the card announces itself.
 */
@Composable
internal fun ItemThumbnail(item: SavedItemCardUi, monogramSize: TextUnit, modifier: Modifier) {
    val url = item.imageUrl.takeIf { item.metadata == MetadataState.Ready }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.background(item.tint), contentAlignment = Alignment.Center) {
        if (!loaded) Monogram(item.monogram, monogramSize, item.monogramInk)
        if (url != null) {
            AsyncImage(
                url, null, Modifier.matchParentSize().testTag(THUMBNAIL_IMAGE_TAG),
                contentScale = ContentScale.Crop, onSuccess = { loaded = true }, onError = { loaded = false },
            )
        }
    }
}

/** Paper shimmer over pending content: a [band] sweeping across, its phase read only while drawing. */
private fun Modifier.shimmer(phase: State<Float>?, band: Color): Modifier = if (phase == null) this else drawWithContent {
    drawContent()
    val w = size.width
    val p = phase.value
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent, 0.5f to band, 1f to Color.Transparent,
            start = Offset(w * p - w / 2, 0f), end = Offset(w * p + w / 2, size.height),
        ),
    )
}

/**
 * "site · time · length" on one line; when it does not fit, the length goes first (handoff), then the line ellipsizes.
 * Both variants are composed once and the layout picks one, without subcomposition.
 */
@Composable
fun MetaLine(item: SavedItemCardUi, modifier: Modifier = Modifier) {
    val c = Stash.colors
    val style = Stash.type.meta
    val time = item.time?.resolve()
    val length = item.length?.resolve()
    val site = middleEllipsis(item.siteName)
    val full = when {
        time != null && length != null -> stringResource(Res.string.card_meta_three, site, time, length)
        time != null -> stringResource(Res.string.card_meta_two, site, time)
        length != null -> stringResource(Res.string.card_meta_two, site, length)
        else -> site
    }
    val short = if (time != null) stringResource(Res.string.card_meta_two, site, time) else site
    Layout(
        content = {
            Txt(full, style, c.inkFaded, maxLines = 1)
            Txt(short, style, c.inkFaded, maxLines = 1)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val (fullText, shortText) = measurables
        val fits = full == short || fullText.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
        val placeable = (if (fits) fullText else shortText).measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** "site · Couldn't load details · Retry" with a 48 dp-tall Retry text button. */
@Composable
private fun FailedMeta(siteName: String, onRetry: () -> Unit) {
    val c = Stash.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xxs)) {
        Txt(stringResource(Res.string.card_failed_meta, middleEllipsis(siteName)), Stash.type.meta, c.inkFaded, Modifier.weight(1f, fill = false), maxLines = 1)
        Box(Modifier.heightIn(min = 48.dp).focusRing().clickable(role = Role.Button, onClick = onRetry), contentAlignment = Alignment.Center) {
            Txt(stringResource(Res.string.common_retry), Stash.type.labelSmall, c.primary)
        }
    }
}

/** Unread dot: 10 dp danger dot in a 2 dp surface ring. Leaving, it swells to 1.6× and cracks to 0 with a 40° turn. */
@Composable
fun UnreadDot(visible: Boolean, modifier: Modifier = Modifier) {
    val c = Stash.colors
    val reduceMotion = Stash.reduceMotion
    // 0 = shown, 1 = gone.
    val gone = remember { Animatable(if (visible) 0f else 1f) }
    val shown = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible, reduceMotion) {
        if (reduceMotion) {
            gone.snapTo(if (visible) 0f else 1f); shown.snapTo(if (visible) 1f else 0f); return@LaunchedEffect
        }
        if (visible) {
            gone.snapTo(0f)
            shown.animateTo(1f, tween(StashMotion.CHECK_POP, easing = StashMotion.Overshoot))
        } else {
            gone.animateTo(1f, tween(StashMotion.DOT_CRACK, easing = StashMotion.EaseIn))
            shown.snapTo(0f)
        }
    }
    val hidden by remember { derivedStateOf { shown.value == 0f && gone.value >= 1f } }
    if (hidden) return
    Box(
        modifier.size(14.dp).graphicsLayer {
            val p = gone.value
            val s = if (p < 0.4f) 1f + 0.6f * (p / 0.4f) else 1.6f * (1f - (p - 0.4f) / 0.6f)
            val k = s * shown.value
            scaleX = k; scaleY = k; rotationZ = 40f * p
        }.clip(CircleShape).background(c.surface).padding(2.dp).clip(CircleShape).background(c.danger),
    )
}
