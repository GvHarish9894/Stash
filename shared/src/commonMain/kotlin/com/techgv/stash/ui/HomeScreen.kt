package com.techgv.stash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techgv.stash.Row as ListRow
import com.techgv.stash.StashState
import com.techgv.stash.StashViewModel
import com.techgv.stash.effectiveFilter
import com.techgv.stash.hintText
import com.techgv.stash.minutesOf
import com.techgv.stash.onShelf
import com.techgv.stash.inArchive
import com.techgv.stash.plural
import com.techgv.stash.shelfChips
import com.techgv.stash.shelfRows
import com.techgv.stash.shortestUnread
import com.techgv.stash.ui.components.BottomSheet
import com.techgv.stash.ui.components.CardEnter
import com.techgv.stash.ui.components.Candle
import com.techgv.stash.ui.components.Chip
import com.techgv.stash.ui.components.CircleIconButton
import com.techgv.stash.ui.components.GroupHeader
import com.techgv.stash.ui.components.ItemCard
import com.techgv.stash.ui.components.OutlineButton
import com.techgv.stash.ui.components.PrimaryButton
import com.techgv.stash.ui.components.StashIcons
import com.techgv.stash.ui.components.Txt
import com.techgv.stash.ui.components.contentWidth
import com.techgv.stash.ui.components.dashedBorder
import com.techgv.stash.ui.components.enter
import com.techgv.stash.ui.components.exit
import com.techgv.stash.ui.components.listGap
import com.techgv.stash.ui.components.navBarsPad
import com.techgv.stash.ui.components.press
import com.techgv.stash.ui.components.statusBarsPad
import com.techgv.stash.ui.theme.LocalStrings
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora
import com.techgv.stash.ui.theme.playfair

@Composable
fun HomeScreen(s: StashState, vm: StashViewModel, listState: LazyListState) {
    val c = Stash.colors
    val L = LocalStrings.current
    val visible = s.items.onShelf()
    val archived = s.items.inArchive()
    val unread = visible.count { !it.read }
    val rows = shelfRows(s.items, s.filter, s.prefs.sortBy, s.prefs.groupBy, L)
    val filter = effectiveFilter(s.filter, visible)
    val lit = s.readToday

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.contentWidth().statusBarsPad()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt("Stash", playfair(32, 34), c.primary, Modifier.semantics { heading() })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StreakChip(s, vm::openStreak)
                        Txt("$unread ${L.unread}", inter(13), c.faded, maxLines = 1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(StashIcons.archive(1.7f), L.archive, vm::openArchive)
                    CircleIconButton(StashIcons.sliders(), L.settings, vm::openSettings)
                    Row(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).dashedBorder(c.primary, 20.dp)
                            .press(vm::share, 0.94f).padding(start = 12.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(StashIcons.share(), null, Modifier.size(16.dp), tint = c.primary)
                        Txt(L.share, inter(12, 600), c.primary)
                    }
                }
            }

            // Search pill
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp).fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(24.dp)).background(c.variant).press(vm::openSearch).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(StashIcons.search(), null, Modifier.size(18.dp), tint = c.faded)
                Txt(L.search, inter(15), c.faded, maxLines = 1)
            }

            // Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shelfChips(visible, L), key = { it.key }) { chip ->
                    Chip(chip.label, chip.key == filter, { vm.setFilter(chip.key) })
                }
            }

            AnimatedVisibility(!lit && unread > 0, enter = enter(fadeIn()), exit = exit(fadeOut())) {
                Nudge(s, vm)
            }
            AnimatedVisibility(!s.hinted && rows.isNotEmpty(), enter = enter(fadeIn()), exit = exit(fadeOut())) {
                Hint(hintText(s.prefs.swipeR, s.prefs.swipeL), vm::dismissHint)
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(listGap(s.prefs.cardLayout)),
            ) {
                var n = 0
                rows.forEach { row ->
                    when (row) {
                        is ListRow.Header -> item(key = "h:${row.label}", contentType = "h") {
                            GroupHeader(row.label, Modifier.animateItem())
                        }
                        is ListRow.Card -> {
                            val i = row.item
                            val idx = n++
                            item(key = i.id, contentType = "card") {
                                ItemCard(
                                    i, s.prefs.cardLayout, s.prefs.swipeR, s.prefs.swipeL,
                                    onOpen = { vm.open(i.id) },
                                    onMenu = { vm.openMenu(i.id) },
                                    onSwipe = { vm.swipe(i.id, it) },
                                    modifier = Modifier.animateItem(),
                                    drawStrike = s.drawId == i.id,
                                    enter = when {
                                        s.addedId == i.id -> CardEnter.Flutter
                                        s.firstLoad -> CardEnter.Rise
                                        else -> CardEnter.None
                                    },
                                    enterDelay = if (s.firstLoad) idx * 55 else 0,
                                )
                            }
                        }
                    }
                }
                if (archived.isNotEmpty() && visible.isNotEmpty()) item(key = "archive-link") {
                    ArchiveLink(
                        "${L.archive} · ${plural(archived.size, "item", "items")}",
                        archRuleShort(s), vm::openArchive, Modifier.animateItem().padding(top = 6.dp),
                    )
                }
                if (rows.isEmpty()) item(key = "empty") {
                    EmptyShelf(
                        if (visible.isEmpty()) "Your shelf is empty" else "Nothing matches",
                        if (visible.isEmpty()) "Share any link to Stash, or tap Add link, and it will be tucked in here."
                        else "Try another word or tap All to see your whole shelf.",
                    )
                }
            }
        }

        // Add link FAB, anchored to the content column on wide screens.
        Box(Modifier.align(Alignment.BottomCenter).contentWidth(), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.navBarsPad().padding(end = 20.dp, bottom = 28.dp).height(56.dp)
                .graphicsLayer { shadowElevation = 16f; shape = RoundedCornerShape(16.dp); clip = true; ambientShadowColor = Color(0x473B2F23); spotShadowColor = Color(0x473B2F23) }
                .background(c.primary).press(vm::openAdd).padding(start = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(StashIcons.bookmarkAdd(), null, Modifier.size(20.dp), tint = c.onPrimary)
            Txt(L.addLink, inter(15, 600), c.onPrimary)
        }
        }

        AddSheet(s, vm)
    }
}

fun archRuleShort(s: StashState) = s.prefs.autoArchive.let {
    if (it.days == 0) "Auto-archive is off" else "Read items move here after ${it.days} days"
}

/** Colours for the streak chip and candle, per the design's lit / late / idle states. */
data class StreakLook(val bg: Color, val bd: Color, val fg: Color, val flame: Color, val flicker: Boolean)

@Composable
fun streakLook(s: StashState): StreakLook {
    val c = Stash.colors
    val lit = s.readToday
    val late = !lit && s.nudgeLevel >= 3 && s.items.onShelf().any { !it.read }
    return StreakLook(
        bg = if (lit) (if (c.dark) Color(0x29E0B45C) else Color(0x2EB8893B)) else Color.Transparent,
        bd = if (lit) c.accent else if (late) c.danger else c.outline,
        fg = if (lit) c.ink else if (late) c.danger else c.faded,
        flame = if (lit) Color(0xFFE0B45C) else if (late) c.danger else c.outline,
        flicker = lit,
    )
}

@Composable
private fun StreakChip(s: StashState, onClick: () -> Unit) {
    val c = Stash.colors
    val look = streakLook(s)
    val label = "${s.streak}-day reading streak" + if (s.readToday) ", today done" else ", not read today"
    Row(
        Modifier.offset(y = 0.dp).height(26.dp).clip(RoundedCornerShape(13.dp)).background(look.bg)
            .border(1.dp, look.bd, RoundedCornerShape(13.dp)).press(onClick, 0.94f)
            .semantics { contentDescription = label }.padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Candle(16.dp, c.faded, look.flame, flicker = look.flicker)
        Txt("${s.streak}", inter(13, 700), look.fg)
    }
}

@Composable
private fun Nudge(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val short = shortestUnread(s.items) ?: return
    val m = minutesOf(short).let { if (it == 99) 5 else it }
    val t = if (short.title.length > 38) short.title.take(36) + "…" else short.title
    val tint = if (c.dark) Color(0x29E0B45C) else Color(0x2EB8893B)
    data class N(val bg: Color, val bd: Color, val fg: Color, val flame: Color, val flicker: Boolean, val ctaBg: Color, val ctaFg: Color, val cta: String, val title: String, val body: String)
    val n = when {
        s.nudgeLevel >= 4 -> N(c.danger, c.danger, Color(0xFFFBF6EA), Color(0xFFE0B45C), true, Color(0xFFFBF6EA), c.danger, "Save it",
            "Last call: your streak ends at midnight", "One $m-min read saves ${s.streak} days.")
        s.nudgeLevel == 3 -> N(tint, c.accent, c.ink, c.accent, true, c.primary, c.onPrimary, "Read now",
            "${s.streak}-day streak ends in 2½ hours", "“$t” · $m min")
        s.nudgeLevel == 2 -> N(c.surface, c.accent, c.ink, c.outline, false, c.primary, c.onPrimary, "Read now",
            "You have not read today yet", "“$t” takes $m min.")
        else -> N(c.surface, c.outline, c.ink, c.outline, false, c.primary, c.onPrimary, "Read now",
            "Keep your ${s.streak}-day streak going", "“$t” takes $m min.")
    }
    // Level 4 pulses a soft danger ring three times.
    val pulse = if (s.nudgeLevel >= 4 && !Stash.calm) {
        rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse)).value
    } else 0f
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).fillMaxWidth()
            .drawBehind {
                if (pulse > 0f) drawRoundRect(
                    Color(0x2E8B2E2A), topLeft = Offset(-6.dp.toPx() * pulse, -6.dp.toPx() * pulse),
                    size = Size(size.width + 12.dp.toPx() * pulse, size.height + 12.dp.toPx() * pulse),
                    cornerRadius = CornerRadius((14 + 6 * pulse).dp.toPx()),
                )
            }
            .clip(RoundedCornerShape(14.dp)).background(n.bg).border(1.dp, n.bd, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Candle(26.dp, n.fg, n.flame, flicker = n.flicker)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(n.title, inter(13, 600), n.fg)
            Txt(n.body, inter(12, lineHeight = 16), n.fg.copy(alpha = 0.85f))
        }
        Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(n.ctaBg).press(vm::readShort, 0.94f).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(n.cta, inter(12, 600), n.ctaFg) }
    }
}

@Composable
private fun Hint(text: String, onDismiss: () -> Unit) {
    val c = Stash.colors
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0x1FB8893B)).dashedBorder(c.accent, 12.dp).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(StashIcons.hand(), null, Modifier.size(18.dp), tint = c.primary)
        Txt(text, inter(12), c.ink, Modifier.weight(1f))
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismiss).semantics { contentDescription = "Dismiss tip" },
            contentAlignment = Alignment.Center,
        ) { Icon(StashIcons.close(), null, Modifier.size(12.dp), tint = c.faded) }
    }
}

@Composable
fun ArchiveLink(title: String, sub: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Stash.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).dashedBorder(c.outline, 14.dp).press(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(StashIcons.archive(1.7f), null, Modifier.size(20.dp), tint = c.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, inter(14, 600), c.ink)
            Txt(sub, inter(12), c.faded)
        }
        Icon(StashIcons.chevronRight(), null, Modifier.size(18.dp), tint = c.ink)
    }
}

/** Open book with a ribbon, gently breathing. */
@Composable
fun EmptyShelf(title: String, body: String) {
    val c = Stash.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(140.dp, 90.dp)) {
            val k = size.width / 140f
            // Two pages skewed ±6° (tan 6° × 32 ≈ 3.4) so they dip towards the spine.
            fun page(x0: Float, lift: Float) = Path().apply {
                moveTo(x0 * k, (12 - lift) * k); lineTo((x0 + 64) * k, (12 + lift) * k)
                lineTo((x0 + 64) * k, (84 + lift) * k); lineTo(x0 * k, (84 - lift) * k); close()
            }
            listOf(page(6f, 3.4f), page(70f, -3.4f)).forEach {
                drawPath(it, c.surface)
                drawPath(it, c.outline, style = Stroke(1.dp.toPx()))
            }
            drawRoundRect(c.primary, Offset(66 * k, 6 * k), Size(8 * k, 82 * k), CornerRadius(4 * k))
        }
        Txt(title, lora(19, 700), c.ink, align = TextAlign.Center)
        Txt(body, inter(13, lineHeight = 19), c.faded, align = TextAlign.Center)
    }
}

@Composable
private fun AddSheet(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    BottomSheet(s.addOpen, vm::closeAdd) {
        Txt("Add a link", lora(22, 700), c.ink, Modifier.semantics { heading() })
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x29B8893B))
                .border(1.dp, c.accent, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(StashIcons.clipboard(), null, Modifier.size(20.dp), tint = c.primary)
            Column(Modifier.weight(1f)) {
                Txt("Link found on your clipboard", inter(13, lineHeight = 18), c.ink)
                Txt("github.com/compose/samples", inter(13, lineHeight = 18), c.faded)
            }
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.primary).press(vm::useClip, 0.94f).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Txt("Use", inter(13, 600), c.onPrimary) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("URL", inter(13, 500), c.faded)
            val focus = remember { FocusRequester() }
            val err = s.addError
            BasicTextField(
                s.addUrl, vm::setAddUrl,
                Modifier.fillMaxWidth().height(52.dp).focusRequester(focus)
                    .semantics { if (err.isNotEmpty()) error(err) },
                textStyle = inter(15).copy(color = c.ink),
                singleLine = true,
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.saveUrl() }),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(c.bg)
                            .border(if (err.isNotEmpty()) 2.dp else 1.dp, if (err.isNotEmpty()) c.danger else c.inputBd, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (s.addUrl.isEmpty()) Txt("https://", inter(15), c.faded)
                        inner()
                    }
                },
            )
            if (err.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(StashIcons.alert(), null, Modifier.size(16.dp), tint = c.danger)
                Txt(err, inter(13, 500), c.danger)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("Cancel", vm::closeAdd, Modifier.weight(1f))
            PrimaryButton("Save to shelf", vm::saveUrl, Modifier.weight(2f))
        }
    }
}
