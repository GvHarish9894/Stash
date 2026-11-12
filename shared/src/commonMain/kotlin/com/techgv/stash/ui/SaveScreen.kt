package com.techgv.stash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.StashState
import com.techgv.stash.StashViewModel
import com.techgv.stash.ui.components.PrimaryButton
import com.techgv.stash.ui.components.Txt
import com.techgv.stash.ui.components.contentWidth
import com.techgv.stash.ui.components.navBarsPad
import com.techgv.stash.ui.components.statusBarsPad
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Out = CubicBezierEasing(.2f, .8f, .2f, 1f)

/** Animatable that runs 0→1 after [delayMs]; instant when Reduce motion is on. */
@Composable
private fun timeline(delayMs: Long, durationMs: Int, easing: androidx.compose.animation.core.Easing = Out): Animatable<Float, *> {
    val calm = Stash.calm
    val a = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!calm) launch {
            delay(delayMs)
            a.animateTo(1f, tween(durationMs, easing = easing))
        }
    }
    return a
}

/** The share-sheet result: the page tucks into a closing book. */
@Composable
fun SaveScreen(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val save = s.saveItem ?: return
    Box(Modifier.fillMaxSize().background(Color.White)) {
        // The page the user shared from.
        Column(Modifier.contentWidth().statusBarsPad().padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFEEEEEE)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Txt(save.site, inter(13), Color(0xFF555555))
            }
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFD9D9D9)))
            Txt(save.title, TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp), Color(0xFF222222))
            Box(Modifier.fillMaxWidth(.92f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE4E4E4)))
            Box(Modifier.fillMaxWidth(.8f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE4E4E4)))
        }
        val scrim = timeline(0, 250)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim.value }.background(Color(0x731E1812)))

        val sheet = timeline(0, 360)
        Column(
            Modifier.align(Alignment.BottomCenter).contentWidth()
                .graphicsLayer { translationY = (1 - sheet.value) * size.height }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg)
                .navBarsPad().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.outline))
            Book()
            val text = timeline(1250, 350, androidx.compose.animation.core.LinearOutSlowInEasing)
            val textMod = Modifier.graphicsLayer { alpha = text.value; translationY = (1 - text.value) * 8.dp.toPx() }
            Txt("Tucked into your shelf", lora(24, 700), c.ink, textMod)
            Txt(save.title, inter(14, lineHeight = 20), c.faded, textMod.widthIn(max = 300.dp), align = TextAlign.Center)
            PrimaryButton("Done", vm::finishSave, textMod, height = 48.dp, radius = 24.dp, size = 14)
        }
    }
}

@Composable
private fun Book() {
    val c = Stash.colors
    val tuck = timeline(250, 800, CubicBezierEasing(.5f, 0f, .3f, 1f))
    val cover = timeline(850, 450)
    val ribbon = timeline(1200, 350)
    Box(Modifier.padding(top = 6.dp).size(200.dp, 180.dp)) {
        val coverShape = RoundedCornerShape(6.dp, 12.dp, 12.dp, 6.dp)
        Box(Modifier.offset(30.dp, 50.dp).size(140.dp, 120.dp).clip(coverShape).background(Color(0xFF5E3A20)))
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
            Box(Modifier.size(12.dp, 120.dp).background(Color(0xFF5E3A20)))
            Box(Modifier.offset(22.dp, 18.dp).size(104.dp, 1.dp).background(c.accent))
            Box(Modifier.offset(22.dp, 102.dp).size(104.dp, 1.dp).background(c.accent))
        }
        // The saved page: drops in, shrinks and disappears into the book.
        Column(
            Modifier.offset(52.dp, 60.dp).size(96.dp, 70.dp)
                .graphicsLayer {
                    val p = tuck.value
                    val (ty, rot, sc, al) = if (p < .55f) {
                        val k = p / .55f
                        listOf(-70f + 64f * k, -8f + 6f * k, 1f - .18f * k, 1f)
                    } else {
                        val k = (p - .55f) / .45f
                        listOf(-6f + 46f * k, -2f + 2f * k, .82f - .42f * k, 1f - k)
                    }
                    translationY = ty * density
                    rotationZ = rot
                    scaleX = sc
                    scaleY = sc
                    alpha = al
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
