package com.techgv.stash.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** `space-*` tokens. */
@Immutable
data class StashSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    /** Between settings sections; sheet side padding. */
    val lg: Dp = 22.dp,
    /** Screen side margins. */
    val xl: Dp = 20.dp,
)

/** `radius-*` tokens; `radius-full` is `CircleShape` / half the height. */
@Immutable
data class StashRadius(
    val thumbnail: Dp = 10.dp,
    val thumbnailCompact: Dp = 8.dp,
    val input: Dp = 12.dp,
    val card: Dp = 14.dp,
    /** FAB and settings groups. */
    val large: Dp = 16.dp,
    /** Bottom sheet top corners. */
    val sheet: Dp = 28.dp,
)

/** `elevation-*` tokens with their warm shadow colours. */
@Immutable
data class StashElevation(
    val card: Dp = 2.dp,
    val cardShadow: Color = Color(0x1A3B2F23),
    val cardDrag: Dp = 10.dp,
    val cardDragShadow: Color = Color(0x473B2F23),
    val fab: Dp = 6.dp,
    val fabShadow: Color = Color(0x473B2F23),
)
