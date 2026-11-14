package com.techgv.stash.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The handoff's four haptic moments. Haptics stay in the UI: a Screen plays them with [perform] on
 * `LocalHapticFeedback`; a haptic that depends on a command result (confirm / reject) reaches the Screen as an effect
 * carrying a [HapticKind]. `SavedItemCard` plays [LongPress] and [SwipeThreshold] itself.
 */
enum class HapticKind {
    /** A card's long-press opened the item menu. */
    LongPress,

    /** A swiped card crossed the 110 dp action threshold. */
    SwipeThreshold,

    /** An action succeeded, for example a link was saved. */
    Confirm,

    /** An action was refused, for example an invalid link. */
    Reject,

    /** A switch turned on / off. */
    ToggleOn,
    ToggleOff,

    /** Another option of a picker was chosen (segmented control, option card, swatch). */
    Select,
}

fun HapticFeedback.perform(kind: HapticKind) = performHapticFeedback(
    when (kind) {
        HapticKind.LongPress -> HapticFeedbackType.LongPress
        HapticKind.SwipeThreshold -> HapticFeedbackType.GestureThresholdActivate
        HapticKind.Confirm -> HapticFeedbackType.Confirm
        HapticKind.Reject -> HapticFeedbackType.Reject
        // Android 14+ (older Android plays none); iOS: light impact and the system selection tick.
        HapticKind.ToggleOn -> HapticFeedbackType.ToggleOn
        HapticKind.ToggleOff -> HapticFeedbackType.ToggleOff
        HapticKind.Select -> HapticFeedbackType.SegmentTick
    },
)

/** Plays the switch haptic for [on], then passes it on. */
fun HapticFeedback.toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    perform(if (on) HapticKind.ToggleOn else HapticKind.ToggleOff)
    onChange(on)
}

/** Selects [option], with the selection tick only when it is not [selected] already. */
fun <T> HapticFeedback.select(option: T, selected: T?, onSelect: (T) -> Unit) {
    if (option != selected) perform(HapticKind.Select)
    onSelect(option)
}

/** Handoff window size classes by width: compact < 600 dp, medium 600–839 dp, expanded ≥ 840 dp. */
enum class WidthClass { Compact, Medium, Expanded }

fun widthClassOf(width: Dp): WidthClass = when {
    width < 600.dp -> WidthClass.Compact
    width < 840.dp -> WidthClass.Medium
    else -> WidthClass.Expanded
}

/** The width class of the window the composition is in. */
@Composable
fun currentWidthClass(): WidthClass {
    val px = LocalWindowInfo.current.containerSize.width
    return widthClassOf(with(LocalDensity.current) { px.toDp() })
}
