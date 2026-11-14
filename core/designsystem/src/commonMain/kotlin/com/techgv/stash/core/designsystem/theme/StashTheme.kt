package com.techgv.stash.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode

val LocalStashColors = staticCompositionLocalOf { stashColors(false, Accent.Leather) }
val LocalStashFonts = staticCompositionLocalOf<StashFonts> { error("StashTheme missing") }
val LocalStashTypography = staticCompositionLocalOf<StashTypography> { error("StashTheme missing") }
val LocalStashSpacing = staticCompositionLocalOf { StashSpacing() }
val LocalStashRadius = staticCompositionLocalOf { StashRadius() }
val LocalStashElevation = staticCompositionLocalOf { StashElevation() }

/** True when the in-app Reduce motion switch or the OS asks for less motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Theme accessors: `Stash.colors.ink`, `Stash.type.cardTitle`, `Stash.spacing.xl`, ... */
object Stash {
    val colors: StashColors @Composable @ReadOnlyComposable get() = LocalStashColors.current
    val fonts: StashFonts @Composable @ReadOnlyComposable get() = LocalStashFonts.current
    val type: StashTypography @Composable @ReadOnlyComposable get() = LocalStashTypography.current
    val spacing: StashSpacing @Composable @ReadOnlyComposable get() = LocalStashSpacing.current
    val radius: StashRadius @Composable @ReadOnlyComposable get() = LocalStashRadius.current
    val elevation: StashElevation @Composable @ReadOnlyComposable get() = LocalStashElevation.current
    val reduceMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

@Composable
fun isDark(theme: ThemeMode): Boolean = when (theme) {
    ThemeMode.Auto -> isSystemInDarkTheme()
    ThemeMode.Parchment -> false
    ThemeMode.Night -> true
}

/** Material 3 colour roles from the Stash tokens, so M3 components (Switch, sheets, snackbars, fields) use the palette. */
fun materialColorScheme(c: StashColors): ColorScheme = ColorScheme(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.variant, onPrimaryContainer = c.ink,
        inversePrimary = c.toastAction,
        secondary = c.brass, onSecondary = c.ink,
        secondaryContainer = c.variant, onSecondaryContainer = c.ink,
        tertiary = c.good, onTertiary = c.onPrimary,
        tertiaryContainer = c.variant, onTertiaryContainer = c.ink,
        background = c.bg, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.variant, onSurfaceVariant = c.inkFaded,
        surfaceTint = c.surface,
        inverseSurface = c.toast, inverseOnSurface = c.onToast,
        error = c.danger, onError = c.onPrimary,
        errorContainer = c.variant, onErrorContainer = c.danger,
        outline = c.inputBorder, outlineVariant = c.outline,
        scrim = c.scrim,
        surfaceBright = c.surface, surfaceDim = c.variant,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.variant,
        surfaceContainerLow = c.surface, surfaceContainerLowest = c.surface,
        primaryFixed = c.variant, primaryFixedDim = c.outline, onPrimaryFixed = c.ink, onPrimaryFixedVariant = c.inkFaded,
        secondaryFixed = c.variant, secondaryFixedDim = c.outline, onSecondaryFixed = c.ink, onSecondaryFixedVariant = c.inkFaded,
        tertiaryFixed = c.variant, tertiaryFixedDim = c.outline, onTertiaryFixed = c.ink, onTertiaryFixedVariant = c.inkFaded,
)

/** Material 3 shapes from the radius tokens. */
fun materialShapes(r: StashRadius) = Shapes(
    extraSmall = RoundedCornerShape(r.thumbnailCompact),
    small = RoundedCornerShape(r.input),
    medium = RoundedCornerShape(r.card),
    large = RoundedCornerShape(r.large),
    extraLarge = RoundedCornerShape(r.sheet),
)

/**
 * Provides every token and maps them onto Material 3 (colours, typography, shapes). [reduceMotion] is the in-app
 * setting and [systemReduceMotion] the OS flag (`A11yPrefs.reduceMotion`, passed in by `shared`); either one turns
 * animation off.
 */
@Composable
fun StashTheme(
    theme: ThemeMode,
    accent: Accent,
    textSize: TextSize,
    reduceMotion: Boolean,
    systemReduceMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val c = stashColors(isDark(theme), accent)
    val fonts = rememberStashFonts()
    val type = stashTypography(fonts, textSize)
    val radius = StashRadius()
    MaterialTheme(colorScheme = materialColorScheme(c), typography = materialTypography(type), shapes = materialShapes(radius)) {
        CompositionLocalProvider(
            LocalStashColors provides c,
            LocalStashFonts provides fonts,
            LocalStashTypography provides type,
            LocalStashRadius provides radius,
            LocalReduceMotion provides (reduceMotion || systemReduceMotion),
            LocalTextStyle provides type.body.copy(color = c.ink),
            content = content,
        )
    }
}
