package com.techgv.stash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.StashState
import com.techgv.stash.StashViewModel
import com.techgv.stash.model.Item
import com.techgv.stash.model.RemindTime
import com.techgv.stash.model.Screen
import com.techgv.stash.onShelf
import com.techgv.stash.todayEpochDay
import com.techgv.stash.ui.components.BottomSheet
import com.techgv.stash.ui.components.Candle
import com.techgv.stash.ui.components.OutlineButton
import com.techgv.stash.ui.components.PrimaryButton
import com.techgv.stash.ui.components.StashIcons
import com.techgv.stash.ui.components.Txt
import com.techgv.stash.ui.components.contentWidth
import com.techgv.stash.ui.components.enter
import com.techgv.stash.ui.components.exit
import com.techgv.stash.ui.components.mutableLast
import com.techgv.stash.ui.components.navBarsPad
import com.techgv.stash.ui.components.press
import com.techgv.stash.ui.components.statusBarsPad
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora
import com.techgv.stash.ui.theme.playfair
import com.techgv.stash.weekdayOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Long-press options for an item. */
@Composable
fun MenuSheet(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val last = remember { mutableLast<Item>() }
    s.find(s.menuId)?.let { last.value = it }
    val m = last.value ?: return
    val id = m.id
    fun act(f: () -> Unit) = { vm.closeMenu(); f() }

    BottomSheet(
        s.menuId != null, vm::closeMenu, closeLabel = "Close options",
        padding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 28.dp), gap = 0.dp,
    ) {
        Row(
            Modifier.padding(top = 8.dp, bottom = 6.dp).fillMaxWidth().drawBehind {
                drawLine(c.variant, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(m.tile)), contentAlignment = Alignment.Center) {
                Txt(m.mono, lora(18, 700), Color(0xFFF4ECD8))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(m.title, lora(15, 600, 20), c.ink, Modifier.semantics { heading() }, maxLines = 1)
                Txt(m.site, inter(12), c.faded)
            }
        }
        val share = act { vm.toast("Share sheet opened") }
        val copy = act { vm.toast("Link copied") }
        val open = act { vm.open(id) }
        if (m.archived) {
            MenuRow(StashIcons.restore(1.8f), "Restore to shelf", c.ink, onClick = act { vm.restore(id) })
            MenuRow(StashIcons.external(), "Open", c.ink, onClick = open)
            MenuRow(StashIcons.share(), "Share", c.ink, onClick = share)
            MenuRow(StashIcons.copy(), "Copy link", c.ink, onClick = copy)
            MenuRow(StashIcons.trash(1.8f), "Delete forever", c.danger, onClick = act { vm.remove(id) })
        } else {
            if (m.read) MenuRow(StashIcons.dot(1.8f), "Mark as unread", c.ink, c.danger, act { vm.toggleRead(id) })
            else MenuRow(StashIcons.check(2.2f), "Mark as read", c.ink, c.good, act { vm.toggleRead(id) })
            MenuRow(StashIcons.external(), "Open", c.ink, onClick = open)
            MenuRow(StashIcons.share(), "Share", c.ink, onClick = share)
            MenuRow(StashIcons.copy(), "Copy link", c.ink, onClick = copy)
            MenuRow(StashIcons.archive(), "Archive", c.ink, onClick = act { vm.archive(id) })
            MenuRow(StashIcons.trash(1.8f), "Remove from shelf", c.danger, onClick = act { vm.remove(id) })
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, color: Color, iconColor: Color = color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = iconColor)
        Txt(label, inter(15, 500), color)
    }
}

private data class Day(val label: String, val name: String, val today: Boolean)

private val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

/** The last seven days, ending today. */
private fun week(): List<Day> {
    val today = todayEpochDay()
    return (6 downTo 0).map { k ->
        val w = weekdayOf(today - k)
        Day(dayNames[w].take(1), dayNames[w], k == 0)
    }
}

@Composable
fun StreakSheet(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val look = streakLook(s)
    val lit = s.readToday
    val unreadAll = s.items.onShelf().any { !it.read }
    val p = s.prefs
    val reminder = if (p.remind) "Daily reminder ${if (p.remindTime == RemindTime.Morning) "at 9 AM" else "at 7 PM"}${if (p.saver) ", streak saver on." else "."}"
    else "Reminders are off."
    BottomSheet(
        s.streakOpen, vm::closeStreak, closeLabel = "Close streak",
        padding = PaddingValues(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 30.dp), gap = 14.dp,
        horizontal = Alignment.CenterHorizontally,
    ) {
        Candle(56.dp, c.faded, look.flame, flicker = look.flicker)
        Txt("${s.streak}-day reading streak", lora(24, 700), c.ink, Modifier.semantics { heading() }, align = TextAlign.Center)
        Txt(streakStatus(s), inter(14, lineHeight = 20), c.faded, align = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week().forEach { d ->
                val done = if (d.today) lit else true
                Column(
                    Modifier.weight(1f).semantics {
                        contentDescription = d.name + if (d.today) (if (done) ", today, read" else ", today, not read yet") else ", read"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Txt(d.label, inter(11, 600), if (d.today) c.ink else c.faded)
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(if (done) c.primary else Color.Transparent)
                            .border(2.dp, if (done) c.primary else if (d.today) c.accent else c.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (done) Icon(StashIcons.check(3f), null, Modifier.size(14.dp), tint = c.onPrimary) }
                }
            }
        }
        Txt("Open or mark one item as read each day to keep it going. $reminder", inter(12, lineHeight = 17), c.faded, align = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Reminders", { vm.closeStreak(); vm.go(Screen.Settings) }, Modifier.weight(1f), height = 48.dp, size = 14)
            PrimaryButton(
                if (lit || !unreadAll) "Close" else "Read something short",
                { vm.closeStreak(); if (!lit && unreadAll) vm.readShort() },
                Modifier.weight(2f), height = 48.dp, size = 14,
            )
        }
    }
}

/** First read of the day: the candle lights, the number rolls over, embers rise. */
@Composable
fun Celebration(s: StashState, vm: StashViewModel) {
    AnimatedVisibility(s.celebrate, enter = enter(fadeIn(tween(250))), exit = exit(fadeOut(tween(240)))) {
        val calm = Stash.calm
        fun anim() = Animatable(if (calm) 1f else 0f)
        val ignite = remember { anim() }
        val glow = remember { anim() }
        val roll = remember { anim() }
        val text = remember { anim() }
        val pop = remember { anim() }
        val embers = remember { List(10) { anim() } }
        LaunchedEffect(Unit) {
            if (calm) return@LaunchedEffect
            val out = CubicBezierEasing(.2f, .8f, .2f, 1f)
            launch { delay(350); ignite.animateTo(1f, tween(700, easing = out)) }
            launch { delay(450); glow.animateTo(1f, tween(900)) }
            launch { delay(1000); roll.animateTo(1f, tween(500, easing = out)) }
            launch { delay(1250); text.animateTo(1f, tween(350)) }
            launch { delay(1400); pop.animateTo(1f, tween(450, easing = out)) }
            val delays = listOf(0, 800, 1600, 400, 2000, 1100, 200, 1800, 600, 1400)
            embers.forEachIndexed { k, e -> launch { delay(delays[k].toLong()); e.animateTo(1f, tween(3000, easing = LinearEasing)) } }
        }
        Column(
            Modifier.fillMaxSize().background(Color(0xEB160F09))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .semantics { contentDescription = "Streak extended to ${s.streak} days. ${s.celeMsg}"; liveRegion = LiveRegionMode.Polite }
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(160.dp, 170.dp), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.align(Alignment.TopCenter).size(140.dp)
                        .graphicsLayer { alpha = glow.value; scaleX = .5f + .5f * glow.value; scaleY = .5f + .5f * glow.value }
                        .background(Brush.radialGradient(0f to Color(0x8CE0B45C), .45f to Color(0x26E0B45C), .7f to Color.Transparent), CircleShape),
                )
                // Overshoot 0 → 1.35 → 1 for the flame.
                val v = ignite.value
                val fs = if (v < .6f) v / .6f * 1.35f else 1.35f - (v - .6f) / .4f * .35f
                Candle(
                    150.dp, Color(0xFFEADFC8), Color(0xFFE0B45C), core = Color(0xFFFBF6EA), band = Color(0xFFD2A26A),
                    wick = Color(0xFF3B2F23), flicker = ignite.value >= 1f, flameScale = fs,
                )
            }
            Box(Modifier.size(200.dp, 76.dp).clip(RoundedCornerShape(0.dp))) {
                val big = playfair(68, 76)
                Txt("${s.oldStreak}", big, Color(0xFFE0B45C), Modifier.fillMaxWidth().graphicsLayer {
                    translationY = -roll.value * size.height; alpha = 1 - roll.value
                }, align = TextAlign.Center)
                Txt("${s.streak}", big, Color(0xFFE0B45C), Modifier.fillMaxWidth().graphicsLayer {
                    translationY = (1 - roll.value) * size.height; alpha = roll.value
                }, align = TextAlign.Center)
            }
            val up = Modifier.graphicsLayer { alpha = text.value; translationY = (1 - text.value) * 8.dp.toPx() }
            Txt("DAY READING STREAK", inter(13, 600).copy(letterSpacing = 2.sp), Color(0xFFEADFC8), up)
            Txt(s.celeMsg, lora(20, 600, 28), Color(0xFFF4ECD8), up.widthIn(max = 300.dp), align = TextAlign.Center)
            Row(up.widthIn(max = 300.dp).fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week().forEach { d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Txt(d.label, inter(11, 600), Color(0xFFB8A88E))
                        val sc = if (!d.today) 1f else pop.value.let { if (it < .6f) it / .6f * 1.3f else 1.3f - (it - .6f) / .4f * .3f }
                        Box(
                            Modifier.size(30.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape)
                                .background(Color(if (d.today) 0xFFE0B45C else 0xFFD2A26A))
                                .border(2.dp, Color(if (d.today) 0xFFFBF6EA else 0xFFD2A26A), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(StashIcons.check(3f), null, Modifier.size(13.dp), tint = Color(0xFF1E1812)) }
                    }
                }
            }
            PrimaryButton(
                "Keep reading", vm::closeCelebration, up.padding(top = 14.dp), height = 52.dp, radius = 26.dp,
                bg = Color(0xFFE0B45C), fg = Color(0xFF1E1812),
            )
        }
        // Embers drifting up from the lower third.
        Canvas(Modifier.fillMaxSize()) {
            val xs = listOf(.22f, .30f, .38f, .46f, .54f, .60f, .68f, .76f, .84f, .14f)
            val sizes = listOf(6, 4, 5, 7, 4, 6, 5, 4, 6, 5)
            val colors = listOf(0xFFE0B45C, 0xFFF4ECD8, 0xFFD97A6F, 0xFFE0B45C, 0xFFF4ECD8, 0xFFE0B45C, 0xFFD97A6F, 0xFFE0B45C, 0xFFF4ECD8, 0xFFE0B45C)
            embers.forEachIndexed { k, e ->
                val p = e.value
                if (calm || p <= 0f || p >= 1f) return@forEachIndexed
                val r = sizes[k].dp.toPx() / 2 * (1 - .6f * p)
                val alpha = if (p < .15f) p / .15f else 1 - (p - .15f) / .85f
                drawCircle(Color(colors[k]), r, Offset(xs[k] * size.width + r, size.height * .7f - p * 300.dp.toPx()), alpha = alpha)
            }
        }
    }
}

/** Reminder preview banner, sliding from the top like a system notification. */
@Composable
fun BoxScope.NotificationBanner(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val last = remember { mutableLast<com.techgv.stash.Notif>() }
    s.notif?.let { last.value = it }
    AnimatedVisibility(
        s.notif != null, Modifier.align(Alignment.TopCenter).statusBarsPad().padding(10.dp),
        enter = enter(slideInVertically(tween(420, easing = CubicBezierEasing(.2f, .8f, .2f, 1f))) { -it * 2 }),
        exit = exit(slideOutVertically(tween(260)) { -it * 2 }),
    ) {
        val n = last.value ?: return@AnimatedVisibility
        Column(
            Modifier.contentWidth().graphicsLayer { shadowElevation = 30f; shape = RoundedCornerShape(20.dp); clip = true }
                .background(c.surface).border(1.dp, c.outline, RoundedCornerShape(20.dp))
                .semantics { liveRegion = LiveRegionMode.Assertive }
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIconTile(s.prefs.appIcon, 20.dp, 6.dp, 13, shadow = false)
                Txt("Stash · ${n.time}", inter(12), c.faded)
            }
            Txt(n.title, inter(15, 600, 20), c.ink)
            Txt(n.body, inter(13, lineHeight = 18), c.faded)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = vm::hideNotif).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt("Later", inter(13, 600), c.faded) }
                PrimaryButton("Read now", { vm.hideNotif(); vm.readShort() }, height = 36.dp, radius = 18.dp, size = 13)
            }
        }
    }
}

/** Undo bar with a 10 s countdown line. */
@Composable
fun BoxScope.UndoToast(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val last = remember { mutableLast<com.techgv.stash.Undo>() }
    s.undo?.let { last.value = it }
    AnimatedVisibility(
        s.undo != null,
        Modifier.align(Alignment.BottomCenter).navBarsPad()
            .padding(start = 16.dp, end = 16.dp, bottom = if (s.screen == Screen.Home) 96.dp else 28.dp),
        enter = enter(slideInVertically { it / 2 } + fadeIn()), exit = exit(fadeOut()),
    ) {
        val u = last.value ?: return@AnimatedVisibility
        val progress = remember(u.stamp) { Animatable(1f) }
        LaunchedEffect(u.stamp) { progress.animateTo(0f, tween(10_000, easing = LinearEasing)) }
        Box(
            Modifier.contentWidth().heightIn(min = 56.dp)
                .graphicsLayer { shadowElevation = 20f; shape = RoundedCornerShape(14.dp); clip = true }
                .background(c.toast)
                .clickable(remember { MutableInteractionSource() }, null) {} // taps on the bar never reach the list below
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(u.kind.text, inter(14), c.onToast, Modifier.weight(1f))
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).press(vm::undo).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt("Undo", inter(14, 600), c.toastAction) }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable(onClick = vm::dismissUndo).semantics { contentDescription = "Dismiss" },
                    contentAlignment = Alignment.Center,
                ) { Icon(StashIcons.close(), null, Modifier.size(12.dp), tint = c.onToast) }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40E0B45C))) {
                Box(Modifier.fillMaxWidth(progress.value).height(3.dp).background(c.toastAction))
            }
        }
    }
}
