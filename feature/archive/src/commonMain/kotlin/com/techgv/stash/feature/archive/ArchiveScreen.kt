package com.techgv.stash.feature.archive

import com.techgv.stash.core.designsystem.component.coveredByModal
import com.techgv.stash.core.designsystem.component.CardEntrance
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.DateHeader
import com.techgv.stash.core.designsystem.component.EmptyIllustration
import com.techgv.stash.core.designsystem.component.EmptyState
import com.techgv.stash.core.designsystem.component.ItemActionSheet
import com.techgv.stash.core.designsystem.component.SavedItemCard
import com.techgv.stash.core.designsystem.component.ScreenHeader
import com.techgv.stash.core.designsystem.component.SearchField
import com.techgv.stash.core.designsystem.component.SearchFieldStyle
import com.techgv.stash.core.designsystem.component.ToastHost
import com.techgv.stash.core.designsystem.component.Txt
import com.techgv.stash.core.designsystem.component.UndoSnackbar
import com.techgv.stash.core.designsystem.component.cardListGap
import com.techgv.stash.core.designsystem.component.contentWidth
import com.techgv.stash.core.designsystem.component.enter
import com.techgv.stash.core.designsystem.component.exit
import com.techgv.stash.core.designsystem.component.itemMenuActions
import com.techgv.stash.core.designsystem.component.navBarsPad
import com.techgv.stash.core.designsystem.component.rememberLast
import com.techgv.stash.core.designsystem.component.stashAnimateItem
import com.techgv.stash.core.designsystem.component.statusBarsPad
import com.techgv.stash.core.designsystem.presentation.groupLabelText
import com.techgv.stash.core.designsystem.presentation.resolve
import com.techgv.stash.core.designsystem.presentation.undoMessage
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.archive_empty_days
import com.techgv.stash.core.resources.archive_empty_off
import com.techgv.stash.core.resources.archive_empty_title
import com.techgv.stash.core.resources.archive_no_match_body
import com.techgv.stash.core.resources.archive_rule_days
import com.techgv.stash.core.resources.archive_rule_off
import com.techgv.stash.core.resources.archive_search_clear
import com.techgv.stash.core.resources.archive_search_hint
import com.techgv.stash.core.resources.common_back_to_shelf
import com.techgv.stash.core.resources.common_item_count
import com.techgv.stash.core.resources.nav_archive
import com.techgv.stash.core.resources.search_item_options
import com.techgv.stash.core.resources.shelf_no_match_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * The archive: back + title + count (focus starts on the back button), the archive search, the rule line, and the
 * paged cards under "This month" / "Earlier" with fixed swipes (end restores, start deletes). [query] is the search
 * field text, kept apart from [state] so typing never waits on a flow. [undoTimeout] is asked each time an undo bar
 * appears.
 */
@Composable
fun ArchiveScreen(
    state: ArchiveState,
    query: String,
    rows: LazyPagingItems<ArchiveRowUi>,
    widthClass: WidthClass,
    undoTimeout: () -> Duration,
    onIntent: (ArchiveIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Stash.colors
    val days = state.autoArchive.days
    // Only while the sheet is open: otherwise system back is the navigation host's.
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = state.menu != null) {
        onIntent(ArchiveIntent.DismissMenu)
    }
    val headerFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { headerFocus.requestFocus() }

    Box(modifier.fillMaxSize().background(c.bg)) {
        // Under the item sheet: out of the accessibility tree and keyboard focus; focus returns to the opener.
        Column((if (widthClass == WidthClass.Compact) Modifier.fillMaxWidth() else Modifier.contentWidth()).statusBarsPad().coveredByModal(state.menu != null)) {
            ScreenHeader(
                stringResource(Res.string.nav_archive), stringResource(Res.string.common_back_to_shelf), { onIntent(ArchiveIntent.Back) },
                // Focus goes to the first focusable child, the back button.
                Modifier.focusRequester(headerFocus),
                subtitle = pluralStringResource(Res.plurals.common_item_count, state.count, state.count),
            )
            SearchField(
                query, { onIntent(ArchiveIntent.QueryChanged(it)) }, { onIntent(ArchiveIntent.QueryChanged("")) },
                placeholder = stringResource(Res.string.archive_search_hint), label = stringResource(Res.string.archive_search_hint),
                modifier = Modifier.padding(start = Stash.spacing.xl, end = Stash.spacing.xl, top = Stash.spacing.xs, bottom = 6.dp).fillMaxWidth(),
                style = SearchFieldStyle.Inline, clearLabel = stringResource(Res.string.archive_search_clear),
                // The keyboard's Search key closes the keyboard: iOS has no other way to.
                onSearch = { focusManager.clearFocus() },
            )
            Txt(
                when {
                    days == null -> stringResource(Res.string.archive_rule_off)
                    else -> pluralStringResource(Res.plurals.archive_rule_days, days, days)
                },
                Stash.type.meta, c.inkFaded, Modifier.padding(start = Stash.spacing.lg, end = Stash.spacing.lg, top = 6.dp, bottom = Stash.spacing.xs),
            )
            val gap = cardListGap(state.layout)
            // Medium width: large-image cards in two columns, as on the shelf (handoff, Layout and window sizes).
            val columns = if (widthClass == WidthClass.Medium && state.layout == CardLayout.LargeImages) 2 else 1
            LazyVerticalGrid(
                GridCells.Fixed(columns),
                Modifier.weight(1f).fillMaxWidth(),
                // Room for the undo bar over the last card, and for the keyboard.
                contentPadding = PaddingValues(
                    start = Stash.spacing.xl, end = Stash.spacing.xl,
                    bottom = 96.dp + WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(
                    rows.itemCount,
                    key = rows.itemKey { it.key },
                    span = { index -> GridItemSpan(if (rows.peek(index) is ArchiveRowUi.Header) maxLineSpan else 1) },
                    contentType = rows.itemContentType { if (it is ArchiveRowUi.Header) "header" else "card" },
                ) { index ->
                    when (val row = rows[index]) {
                        // Archive labels are "This month" / "Earlier", which carry no year.
                        is ArchiveRowUi.Header -> DateHeader(groupLabelText(row.label, currentYear = 0).resolve(), Modifier.stashAnimateItem(this))
                        is ArchiveRowUi.Card -> {
                            val id = ItemId(row.card.id)
                            SavedItemCard(
                                row.card, state.layout, row.startAction, row.endAction,
                                onOpen = { onIntent(ArchiveIntent.Open(id)) },
                                onAction = { onIntent(ArchiveIntent.CardAction(id, it)) },
                                onMore = { onIntent(ArchiveIntent.ShowMenu(id)) },
                                modifier = Modifier.stashAnimateItem(this),
                                titleHighlights = row.titleHighlights,
                                onRetry = { onIntent(ArchiveIntent.RetryMetadata(id)) },
                                // Right restores, left deletes in every layout direction, as the rule line says.
                                fixedLeftRight = true,
                                entrance = if (state.flyBackItemId == row.card.id) CardEntrance.FlyBack else CardEntrance.None,
                                tearing = state.tearingItemId == row.card.id,
                                onTorn = { onIntent(ArchiveIntent.Torn(id)) },
                            )
                        }
                        null -> Unit
                    }
                }
                if (rows.loadState.refresh is LoadState.NotLoading && rows.itemCount == 0) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        val searching = query.isNotBlank() && state.count > 0
                        EmptyState(
                            stringResource(if (searching) Res.string.shelf_no_match_title else Res.string.archive_empty_title),
                            when {
                                searching -> stringResource(Res.string.archive_no_match_body)
                                days == null -> stringResource(Res.string.archive_empty_off)
                                else -> pluralStringResource(Res.plurals.archive_empty_days, days, days)
                            },
                            EmptyIllustration.Archive,
                        )
                    }
                }
            }
        }
        ToastHost(state.toast?.resolve().orEmpty(), bottom = 28.dp)
        UndoBar(state.undo, undoTimeout, onIntent)
        ItemActionSheet(
            state.menu, state.menu?.let(::itemMenuActions).orEmpty(),
            onSelect = { onIntent(ArchiveIntent.MenuAction(it)) },
            onDismiss = { onIntent(ArchiveIntent.DismissMenu) },
            paneTitle = stringResource(Res.string.search_item_options),
        )
    }
}

@Composable
private fun BoxScope.UndoBar(undo: PendingUndo?, undoTimeout: () -> Duration, onIntent: (ArchiveIntent) -> Unit) {
    val last = rememberLast(undo)
    AnimatedVisibility(
        undo != null,
        Modifier.align(Alignment.BottomCenter).navBarsPad().padding(start = Stash.spacing.md, end = Stash.spacing.md, bottom = 28.dp),
        enter = enter(slideInVertically(StashMotion.enter(StashMotion.TOAST)) { it / 2 } + fadeIn(tween(StashMotion.TOAST))),
        exit = exit(fadeOut(StashMotion.exit(StashMotion.TOAST))),
    ) {
        val shown = last ?: return@AnimatedVisibility
        key(shown.opId) {
            val timeout = remember { undoTimeout() }
            UndoSnackbar(
                stringResource(undoMessage(shown.type)!!),
                onUndo = { onIntent(ArchiveIntent.Undo) },
                onDismiss = { onIntent(ArchiveIntent.UndoClosed(shown.opId)) },
                onTimeout = { onIntent(ArchiveIntent.UndoClosed(shown.opId)) },
                modifier = Modifier.contentWidth(),
                timeout = timeout,
            )
        }
    }
}
