package com.techgv.stash.feature.search

import com.techgv.stash.core.designsystem.component.LocalSharedSearchBar
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.BackButton
import com.techgv.stash.core.designsystem.component.DateHeader
import com.techgv.stash.core.designsystem.component.EmptyIllustration
import com.techgv.stash.core.designsystem.component.EmptyState
import com.techgv.stash.core.designsystem.component.FilterChip
import com.techgv.stash.core.designsystem.component.ItemActionSheet
import com.techgv.stash.core.designsystem.component.SearchField
import com.techgv.stash.core.designsystem.component.SearchResultCard
import com.techgv.stash.core.designsystem.component.StashIcons
import com.techgv.stash.core.designsystem.component.ToastHost
import com.techgv.stash.core.designsystem.component.Txt
import com.techgv.stash.core.designsystem.component.UndoSnackbar
import com.techgv.stash.core.designsystem.component.contentWidth
import com.techgv.stash.core.designsystem.component.endEdgeFade
import com.techgv.stash.core.designsystem.component.enter
import com.techgv.stash.core.designsystem.component.exit
import com.techgv.stash.core.designsystem.component.itemMenuActions
import com.techgv.stash.core.designsystem.component.navBarsPad
import com.techgv.stash.core.designsystem.component.rememberLast
import com.techgv.stash.core.designsystem.component.stashAnimateItem
import com.techgv.stash.core.designsystem.component.statusBarsPad
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.designsystem.presentation.plural
import com.techgv.stash.core.designsystem.presentation.resolve
import com.techgv.stash.core.designsystem.presentation.searchFilterLabel
import com.techgv.stash.core.designsystem.presentation.undoMessage
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.common_back_to_shelf
import com.techgv.stash.core.resources.search_item_options
import com.techgv.stash.core.resources.search_field_label
import com.techgv.stash.core.resources.search_found_in_archive
import com.techgv.stash.core.resources.search_help
import com.techgv.stash.core.resources.search_in_archive
import com.techgv.stash.core.resources.search_no_match_body
import com.techgv.stash.core.resources.search_no_match_title
import com.techgv.stash.core.resources.search_placeholder
import com.techgv.stash.core.resources.search_recent
import com.techgv.stash.core.resources.search_summary
import com.techgv.stash.core.resources.search_summary_in_archive
import com.techgv.stash.core.resources.search_summary_in_source
import com.techgv.stash.core.resources.search_summary_with_archived
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * The live result summary: "2 results for “compose”", "… in Medium", "… · 1 archived", "… in your archive"; null when
 * nothing matched. [shelfEmpty] says whether the shelf part of the results is empty: when the Archived chip is hidden it
 * tells "every hit is archived" from "none is" (the chips carry no shelf count).
 */
fun searchSummary(results: SearchResults, shelfEmpty: Boolean): UiText? {
    if (results.total == 0) return null
    val text = results.text
    return when (val filter = results.filter) {
        SearchFilter.Archived -> {
            val n = results.archived ?: results.total
            plural(Res.plurals.search_summary_in_archive, n, n, text)
        }
        is SearchFilter.Source -> {
            val n = results.chips.firstOrNull { it.filter == filter }?.count ?: 0
            plural(Res.plurals.search_summary_in_source, n, n, text, filter.key)
        }
        SearchFilter.All -> {
            val archived = results.archived ?: if (shelfEmpty) results.total else 0
            if (archived > 0) {
                plural(Res.plurals.search_summary_with_archived, results.total, results.total, text, archived)
            } else {
                plural(Res.plurals.search_summary, results.total, results.total, text)
            }
        }
    }
}

/**
 * Search: back + search field (focused on entry while [autofocus]); recent searches and the help line before typing; then result chips,
 * the live summary, and one list of shelf hits followed by the "In your archive" section. [query] is the field text,
 * kept apart from [state] so typing never waits on a flow. [undoTimeout] is asked each time an undo bar appears.
 */
@Composable
fun SearchScreen(
    state: SearchState,
    query: String,
    shelfResults: LazyPagingItems<SearchResultUi>,
    archiveResults: LazyPagingItems<SearchResultUi>,
    widthClass: WidthClass,
    undoTimeout: () -> Duration,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier = Modifier,
    autofocus: Boolean = true,
) {
    val c = Stash.colors
    val focusManager = LocalFocusManager.current
    // System back closes the sheet first, then leaves (saving the query).
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None)) {
        onIntent(if (state.menu != null) SearchIntent.DismissMenu else SearchIntent.Back)
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        // Under the item sheet: out of the accessibility tree and keyboard focus; focus returns to the opener.
        Column((if (widthClass == WidthClass.Compact) Modifier.fillMaxWidth() else Modifier.contentWidth()).statusBarsPad().coveredByModal(state.menu != null)) {
            Row(
                Modifier.padding(start = Stash.spacing.md, end = Stash.spacing.md, top = Stash.spacing.xl, bottom = Stash.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BackButton(stringResource(Res.string.common_back_to_shelf), { onIntent(SearchIntent.Back) })
                SearchField(
                    query, { onIntent(SearchIntent.QueryChanged(it)) }, { onIntent(SearchIntent.QueryChanged("")) },
                    placeholder = stringResource(Res.string.search_placeholder),
                    label = stringResource(Res.string.search_field_label),
                    modifier = Modifier.weight(1f).then(LocalSharedSearchBar.current),
                    autofocus = autofocus,
                    // The keyboard's Search key closes the keyboard: iOS has no other way to.
                    onSearch = { focusManager.clearFocus() },
                )
            }
            if (query.isBlank()) {
                Recents(state.recents, onIntent, Modifier.weight(1f))
            } else {
                Results(state.results, shelfResults, archiveResults, state.flyBackItemId, onIntent, Modifier.weight(1f))
            }
        }
        ToastHost(state.toast?.resolve().orEmpty(), bottom = 28.dp)
        UndoBar(state.undo, undoTimeout, onIntent)
        ItemActionSheet(
            state.menu, state.menu?.let(::itemMenuActions).orEmpty(),
            onSelect = { onIntent(SearchIntent.MenuAction(it)) },
            onDismiss = { onIntent(SearchIntent.DismissMenu) },
            paneTitle = stringResource(Res.string.search_item_options),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Recents(recents: List<String>, onIntent: (SearchIntent) -> Unit, modifier: Modifier) {
    val c = Stash.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = Stash.spacing.xl, vertical = Stash.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
    ) {
        if (recents.isNotEmpty()) {
            Txt(stringResource(Res.string.search_recent).uppercase(), Stash.type.section, c.primary, Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xs), verticalArrangement = Arrangement.spacedBy(Stash.spacing.xs)) {
                recents.forEach { recent ->
                    FilterChip(
                        recent, selected = false, onClick = { onIntent(SearchIntent.QueryChanged(recent)) }, dashed = true,
                        leading = { Icon(StashIcons.clock(), null, Modifier.size(13.dp), tint = c.inkFaded) },
                        role = Role.Button,
                    )
                }
            }
        }
        Txt(stringResource(Res.string.search_help), Stash.type.bodySmall, c.inkFaded, Modifier.padding(top = Stash.spacing.xs))
    }
}

@Composable
private fun Results(
    results: SearchResults?,
    shelf: LazyPagingItems<SearchResultUi>,
    archive: LazyPagingItems<SearchResultUi>,
    flyBackItemId: String?,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier,
) {
    val c = Stash.colors
    val shelfLoaded = shelf.loadState.refresh is LoadState.NotLoading
    // Without placeholders the shelf list grows as it pages, so the archive section waits for its end.
    val shelfComplete = shelfLoaded && shelf.loadState.append.endOfPaginationReached
    // The chips and the summary scroll away with the results, so a short window with the keyboard up (phone landscape)
    // still shows results.
    val side = Modifier.padding(horizontal = Stash.spacing.xl)
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.ime.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (results != null && results.total > 0) {
            item(key = "chips", contentType = "chips") {
                Column {
                    LazyRow(
                        Modifier.endEdgeFade(),
                        contentPadding = PaddingValues(start = Stash.spacing.xl, end = Stash.spacing.xl, top = Stash.spacing.xs, bottom = Stash.spacing.xxs),
                        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xs),
                    ) {
                        items(results.chips, key = { it.filter.toString() }) { chip ->
                            FilterChip(
                                searchFilterLabel(chip.filter).resolve(), chip.filter == results.filter,
                                { onIntent(SearchIntent.SelectFilter(chip.filter)) }, count = chip.count,
                            )
                        }
                    }
                    val summary = if (shelfLoaded) searchSummary(results, shelfEmpty = shelf.itemCount == 0) else null
                    Txt(
                        summary?.resolve().orEmpty(), Stash.type.bodySmall, c.inkFaded,
                        // The list's 10 dp gap follows instead of bottom padding.
                        Modifier.padding(start = Stash.spacing.xl, end = Stash.spacing.xl, top = Stash.spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        items(shelf.itemCount, key = shelf.itemKey { "s:${it.card.id}" }, contentType = { "result" }) { index ->
            ResultCard(shelf[index], flyBackItemId, onIntent, Modifier.stashAnimateItem(this).then(side))
        }
        if (shelfComplete && archive.itemCount > 0) {
            item(key = "archive-header", contentType = "header") {
                DateHeader(
                    stringResource(if (shelf.itemCount > 0) Res.string.search_in_archive else Res.string.search_found_in_archive),
                    Modifier.padding(top = Stash.spacing.xxs).stashAnimateItem(this).then(side), leading = StashIcons.archive(),
                )
            }
            items(archive.itemCount, key = archive.itemKey { "a:${it.card.id}" }, contentType = { "result" }) { index ->
                ResultCard(archive[index], flyBackItemId, onIntent, Modifier.stashAnimateItem(this).then(side))
            }
        }
        if (results != null && results.total == 0) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    stringResource(Res.string.search_no_match_title), stringResource(Res.string.search_no_match_body),
                    EmptyIllustration.None, side,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: SearchResultUi?, flyBackItemId: String?, onIntent: (SearchIntent) -> Unit, modifier: Modifier) {
    if (result == null) {
        // A placeholder while its page loads (only with placeholders enabled).
        Box(modifier.fillMaxWidth().height(76.dp))
        return
    }
    val id = ItemId(result.card.id)
    SearchResultCard(
        result.card,
        onOpen = { onIntent(SearchIntent.Open(id)) },
        onAction = { onIntent(SearchIntent.CardAction(id, it)) },
        onMore = { onIntent(SearchIntent.ShowMenu(id)) },
        modifier = modifier,
        titleHighlights = result.titleHighlights,
        siteHighlights = result.siteHighlights,
        onRetry = { onIntent(SearchIntent.RetryMetadata(id)) },
        entrance = if (result.card.id == flyBackItemId) CardEntrance.FlyBack else CardEntrance.None,
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.UndoBar(
    undo: PendingUndo?,
    undoTimeout: () -> Duration,
    onIntent: (SearchIntent) -> Unit,
) {
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
                onUndo = { onIntent(SearchIntent.Undo) },
                onDismiss = { onIntent(SearchIntent.UndoClosed(shown.opId)) },
                onTimeout = { onIntent(SearchIntent.UndoClosed(shown.opId)) },
                modifier = Modifier.contentWidth(),
                timeout = timeout,
            )
        }
    }
}
