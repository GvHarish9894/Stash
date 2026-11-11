package com.techgv.stash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techgv.stash.Row as ListRow
import com.techgv.stash.StashState
import com.techgv.stash.StashViewModel
import com.techgv.stash.archiveRows
import com.techgv.stash.inArchive
import com.techgv.stash.model.SwipeAction
import com.techgv.stash.plural
import com.techgv.stash.splitMatch
import com.techgv.stash.ui.components.BackButton
import com.techgv.stash.ui.components.GroupHeader
import com.techgv.stash.ui.components.ItemCard
import com.techgv.stash.ui.components.MarkColor
import com.techgv.stash.ui.components.StashIcons
import com.techgv.stash.ui.components.Txt
import com.techgv.stash.ui.components.contentWidth
import com.techgv.stash.ui.components.highlighted
import com.techgv.stash.ui.components.listGap
import com.techgv.stash.ui.components.statusBarsPad
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.inter
import com.techgv.stash.ui.theme.lora

@Composable
fun ArchiveScreen(s: StashState, vm: StashViewModel) {
    val c = Stash.colors
    val archived = s.items.inArchive()
    val rows = archiveRows(s.items, s.archQuery)
    val days = s.prefs.autoArchive.days

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.contentWidth().statusBarsPad()) {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BackButton("Back to shelf", vm::closeArchive)
                Column {
                    Txt("Archive", lora(24, 700), c.ink, Modifier.semantics { heading() })
                    Txt(plural(archived.size, "item", "items"), inter(12), c.faded)
                }
            }
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp).fillMaxWidth().height(46.dp)
                    .clip(RoundedCornerShape(23.dp)).background(c.surface).border(1.dp, c.inputBd, RoundedCornerShape(23.dp))
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(StashIcons.search(), null, Modifier.size(18.dp), tint = c.faded)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (s.archQuery.isEmpty()) Txt("Search the archive", inter(15), c.faded)
                    BasicTextField(
                        s.archQuery, vm::setArchQuery,
                        Modifier.fillMaxWidth().semantics { contentDescription = "Search the archive" },
                        textStyle = inter(15).copy(color = c.ink),
                        singleLine = true,
                        cursorBrush = SolidColor(c.primary),
                    )
                }
                if (s.archQuery.isNotBlank()) ClearButton("Clear archive search", c.surface, 30) { vm.setArchQuery("") }
            }
            Txt(
                if (days == 0) "Auto-archive is off. Swipe right to restore an item, left to delete it."
                else "Read items move here after $days days. Swipe right to restore, left to delete.",
                inter(12, lineHeight = 17), c.faded, Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 8.dp),
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(listGap(s.prefs.cardLayout)),
            ) {
                rows.forEach { row ->
                    when (row) {
                        is ListRow.Header -> item(key = "h:${row.label}") { GroupHeader(row.label, Modifier.animateItem()) }
                        is ListRow.Card -> item(key = row.item.id) {
                            val i = row.item
                            ItemCard(
                                i, s.prefs.cardLayout, SwipeAction.Restore, SwipeAction.Delete,
                                onOpen = { vm.open(i.id) },
                                onMenu = { vm.openMenu(i.id) },
                                onSwipe = { vm.swipe(i.id, it) },
                                modifier = Modifier.animateItem(),
                                title = highlighted(splitMatch(i.title, s.archQuery), MarkColor),
                            )
                        }
                    }
                }
                if (rows.isEmpty()) item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(StashIcons.archive(1.4f), null, Modifier.size(44.dp), tint = c.outline)
                        Txt(if (archived.isNotEmpty()) "Nothing matches" else "Nothing archived yet", lora(19, 700), c.ink, align = TextAlign.Center)
                        Txt(
                            when {
                                archived.isNotEmpty() -> "Try another word, or search by site name."
                                days == 0 -> "Auto-archive is off. Long-press any item and choose Archive."
                                else -> "Read items move here $days days after you read them."
                            },
                            inter(13, lineHeight = 19), c.faded, align = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
