package com.techgv.stash.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.model.CardLayout
import com.techgv.stash.model.Item
import com.techgv.stash.model.SwipeAction
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.StashColors
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** How a card arrives on screen. */
enum class CardEnter { None, Rise, Flutter }

private val Overshoot = CubicBezierEasing(.3f, 1.35f, .5f, 1f)

/** Colour, icon and label for a swipe action background. */
fun swipeVisual(a: SwipeAction, item: Item, c: StashColors): Triple<Color, ImageVector, String> = when (a) {
    SwipeAction.Read -> if (item.read) Triple(c.good, StashIcons.dot(), "Unread") else Triple(c.good, StashIcons.check(), "Read")
    SwipeAction.Remove -> Triple(c.danger, StashIcons.torn(), "Remove")
    SwipeAction.Archive -> Triple(c.primary, StashIcons.archive(2f), "Archive")
    SwipeAction.Share -> Triple(c.share, StashIcons.share(2f), "Share")
    SwipeAction.Restore -> Triple(c.good, StashIcons.restore(), "Restore")
    SwipeAction.Delete -> Triple(c.danger, StashIcons.trash(), "Delete")
    SwipeAction.None -> Triple(Color.Transparent, StashIcons.dot(), "")
}

/** Title with the matched part highlighted, as in search and archive results. */
fun highlighted(parts: Triple<String, String, String>, mark: Color): AnnotatedString = buildAnnotatedString {
    append(parts.first)
    withStyle(SpanStyle(background = mark)) { append(parts.second) }
    append(parts.third)
}

val MarkColor = Color(0x4DB8893B)

private data class Lay(
    val row: Boolean, val gap: Dp, val pad: PaddingValues, val tile: Dp, val tileH: Dp, val tileR: RoundedCornerShape,
    val mono: Int, val clamp: Int, val textGap: Dp, val textPad: PaddingValues, val titleFs: TextUnit, val titleLh: TextUnit,
)

/** Gap between cards for a card layout. */
fun listGap(layout: CardLayout) = when (layout) {
    CardLayout.Compact -> 6.dp
    CardLayout.Large -> 14.dp
    CardLayout.Comfortable -> 10.dp
}

/**
 * Shelf / archive card: tap opens, long-press opens options, horizontal drag swipes.
 * [onSwipe] fires once the card has flown off (remove/archive/restore/delete) or on release (read/share).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    item: Item,
    layout: CardLayout,
    swipeRight: SwipeAction,
    swipeLeft: SwipeAction,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onSwipe: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    title: AnnotatedString = AnnotatedString(item.title),
    drawStrike: Boolean = false,
    enter: CardEnter = CardEnter.None,
    enterDelay: Int = 0,
) {
    val c = Stash.colors
    val ts = Stash.title
    val calm = Stash.calm
    val scope = rememberCoroutineScope()
    val fs = ts.size.value
    val lay = when (layout) {
        CardLayout.Compact -> Lay(true, 10.dp, PaddingValues(10.dp, 9.dp, 28.dp, 9.dp), 40.dp, 40.dp, RoundedCornerShape(8.dp), 17, 1, 2.dp,
            PaddingValues(0.dp), (fs - 1).sp, ((fs - 1) * 1.35f).toInt().sp)
        CardLayout.Large -> Lay(false, 12.dp, PaddingValues(bottom = 14.dp), 0.dp, 156.dp, RoundedCornerShape(13.dp, 13.dp, 0.dp, 0.dp), 52, 3, 6.dp,
            PaddingValues(start = 14.dp, end = 30.dp), (fs + 2).sp, ((fs + 2) * 1.35f).toInt().sp)
        CardLayout.Comfortable -> Lay(true, 12.dp, PaddingValues(12.dp, 12.dp, 26.dp, 12.dp), 60.dp, 60.dp, RoundedCornerShape(10.dp), 24, 3, 6.dp,
            PaddingValues(0.dp), ts.size, ts.lineHeight)
    }
    val read = item.read && !item.archived

    // Entrance: rise on first load, flutter for a just-added item.
    val enterAnim = remember { Animatable(if (enter == CardEnter.None || calm) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enterAnim.value < 1f) {
            delay(enterDelay.toLong())
            enterAnim.animateTo(1f, tween(if (enter == CardEnter.Flutter) 700 else 460, easing = CubicBezierEasing(.2f, .8f, .2f, 1f)))
        }
    }

    // Swipe.
    val dx = remember { Animatable(0f) }
    val widthPx = remember { floatArrayOf(0f) }
    // Raw finger distance, tracked synchronously so fast drags are not lost.
    val raw = remember { floatArrayOf(0f) }
    val swipeCb by rememberUpdatedState(onSwipe)
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 110.dp.toPx() }
    fun actionFor(x: Float) = (if (x > 0) swipeRight else swipeLeft).takeIf { it != SwipeAction.None }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && dx.value == 0f) 0.97f else 1f, motion(spring(0.45f, Spring.StiffnessMedium)))

    val strike = remember { Animatable(if (drawStrike && !calm) 0f else 1f) }
    LaunchedEffect(drawStrike) {
        if (drawStrike && !calm) {
            strike.snapTo(0f)
            delay(50)
            strike.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
    }
    val titleColor by animateColorAsState(if (read) c.faded else c.ink, motion(tween(300)))
    val tileAlpha by animateFloatAsState(if (read) 0.55f else 1f, motion(tween(300)))

    val stateLabel = if (item.archived) "Archived. " else if (item.read) "Read. " else "Unread. "
    val a11yActions = buildList {
        actionFor(1f)?.let { a -> add(CustomAccessibilityAction(swipeVisual(a, item, c).third) { onSwipe(a); true }) }
        actionFor(-1f)?.let { a -> add(CustomAccessibilityAction(swipeVisual(a, item, c).third) { onSwipe(a); true }) }
        add(CustomAccessibilityAction("More options") { onMenu(); true })
    }

    Box(
        modifier
            .graphicsLayer {
                val p = enterAnim.value
                if (enter == CardEnter.Flutter) {
                    translationY = (1 - p) * -46.dp.toPx()
                    rotationZ = (1 - p) * 4f
                } else translationY = (1 - p) * 14.dp.toPx()
                alpha = min(1f, p * 1.6f)
            }
            .clearAndSetSemantics {
                contentDescription = stateLabel + item.title + ". " + item.site + ", " + (if (item.archived) item.archLabel else item.meta)
                onClick { onOpen(); true }
                onLongClick { onMenu(); true }
                customActions = a11yActions
            },
    ) {
        // Action revealed under the card while dragging.
        val x = dx.value
        val act = if (x != 0f) actionFor(x) else null
        if (act != null) {
            val (color, icon, label) = swipeVisual(act, item, c)
            val iconScale = 0.6f + min(abs(x), threshold) / threshold * 0.55f
            Row(
                Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)).background(color)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (x > 0) Arrangement.spacedBy(8.dp, Alignment.Start) else Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                val iconView = @Composable {
                    Icon(icon, null, Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = c.onPrimary)
                }
                if (x > 0) iconView()
                Txt(label, inter(14, 600), c.onPrimary)
                if (x < 0) iconView()
            }
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX = dx.value
                    rotationZ = dx.value * 0.012f
                    scaleX = pressScale
                    scaleY = pressScale
                    shadowElevation = if (dx.value != 0f) 16f else 3f
                    shape = RoundedCornerShape(14.dp)
                    clip = true
                    widthPx[0] = size.width
                }
                .background(c.surface)
                .border(1.dp, c.outline, RoundedCornerShape(14.dp))
                .pointerInput(swipeRight, swipeLeft, item.read, item.archived) {
                    detectHorizontalDragGestures(
                        onDragStart = { raw[0] = 0f },
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            raw[0] += drag
                            // Resist when there is no action that way.
                            val x = if (actionFor(raw[0]) == null) raw[0] * 0.22f else raw[0]
                            scope.launch { dx.snapTo(x) }
                        },
                        onDragEnd = {
                            val a = if (abs(raw[0]) > threshold) actionFor(raw[0]) else null
                            raw[0] = 0f
                            val flyOff = a == SwipeAction.Remove || a == SwipeAction.Delete || a == SwipeAction.Archive || a == SwipeAction.Restore
                            scope.launch {
                                if (flyOff) {
                                    val dir = if (dx.value > 0) 1 else -1
                                    if (!calm) dx.animateTo(dir * (widthPx[0] + 120f), tween(230))
                                    swipeCb(a!!)
                                } else {
                                    if (a != null) swipeCb(a)
                                    dx.animateTo(0f, if (calm) spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh) else tween(380, easing = Overshoot))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dx.animateTo(0f) } },
                    )
                }
                .combinedClickable(source, indication = null, onClick = onOpen, onLongClick = onMenu),
        ) {
            val content = @Composable {
                Box(
                    (if (lay.row) Modifier.size(lay.tile) else Modifier.fillMaxWidth().height(lay.tileH))
                        .graphicsLayer { alpha = tileAlpha }.clip(lay.tileR).background(Color(item.tile)),
                    contentAlignment = Alignment.Center,
                ) { Txt(item.mono, lora(lay.mono, 700), Color(item.ink)) }
                Column(Modifier.padding(lay.textPad), verticalArrangement = Arrangement.spacedBy(lay.textGap)) {
                    Text(
                        title, color = titleColor, maxLines = lay.clamp, overflow = TextOverflow.Ellipsis,
                        style = lora(lay.titleFs, 600, lay.titleLh),
                    )
                    Box(
                        Modifier.drawWithContent {
                            drawContent()
                            if (read) {
                                val y = size.height * 0.52f
                                drawLine(c.faded, Offset(0f, y), Offset(size.width * strike.value, y), 1.5.dp.toPx())
                            }
                        },
                    ) {
                        Txt(
                            "${item.site} · ${if (item.archived) item.archLabel else item.meta}", inter(12), c.faded,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (lay.row) {
                Row(Modifier.fillMaxWidth().padding(lay.pad), horizontalArrangement = Arrangement.spacedBy(lay.gap), verticalAlignment = Alignment.CenterVertically) { content() }
            } else {
                Column(Modifier.fillMaxWidth().padding(lay.pad), verticalArrangement = Arrangement.spacedBy(lay.gap)) { content() }
            }
            UnreadDot(!item.read, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp))
        }
    }
}

/** Red unread dot; it "cracks" (grows then vanishes) when the item is read. */
@Composable
fun UnreadDot(visible: Boolean, modifier: Modifier = Modifier) {
    val c = Stash.colors
    AnimatedVisibility(
        visible, modifier,
        enter = enter(androidx.compose.animation.scaleIn(tween(380, easing = Overshoot))),
        exit = exit(scaleOut(tween(400), targetScale = 0f) + fadeOut(tween(400))),
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(c.surface).padding(2.dp).clip(CircleShape).background(c.danger))
    }
}
