package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.designsystem.theme.motionSpec
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.chip_label_count
import org.jetbrains.compose.resources.stringResource

// Small controls: 32-40 dp visuals. Compose widens hit testing of smaller clickable nodes to the 48 dp minimum touch
// target, so the handoff's 48 dp touch sizes hold without changing the drawn size.

/**
 * Filter or option pill, at least 36 dp tall and growing with the font scale: "Medium · 6" when [count] is set.
 * Selected = primary fill. [role] is how it is announced: `Role.Tab` for filters (default), `Role.RadioButton` for a
 * single choice inside a `selectableGroup()` (swipe options), `Role.Button` for actions such as recent searches.
 * [dashed] draws a dashed outline (recent searches, the "More…" chip). Pressing scales it to 0.94.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    dashed: Boolean = false,
    role: Role = Role.Tab,
) {
    val c = Stash.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) StashMotion.CHIP_PRESS_SCALE else 1f, motionSpec(tween(StashMotion.CHIP_PRESS, easing = StashMotion.Ease)),
    )
    val bg by animateColorAsState(if (selected) c.primary else if (dashed) Color.Transparent else c.surface, motionSpec(tween(StashMotion.SELECT)))
    val fg by animateColorAsState(if (selected) c.onPrimary else c.ink, motionSpec(tween(StashMotion.SELECT)))
    val bd = if (selected) c.primary else c.outline
    Row(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 36.dp).focusRing(CircleShape).clip(CircleShape).background(bg)
            .then(if (dashed) Modifier.dashedBorder(bd, 18.dp) else Modifier.border(1.dp, bd, CircleShape))
            .selectable(selected, interactionSource = source, indication = null, onClick = onClick, role = role)
            .padding(horizontal = 14.dp, vertical = Stash.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Txt(if (count == null) label else stringResource(Res.string.chip_label_count, label, count), Stash.type.label, fg, maxLines = 1)
    }
}

/** Shared by the options of one radio group, so the option that arrow keys select can take focus; see [radioGroupOption]. */
@Stable
class RadioGroupFocus internal constructor() {
    internal var focusOnSelect by mutableIntStateOf(-1)
}

@Composable
fun rememberRadioGroupFocus(): RadioGroupFocus = remember { RadioGroupFocus() }

/**
 * Keyboard behaviour of option [index] of [count] in a radio group (handoff): arrow keys, Home and End select another
 * option ([onSelect] gets its index) and move focus to it; only the selected option (the first when none is) is a Tab
 * stop. Left and right follow the layout direction. Put it before the option's `selectable`, give every option of the
 * group the same [group], and the group `selectableGroup()`.
 */
@Composable
fun Modifier.radioGroupOption(group: RadioGroupFocus, index: Int, selectedIndex: Int, count: Int, onSelect: (Int) -> Unit): Modifier {
    val requester = remember { FocusRequester() }
    val selected = index == selectedIndex
    val tabStop = selected || (selectedIndex !in 0 until count && index == 0)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(selected) {
        if (selected && group.focusOnSelect == index) {
            group.focusOnSelect = -1
            requester.requestFocus()
        }
    }
    return focusRequester(requester)
        .focusProperties { if (!tabStop) canFocus = false }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val target = when (event.key) {
                Key.DirectionUp -> index - 1
                Key.DirectionDown -> index + 1
                Key.DirectionLeft -> if (rtl) index + 1 else index - 1
                Key.DirectionRight -> if (rtl) index - 1 else index + 1
                Key.MoveHome -> 0
                Key.MoveEnd -> count - 1
                else -> return@onPreviewKeyEvent false
            }.mod(count)
            if (target != selectedIndex) {
                group.focusOnSelect = target
                onSelect(target)
            }
            true
        }
}

/**
 * Segmented radio group on a variant track (options at least 32 dp, growing with the font scale; labels ellipsize).
 * [fill] spreads the options evenly across the width. [description] is what a screen reader says for an option when
 * its label is an abbreviation ("Small" for "S"). Arrow keys, Home and End change the selection ([radioGroupOption]).
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    enabled: Boolean = true,
    description: (@Composable (T) -> String)? = null,
) {
    val c = Stash.colors
    val group = rememberRadioGroupFocus()
    val selectedIndex = options.indexOf(selected)
    Row(
        // No clip on the track: it would cut off the options' focus rings.
        modifier.graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .background(c.variant, CircleShape).padding(3.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xxs),
    ) {
        options.forEachIndexed { i, o ->
            val on = o == selected
            val bg by animateColorAsState(if (on) c.primary else Color.Transparent, motionSpec(tween(StashMotion.SELECT)))
            val fg by animateColorAsState(if (on) c.onPrimary else c.inkFaded, motionSpec(tween(StashMotion.SELECT)))
            val spoken = description?.invoke(o)
            Box(
                (if (fill) Modifier.weight(1f) else Modifier.widthIn(min = 44.dp)).heightIn(min = 32.dp)
                    .focusRing(CircleShape).clip(CircleShape).background(bg)
                    .radioGroupOption(group, i, selectedIndex, options.size) { onSelect(options[it]) }
                    .selectable(on, enabled = enabled, onClick = { onSelect(o) }, role = Role.RadioButton)
                    .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
                    .padding(horizontal = if (fill) Stash.spacing.xxs else Stash.spacing.sm, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(label(o), Stash.type.labelSmall, fg, maxLines = 1) }
        }
    }
}

/**
 * 52 × 32 dp switch with a springy 24 dp thumb; off track `color-input-border`, disabled at 55 %.
 * With [onCheckedChange] null it only draws (use it inside `SettingsSwitchRow`, where the whole row toggles).
 */
@Composable
fun StashSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val c = Stash.colors
    val track by animateColorAsState(if (checked) c.primary else c.inputBorder, motionSpec(tween(StashMotion.SELECT)))
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, motionSpec(StashMotion.switchThumb()))
    Box(
        modifier.size(52.dp, 32.dp).focusRing(CircleShape).graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .clip(CircleShape).background(track)
            .then(
                if (onCheckedChange == null) Modifier
                else Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                    .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
            ),
    ) {
        Box(
            Modifier.padding(4.dp).offset { androidx.compose.ui.unit.IntOffset(x.roundToPx(), 0) }.size(24.dp)
                .graphicsLayer { shadowElevation = 2f; shape = CircleShape; clip = true }
                .background(c.surface),
        )
    }
}

/** Uppercase Lora header followed by a hairline that fills the row; a heading for screen readers. */
@Composable
fun DateHeader(label: String, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val c = Stash.colors
    Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        leading?.let { Icon(it, null, Modifier.size(16.dp), tint = c.primary) }
        Txt(label.uppercase(), Stash.type.section, c.primary, Modifier.semantics { heading() })
        Box(Modifier.weight(1f).height(1.dp).background(c.outline))
    }
}
