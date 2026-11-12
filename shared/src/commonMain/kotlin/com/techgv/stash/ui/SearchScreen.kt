package com.techgv.stash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.StashState
import com.techgv.stash.StashViewModel
import com.techgv.stash.model.Item
import com.techgv.stash.search
import com.techgv.stash.splitMatch
import com.techgv.stash.ui.components.Chip
import com.techgv.stash.ui.components.BackButton
import com.techgv.stash.ui.components.GroupHeader
import com.techgv.stash.ui.components.MarkColor
import com.techgv.stash.ui.components.StashIcons
import com.techgv.stash.ui.components.Mono
import com.techgv.stash.ui.components.Txt
import com.techgv.stash.ui.components.contentWidth
import com.techgv.stash.ui.components.dashedBorder
import com.techgv.stash.ui.components.highlighted
import com.techgv.stash.ui.components.press
import com.techgv.stash.ui.components.statusBarsPad
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val hasQuery = s.query.isNotBlank()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.contentWidth().statusBarsPad()) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BackButton("Back to shelf", vm::closeSearch)
                Row(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.surface)
                        .border(2.dp, c.primary, RoundedCornerShape(24.dp)).padding(start = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (s.query.isEmpty()) Txt("Title or site", inter(15, 500), c.faded)
                        BasicTextField(
                            s.query, vm::setQuery,
                            Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Search saved items" },
                            textStyle = inter(15, 500).copy(color = c.ink),
                            singleLine = true,
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                        )
                    }
                    if (hasQuery) ClearButton("Clear search", c.variant, 32) { vm.setQuery("") }
                }
            }

            if (hasQuery) {
                val r = search(s.items, s.query, s.sFilter)
                if (r.total > 0) LazyRow(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(r.chips, key = { it.key }) { chip -> Chip(chip.label, chip.key == r.filter, { vm.setSearchFilter(chip.key) }) }
                }
                if (r.summary.isNotEmpty()) Txt(
                    r.summary, inter(13), c.faded,
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(r.shelf, key = { it.id }) { i -> ResultCard(i, s.query) { vm.open(i.id) } }
                    if (r.archived.isNotEmpty()) item(key = "arch-h") {
                        GroupHeader(
                            if (r.shelf.isNotEmpty()) "In your archive" else "Found in your archive",
                            Modifier.padding(top = 4.dp), leading = StashIcons.archive(),
                        )
                    }
                    items(r.archived, key = { "a" + it.id }) { i -> ResultCard(i, s.query) { vm.open(i.id) } }
                    if (r.total == 0) item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 48.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Txt("No matches on your shelf", lora(19, 700), c.ink, align = TextAlign.Center)
                            Txt("Try a shorter word, or search by site name like “medium”.", inter(13, lineHeight = 19), c.faded, align = TextAlign.Center)
                        }
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Txt("RECENT SEARCHES", lora(13, 600).copy(letterSpacing = 1.5.sp), c.primary, Modifier.semantics { heading() })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.recent.forEach { q ->
                            Row(
                                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).dashedBorder(c.outline, 18.dp)
                                    .press({ vm.setQuery(q) }, 0.94f).padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(StashIcons.clock(), null, Modifier.size(13.dp), tint = c.faded)
                                Txt(q, inter(13, 500), c.ink)
                            }
                        }
                    }
                    Txt(
                        "Search looks through titles and site names of everything on your shelf. Your shelf and its filters stay as they were.",
                        inter(13, lineHeight = 19), c.faded, Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ClearButton(label: String, bg: androidx.compose.ui.graphics.Color, size: Int, onClick: () -> Unit) {
    val c = Stash.colors
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(StashIcons.close(), null, Modifier.size(12.dp), tint = c.ink) }
}

@Composable
private fun ResultCard(i: Item, q: String, onOpen: () -> Unit) {
    val c = Stash.colors
    val ts = Stash.title
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(14.dp)).press(onOpen, label = "Open ${i.title}").padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(i.tile).copy(alpha = if (i.archived) 0.7f else if (i.read) 0.55f else 1f)),
            contentAlignment = Alignment.Center,
        ) { Mono(i.mono, 22, androidx.compose.ui.graphics.Color(i.ink)) }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                highlighted(splitMatch(i.title, q), MarkColor), color = if (i.read) c.faded else c.ink,
                style = lora(ts.size, 600, ts.lineHeight),
            )
            Text(
                highlighted(splitMatch(i.site, q), MarkColor) + androidx.compose.ui.text.AnnotatedString(" · " + if (i.archived) i.archLabel else bucketOf(i)),
                color = c.faded, style = inter(12),
            )
        }
    }
}

private fun bucketOf(i: Item) = when {
    i.daysAgo == 0 -> "Today"
    i.daysAgo == 1 -> "Yesterday"
    else -> "This week"
}
