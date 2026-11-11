package com.techgv.stash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.StashState
import com.techgv.stash.StashViewModel
import com.techgv.stash.inArchive
import com.techgv.stash.model.Accent
import com.techgv.stash.model.AppIcon
import com.techgv.stash.model.AutoArchive
import com.techgv.stash.model.CardLayout
import com.techgv.stash.model.GroupBy
import com.techgv.stash.model.Lang
import com.techgv.stash.model.OpenIn
import com.techgv.stash.model.RemindTime
import com.techgv.stash.model.SortBy
import com.techgv.stash.model.SwipeAction
import com.techgv.stash.model.TextSize
import com.techgv.stash.model.ThemeMode
import com.techgv.stash.onShelf
import com.techgv.stash.plural
import com.techgv.stash.ui.components.BackButton
import com.techgv.stash.ui.components.Badge
import com.techgv.stash.ui.components.Candle
import com.techgv.stash.ui.components.Chip
import com.techgv.stash.ui.components.OutlineButton
import com.techgv.stash.ui.components.PrimaryButton
import com.techgv.stash.ui.components.Segmented
import com.techgv.stash.ui.components.StashIcons
import com.techgv.stash.ui.components.StashSwitch
import com.techgv.stash.ui.components.ToastHost
import com.techgv.stash.ui.components.Txt
import com.techgv.stash.ui.components.contentWidth
import com.techgv.stash.ui.components.enter
import com.techgv.stash.ui.components.navBarsPad
import com.techgv.stash.ui.components.statusBarsPad
import com.techgv.stash.ui.components.swipeVisual
import com.techgv.stash.ui.theme.LocalStrings
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.accentColor
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora
import com.techgv.stash.ui.theme.playfair

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val p = s.prefs
    val visible = s.items.onShelf()
    val readN = visible.count { it.read }
    val archivedN = s.items.inArchive().size
    val uri = LocalUriHandler.current

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.contentWidth().statusBarsPad()) {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BackButton("Back to shelf", { vm.go(com.techgv.stash.model.Screen.Home) })
                Txt(LocalStrings.current.settings, lora(24, 700), c.ink, Modifier.semantics { heading() })
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navBarsPad()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Section("Appearance") {
                    Block("Theme") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemeMode.entries.forEach { m -> ThemeOption(m, p.theme == m, Modifier.weight(1f)) { vm.prefs { copy(theme = m) } } }
                        }
                    }
                    Divider()
                    SettingRow("Accent colour", "Buttons, selected chips and links") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Accent.entries.forEach { a ->
                                val on = p.accent == a
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).border(2.dp, if (on) c.ink else Color.Transparent, CircleShape)
                                        .selectable(on, role = Role.RadioButton) { vm.prefs { copy(accent = a) } }
                                        .semantics { contentDescription = a.label }.padding(3.dp),
                                ) { Box(Modifier.fillMaxSize().clip(CircleShape).background(accentColor(a, c.dark))) }
                            }
                        }
                    }
                    Divider()
                    Block("Card layout", "How items look on your shelf and in the archive") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CardLayout.entries.forEach { l -> LayoutOption(l, p.cardLayout == l, Modifier.weight(1f)) { vm.prefs { copy(cardLayout = l) } } }
                        }
                    }
                    Divider()
                    SettingRow("Text size", subContent = {
                        Txt("Aa — card titles", lora(Stash.title.size, 600), c.faded)
                    }) {
                        Segmented(TextSize.entries, p.textSize, { it.name }, { t -> vm.prefs { copy(textSize = t) } })
                    }
                    Divider()
                    SettingRow("Reduce motion", "Turns off page and card animations") {
                        StashSwitch(p.calm, "Reduce motion") { v -> vm.prefs { copy(calm = v) } }
                    }
                }

                Section("Reading") {
                    SettingRow("Mark read when opened", "Or keep items unread until you mark them") {
                        StashSwitch(p.autoRead, "Mark read when opened") { v -> vm.prefs { copy(autoRead = v) } }
                    }
                    Divider()
                    SettingRow("Open in reader mode", "Clean serif text instead of the full web page") {
                        StashSwitch(p.readerDefault, "Open in reader mode") { v -> vm.prefs { copy(readerDefault = v) } }
                    }
                    Divider()
                    SettingRow("Open links in") {
                        Segmented(OpenIn.entries, p.openIn, { it.label }, { o -> vm.prefs { copy(openIn = o) } })
                    }
                }

                Section("Streak and reminders") {
                    val look = streakLook(s)
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(look.bg), contentAlignment = Alignment.Center) {
                            Candle(26.dp, c.faded, look.flame, flicker = look.flicker)
                        }
                        Labels("${s.streak}-day reading streak", streakStatus(s))
                    }
                    Divider()
                    SettingRow("Daily reminder", "A nudge with one short read from your shelf") {
                        StashSwitch(p.remind, "Daily reminder") { v -> vm.prefs { copy(remind = v) } }
                    }
                    Divider()
                    SettingRow("Remind me") {
                        Segmented(RemindTime.entries, p.remindTime, { it.label }, { t -> vm.prefs { copy(remindTime = t) } })
                    }
                    Divider()
                    SettingRow("Streak saver", "Up to 3 evening nudges if you have not read yet") {
                        StashSwitch(p.saver, "Streak saver") { v -> vm.prefs { copy(saver = v) } }
                    }
                    Divider()
                    Block("Preview a reminder", "See what arrives at each time today") {
                        Segmented(listOf(0, 2, 3, 4), s.lastPreview, { mapOf(0 to "Daily", 2 to "7 PM", 3 to "9:30 PM", 4 to "11 PM")[it]!! }, vm::previewNudge, Modifier.fillMaxWidth(), fill = true)
                        Txt("No reminders once you have read today, and none at all when nothing is unread.", inter(12, lineHeight = 17), c.faded)
                    }
                    Divider()
                    ClickRow("Reset today (demo)", "Replay the reminders and the streak celebration", vm::resetToday, chevron = false)
                }

                Section("Swipe gestures") {
                    Block("Swipe right", "Drag a card towards the right") {
                        SwipeOptions(listOf(SwipeAction.Read, SwipeAction.Archive, SwipeAction.Share, SwipeAction.Remove, SwipeAction.None), p.swipeR) { a -> vm.prefs { copy(swipeR = a) } }
                    }
                    Divider()
                    Block("Swipe left", "Drag a card towards the left") {
                        SwipeOptions(listOf(SwipeAction.Remove, SwipeAction.Archive, SwipeAction.Read, SwipeAction.Share, SwipeAction.None), p.swipeL) { a -> vm.prefs { copy(swipeL = a) } }
                    }
                    Txt(
                        "In the archive, right always restores and left deletes. Every action is also in the long-press menu.",
                        inter(12, lineHeight = 17), c.faded, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )
                }

                Section("Shelf") {
                    Block("Sort", "Order of items on your shelf") {
                        Segmented(SortBy.entries, p.sortBy, { it.label }, { o -> vm.prefs { copy(sortBy = o) } }, Modifier.fillMaxWidth(), fill = true)
                    }
                    Divider()
                    Block("Group by", if (p.sortBy == SortBy.Source) "Grouped by source while sorting by source" else "Headers between items on your shelf") {
                        Segmented(
                            GroupBy.entries, p.groupBy.takeIf { p.sortBy != SortBy.Source }, { it.label },
                            { g -> vm.prefs { copy(groupBy = g, sortBy = if (sortBy == SortBy.Source) SortBy.Newest else sortBy) } },
                            Modifier.fillMaxWidth(), fill = true,
                        )
                    }
                    Divider()
                    Block("Auto-archive read items", "Read items move off your shelf after this long. Unread items never move.") {
                        Segmented(AutoArchive.entries, p.autoArchive, { it.label }, vm::pickAutoArchive, Modifier.fillMaxWidth(), fill = true)
                        AnimatedVisibility(s.archPending != null, enter = enter(fadeIn())) {
                            val n = vm.archPendingCount()
                            ConfirmBox("Move ${plural(n, "read item", "read items")} to Archive now?", null, "Not yet", vm::archLater, "Move now", vm::archNow, c.primary, c.bg)
                        }
                    }
                    Divider()
                    ClickRow("Open archive", plural(archivedN, "item", "items"), vm::openArchive)
                    Divider()
                    ClickRow("Show gesture tip again", "Swipe and long-press hints on the shelf", vm::showTipAgain)
                }

                Section("Account and backup") {
                    SettingRow("Sign in with Google", "Keep your shelf when you change phones") { Badge("Phase 2", c.accent) }
                    Divider()
                    SettingRow("Back up to Google Drive", "Private app folder in your own Drive") { Badge("Phase 2", c.accent) }
                    Divider()
                    SettingRow("Export and import", "Your shelf as a JSON file") { Badge("Soon", null, c.variant, c.faded) }
                }

                Section("Your data") {
                    if (!s.clearConfirm) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = vm::clearRead).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            Txt("Clear read items", inter(15, 500), c.danger)
                            Txt(if (readN > 0) "${plural(readN, "read item", "read items")} on your shelf" else "No read items yet", inter(12), c.faded)
                        }
                    } else {
                        Box(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            ConfirmBox(
                                "Clear ${plural(readN, "read item", "read items")}?", "They are removed from your shelf and can't be restored.",
                                "Cancel", vm::cancelClear, "Clear", vm::confirmClear, c.danger, Color.Transparent, border = false,
                            )
                        }
                    }
                }

                Section("Home screen and language") {
                    ClickRow("Home-screen widget", "Your next unread items and streak, one tap away", { vm.go(com.techgv.stash.model.Screen.Widget) })
                    Divider()
                    Block("App icon", "Shown on your home screen and in notifications", gap = 12.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppIcon.entries.forEach { ic ->
                                val on = p.appIcon == ic
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(2.dp, if (on) c.primary else Color.Transparent, RoundedCornerShape(14.dp))
                                        .selectable(on, role = Role.RadioButton) {
                                            vm.prefs { copy(appIcon = ic) }
                                            vm.settingsToast("App icon changed to ${ic.label}")
                                        }
                                        .padding(vertical = 8.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AppIconTile(ic, 48.dp, 14.dp, 28)
                                    Txt(ic.label, inter(11, 600), c.ink)
                                }
                            }
                        }
                    }
                    Divider()
                    Block("App language", "Follows your phone unless you pick one") {
                        Segmented(Lang.entries, p.lang, { it.label }, { l -> vm.prefs { copy(lang = l) } }, Modifier.fillMaxWidth(), fill = true)
                    }
                }

                Section("About") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { uri.openUri("https://github.com/GvHarish9894") }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                            Icon(StashIcons.github(), null, Modifier.size(20.dp), tint = c.bg)
                        }
                        Labels("Source code on GitHub", "github.com/GvHarish9894", Modifier.weight(1f))
                        Icon(StashIcons.external(), null, Modifier.size(18.dp), tint = c.faded)
                    }
                    Divider()
                    SettingRow("Privacy", "Everything stays on this device. No account, no tracking.")
                    Divider()
                    SettingRow("Version") { Txt("1.0.0 (MVP)", inter(14), c.faded) }
                }
            }
        }
        ToastHost(s.settingsToast, 32.dp)
    }
}

fun streakStatus(s: StashState): String {
    val unread = s.items.onShelf().any { !it.read }
    return when {
        s.readToday -> "Today’s read is done. See you tomorrow."
        unread -> "Read one thing before midnight to make it ${s.streak + 1}."
        else -> "Nothing unread, so your streak is safe today and no reminders will come."
    }
}

@Composable
fun AppIconTile(ic: AppIcon, size: Dp, radius: Dp, font: Int, shadow: Boolean = true) {
    Box(
        Modifier.size(size)
            .graphicsLayer { if (shadow) { shadowElevation = 6f; shape = RoundedCornerShape(radius) } }
            .clip(RoundedCornerShape(radius)).background(Color(ic.bg)).border(1.dp, Color(ic.bd), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) { Txt("S", playfair(font), Color(ic.fg)) }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = Stash.colors
    Column {
        Txt(
            title.uppercase(), lora(12, 600).copy(letterSpacing = 1.5.sp), c.primary,
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp).semantics { heading() },
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(16.dp)),
            content = content,
        )
    }
}

@Composable
private fun Divider() = Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Stash.colors.variant))

@Composable
private fun Labels(title: String, sub: String?, modifier: Modifier = Modifier, subContent: (@Composable () -> Unit)? = null) {
    val c = Stash.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(title, inter(15, 500), c.ink)
        if (!sub.isNullOrEmpty()) Txt(sub, inter(12, lineHeight = 17), c.faded)
        subContent?.invoke()
    }
}

@Composable
private fun SettingRow(
    title: String,
    sub: String? = null,
    subContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Labels(title, sub, Modifier.weight(1f), subContent)
        trailing?.invoke(this)
    }
}

@Composable
private fun ClickRow(title: String, sub: String, onClick: () -> Unit, chevron: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Labels(title, sub, Modifier.weight(1f))
        if (chevron) Icon(StashIcons.chevronRight(), null, Modifier.size(18.dp), tint = Stash.colors.faded)
    }
}

@Composable
private fun Block(title: String, sub: String? = null, gap: Dp = 10.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Labels(title, sub)
        content()
    }
}

@Composable
private fun ConfirmBox(
    question: String,
    detail: String?,
    no: String,
    onNo: () -> Unit,
    yes: String,
    onYes: () -> Unit,
    yesColor: Color,
    bg: Color,
    border: Boolean = true,
) {
    val c = Stash.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
            .then(if (border) Modifier.border(1.dp, c.inputBd, RoundedCornerShape(12.dp)) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(question, inter(if (border) 14 else 15, 600), c.ink)
        detail?.let { Txt(it, inter(12, lineHeight = 17), c.faded) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(no, onNo, Modifier.weight(1f), height = 44.dp, radius = 12.dp, border = c.inputBd, size = 14)
            PrimaryButton(yes, onYes, Modifier.weight(1f), height = 44.dp, radius = 12.dp, bg = yesColor, size = 14)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwipeOptions(options: List<SwipeAction>, selected: SwipeAction, onPick: (SwipeAction) -> Unit) {
    val c = Stash.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { a ->
            val on = a == selected
            Chip(
                a.label, on, { onPick(a) }, weight = 600,
                leading = if (a == SwipeAction.None) null else {
                    {
                        Icon(
                            swipeVisual(a, com.techgv.stash.model.sampleItems[0], c).second, null, Modifier.size(18.dp),
                            tint = if (on) c.onPrimary else c.ink,
                        )
                    }
                },
            )
        }
    }
}

/** Mini preview of a theme: header bar and two cards, split diagonally for Auto. */
@Composable
private fun ThemeOption(m: ThemeMode, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Stash.colors
    val dk = m == ThemeMode.Night
    val bg = Color(if (dk) 0xFF1E1812 else 0xFFF4ECD8)
    val surface = Color(if (dk) 0xFF2A2219 else 0xFFFAF4E6)
    val outline = Color(if (dk) 0xFF4A3D2E else 0xFFCBB894)
    val primary = Color(if (dk) 0xFFD2A26A else 0xFF7A4E2D)
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).border(2.dp, if (on) c.primary else c.outline, RoundedCornerShape(14.dp))
            .selectable(on, role = Role.RadioButton, onClick = onClick).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(9.dp)).background(bg).border(1.dp, outline, RoundedCornerShape(9.dp))) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(0.5f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(primary))
                repeat(2) { Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(surface).border(1.dp, outline, RoundedCornerShape(4.dp))) }
            }
            if (m == ThemeMode.Auto) Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.5f).fillMaxHeight().background(Color(0xFF1E1812)))
        }
        Txt(m.label, inter(12, 600), c.ink, Modifier.fillMaxWidth(), align = TextAlign.Center, maxLines = 1)
    }
}

/** Mini preview of a card layout. */
@Composable
private fun LayoutOption(l: CardLayout, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Stash.colors
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).border(2.dp, if (on) c.primary else c.outline, RoundedCornerShape(14.dp))
            .selectable(on, role = Role.RadioButton, onClick = onClick).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(9.dp)).background(c.bg).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val card = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(6.dp))
            when (l) {
                CardLayout.Comfortable -> repeat(2) {
                    Row(card.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(c.primary))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.fillMaxWidth(0.9f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.ink))
                            Box(Modifier.fillMaxWidth(0.55f).height(3.dp).clip(RoundedCornerShape(1.dp)).background(c.faded))
                        }
                    }
                }
                CardLayout.Compact -> repeat(4) {
                    Row(card.padding(horizontal = 5.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(c.primary))
                        Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(1.dp)).background(c.ink))
                    }
                }
                CardLayout.Large -> Column(card) {
                    Box(Modifier.fillMaxWidth().height(30.dp).background(c.primary))
                    Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.ink))
                        Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(1.dp)).background(c.faded))
                    }
                }
            }
        }
        Txt(l.label, inter(12, 600), c.ink, Modifier.fillMaxWidth(), align = TextAlign.Center, maxLines = 1)
    }
}
