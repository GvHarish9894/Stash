package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import com.techgv.stash.core.designsystem.theme.motionSpec
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.core.designsystem.presentation.CardLifecycle
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.label
import com.techgv.stash.core.designsystem.presentation.weekdayInitial
import com.techgv.stash.core.designsystem.presentation.weekdayName
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.domain.DayMark
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.domain.WeekDay
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.add_link_clipboard
import com.techgv.stash.core.resources.add_link_clipboard_hidden
import com.techgv.stash.core.resources.add_link_save
import com.techgv.stash.core.resources.add_link_title
import com.techgv.stash.core.resources.add_link_url_label
import com.techgv.stash.core.resources.add_link_url_placeholder
import com.techgv.stash.core.resources.add_link_use
import com.techgv.stash.core.resources.common_cancel
import com.techgv.stash.core.resources.common_close
import com.techgv.stash.core.resources.menu_archive
import com.techgv.stash.core.resources.menu_close
import com.techgv.stash.core.resources.menu_copy_link
import com.techgv.stash.core.resources.menu_delete
import com.techgv.stash.core.resources.menu_mark_read
import com.techgv.stash.core.resources.menu_mark_unread
import com.techgv.stash.core.resources.menu_open
import com.techgv.stash.core.resources.menu_remove
import com.techgv.stash.core.resources.menu_restore
import com.techgv.stash.core.resources.menu_share
import com.techgv.stash.core.resources.sources_sheet_close
import com.techgv.stash.core.resources.sources_sheet_title
import com.techgv.stash.core.resources.streak_close
import com.techgv.stash.core.resources.streak_day_not_read
import com.techgv.stash.core.resources.streak_day_read
import com.techgv.stash.core.resources.streak_day_rest
import com.techgv.stash.core.resources.streak_day_today_not_read
import com.techgv.stash.core.resources.streak_day_today_read
import com.techgv.stash.core.resources.streak_read_short
import com.techgv.stash.core.resources.streak_reminder
import com.techgv.stash.core.resources.streak_reminder_off
import com.techgv.stash.core.resources.streak_reminder_saver
import com.techgv.stash.core.resources.streak_reminders
import com.techgv.stash.core.resources.streak_rule
import com.techgv.stash.core.resources.streak_status_done
import com.techgv.stash.core.resources.streak_status_not_yet
import com.techgv.stash.core.resources.streak_status_nothing_unread
import com.techgv.stash.core.resources.streak_title
import org.jetbrains.compose.resources.stringResource

/** Bottom sheets are at most this wide on medium and expanded windows, centred (handoff). */
val MaxSheetWidth = 560.dp

/**
 * Scrim plus a bottom sheet with 28 dp top corners and a drag-handle close button. Slides 360 ms in, 240 ms out.
 * [paneTitle] is announced when the sheet opens ("Item options"). Scrim tap, the handle, Esc and dragging the sheet down
 * call [onDismiss]; the caller handles system back. On open the handle takes keyboard focus and focus stays in the sheet
 * until it closes; the caller marks what is underneath with `Modifier.coveredByModal(visible)`, which also returns
 * focus to the opener. The sheet stays below the status bar and its content scrolls when it does not fit (large text),
 * so [content] must not hold a lazy list or use vertical weights.
 */
@Composable
fun BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    closeLabel: String = stringResource(Res.string.common_close),
    paneTitle: String? = null,
    background: Color = Stash.colors.surface,
    padding: PaddingValues = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 32.dp),
    gap: Dp = Stash.spacing.md,
    horizontal: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetShape = RoundedCornerShape(topStart = Stash.radius.sheet, topEnd = Stash.radius.sheet)
    val direction = LocalLayoutDirection.current
    val drag = rememberSheetDrag(visible, onDismiss)
    val handleFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = enter(fadeIn(tween(StashMotion.SCRIM_IN))), exit = exit(fadeOut(StashMotion.exit(StashMotion.SHEET_OUT)))) {
            // A tap on the scrim dismisses; screen readers use the labelled handle or back instead of an unnamed node.
            Box(Modifier.fillMaxSize().background(Stash.colors.scrim).pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
        }
        AnimatedVisibility(
            visible,
            // The sheet never slides under the status bar; taller content scrolls inside it.
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = Stash.spacing.xs),
            enter = enter(slideInVertically(StashMotion.enter(StashMotion.SHEET_IN)) { it }),
            exit = exit(slideOutVertically(StashMotion.exit(StashMotion.SHEET_OUT)) { it }),
        ) {
            LaunchedEffect(visible) { if (visible) handleFocus.requestFocus() }
            Column(
                Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = MaxSheetWidth).fillMaxWidth()
                    .offset { IntOffset(0, drag.offset.roundToInt()) }
                    .graphicsLayer { shadowElevation = 30f; shape = sheetShape; clip = true }
                    .background(background)
                    // Taps on the sheet never reach the scrim; adds no semantics.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .draggable(drag.draggableState, Orientation.Vertical, onDragStopped = { drag.settle(it) })
                    .nestedScroll(drag.connection)
                    .onKeyEvent { if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) { onDismiss(); true } else false }
                    // Focus stays in the sheet while it is open.
                    .focusProperties { if (visible) onExit = { cancelFocusChange() } }
                    .focusGroup()
                    .then(if (paneTitle != null) Modifier.semantics { this.paneTitle = paneTitle } else Modifier)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(
                        start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
                        top = padding.calculateTopPadding(),
                    ),
                horizontalAlignment = horizontal,
            ) {
                SheetHandle(onDismiss, closeLabel, Modifier.align(Alignment.CenterHorizontally).focusRequester(handleFocus))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(top = gap, bottom = padding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    horizontalAlignment = horizontal,
                    content = content,
                )
            }
        }
    }
}

/** How far a sheet is dragged down, and its drag handling: the handle and header drag it, scrolled content hands over. */
@Stable
private class SheetDrag(private val closeDistance: Float, private val closeVelocity: Float, private val back: AnimationSpec<Float>) {
    var offset by mutableFloatStateOf(0f)
    var onDismiss: () -> Unit = {}
    var visible = true

    val draggableState = DraggableState { offset = (offset + it).coerceAtLeast(0f) }

    /** Closes past [closeDistance] or on a downward fling, as Material's sheet does; springs back otherwise. */
    suspend fun settle(velocity: Float) {
        if (offset <= 0f) return
        if (offset > closeDistance || velocity > closeVelocity) {
            onDismiss()
            // A caller that keeps the sheet open sees it spring back once it has recomposed.
            withFrameNanos { }
            withFrameNanos { }
            if (!visible) return
        }
        animate(offset, 0f, animationSpec = back) { v, _ -> offset = v }
    }

    val connection = object : NestedScrollConnection {
        // Dragging back up first lifts the sheet, then scrolls the content.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f || offset <= 0f) return Offset.Zero
            val used = available.y.coerceAtLeast(-offset)
            offset += used
            return Offset(0f, used)
        }

        // A pull down that the content cannot scroll moves the sheet.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            offset += available.y
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset <= 0f) return Velocity.Zero
            settle(available.y)
            return available
        }
    }
}

@Composable
private fun rememberSheetDrag(visible: Boolean, onDismiss: () -> Unit): SheetDrag {
    val density = LocalDensity.current
    val back = motionSpec(StashMotion.enter<Float>(StashMotion.SHEET_IN))
    val drag = remember(density, Stash.reduceMotion) { SheetDrag(with(density) { 56.dp.toPx() }, with(density) { 125.dp.toPx() }, back) }
    drag.onDismiss = onDismiss
    drag.visible = visible
    // A sheet that opens again starts in place; one that closes slides away from where it was dragged.
    LaunchedEffect(visible) { if (visible) drag.offset = 0f }
    return drag
}

/** The sheet's close handle: a 40 × 4 dp bar in a 20 dp slot, with a 64 × 48 dp touch and focus target. */
@Composable
fun SheetHandle(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            // Keeps the 20 dp slot in the layout while the target is 48 dp tall, centred on the bar.
            .layout { measurable, _ ->
                val target = measurable.measure(Constraints.fixed(64.dp.roundToPx(), 48.dp.roundToPx()))
                val slot = 20.dp.roundToPx()
                layout(target.width, slot) { target.place(0, (slot - target.height) / 2) }
            }
            .focusRing(RoundedCornerShape(Stash.radius.input), inset = true)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Stash.colors.outline)) }
}

/** Rows of the item options sheet. */
enum class ItemMenuAction { MarkRead, MarkUnread, Open, Share, CopyLink, Archive, Remove, Restore, Delete }

/** Handoff sets. Shelf: Mark read/unread, Open, Share, Copy link, Archive, Remove. Archived: Restore, Open, Share, Copy link, Delete forever. */
fun itemMenuActions(item: SavedItemCardUi): List<ItemMenuAction> = if (item.lifecycle == CardLifecycle.Archived) {
    listOf(ItemMenuAction.Restore, ItemMenuAction.Open, ItemMenuAction.Share, ItemMenuAction.CopyLink, ItemMenuAction.Delete)
} else {
    listOf(
        if (item.unread) ItemMenuAction.MarkRead else ItemMenuAction.MarkUnread,
        ItemMenuAction.Open, ItemMenuAction.Share, ItemMenuAction.CopyLink, ItemMenuAction.Archive, ItemMenuAction.Remove,
    )
}

/**
 * Long-press options for [item] with the rows in [actions] (usually `itemMenuActions(item)`); null hides the sheet,
 * the last item stays drawn while it slides away. The sheet is announced by its [paneTitle].
 */
@Composable
fun ItemActionSheet(
    item: SavedItemCardUi?,
    actions: List<ItemMenuAction>,
    onSelect: (ItemMenuAction) -> Unit,
    onDismiss: () -> Unit,
    paneTitle: String? = null,
) {
    val c = Stash.colors
    val m = rememberLast(item) ?: return
    val shownActions = rememberLast(actions.takeIf { item != null }) ?: actions
    BottomSheet(
        item != null, onDismiss, closeLabel = stringResource(Res.string.menu_close), paneTitle = paneTitle,
        padding = PaddingValues(start = Stash.spacing.sm, end = Stash.spacing.sm, top = 14.dp, bottom = 28.dp), gap = 0.dp,
    ) {
        Row(
            Modifier.padding(top = Stash.spacing.xs, bottom = 6.dp).fillMaxWidth().drawBehind {
                drawLine(c.variant, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }.padding(start = Stash.spacing.sm, end = Stash.spacing.sm, top = Stash.spacing.xxs, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
        ) {
            ItemThumbnail(m, 18.sp, Modifier.size(44.dp).clip(RoundedCornerShape(Stash.radius.thumbnailCompact)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(m.title, Stash.type.itemTitle, c.ink, Modifier.semantics { heading() }, maxLines = 1)
                Txt(m.siteName, Stash.type.meta, c.inkFaded)
            }
        }
        shownActions.forEach { action ->
            val (icon, label) = when (action) {
                ItemMenuAction.MarkRead -> StashIcons.check(2.2f) to Res.string.menu_mark_read
                ItemMenuAction.MarkUnread -> StashIcons.dot(1.8f) to Res.string.menu_mark_unread
                ItemMenuAction.Open -> StashIcons.external() to Res.string.menu_open
                ItemMenuAction.Share -> StashIcons.share() to Res.string.menu_share
                ItemMenuAction.CopyLink -> StashIcons.copy() to Res.string.menu_copy_link
                ItemMenuAction.Archive -> StashIcons.archive() to Res.string.menu_archive
                ItemMenuAction.Remove -> StashIcons.trash(1.8f) to Res.string.menu_remove
                ItemMenuAction.Restore -> StashIcons.restore(1.8f) to Res.string.menu_restore
                ItemMenuAction.Delete -> StashIcons.trash(1.8f) to Res.string.menu_delete
            }
            val destructive = action == ItemMenuAction.Remove || action == ItemMenuAction.Delete
            val iconColor = when (action) {
                ItemMenuAction.MarkRead -> c.good
                ItemMenuAction.MarkUnread -> c.danger
                else -> if (destructive) c.danger else c.ink
            }
            MenuRow(icon, stringResource(label), if (destructive) c.danger else c.ink, iconColor) { onSelect(action) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, color: Color, iconColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRing(RoundedCornerShape(Stash.radius.input)).clip(RoundedCornerShape(Stash.radius.input))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Stash.spacing.sm, vertical = Stash.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = iconColor)
        Txt(label, Stash.type.bodyStrong, color)
    }
}

/** What the clipboard offers the add-link sheet. */
@Immutable
sealed interface ClipboardSuggestion {
    /** The link is known and shown under the title. */
    data class Visible(val url: String) : ClipboardSuggestion

    /** The platform says a link is there but reveals it only when the user taps Use (iOS paste privacy). */
    data object Hidden : ClipboardSuggestion
}

/** Brass-tinted suggestion row with "Use", in its visible-link and hidden-link forms. */
@Composable
fun ClipboardSuggestionRow(suggestion: ClipboardSuggestion, onUse: () -> Unit, modifier: Modifier = Modifier) {
    val c = Stash.colors
    val shape = RoundedCornerShape(Stash.radius.input)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.brass.copy(alpha = 0.16f))
            .border(1.dp, c.brass, shape).padding(horizontal = 14.dp, vertical = Stash.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
    ) {
        Icon(StashIcons.clipboard(), null, Modifier.size(20.dp), tint = c.primary)
        Column(Modifier.weight(1f)) {
            when (suggestion) {
                is ClipboardSuggestion.Visible -> {
                    Txt(stringResource(Res.string.add_link_clipboard), Stash.type.bodySmall, c.ink)
                    Txt(suggestion.url, Stash.type.bodySmall, c.inkFaded, maxLines = 1)
                }
                ClipboardSuggestion.Hidden -> Txt(stringResource(Res.string.add_link_clipboard_hidden), Stash.type.bodySmall, c.ink)
            }
        }
        Box(
            Modifier.heightIn(min = 36.dp).focusRing(CircleShape).clip(CircleShape).background(c.primary).press(onUse, 0.94f).padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(stringResource(Res.string.add_link_use), Stash.type.label, c.onPrimary) }
    }
}

/**
 * Add a link: optional clipboard suggestion with "Use", a 52 dp URL field, Cancel and Save to shelf. [error] draws a 2 dp
 * danger border, an icon and the message below, and marks the field invalid for screen readers.
 */
@Composable
fun AddLinkSheet(
    visible: Boolean,
    url: String,
    onUrlChange: (String) -> Unit,
    clipboard: ClipboardSuggestion?,
    error: String?,
    onUseClipboard: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    paneTitle: String? = null,
) {
    val c = Stash.colors
    val title = stringResource(Res.string.add_link_title)
    // Each Save is a new attempt: the error line is recreated so screen readers read it again, even when unchanged.
    var attempts by remember { mutableIntStateOf(0) }
    val save = { attempts++; onSave() }
    BottomSheet(visible, onDismiss, paneTitle = paneTitle ?: title) {
        Txt(title, Stash.type.sheetTitle, c.ink, Modifier.semantics { heading() })
        if (clipboard != null) ClipboardSuggestionRow(clipboard, onUseClipboard)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val fieldLabel = stringResource(Res.string.add_link_url_label)
            Txt(fieldLabel, Stash.type.bodySmallStrong, c.inkFaded)
            val shape = RoundedCornerShape(Stash.radius.input)
            BasicTextField(
                url, onUrlChange,
                Modifier.fillMaxWidth().height(52.dp).focusRing(shape)
                    .semantics { contentDescription = fieldLabel; if (error != null) error(error) },
                textStyle = Stash.type.body.copy(color = c.ink),
                singleLine = true,
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize().clip(shape).background(c.bg)
                            .border(if (error != null) 2.dp else 1.dp, if (error != null) c.danger else c.inputBorder, shape)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (url.isEmpty()) Txt(stringResource(Res.string.add_link_url_placeholder), Stash.type.body, c.inkFaded)
                        inner()
                    }
                },
            )
            if (error != null) key(attempts) {
                Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(StashIcons.alert(), null, Modifier.size(16.dp), tint = c.danger)
                    Txt(error, Stash.type.bodySmallStrong, c.danger)
                }
            }
        }
        RowOrStack(listOf(1f, 2f)) {
            OutlineButton(stringResource(Res.string.common_cancel), onDismiss)
            PrimaryButton(stringResource(Res.string.add_link_save), save)
        }
    }
}

/** A source in the "More…" sheet. */
@Immutable
data class SourceChipUi(val key: String, val label: String, val count: Int)

/** The "More…" sheet: the sources beyond the first 12 chips, as selectable chips; picking one selects it and closes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreSourcesSheet(visible: Boolean, sources: List<SourceChipUi>, selectedKey: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(Res.string.sources_sheet_title)
    val shown = rememberLast(sources.takeIf { visible }) ?: sources
    BottomSheet(visible, onDismiss, closeLabel = stringResource(Res.string.sources_sheet_close), paneTitle = title) {
        Txt(title, Stash.type.sheetTitle, Stash.colors.ink, Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xs), verticalArrangement = Arrangement.spacedBy(Stash.spacing.xs)) {
            shown.forEach { s -> FilterChip(s.label, s.key == selectedKey, { onSelect(s.key) }, count = s.count) }
        }
    }
}

/** What the streak sheet says about today. */
enum class StreakSheetStatus { DoneToday, NotYet, NothingUnread }

fun streakSheetStatus(readToday: Boolean, hasUnread: Boolean): StreakSheetStatus = when {
    readToday -> StreakSheetStatus.DoneToday
    hasUnread -> StreakSheetStatus.NotYet
    else -> StreakSheetStatus.NothingUnread
}

@Composable
fun streakStatusText(status: StreakSheetStatus, streak: Int): String = when (status) {
    StreakSheetStatus.DoneToday -> stringResource(Res.string.streak_status_done)
    StreakSheetStatus.NotYet -> stringResource(Res.string.streak_status_not_yet, streak + 1)
    StreakSheetStatus.NothingUnread -> stringResource(Res.string.streak_status_nothing_unread)
}

/**
 * Streak sheet: 56 dp candle, title, today's status, the 7-day strip, the reminder rule and two 48 dp buttons.
 * [reminderTime] is null when the daily reminder is off. The primary button reads something short while today is
 * open ([NotYet][StreakSheetStatus.NotYet]); it is disabled when [readShortEnabled] is false, and closes the sheet otherwise.
 */
@Composable
fun StreakSheet(
    visible: Boolean,
    streak: Int,
    status: StreakStatus,
    sheetStatus: StreakSheetStatus,
    week: List<WeekDay>,
    reminderTime: ReminderTime?,
    streakSaver: Boolean,
    onReminders: () -> Unit,
    onReadShort: () -> Unit,
    onDismiss: () -> Unit,
    readShortEnabled: Boolean = true,
) {
    val c = Stash.colors
    val look = streakLook(status)
    val reminder = when {
        reminderTime == null -> stringResource(Res.string.streak_reminder_off)
        streakSaver -> stringResource(Res.string.streak_reminder_saver, stringResource(reminderTime.label))
        else -> stringResource(Res.string.streak_reminder, stringResource(reminderTime.label))
    }
    val title = stringResource(Res.string.streak_title, streak)
    BottomSheet(
        visible, onDismiss, closeLabel = stringResource(Res.string.streak_close), paneTitle = title,
        padding = PaddingValues(start = Stash.spacing.lg, end = Stash.spacing.lg, top = 14.dp, bottom = 30.dp), gap = 14.dp,
        horizontal = Alignment.CenterHorizontally,
    ) {
        Candle(56.dp, c.inkFaded, look.flame, flicker = look.flicker)
        Txt(title, Stash.type.screenTitle, c.ink, Modifier.semantics { heading() }, align = TextAlign.Center)
        Txt(streakStatusText(sheetStatus, streak), Stash.type.callout, c.inkFaded, align = TextAlign.Center)
        WeekStrip(week, Modifier.fillMaxWidth())
        Txt(stringResource(Res.string.streak_rule, reminder), Stash.type.meta, c.inkFaded, align = TextAlign.Center)
        RowOrStack(listOf(1f, 2f), Modifier.fillMaxWidth(), gap = 10.dp) {
            OutlineButton(stringResource(Res.string.streak_reminders), onReminders, height = 48.dp, style = Stash.type.calloutStrong)
            val readShort = sheetStatus == StreakSheetStatus.NotYet
            PrimaryButton(
                stringResource(if (readShort) Res.string.streak_read_short else Res.string.common_close),
                if (readShort) onReadShort else onDismiss,
                enabled = !readShort || readShortEnabled, height = 48.dp, style = Stash.type.calloutStrong,
            )
        }
    }
}

/** How a week-strip day is announced: "Tuesday, read", "Wednesday, today, not read yet". */
@Composable
internal fun weekDayDescription(d: WeekDay): String {
    val done = d.mark != DayMark.NotRead
    return stringResource(
        when {
            d.isToday && done -> Res.string.streak_day_today_read
            d.isToday -> Res.string.streak_day_today_not_read
            d.mark == DayMark.Rest -> Res.string.streak_day_rest
            done -> Res.string.streak_day_read
            else -> Res.string.streak_day_not_read
        },
        stringResource(weekdayName(d.date.dayOfWeek)),
    )
}

/** Seven 32 dp day circles, oldest first; each day is announced ("Tuesday, read"). */
@Composable
fun WeekStrip(week: List<WeekDay>, modifier: Modifier = Modifier) {
    val c = Stash.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { d ->
            val done = d.mark != DayMark.NotRead
            val description = weekDayDescription(d)
            Column(
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Txt(stringResource(weekdayInitial(d.date.dayOfWeek)), Stash.type.captionStrong, if (d.isToday) c.ink else c.inkFaded)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (done) c.primary else Color.Transparent)
                        .border(2.dp, if (done) c.primary else if (d.isToday) c.brass else c.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (done) Icon(StashIcons.check(3f), null, Modifier.size(14.dp), tint = c.onPrimary) }
            }
        }
    }
}
