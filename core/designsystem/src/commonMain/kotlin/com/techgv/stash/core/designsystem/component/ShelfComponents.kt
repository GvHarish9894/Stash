package com.techgv.stash.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.common_read_now
import com.techgv.stash.core.resources.nudge_daily_title
import com.techgv.stash.core.resources.nudge_evening_title
import com.techgv.stash.core.resources.nudge_last_call_body
import com.techgv.stash.core.resources.nudge_last_call_body_no_minutes
import com.techgv.stash.core.resources.nudge_last_call_title
import com.techgv.stash.core.resources.nudge_late_body
import com.techgv.stash.core.resources.nudge_late_body_no_minutes
import com.techgv.stash.core.resources.nudge_late_title
import com.techgv.stash.core.resources.nudge_save_it
import com.techgv.stash.core.resources.nudge_takes
import com.techgv.stash.core.resources.nudge_takes_no_minutes
import com.techgv.stash.core.resources.reminder_daily_title
import com.techgv.stash.core.resources.search_clear
import com.techgv.stash.core.resources.streak_chip_lit
import com.techgv.stash.core.resources.streak_chip_unlit
import com.techgv.stash.core.resources.tip_dismiss
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Colours for the streak chip, sheet and widget candle in each streak state. */
@Immutable
data class StreakLook(val bg: Color, val border: Color, val fg: Color, val flame: Color, val flicker: Boolean)

@Composable
fun streakLook(status: StreakStatus): StreakLook {
    val c = Stash.colors
    return when (status) {
        StreakStatus.Lit -> StreakLook(c.brassFill, c.brass, c.ink, c.flame, flicker = true)
        StreakStatus.AtRisk -> StreakLook(Color.Transparent, c.danger, c.danger, c.danger, flicker = false)
        StreakStatus.Unlit -> StreakLook(Color.Transparent, c.outline, c.inkFaded, c.outline, flicker = false)
    }
}

/**
 * 26 dp pill with a 16 dp candle and the streak count (48 dp touch). Lit: brass, flame flickers; at risk: danger. The
 * label always follows [status]; [look] may differ from it (phone landscape, where the chip carries the nudge's colours).
 */
@Composable
fun StreakChip(count: Int, status: StreakStatus, onClick: () -> Unit, modifier: Modifier = Modifier, look: StreakLook = streakLook(status)) {
    val c = Stash.colors
    val label = stringResource(if (status == StreakStatus.Lit) Res.string.streak_chip_lit else Res.string.streak_chip_unlit, count)
    Row(
        modifier.heightIn(min = 26.dp).focusRing(CircleShape).clip(CircleShape).background(look.bg)
            .border(1.dp, look.border, CircleShape).press(onClick, 0.94f)
            .semantics { contentDescription = label }.padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Candle(16.dp, c.inkFaded, look.flame, flicker = look.flicker)
        // The label already says the count; the visible number is not read a second time.
        Txt("$count", Stash.type.count, look.fg, Modifier.clearAndSetSemantics {})
    }
}

/**
 * Set by the app on Home's search pill and on the Search screen's field: the shared search bar that travels between
 * them (handoff, Home → Search). No motion by default.
 */
val LocalSharedSearchBar = compositionLocalOf<Modifier> { Modifier }

/** Home search pill: a 48 dp button that opens search. */
@Composable
fun SearchPill(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Stash.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).focusRing(CircleShape).clip(CircleShape).background(c.variant).press(onClick)
            .padding(horizontal = Stash.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(StashIcons.search(), null, Modifier.size(18.dp), tint = c.inkFaded)
        Txt(hint, Stash.type.body, c.inkFaded, maxLines = 1)
    }
}

/** The look of a [SearchField]. */
enum class SearchFieldStyle {
    /** Search screen: 48 dp, surface, 2 dp primary border, no leading icon. */
    Prominent,

    /** In-screen filter such as the archive search: 46 dp, surface, 1 dp input border, leading search icon. */
    Inline,
}

/**
 * A search text field: [placeholder] while empty, a round clear button ([clearLabel]) while it has text. [label] is
 * the field's accessible name. [autofocus] focuses it on entry (the search screen); [onSearch] is the keyboard action.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    style: SearchFieldStyle = SearchFieldStyle.Prominent,
    autofocus: Boolean = style == SearchFieldStyle.Prominent,
    clearLabel: String = stringResource(Res.string.search_clear),
    onSearch: () -> Unit = {},
) {
    val c = Stash.colors
    val focus = remember { FocusRequester() }
    if (autofocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val prominent = style == SearchFieldStyle.Prominent
    Row(
        modifier.heightIn(min = if (prominent) 48.dp else 46.dp).clip(CircleShape).background(c.surface)
            .border(if (prominent) 2.dp else 1.dp, if (prominent) c.primary else c.inputBorder, CircleShape)
            .padding(start = 14.dp, end = if (prominent) Stash.spacing.xs else Stash.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!prominent) Icon(StashIcons.search(), null, Modifier.size(18.dp), tint = c.inkFaded)
        val textStyle = if (prominent) Stash.type.bodyStrong else Stash.type.body
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Txt(placeholder, textStyle, c.inkFaded, maxLines = 1)
            BasicTextField(
                query, onQueryChange,
                Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = label },
                textStyle = textStyle.copy(color = c.ink),
                singleLine = true,
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }
        if (query.isNotBlank()) ClearButton(clearLabel, if (prominent) c.variant else c.surface, if (prominent) 32.dp else 30.dp, onClear)
    }
}

/**
 * Streak nudge, escalating with the reminder schedule: Daily and Evening on surface, Late brass-tinted, LastCall a danger
 * fill with on-primary text whose glow pulses three times. [itemTitle] is the shortest unread item, shown as given (the
 * domain caps it); [minutes] its length, null when unknown. At [streak] 0 a Daily nudge reads "Your shelf is waiting"
 * rather than a 0-day streak.
 */
@Composable
fun NudgeBanner(level: ReminderLevel, streak: Int, itemTitle: String, minutes: Int?, onRead: () -> Unit, modifier: Modifier = Modifier) {
    val c = Stash.colors
    data class Look(val bg: Color, val bd: Color, val fg: Color, val flame: Color, val flicker: Boolean, val ctaBg: Color, val ctaFg: Color)
    val look = when (level) {
        ReminderLevel.LastCall -> Look(c.danger, c.danger, c.onPrimary, c.flame, true, c.onPrimary, c.danger)
        ReminderLevel.Late -> Look(c.brassFill, c.brass, c.ink, c.brass, true, c.primary, c.onPrimary)
        ReminderLevel.Evening -> Look(c.surface, c.brass, c.ink, c.outline, false, c.primary, c.onPrimary)
        ReminderLevel.Daily -> Look(c.surface, c.outline, c.ink, c.outline, false, c.primary, c.onPrimary)
    }
    val takes = if (minutes == null) stringResource(Res.string.nudge_takes_no_minutes, itemTitle) else stringResource(Res.string.nudge_takes, itemTitle, minutes)
    val (heading, body) = when (level) {
        ReminderLevel.LastCall -> stringResource(Res.string.nudge_last_call_title) to (
            if (minutes == null) pluralStringResource(Res.plurals.nudge_last_call_body_no_minutes, streak, streak)
            else pluralStringResource(Res.plurals.nudge_last_call_body, streak, streak, minutes)
            )
        ReminderLevel.Late -> stringResource(Res.string.nudge_late_title, streak) to (
            if (minutes == null) stringResource(Res.string.nudge_late_body_no_minutes, itemTitle)
            else stringResource(Res.string.nudge_late_body, itemTitle, minutes)
            )
        ReminderLevel.Evening -> stringResource(Res.string.nudge_evening_title) to takes
        // With no streak yet there is nothing to keep going: the daily reminder's own heading instead.
        ReminderLevel.Daily -> stringResource(if (streak == 0) Res.string.reminder_daily_title else Res.string.nudge_daily_title, streak) to takes
    }
    val reduceMotion = Stash.reduceMotion
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(level, reduceMotion) {
        pulse.snapTo(0f)
        if (level == ReminderLevel.LastCall && !reduceMotion) {
            repeat(StashMotion.NUDGE_PULSE_CYCLES) {
                pulse.animateTo(1f, tween(StashMotion.NUDGE_PULSE_CYCLE / 2, easing = StashMotion.EaseInOut))
                pulse.animateTo(0f, tween(StashMotion.NUDGE_PULSE_CYCLE / 2, easing = StashMotion.EaseInOut))
            }
        }
    }
    val glow = c.danger.copy(alpha = 0.18f)
    val radius = Stash.radius.card
    Row(
        modifier.fillMaxWidth()
            .drawBehind {
                val p = pulse.value
                if (p > 0f) {
                    drawRoundRect(
                        glow, topLeft = Offset(-6.dp.toPx() * p, -6.dp.toPx() * p),
                        size = Size(size.width + 12.dp.toPx() * p, size.height + 12.dp.toPx() * p),
                        cornerRadius = CornerRadius((radius + 6.dp * p).toPx()),
                    )
                }
            }
            .clip(RoundedCornerShape(radius)).background(look.bg).border(1.dp, look.bd, RoundedCornerShape(radius))
            .padding(start = Stash.spacing.sm, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Candle(26.dp, look.fg, look.flame, flicker = look.flicker)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(heading, Stash.type.label, look.fg)
            Txt(body, Stash.type.meta, look.fg.copy(alpha = 0.85f))
        }
        Box(
            Modifier.heightIn(min = 36.dp).focusRing(CircleShape).clip(CircleShape).background(look.ctaBg).press(onRead, 0.94f).padding(horizontal = Stash.spacing.sm, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Txt(stringResource(if (level == ReminderLevel.LastCall) Res.string.nudge_save_it else Res.string.common_read_now), Stash.type.labelSmall, look.ctaFg)
        }
    }
}

/** Dashed brass tip explaining the swipe gestures; [text] comes from `gestureTipText`. */
@Composable
fun GestureTip(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Stash.colors
    val radius = Stash.radius.input
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(radius))
            .background(c.tipFill).dashedBorder(c.brass, radius).padding(start = 14.dp, end = 6.dp, top = Stash.spacing.xs, bottom = Stash.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(StashIcons.hand(), null, Modifier.size(18.dp), tint = c.primary)
        Txt(text, Stash.type.meta, c.ink, Modifier.weight(1f))
        val label = stringResource(Res.string.tip_dismiss)
        Box(
            Modifier.size(32.dp).focusRing(CircleShape).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(StashIcons.close(), null, Modifier.size(12.dp), tint = c.inkFaded) }
    }
}

/** Dashed card at the end of the shelf that opens the archive. */
@Composable
fun ArchiveLinkCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Stash.colors
    val radius = Stash.radius.card
    Row(
        modifier.fillMaxWidth().focusRing(RoundedCornerShape(radius)).clip(RoundedCornerShape(radius)).dashedBorder(c.outline, radius).press(onClick)
            .padding(horizontal = Stash.spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
    ) {
        Icon(StashIcons.archive(1.7f), null, Modifier.size(20.dp), tint = c.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, Stash.type.calloutStrong, c.ink)
            Txt(subtitle, Stash.type.meta, c.inkFaded)
        }
        Icon(StashIcons.chevronRight(), null, Modifier.size(18.dp), tint = c.ink)
    }
}
