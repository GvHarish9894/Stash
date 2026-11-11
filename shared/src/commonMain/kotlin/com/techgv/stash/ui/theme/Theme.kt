package com.techgv.stash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.techgv.stash.model.Accent
import com.techgv.stash.model.TextSize
import com.techgv.stash.model.ThemeMode
import org.jetbrains.compose.resources.Font
import stash.shared.generated.resources.Res
import stash.shared.generated.resources.inter_bold
import stash.shared.generated.resources.inter_medium
import stash.shared.generated.resources.inter_regular
import stash.shared.generated.resources.inter_semibold
import stash.shared.generated.resources.lora_bold
import stash.shared.generated.resources.lora_italic
import stash.shared.generated.resources.lora_regular
import stash.shared.generated.resources.lora_semibold
import stash.shared.generated.resources.playfair_bold_italic

@Immutable
data class StashColors(
    val dark: Boolean,
    val root: Color,
    val bg: Color,
    val surface: Color,
    val variant: Color,
    val outline: Color,
    val ink: Color,
    val faded: Color,
    val accent: Color,
    val good: Color,
    val danger: Color,
    val toast: Color,
    val onToast: Color,
    val toastAction: Color,
    val onPrimary: Color,
    val onPrimaryMuted: Color,
    val primary: Color,
    val switchOff: Color,
    val inputBd: Color,
    val share: Color,
)

fun accentColor(accent: Accent, dark: Boolean): Color = Color(
    when (accent) {
        Accent.Leather -> if (dark) 0xFFD2A26A else 0xFF7A4E2D
        Accent.Oxblood -> if (dark) 0xFFD97A6F else 0xFF8B2E2A
        Accent.Forest -> if (dark) 0xFF8DB49A else 0xFF3F5E4A
        Accent.Ink -> if (dark) 0xFF8FB0D6 else 0xFF2F4A6B
    }
)

fun stashColors(dark: Boolean, accent: Accent): StashColors = if (dark) StashColors(
    dark = true,
    root = Color(0xFF0E0B08), bg = Color(0xFF1E1812), surface = Color(0xFF2A2219),
    variant = Color(0xFF3D3226), outline = Color(0xFF4A3D2E), ink = Color(0xFFEADFC8),
    faded = Color(0xFFB8A88E), accent = Color(0xFFE0B45C), good = Color(0xFF8DB49A),
    danger = Color(0xFFD97A6F), toast = Color(0xFFEADFC8), onToast = Color(0xFF1E1812),
    toastAction = Color(0xFF7A4E2D), onPrimary = Color(0xFF1E1812), onPrimaryMuted = Color(0xFF3B2F23),
    primary = accentColor(accent, true), switchOff = Color(0xFF8A7861), inputBd = Color(0xFF8A7861),
    share = Color(0xFF8FB0D6),
) else StashColors(
    dark = false,
    root = Color(0xFF2A2219), bg = Color(0xFFF4ECD8), surface = Color(0xFFFAF4E6),
    variant = Color(0xFFE8DCC0), outline = Color(0xFFCBB894), ink = Color(0xFF3B2F23),
    faded = Color(0xFF6E5D49), accent = Color(0xFFB8893B), good = Color(0xFF3F5E4A),
    danger = Color(0xFF8B2E2A), toast = Color(0xFF3B2F23), onToast = Color(0xFFF4ECD8),
    toastAction = Color(0xFFE0B45C), onPrimary = Color(0xFFFBF6EA), onPrimaryMuted = Color(0xFFEADFC8),
    primary = accentColor(accent, false), switchOff = Color(0xFF857158), inputBd = Color(0xFF857158),
    share = Color(0xFF2F4A6B),
)

@Immutable
data class StashFonts(val inter: FontFamily, val lora: FontFamily, val playfair: FontFamily)

/** Card title size and line height, driven by the Text size setting. */
@Immutable
data class TitleSize(val size: TextUnit, val lineHeight: TextUnit)

fun titleSize(size: TextSize) = when (size) {
    TextSize.S -> TitleSize(14.sp, 20.sp)
    TextSize.M -> TitleSize(15.sp, 21.sp)
    TextSize.L -> TitleSize(17.sp, 24.sp)
}

val LocalStashColors = staticCompositionLocalOf { stashColors(false, Accent.Leather) }
val LocalStashFonts = staticCompositionLocalOf<StashFonts> { error("StashTheme missing") }
val LocalTitleSize = staticCompositionLocalOf { titleSize(TextSize.M) }

/** True when the user turned on Reduce motion. */
val LocalCalm = staticCompositionLocalOf { false }

object Stash {
    val colors: StashColors @Composable @ReadOnlyComposable get() = LocalStashColors.current
    val fonts: StashFonts @Composable @ReadOnlyComposable get() = LocalStashFonts.current
    val title: TitleSize @Composable @ReadOnlyComposable get() = LocalTitleSize.current
    val calm: Boolean @Composable @ReadOnlyComposable get() = LocalCalm.current
}

/** Inter text style, the default UI face. */
@Composable
@ReadOnlyComposable
fun inter(size: Int, weight: Int = 400, lineHeight: Int? = null) = TextStyle(
    fontFamily = Stash.fonts.inter,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
)

/** Lora serif style for titles and headings. */
@Composable
@ReadOnlyComposable
fun lora(size: TextUnit, weight: Int = 600, lineHeight: TextUnit = TextUnit.Unspecified, italic: Boolean = false) = TextStyle(
    fontFamily = Stash.fonts.lora,
    fontWeight = FontWeight(weight),
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    fontSize = size,
    lineHeight = lineHeight,
)

@Composable
@ReadOnlyComposable
fun lora(size: Int, weight: Int = 600, lineHeight: Int? = null) =
    lora(size.sp, weight, lineHeight?.sp ?: TextUnit.Unspecified)

/** Playfair Display bold italic, used for the wordmark and big numbers. */
@Composable
@ReadOnlyComposable
fun playfair(size: Int, lineHeight: Int? = null) = TextStyle(
    fontFamily = Stash.fonts.playfair,
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
    fontSize = size.sp,
    lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.Auto -> isSystemInDarkTheme()
    ThemeMode.Parchment -> false
    ThemeMode.Night -> true
}

@Composable
fun StashTheme(
    mode: ThemeMode,
    accent: Accent,
    textSize: TextSize,
    calm: Boolean,
    content: @Composable () -> Unit,
) {
    val c = stashColors(isDark(mode), accent)
    val fonts = StashFonts(
        inter = FontFamily(
            Font(Res.font.inter_regular, FontWeight.Normal),
            Font(Res.font.inter_medium, FontWeight.Medium),
            Font(Res.font.inter_semibold, FontWeight.SemiBold),
            Font(Res.font.inter_bold, FontWeight.Bold),
        ),
        lora = FontFamily(
            Font(Res.font.lora_regular, FontWeight.Normal),
            Font(Res.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
            Font(Res.font.lora_semibold, FontWeight.SemiBold),
            Font(Res.font.lora_bold, FontWeight.Bold),
        ),
        playfair = FontFamily(Font(Res.font.playfair_bold_italic, FontWeight.Bold, FontStyle.Italic)),
    )
    val scheme = if (c.dark) darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, background = c.bg, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, surfaceVariant = c.variant, onSurfaceVariant = c.faded,
        outline = c.outline, error = c.danger,
    ) else lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, background = c.bg, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, surfaceVariant = c.variant, onSurfaceVariant = c.faded,
        outline = c.outline, error = c.danger,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalStashColors provides c,
            LocalStashFonts provides fonts,
            LocalTitleSize provides titleSize(textSize),
            LocalCalm provides calm,
            LocalTextStyle provides TextStyle(fontFamily = fonts.inter, fontSize = 15.sp, color = c.ink),
            content = content,
        )
    }
}
