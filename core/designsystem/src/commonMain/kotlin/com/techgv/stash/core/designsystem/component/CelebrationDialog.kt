package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.presentation.weekdayInitial
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.domain.DayMark
import com.techgv.stash.core.domain.WeekDay
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.celebration_a11y
import com.techgv.stash.core.resources.celebration_caption
import com.techgv.stash.core.resources.celebration_keep_reading
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The celebration is drawn on its own dark scrim in both themes, so it keeps the prototype's fixed palette.
private val Scrim = Color(0xEB160F09)
private val Gold = Color(0xFFE0B45C)
private val Parchment = Color(0xFFF4ECD8)
private val Cream = Color(0xFFEADFC8)
private val Light = Color(0xFFFBF6EA)
private val Tan = Color(0xFFD2A26A)
private val Muted = Color(0xFFB8A88E)
private val Night = Color(0xFF1E1812)
private val Wick = Color(0xFF3B2F23)
private val Ember = Color(0xFFD97A6F)

/**
 * First read of the day: the candle lights, the count rolls from [from] to [to], sparks rise and today's dot pops.
 * Announced as "Streak extended to 13 days. [message]". Like a sheet, "Keep reading" takes keyboard focus on open, focus
 * stays in the dialog while it is shown and Esc closes it; mark the content underneath with `Modifier.coveredByModal`.
 */
@Composable
fun CelebrationDialog(visible: Boolean, from: Int, to: Int, message: String, week: List<WeekDay>, onClose: () -> Unit) {
    AnimatedVisibility(visible, enter = enter(fadeIn(tween(StashMotion.SCRIM_IN))), exit = exit(fadeOut(StashMotion.exit(StashMotion.SHEET_OUT)))) {
        val reduceMotion = Stash.reduceMotion
        fun anim() = Animatable(if (reduceMotion) 1f else 0f)
        val ignite = remember { anim() }
        val glow = remember { anim() }
        val roll = remember { anim() }
        val text = remember { anim() }
        val pop = remember { anim() }
        val embers = remember { List(10) { anim() } }
        LaunchedEffect(Unit) {
            if (reduceMotion) return@LaunchedEffect
            launch { delay(StashMotion.CELEBRATION_IGNITE_DELAY.toLong()); ignite.animateTo(1f, StashMotion.enter(StashMotion.CELEBRATION_IGNITE)) }
            launch { delay(StashMotion.CELEBRATION_GLOW_DELAY.toLong()); glow.animateTo(1f, tween(StashMotion.CELEBRATION_GLOW)) }
            launch { delay(StashMotion.CELEBRATION_ROLL_DELAY.toLong()); roll.animateTo(1f, StashMotion.enter(StashMotion.CELEBRATION_ROLL)) }
            launch { delay(StashMotion.CELEBRATION_TEXT_DELAY.toLong()); text.animateTo(1f, tween(StashMotion.CELEBRATION_TEXT)) }
            launch { delay(StashMotion.CELEBRATION_DOT_DELAY.toLong()); pop.animateTo(1f, StashMotion.enter(StashMotion.CELEBRATION_DOT)) }
            val delays = listOf(0, 800, 1600, 400, 2000, 1100, 200, 1800, 600, 1400)
            embers.forEachIndexed { k, e ->
                launch { delay(delays[k].toLong()); e.animateTo(1f, tween(StashMotion.CELEBRATION_SPARK, easing = LinearEasing)) }
            }
        }
        val announcement = pluralStringResource(Res.plurals.celebration_a11y, to, to, message)
        val closeFocus = remember { FocusRequester() }
        LaunchedEffect(visible) { if (visible) closeFocus.requestFocus() }
        Box(
            Modifier.fillMaxSize().background(Scrim)
                .pointerInput(Unit) { detectTapGestures { } } // swallows taps; adds no semantics
                .onKeyEvent { if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) { onClose(); true } else false }
                .focusProperties { if (visible) onExit = { cancelFocusChange() } }
                .focusGroup()
                .semantics { contentDescription = announcement; paneTitle = announcement; liveRegion = LiveRegionMode.Polite },
        ) {
          // Centred when it fits; on a short window (phone landscape) it scrolls, so "Keep reading" stays reachable.
          Column(
            Modifier.scrollFillingViewport(rememberScrollState()).fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
          ) {
            Box(Modifier.size(160.dp, 170.dp), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.align(Alignment.TopCenter).size(140.dp)
                        .graphicsLayer { alpha = glow.value; scaleX = .5f + .5f * glow.value; scaleY = .5f + .5f * glow.value }
                        .background(Brush.radialGradient(0f to Gold.copy(alpha = .55f), .45f to Gold.copy(alpha = .15f), .7f to Color.Transparent), CircleShape),
                )
                val lit by remember { derivedStateOf { ignite.value >= 1f } }
                Candle(150.dp, Cream, Gold, core = Light, band = Tan, wick = Wick, flicker = lit) {
                    // Overshoot 0 → 1.35 → 1 for the flame.
                    val v = ignite.value
                    if (v < .6f) v / .6f * 1.35f else 1.35f - (v - .6f) / .4f * .35f
                }
            }
            Box(Modifier.size(200.dp, 76.dp).clip(RoundedCornerShape(0.dp))) {
                val big = Stash.type.streakNumber
                Txt("$from", big, Gold, Modifier.fillMaxWidth().graphicsLayer {
                    translationY = -roll.value * size.height; alpha = 1 - roll.value
                }, align = TextAlign.Center)
                Txt("$to", big, Gold, Modifier.fillMaxWidth().graphicsLayer {
                    translationY = (1 - roll.value) * size.height; alpha = roll.value
                }, align = TextAlign.Center)
            }
            val up = Modifier.graphicsLayer { alpha = text.value; translationY = (1 - text.value) * 8.dp.toPx() }
            Txt(stringResource(Res.string.celebration_caption), Stash.type.eyebrow, Cream, up)
            Txt(message, Stash.type.celebrationMessage, Parchment, up.widthIn(max = 300.dp), align = TextAlign.Center)
            Row(up.widthIn(max = 300.dp).fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { d ->
                    val description = weekDayDescription(d)
                    Column(
                        Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Txt(stringResource(weekdayInitial(d.date.dayOfWeek)), Stash.type.captionStrong, Muted)
                        val done = d.mark != DayMark.NotRead
                        Box(
                            Modifier.size(30.dp).graphicsLayer {
                                val sc = if (!d.isToday) 1f else pop.value.let { if (it < .6f) it / .6f * 1.3f else 1.3f - (it - .6f) / .4f * .3f }
                                scaleX = sc; scaleY = sc
                            }.clip(CircleShape)
                                .background(if (d.isToday) Gold else if (done) Tan else Color.Transparent)
                                .border(2.dp, if (d.isToday) Light else Tan, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (done || d.isToday) Icon(StashIcons.check(3f), null, Modifier.size(13.dp), tint = Night) }
                    }
                }
            }
            PrimaryButton(
                stringResource(Res.string.celebration_keep_reading), onClose, up.padding(top = 14.dp).focusRequester(closeFocus), height = 52.dp, radius = 26.dp,
                bg = Gold, fg = Night,
            )
          }
        }
        // Sparks drifting up once from the lower third.
        Canvas(Modifier.fillMaxSize()) {
            val xs = listOf(.22f, .30f, .38f, .46f, .54f, .60f, .68f, .76f, .84f, .14f)
            val sizes = listOf(6, 4, 5, 7, 4, 6, 5, 4, 6, 5)
            val colors = listOf(Gold, Parchment, Ember, Gold, Parchment, Gold, Ember, Gold, Parchment, Gold)
            embers.forEachIndexed { k, e ->
                val p = e.value
                if (reduceMotion || p <= 0f || p >= 1f) return@forEachIndexed
                val r = sizes[k].dp.toPx() / 2 * (1 - .6f * p)
                val alpha = if (p < .15f) p / .15f else 1 - (p - .15f) / .85f
                drawCircle(colors[k], r, Offset(xs[k] * size.width + r, size.height * .7f - p * 300.dp.toPx()), alpha = alpha)
            }
        }
    }
}

/** Scrolls vertically but is at least as tall as the viewport, so a centring arrangement still centres short content. */
private fun Modifier.scrollFillingViewport(state: ScrollState): Modifier {
    var viewport = 0
    return layout { measurable, constraints ->
        viewport = constraints.maxHeight
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }.verticalScroll(state).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = viewport))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
