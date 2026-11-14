package com.techgv.stash.feature.quicksave

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.component.BottomSheet
import com.techgv.stash.core.designsystem.component.PrimaryButton
import com.techgv.stash.core.designsystem.component.Txt
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.common_done
import com.techgv.stash.core.resources.quicksave_already
import com.techgv.stash.core.resources.quicksave_failed_body
import com.techgv.stash.core.resources.quicksave_failed_title
import com.techgv.stash.core.resources.quicksave_nothing_body
import com.techgv.stash.core.resources.quicksave_nothing_title
import com.techgv.stash.core.resources.save_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** What a share into Stash did. Android maps its own share outcome onto this. */
enum class QuickSaveOutcome {
    Saved,

    /** The link was already there: moved to the top, unread again. */
    AlreadyOnShelf,

    /** The shared text held no link. */
    NothingToSave,

    /** Storage failed; nothing was saved. */
    NotSaved,
}

/** When the sheet closes on its own: 4.5 s after a save, 2 s for nothing to save; never for a failure the user must read. */
val QuickSaveOutcome.autoClose: Duration?
    get() = when (this) {
        QuickSaveOutcome.Saved, QuickSaveOutcome.AlreadyOnShelf -> 4500.milliseconds
        QuickSaveOutcome.NothingToSave -> 2.seconds
        QuickSaveOutcome.NotSaved -> null
    }

/**
 * The quick-save sheet the Android share target shows over the sharing app (a translucent activity). It needs only a
 * surrounding `StashTheme`: no navigation host, no ViewModel. A save plays the tuck-into-book hero (static under Reduce
 * motion). Done, back, the scrim, the handle and the auto-close ([autoClose]) all call [onDone].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QuickSaveSheet(outcome: QuickSaveOutcome, onDone: () -> Unit) {
    val c = Stash.colors
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(outcome) {
        val after = outcome.autoClose ?: return@LaunchedEffect
        delay(after)
        done()
    }
    // Deprecated for NavigationEventHandler, whose artifact is not in the catalog; shared's App uses the same.
    @Suppress("DEPRECATION")
    BackHandler(onBack = onDone)
    // Starts hidden so the sheet slides up.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val saved = outcome == QuickSaveOutcome.Saved || outcome == QuickSaveOutcome.AlreadyOnShelf
    val title = stringResource(
        when (outcome) {
            QuickSaveOutcome.Saved -> Res.string.save_title
            QuickSaveOutcome.AlreadyOnShelf -> Res.string.quicksave_already
            QuickSaveOutcome.NothingToSave -> Res.string.quicksave_nothing_title
            QuickSaveOutcome.NotSaved -> Res.string.quicksave_failed_title
        },
    )
    val body = when (outcome) {
        QuickSaveOutcome.NothingToSave -> stringResource(Res.string.quicksave_nothing_body)
        QuickSaveOutcome.NotSaved -> stringResource(Res.string.quicksave_failed_body)
        else -> null
    }
    BottomSheet(shown, onDone, paneTitle = title, background = c.bg, gap = 14.dp, horizontal = Alignment.CenterHorizontally) {
        // The text rises after the book closes; without the hero it is there at once.
        val text = if (saved) timeline(StashMotion.QUICK_SAVE_TEXT_DELAY, StashMotion.QUICK_SAVE_TEXT) else null
        val rise = Modifier.graphicsLayer {
            val p = text?.value ?: 1f
            alpha = p
            translationY = (1 - p) * 8.dp.toPx()
        }
        if (saved) Book()
        Txt(title, Stash.type.screenTitle, c.ink, rise.semantics { heading() }, align = TextAlign.Center)
        if (body != null) Txt(body, Stash.type.callout, c.inkFaded, rise.widthIn(max = 300.dp), align = TextAlign.Center)
        PrimaryButton(stringResource(Res.string.common_done), onDone, rise, height = 48.dp, radius = 24.dp, style = Stash.type.calloutStrong)
    }
}

/** 0 → 1 after [delayMillis] over [durationMillis]; 1 at once under Reduce motion. */
@Composable
private fun timeline(delayMillis: Int, durationMillis: Int): Animatable<Float, *> {
    val calm = Stash.reduceMotion
    val anim = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (calm) return@LaunchedEffect
        delay(delayMillis.toLong())
        anim.animateTo(1f, tween(durationMillis, easing = StashMotion.EaseOutEmphasized))
    }
    return anim
}

/** The hero: the link card drops and tucks into the book, the cover closes, the ribbon drops. Decorative. */
@Composable
private fun Book() {
    val c = Stash.colors
    val tuck = timeline(StashMotion.QUICK_SAVE_TUCK_DELAY, StashMotion.QUICK_SAVE_TUCK)
    val cover = timeline(StashMotion.QUICK_SAVE_COVER_DELAY, StashMotion.QUICK_SAVE_COVER)
    val ribbon = timeline(StashMotion.QUICK_SAVE_RIBBON_DELAY, StashMotion.QUICK_SAVE_RIBBON)
    Box(Modifier.padding(top = 6.dp).size(200.dp, 180.dp)) {
        val coverShape = RoundedCornerShape(6.dp, 12.dp, 12.dp, 6.dp)
        Box(Modifier.offset(30.dp, 50.dp).size(140.dp, 120.dp).clip(coverShape).background(c.root))
        Box(
            Modifier.offset(30.dp, 50.dp).size(140.dp, 120.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, .5f)
                    rotationY = -70f * (1 - cover.value)
                    cameraDistance = 12f * density
                    shadowElevation = 18f
                    shape = coverShape
                    clip = true
                }
                .background(c.primary),
        ) {
            Box(Modifier.size(12.dp, 120.dp).background(c.root))
            Box(Modifier.offset(22.dp, 18.dp).size(104.dp, 1.dp).background(c.brass))
            Box(Modifier.offset(22.dp, 102.dp).size(104.dp, 1.dp).background(c.brass))
        }
        // The saved page: drops from −70 dp, shrinks to 0.4 and disappears into the book at +40 dp.
        Column(
            Modifier.offset(52.dp, 60.dp).size(96.dp, 70.dp)
                .graphicsLayer {
                    val p = tuck.value
                    val ty: Float
                    val scale: Float
                    if (p < .55f) {
                        val k = p / .55f
                        ty = -70f + 64f * k
                        rotationZ = -8f + 6f * k
                        scale = 1f - .18f * k
                    } else {
                        val k = (p - .55f) / .45f
                        ty = -6f + 46f * k
                        rotationZ = -2f + 2f * k
                        scale = .82f - .42f * k
                        alpha = 1f - k
                    }
                    translationY = ty * density
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(8.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(8.dp)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.fillMaxWidth(.9f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ink))
            Box(Modifier.fillMaxWidth(.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ink))
            Box(Modifier.fillMaxWidth(.5f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.outline))
        }
        // Bookmark ribbon with a notched end.
        Box(
            Modifier.offset(132.dp, 46.dp).size(18.dp, 70.dp)
                .graphicsLayer { transformOrigin = TransformOrigin(.5f, 0f); scaleY = ribbon.value }
                .clip(GenericShape { s, _ ->
                    moveTo(0f, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height); lineTo(s.width / 2, s.height * .82f); lineTo(0f, s.height); close()
                })
                .background(c.danger),
        )
    }
}
