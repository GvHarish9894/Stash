package com.techgv.stash.feature.shelf

import com.techgv.stash.core.designsystem.component.LocalSharedSearchBar
import androidx.compose.ui.graphics.Color
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.designsystem.component.shrinkOut
import com.techgv.stash.core.designsystem.component.rememberWithLeaving
import com.techgv.stash.core.designsystem.component.coveredByModal
import com.techgv.stash.core.designsystem.component.streakLook
import com.techgv.stash.core.designsystem.component.StreakLook
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.AddLinkSheet
import com.techgv.stash.core.designsystem.component.ArchiveLinkCard
import com.techgv.stash.core.designsystem.component.CardEntrance
import com.techgv.stash.core.designsystem.component.CircleIconButton
import com.techgv.stash.core.designsystem.component.DateHeader
import com.techgv.stash.core.designsystem.component.EmptyIllustration
import com.techgv.stash.core.designsystem.component.EmptyState
import com.techgv.stash.core.designsystem.component.FilterChip
import com.techgv.stash.core.designsystem.component.GestureTip
import com.techgv.stash.core.designsystem.component.ItemActionSheet
import com.techgv.stash.core.designsystem.component.MoreSourcesSheet
import com.techgv.stash.core.designsystem.component.NudgeBanner
import com.techgv.stash.core.designsystem.component.SavedItemCard
import com.techgv.stash.core.designsystem.component.SearchPill
import com.techgv.stash.core.designsystem.component.SourceChipUi
import com.techgv.stash.core.designsystem.component.StashFab
import com.techgv.stash.core.designsystem.component.StashIcons
import com.techgv.stash.core.designsystem.component.StreakChip
import com.techgv.stash.core.designsystem.component.ToastHost
import com.techgv.stash.core.designsystem.component.Txt
import com.techgv.stash.core.designsystem.component.UndoSnackbar
import com.techgv.stash.core.designsystem.component.cardListGap
import com.techgv.stash.core.designsystem.component.contentWidth
import com.techgv.stash.core.designsystem.component.endEdgeFade
import com.techgv.stash.core.designsystem.component.enter
import com.techgv.stash.core.designsystem.component.exit
import com.techgv.stash.core.designsystem.component.itemMenuActions
import com.techgv.stash.core.designsystem.component.navBarsPad
import com.techgv.stash.core.designsystem.component.rememberLast
import com.techgv.stash.core.designsystem.component.stashAnimateItem
import com.techgv.stash.core.designsystem.component.statusBarsPad
import com.techgv.stash.core.designsystem.presentation.gestureTipText
import com.techgv.stash.core.designsystem.presentation.groupLabelText
import com.techgv.stash.core.designsystem.presentation.resolve
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.presentation.undoMessage
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.add_link_error_empty
import com.techgv.stash.core.resources.add_link_error_invalid
import com.techgv.stash.core.resources.app_wordmark
import com.techgv.stash.core.resources.chip_all
import com.techgv.stash.core.resources.chip_more
import com.techgv.stash.core.resources.chip_unread
import com.techgv.stash.core.resources.common_item_count
import com.techgv.stash.core.resources.nav_archive
import com.techgv.stash.core.resources.nav_settings
import com.techgv.stash.core.resources.shelf_add_link
import com.techgv.stash.core.resources.shelf_archive_link_title
import com.techgv.stash.core.resources.shelf_archive_rule_days
import com.techgv.stash.core.resources.shelf_archive_rule_off
import com.techgv.stash.core.resources.shelf_empty_body
import com.techgv.stash.core.resources.shelf_empty_title
import com.techgv.stash.core.resources.shelf_menu_title
import com.techgv.stash.core.resources.shelf_no_match_body
import com.techgv.stash.core.resources.shelf_no_match_title
import com.techgv.stash.core.resources.shelf_search_hint
import com.techgv.stash.core.resources.shelf_toast_action_failed
import com.techgv.stash.core.resources.shelf_toast_already_on_shelf
import com.techgv.stash.core.resources.shelf_toast_browser_failed
import com.techgv.stash.core.resources.shelf_toast_save_failed
import com.techgv.stash.core.resources.shelf_unread_count
import com.techgv.stash.core.resources.toast_link_copied
import com.techgv.stash.core.resources.toast_opened_in_browser
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

private val SideMargin = 20.dp

/** Width of the shelf pane beside [ShelfScreen]'s detail pane (handoff, Layout and window sizes: expanded). */
private val ShelfPaneWidth = 400.dp

/** Decision D29: a window shorter than this is a phone turned sideways. */
private val PhoneLandscapeMaxHeight = 480.dp

/**
 * Home. Stateless: [state] and the paged [rows] from [ShelfViewModel], [onIntent] for every action. [undoTimeout] is
 * asked each time an undo bar appears; [onRefresh] runs while the pull-to-refresh indicator shows. Medium width centres
 * a 640 dp column (two columns for large-image cards). With a [detailPane] (expanded windows) the shelf is a 400 dp pane
 * beside it, and the shelf's sheets and their scrim cover both panes; under a sheet the detail pane leaves the
 * accessibility tree and its back handlers pause. [selectedItemId] is the item open in that pane (its card shows
 * selected); [settleItemId] is the item whose reader just closed (its card settles).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ShelfScreen(
    state: ShelfState,
    rows: LazyPagingItems<ShelfListRow>,
    widthClass: WidthClass,
    undoTimeout: () -> Duration,
    onRefresh: suspend () -> Unit,
    onIntent: (ShelfIntent) -> Unit,
    modifier: Modifier = Modifier,
    detailPane: (@Composable () -> Unit)? = null,
    selectedItemId: ItemId? = null,
    settleItemId: ItemId? = null,
) {
    val c = Stash.colors
    val column = if (widthClass == WidthClass.Medium) Modifier.contentWidth() else Modifier.fillMaxWidth()
    val sheetOpen = state.menuItem != null || state.moreSourcesOpen || state.addLink != null
    // Deprecated for NavigationEventHandler, whose artifact is not in the catalog; shared's App uses the same.
    @Suppress("DEPRECATION")
    BackHandler(enabled = sheetOpen) {
        onIntent(
            when {
                state.menuItem != null -> ShelfIntent.CloseMenu
                state.moreSourcesOpen -> ShelfIntent.CloseMoreSources
                else -> ShelfIntent.CloseAddLink
            },
        )
    }

    val pane = if (detailPane == null) Modifier.fillMaxSize() else Modifier.width(ShelfPaneWidth).fillMaxHeight()
    Box(modifier.fillMaxSize().background(c.bg)) {
        // While a sheet is open everything underneath leaves the accessibility tree and keyboard focus; closing it
        // returns focus to the opener (handoff).
        Row(Modifier.fillMaxSize().coveredByModal(sheetOpen)) {
            Box(pane) {
                ShelfList(state, rows, widthClass, column, onRefresh, onIntent, selectedItemId, settleItemId)
                Box(Modifier.align(Alignment.BottomCenter).then(column), contentAlignment = Alignment.BottomEnd) {
                    StashFab(
                        stringResource(Res.string.shelf_add_link), StashIcons.bookmarkAdd(), { onIntent(ShelfIntent.OpenAddLink) },
                        Modifier.navBarsPad().padding(end = SideMargin, bottom = 28.dp),
                    )
                }
                UndoBar(state.undo, undoTimeout, onIntent, column)
            }
            if (detailPane != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.outline))
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(enabled = !sheetOpen)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { detailPane() }
                }
            }
        }

        val menuCard = state.menuItem?.toCardUi(state.now, state.zone)
        ItemActionSheet(
            menuCard, menuCard?.let(::itemMenuActions).orEmpty(), { onIntent(ShelfIntent.MenuAction(it)) },
            { onIntent(ShelfIntent.CloseMenu) }, paneTitle = stringResource(Res.string.shelf_menu_title),
        )
        MoreSourcesSheet(
            state.moreSourcesOpen,
            state.chips.more.map { SourceChipUi(it.key, it.key, it.count) },
            selectedKey = (state.filter as? ShelfFilter.Source)?.key,
            onSelect = { onIntent(ShelfIntent.SelectFilter(ShelfFilter.Source(it))) },
            onDismiss = { onIntent(ShelfIntent.CloseMoreSources) },
        )
        val add = rememberLast(state.addLink)
        AddLinkSheet(
            visible = state.addLink != null,
            url = add?.url.orEmpty(),
            onUrlChange = { onIntent(ShelfIntent.AddLinkUrlChanged(it)) },
            clipboard = add?.clipboard,
            error = when (add?.error) {
                AddLinkError.Empty -> stringResource(Res.string.add_link_error_empty)
                AddLinkError.NotALink -> stringResource(Res.string.add_link_error_invalid)
                null -> null
            },
            onUseClipboard = { onIntent(ShelfIntent.UseClipboard) },
            onSave = { onIntent(ShelfIntent.SaveLink) },
            onDismiss = { onIntent(ShelfIntent.CloseAddLink) },
        )
        Box(pane) { ToastHost(state.toast?.let { stringResource(toastText(it)) }.orEmpty(), bottom = 96.dp) }
    }
}

private fun toastText(toast: ShelfToast) = when (toast) {
    ShelfToast.OpenedInBrowser -> Res.string.toast_opened_in_browser
    ShelfToast.BrowserFailed -> Res.string.shelf_toast_browser_failed
    ShelfToast.LinkCopied -> Res.string.toast_link_copied
    ShelfToast.AlreadyOnShelf -> Res.string.shelf_toast_already_on_shelf
    ShelfToast.SaveFailed -> Res.string.shelf_toast_save_failed
    ShelfToast.ActionFailed -> Res.string.shelf_toast_action_failed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfList(
    state: ShelfState,
    rows: LazyPagingItems<ShelfListRow>,
    widthClass: WidthClass,
    column: Modifier,
    onRefresh: suspend () -> Unit,
    onIntent: (ShelfIntent) -> Unit,
    selectedItemId: ItemId?,
    settleItemId: ItemId?,
) {
    val layout = state.preferences.cardLayout
    val gap = cardListGap(layout)
    val columns = if (widthClass == WidthClass.Medium && layout == CardLayout.LargeImages) 2 else 1
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    PullToRefreshBox(
        refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    onRefresh()
                } finally {
                    refreshing = false
                }
            }
        },
        Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            Modifier.fillMaxSize().then(column).statusBarsPad(),
            rememberLazyGridState(),
            contentPadding = PaddingValues(start = SideMargin, end = SideMargin, bottom = 110.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            item(key = "top", span = { GridItemSpan(maxLineSpan) }, contentType = "top") {
                ShelfTop(state, showTip = rows.itemCount > 0, onIntent)
            }
            shelfRows(state, rows, onIntent, selectedItemId, settleItemId)
            val loaded = rows.loadState.refresh is LoadState.NotLoading
            if (loaded && rows.itemCount == 0) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    if (state.chips.all == 0) {
                        EmptyState(stringResource(Res.string.shelf_empty_title), stringResource(Res.string.shelf_empty_body), EmptyIllustration.Book)
                    } else {
                        EmptyState(stringResource(Res.string.shelf_no_match_title), stringResource(Res.string.shelf_no_match_body), EmptyIllustration.Book)
                    }
                }
            }
            if (state.archiveCount > 0 && state.chips.all > 0) {
                item(key = "archive-link", span = { GridItemSpan(maxLineSpan) }, contentType = "archive-link") {
                    val days = state.preferences.autoArchive.days
                    ArchiveLinkCard(
                        stringResource(
                            Res.string.shelf_archive_link_title, stringResource(Res.string.nav_archive),
                            pluralStringResource(Res.plurals.common_item_count, state.archiveCount, state.archiveCount),
                        ),
                        if (days == null) stringResource(Res.string.shelf_archive_rule_off)
                        else pluralStringResource(Res.plurals.shelf_archive_rule_days, days, days),
                        { onIntent(ShelfIntent.OpenArchive) },
                        Modifier.stashAnimateItem(this).padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.shelfRows(
    state: ShelfState,
    rows: LazyPagingItems<ShelfListRow>,
    onIntent: (ShelfIntent) -> Unit,
    selectedItemId: ItemId?,
    settleItemId: ItemId?,
) {
    items(
        rows.itemCount,
        key = rows.itemKey { it.key },
        span = { index -> GridItemSpan(if (rows.peek(index) is ShelfListRow.Header) maxLineSpan else 1) },
        contentType = rows.itemContentType { if (it is ShelfListRow.Header) "header" else "card" },
    ) { index ->
        when (val row = rows[index]) {
            null -> Unit
            is ShelfListRow.Header -> {
                val year = state.now.toLocalDateTime(state.zone).year
                DateHeader(groupLabelText(row.label, year).resolve(), Modifier.stashAnimateItem(this))
            }
            is ShelfListRow.Card -> {
                val id = row.item.id
                // Mapped when the item or the minute changes (compared by value, so a reloaded page of equal items
                // keeps it), not on every state change of the screen.
                val trace = LocalShelfCardTrace.current
                val card = remember(row.item, state.now, state.zone) {
                    trace?.invoke("map ${id.value}")
                    row.item.toCardUi(state.now, state.zone)
                }
                ShelfCard(
                    card, state.preferences.cardLayout, row.startAction, row.endAction, onIntent,
                    Modifier.stashAnimateItem(this),
                    animateStrike = state.struckItemId == id,
                    entrance = when {
                        state.newItemId == id -> CardEntrance.Flutter
                        state.flyBackItemId == id -> CardEntrance.FlyBack
                        settleItemId == id -> CardEntrance.Settle
                        state.firstLoad -> CardEntrance.Rise
                        else -> CardEntrance.None
                    },
                    entranceDelayMillis = if (state.firstLoad) index.coerceAtMost(StashMotion.LIST_STAGGER_MAX_ITEMS) * StashMotion.LIST_STAGGER else 0,
                    tearing = state.tearingItemId == id,
                    selected = selectedItemId == id,
                )
            }
        }
    }
}

/** Tests count the work per card with it: told "map <id>" when an item is mapped, "compose <id>" when its card composes. */
internal val LocalShelfCardTrace = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * A shelf card from values compared by equality only, so a state change of the screen that does not touch this card
 * (a toast, an undo bar, the minute tick for an older item) skips it.
 */
@Composable
private fun ShelfCard(
    card: SavedItemCardUi,
    layout: CardLayout,
    startAction: SwipeOutcome,
    endAction: SwipeOutcome,
    onIntent: (ShelfIntent) -> Unit,
    modifier: Modifier,
    animateStrike: Boolean,
    entrance: CardEntrance,
    entranceDelayMillis: Int,
    tearing: Boolean,
    selected: Boolean,
) {
    LocalShelfCardTrace.current?.invoke("compose ${card.id}")
    val id = ItemId(card.id)
    SavedItemCard(
        card, layout,
        startAction = startAction,
        endAction = endAction,
        onOpen = { onIntent(ShelfIntent.Open(id)) },
        onAction = { onIntent(ShelfIntent.Act(id, it)) },
        onMore = { onIntent(ShelfIntent.OpenMenu(id)) },
        modifier = modifier,
        onRetry = { onIntent(ShelfIntent.Retry(id)) },
        animateStrike = animateStrike,
        entrance = entrance,
        entranceDelayMillis = entranceDelayMillis,
        tearing = tearing,
        onTorn = { onIntent(ShelfIntent.Torn(id)) },
        selected = selected,
    )
}

/** Header, search pill, filter chips, nudge and gesture tip: the top of the list, so they scroll away with it. */
@Composable
private fun ShelfTop(state: ShelfState, showTip: Boolean, onIntent: (ShelfIntent) -> Unit) {
    val c = Stash.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = SideMargin, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(stringResource(Res.string.app_wordmark), Stash.type.wordmark, c.primary, Modifier.semantics { heading() })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val status = state.streak?.status ?: StreakStatus.Unlit
                    StreakChip(
                        state.streak?.count ?: 0, status, { onIntent(ShelfIntent.OpenStreak) },
                        look = streakChipNudge(state, isPhoneLandscape())?.let { nudgeChipLook(it) } ?: streakLook(status),
                    )
                    Txt(stringResource(Res.string.shelf_unread_count, state.chips.unread), Stash.type.bodySmall, c.inkFaded, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(StashIcons.archive(1.7f), stringResource(Res.string.nav_archive), { onIntent(ShelfIntent.OpenArchive) })
                CircleIconButton(StashIcons.sliders(), stringResource(Res.string.nav_settings), { onIntent(ShelfIntent.OpenSettings) })
            }
        }
        SearchPill(
            stringResource(Res.string.shelf_search_hint), { onIntent(ShelfIntent.OpenSearch) },
            Modifier.padding(top = 8.dp, bottom = 4.dp).then(LocalSharedSearchBar.current),
        )
        FilterChips(state, onIntent)
        val nudge = state.nudge
        // Phone landscape: the banner hides and the streak chip carries the urgency (handoff).
        if (nudge != null && !isPhoneLandscape()) {
            NudgeBanner(
                nudge.level, nudge.streak, nudge.title, nudge.minutes, { onIntent(ShelfIntent.ReadNudge) },
                Modifier.padding(bottom = 8.dp),
            )
        }
        AnimatedVisibility(
            state.showGestureTip && showTip,
            enter = enter(fadeIn(tween(StashMotion.TOAST))), exit = exit(fadeOut(StashMotion.exit(StashMotion.TOAST))),
        ) {
            GestureTip(
                gestureTipText(state.preferences.swipeStartAction, state.preferences.swipeEndAction, LocalLayoutDirection.current).resolve(),
                { onIntent(ShelfIntent.DismissGestureTip) },
                Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/** All, Unread, up to 12 sources and "More…" (decision D5), scrolling edge to edge with the end fade. */
@Composable
private fun FilterChips(state: ShelfState, onIntent: (ShelfIntent) -> Unit) {
    val chips = state.chips
    val selectedSource = (state.filter as? ShelfFilter.Source)?.key
    fun select(filter: ShelfFilter) = onIntent(ShelfIntent.SelectFilter(filter))
    val sources = rememberWithLeaving(chips.sources) { it.key }
    LazyRow(
        Modifier.bleed(SideMargin).endEdgeFade().selectableGroup(),
        contentPadding = PaddingValues(horizontal = SideMargin, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(stringResource(Res.string.chip_all), state.filter == ShelfFilter.All, { select(ShelfFilter.All) }, count = chips.all)
        }
        item(key = "unread") {
            FilterChip(stringResource(Res.string.chip_unread), state.filter == ShelfFilter.Unread, { select(ShelfFilter.Unread) }, count = chips.unread)
        }
        // A source that loses its last item shrinks out (handoff).
        items(sources, key = { "source:${it.value.key}" }) { (source, leaving) ->
            FilterChip(
                source.key, selectedSource == source.key, { select(ShelfFilter.Source(source.key)) },
                Modifier.stashAnimateItem(this).shrinkOut(leaving), count = source.count,
            )
        }
        if (chips.more.isNotEmpty()) {
            item(key = "more") {
                FilterChip(
                    stringResource(Res.string.chip_more), chips.more.any { it.key == selectedSource }, { onIntent(ShelfIntent.OpenMoreSources) },
                    Modifier.stashAnimateItem(this), dashed = true,
                )
            }
        }
    }
}

@Composable
private fun UndoBar(undo: UndoOffer?, undoTimeout: () -> Duration, onIntent: (ShelfIntent) -> Unit, column: Modifier) {
    val last = rememberLast(undo)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            undo != null,
            Modifier.align(Alignment.BottomCenter).then(column).navBarsPad().padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
            enter = enter(slideInVertically(StashMotion.enter(StashMotion.TOAST)) { it / 2 } + fadeIn(tween(StashMotion.TOAST))),
            exit = exit(fadeOut(StashMotion.exit(StashMotion.TOAST))),
        ) {
            val offer = last ?: return@AnimatedVisibility
            key(offer.opId) {
                val timeout = remember { undoTimeout() }
                UndoSnackbar(
                    // Offers exist only for remove, archive, restore and delete, which all have a message.
                    stringResource(undoMessage(offer.type)!!),
                    onUndo = { onIntent(ShelfIntent.Undo) },
                    onDismiss = { onIntent(ShelfIntent.DismissUndo(offer.opId)) },
                    onTimeout = { onIntent(ShelfIntent.DismissUndo(offer.opId)) },
                    timeout = timeout,
                )
            }
        }
    }
}

/** A phone turned sideways (decision D29): a window under 480 dp tall, whatever its width. */
@Composable
private fun isPhoneLandscape(): Boolean =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < PhoneLandscapeMaxHeight

/**
 * Phone landscape (decision D29): the nudge banner hides and the streak chip takes the nudge's colours (Evening, Late,
 * LastCall; a Daily nudge leaves it as it is) while there is a streak to lose. A lit chip is never overridden, and an
 * at-risk chip keeps its own danger look, which is the more urgent one. The chip's label and semantics keep the real status. Null: the chip's own look.
 */
internal fun streakChipNudge(state: ShelfState, phoneLandscape: Boolean): ReminderLevel? {
    val streak = state.streak ?: return null
    val level = state.nudge?.level ?: return null
    return level.takeIf { phoneLandscape && it != ReminderLevel.Daily && streak.count > 0 && streak.status == StreakStatus.Unlit }
}

/** The streak chip in a [level] nudge's colours (the banner's border, fill and flame). */
@Composable
private fun nudgeChipLook(level: ReminderLevel): StreakLook {
    val c = Stash.colors
    return when (level) {
        ReminderLevel.LastCall -> streakLook(StreakStatus.AtRisk)
        ReminderLevel.Late -> StreakLook(c.brassFill, c.brass, c.ink, c.brass, flicker = false)
        ReminderLevel.Evening, ReminderLevel.Daily -> StreakLook(Color.Transparent, c.brass, c.ink, c.outline, flicker = false)
    }
}

/** Lets a full-width row (the chip strip) run under the list's side margins. */
private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
