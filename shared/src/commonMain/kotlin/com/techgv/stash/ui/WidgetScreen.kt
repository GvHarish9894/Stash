package com.techgv.stash.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techgv.stash.StashState
import com.techgv.stash.StashViewModel
import com.techgv.stash.model.Screen
import com.techgv.stash.model.WidgetSize
import com.techgv.stash.nextUp
import com.techgv.stash.onShelf
import com.techgv.stash.ui.components.BackButton
import com.techgv.stash.ui.components.Candle
import com.techgv.stash.ui.components.PrimaryButton
import com.techgv.stash.ui.components.Segmented
import com.techgv.stash.ui.components.ToastHost
import com.techgv.stash.ui.components.Txt
import com.techgv.stash.ui.components.contentWidth
import com.techgv.stash.ui.components.navBarsPad
import com.techgv.stash.ui.components.statusBarsPad
import com.techgv.stash.ui.theme.LocalStrings
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora
import com.techgv.stash.ui.theme.playfair

@Composable
fun WidgetScreen(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val L = LocalStrings.current
    val next = nextUp(s.items)
    val unread = s.items.onShelf().count { !it.read }
    val look = streakLook(s)
    val icon = s.prefs.appIcon

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.contentWidth().statusBarsPad().verticalScroll(rememberScrollState()).navBarsPad()) {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BackButton("Back to settings", { vm.go(Screen.Settings) })
                Txt("Home-screen widget", lora(24, 700), c.ink, Modifier.semantics { heading() })
            }

            // Wallpaper with the widget and a dock.
            Box(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().height(470.dp).clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF3A2A1E))
                    .drawBehind {
                        drawRect(Brush.radialGradient(listOf(Color(0x38E0B45C), Color.Transparent), Offset(size.width * .2f, size.height * .15f), size.maxDimension * .45f))
                        drawRect(Brush.radialGradient(listOf(Color(0x8C7A4E2D), Color.Transparent), Offset(size.width * .85f, size.height * .8f), size.maxDimension * .55f))
                    }
                    .padding(start = 16.dp, end = 16.dp, top = 26.dp),
            ) {
                val card = Modifier.graphicsLayer { shadowElevation = 26f; shape = RoundedCornerShape(24.dp); clip = true }
                    .background(c.surface).border(1.dp, c.outline, RoundedCornerShape(24.dp)).padding(14.dp)
                if (s.prefs.widgetSize == WidgetSize.Medium) {
                    Column(Modifier.fillMaxWidth().then(card), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppIconTile(icon, 22.dp, 7.dp, 14, shadow = false)
                            Txt("Stash", playfair(17), c.primary, Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Candle(14.dp, c.faded, look.flame, flicker = look.flicker)
                                Txt("${s.streak}", inter(12, 700), look.fg)
                            }
                            Txt("· $unread ${L.unread}", inter(12), c.faded)
                        }
                        next.forEach { i ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg).clickable { vm.open(i.id) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(i.tile)), contentAlignment = Alignment.Center) {
                                    Txt(i.mono, lora(15, 700), Color(i.ink))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Txt(i.title, lora(13, 600, 17), c.ink, maxLines = 1)
                                    Txt("${i.site} · ${i.meta.substringAfterLast("· ")}", inter(11), c.faded, maxLines = 1)
                                }
                            }
                        }
                        if (next.isEmpty()) Txt(
                            "All caught up. Nothing waiting on your shelf.", lora(14, 600), c.faded,
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 18.dp), align = TextAlign.Center,
                        )
                    }
                } else {
                    Column(Modifier.size(160.dp).then(card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Txt("Stash", playfair(15), c.primary, Modifier.weight(1f))
                            Candle(13.dp, c.faded, look.flame, flicker = look.flicker)
                            Txt("${s.streak}", inter(12, 700), look.fg)
                        }
                        Txt("$unread", playfair(40, 44), c.ink)
                        Txt(L.unread, inter(11), c.faded, Modifier.offset(y = (-4).dp))
                        Box(Modifier.weight(1f))
                        Txt(
                            next.firstOrNull()?.title ?: "All caught up", lora(12, 600, 16), c.ink,
                            Modifier.clickable { next.firstOrNull()?.let { vm.open(it.id) } }, maxLines = 2,
                        )
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    repeat(4) { k ->
                        if (k == 2) AppIconTile(icon, 50.dp, 15.dp, 28)
                        else Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0x40F4ECD8)))
                    }
                }
            }

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(WidgetSize.entries, s.prefs.widgetSize, { it.label }, { w -> vm.prefs { copy(widgetSize = w) } }, Modifier.fillMaxWidth(), fill = true)
                PrimaryButton("Add to home screen", { vm.settingsToast("Pin request sent to your launcher") }, Modifier.fillMaxWidth())
            }
        }
        ToastHost(s.settingsToast, 32.dp, width = 240.dp)
    }
}
