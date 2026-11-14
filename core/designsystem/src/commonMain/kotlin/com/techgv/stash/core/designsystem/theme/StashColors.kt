package com.techgv.stash.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.techgv.stash.core.model.Accent

/** The handoff's colour tokens; names follow the token table (`color-ink-faded` is [inkFaded]). */
@Immutable
data class StashColors(
    val dark: Boolean,
    val root: Color,
    val bg: Color,
    val surface: Color,
    val variant: Color,
    val outline: Color,
    val inputBorder: Color,
    val ink: Color,
    val inkFaded: Color,
    val primary: Color,
    val onPrimary: Color,
    val onPrimaryMuted: Color,
    val brass: Color,
    val good: Color,
    val danger: Color,
    val share: Color,
    val toast: Color,
    val onToast: Color,
    val toastAction: Color,
    val flame: Color,
    /** Lit streak chip and level-3 nudge fill: brass at about 18 %. */
    val brassFill: Color,
    /** Gesture tip fill: brass at 12 %. */
    val tipFill: Color,
    /** Search match highlight behind text. */
    val highlight: Color,
    /** Scrim behind bottom sheets. */
    val scrim: Color,
    /** The light band that sweeps over pending card content. */
    val shimmer: Color,
)

fun accentColor(accent: Accent, dark: Boolean): Color = Color(
    when (accent) {
        Accent.Leather -> if (dark) 0xFFD2A26A else 0xFF7A4E2D
        Accent.Oxblood -> if (dark) 0xFFD97A6F else 0xFF8B2E2A
        Accent.Forest -> if (dark) 0xFF8DB49A else 0xFF3F5E4A
        Accent.InkBlue -> if (dark) 0xFF8FB0D6 else 0xFF2F4A6B
    },
)

fun stashColors(dark: Boolean, accent: Accent): StashColors = if (dark) {
    StashColors(
        dark = true,
        root = Color(0xFF0E0B08), bg = Color(0xFF1E1812), surface = Color(0xFF2A2219),
        variant = Color(0xFF3D3226), outline = Color(0xFF4A3D2E), inputBorder = Color(0xFF8A7861),
        ink = Color(0xFFEADFC8), inkFaded = Color(0xFFB8A88E),
        primary = accentColor(accent, true), onPrimary = Color(0xFF1E1812), onPrimaryMuted = Color(0xFF3B2F23),
        brass = Color(0xFFE0B45C), good = Color(0xFF8DB49A), danger = Color(0xFFD97A6F), share = Color(0xFF8FB0D6),
        toast = Color(0xFFEADFC8), onToast = Color(0xFF1E1812), toastAction = Color(0xFF7A4E2D),
        flame = Color(0xFFE0B45C), brassFill = Color(0x2EE0B45C), tipFill = Color(0x1FE0B45C),
        highlight = Color(0x4DE0B45C), scrim = Color(0x6B1E1812), shimmer = Color(0x1FEADFC8),
    )
} else {
    StashColors(
        dark = false,
        root = Color(0xFF2A2219), bg = Color(0xFFF4ECD8), surface = Color(0xFFFAF4E6),
        variant = Color(0xFFE8DCC0), outline = Color(0xFFCBB894), inputBorder = Color(0xFF857158),
        ink = Color(0xFF3B2F23), inkFaded = Color(0xFF6E5D49),
        primary = accentColor(accent, false), onPrimary = Color(0xFFFBF6EA), onPrimaryMuted = Color(0xFFEADFC8),
        brass = Color(0xFFB8893B), good = Color(0xFF3F5E4A), danger = Color(0xFF8B2E2A), share = Color(0xFF2F4A6B),
        toast = Color(0xFF3B2F23), onToast = Color(0xFFF4ECD8), toastAction = Color(0xFFE0B45C),
        flame = Color(0xFFE0B45C), brassFill = Color(0x2EB8893B), tipFill = Color(0x1FB8893B),
        highlight = Color(0x4DB8893B), scrim = Color(0x6B1E1812), shimmer = Color(0x66FAF4E6),
    )
}
