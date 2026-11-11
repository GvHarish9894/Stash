package com.techgv.stash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.StashState
import com.techgv.stash.StashViewModel
import com.techgv.stash.ui.components.BackButton
import com.techgv.stash.ui.components.StashIcons
import com.techgv.stash.ui.components.Txt
import com.techgv.stash.ui.components.contentWidth
import com.techgv.stash.ui.components.enter
import com.techgv.stash.ui.components.exit
import com.techgv.stash.ui.components.motion
import com.techgv.stash.ui.components.mutableLast
import com.techgv.stash.ui.components.navBarsPad
import com.techgv.stash.ui.components.statusBarsPad
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora
import com.techgv.stash.ui.theme.playfair

private data class ReaderSize(val h: Int, val hl: Int, val b: Int, val bl: Int, val gap: Int)

private val readerSizes = listOf(
    ReaderSize(22, 29, 15, 23, 16), ReaderSize(24, 31, 17, 26, 18), ReaderSize(27, 34, 19, 29, 20), ReaderSize(30, 37, 21, 32, 22),
)

/** Placeholder text lines (the real article text arrives with the content fetcher). */
@Composable
private fun Lines(color: Color, widths: List<Float>, h: Int = 9, gap: Int = 9) {
    Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        widths.forEach { Box(Modifier.fillMaxWidth(it).height(h.dp).clip(RoundedCornerShape((h / 2).dp)).background(color)) }
    }
}

@Composable
fun ReaderScreen(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val item = s.find(s.readerId) ?: return
    val mins = item.meta.substringAfterLast("· ")

    Box(Modifier.fillMaxSize().background(if (s.readerClean) c.bg else Color.White)) {
        Column(Modifier.fillMaxSize()) {
            // Top bar in the accent colour.
            Box(Modifier.fillMaxWidth().background(c.primary).statusBarsPad()) {
                Row(
                    Modifier.contentWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BackButton("Close and return to Stash", vm::closeReader, c.onPrimary, StashIcons.close(1.8f))
                    Column(Modifier.weight(1f)) {
                        Txt(item.title, inter(14, 600), c.onPrimary, maxLines = 1)
                        Txt(item.site, inter(12), c.onPrimaryMuted, maxLines = 1)
                    }
                    val bg by animateColorAsState(if (s.readerClean) c.onPrimary else Color.Transparent, motion(tween(200)))
                    Box(
                        Modifier.height(36.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                            .border(1.dp, Color(0x80FBF6EA), RoundedCornerShape(18.dp)).clickable(onClick = vm::toggleClean)
                            .semantics { contentDescription = "Reader mode"; selected = s.readerClean }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Txt("Aa", playfair(15), if (s.readerClean) c.primary else c.onPrimary) }
                    Box(
                        Modifier.padding(end = 2.dp).height(36.dp).clip(RoundedCornerShape(18.dp))
                            .border(1.dp, Color(0x80FBF6EA), RoundedCornerShape(18.dp)).clickable(onClick = vm::readerUnread).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Txt("Mark unread", inter(12, 600), c.onPrimary) }
                }
            }
            // Page load bar.
            val load = remember(item.id) { Animatable(0.08f) }
            val loadSpec = motion(tween<Float>(1200, easing = CubicBezierEasing(.3f, .7f, .4f, 1f)))
            LaunchedEffect(item.id) { load.animateTo(1f, loadSpec) }
            Box(Modifier.fillMaxWidth().height(3.dp).background(c.variant)) {
                Box(Modifier.fillMaxWidth(load.value).height(3.dp).background(c.accent))
            }

            if (!s.readerClean) {
                Column(Modifier.contentWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFDCDCDC)))
                    Txt(item.title, TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp), Color(0xFF222222))
                    Txt("The publisher's page renders here, as on the web.", inter(13), Color(0xFF666666))
                    Lines(Color(0xFFE6E6E6), listOf(1f, .94f, .88f, .6f), h = 10, gap = 10)
                }
            } else {
                val f = readerSizes[s.readerFs]
                val h by animateIntAsState(f.h, motion(tween(200)))
                val b by animateIntAsState(f.b, motion(tween(200)))
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        Modifier.contentWidth().padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 90.dp).navBarsPad(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Txt("${item.site} · $mins".uppercase(), inter(12, 600).copy(letterSpacing = 1.5.sp), c.primary)
                        Txt(item.title, lora(h.sp, 700, (h * f.hl / f.h).sp), c.ink)
                        Txt(item.desc, lora(b.sp, 400, (b * f.bl / f.b).sp, italic = true), c.faded)
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f).height(1.dp).background(c.outline))
                            Txt("❦", inter(15), c.accent)
                            Box(Modifier.weight(1f).height(1.dp).background(c.outline))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(f.gap.dp)) {
                            Lines(c.variant, listOf(1f, .96f, .92f, .58f))
                            Lines(c.variant, listOf(1f, .9f, .97f, .4f))
                        }
                        Txt("In the app, the article's own text appears here, cleaned of ads, pop-ups and menus.", inter(12, lineHeight = 17), c.faded)
                    }
                }
            }
        }

        if (s.readerClean) Row(
            Modifier.align(Alignment.BottomEnd).navBarsPad().padding(end = 16.dp, bottom = 28.dp)
                .graphicsLayer { shadowElevation = 16f; shape = RoundedCornerShape(24.dp); clip = true }
                .background(c.surface).border(1.dp, c.outline, RoundedCornerShape(24.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SizeButton("A−", 14, "Smaller text") { vm.readerSize(-1) }
            SizeButton("A+", 19, "Larger text") { vm.readerSize(1) }
        }

        val toast = remember { mutableLast<String>() }
        if (s.readerToast.isNotEmpty()) toast.value = s.readerToast
        AnimatedVisibility(
            s.readerToast.isNotEmpty(),
            Modifier.align(Alignment.BottomCenter).navBarsPad().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            enter = enter(slideInVertically { it } + fadeIn()), exit = exit(fadeOut()),
        ) {
            Row(
                Modifier.contentWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.good),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(StashIcons.check(2.2f), null, Modifier.size(16.dp), tint = c.onPrimary)
                Txt(toast.value ?: "", inter(13, 500), c.onPrimary)
            }
        }
    }
}

@Composable
private fun SizeButton(text: String, size: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp, 40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Txt(text, lora(size, 600), Stash.colors.ink) }
}
