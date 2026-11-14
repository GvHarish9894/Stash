package com.techgv.stash.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.inter_bold
import com.techgv.stash.core.resources.inter_medium
import com.techgv.stash.core.resources.inter_regular
import com.techgv.stash.core.resources.inter_semibold
import com.techgv.stash.core.resources.lora_bold
import com.techgv.stash.core.resources.lora_italic
import com.techgv.stash.core.resources.lora_regular
import com.techgv.stash.core.resources.lora_semibold
import com.techgv.stash.core.resources.playfair_bold_italic
import org.jetbrains.compose.resources.Font

@Immutable
data class StashFonts(val inter: FontFamily, val lora: FontFamily, val playfair: FontFamily)

@Composable
fun rememberStashFonts() = StashFonts(
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

/**
 * Every text style the app uses. The handoff's `type-*` tokens come first; the rest are the smaller roles the components
 * need, so no screen builds a style from a raw size. Glyphs drawn to the size of their container ([monogram],
 * [appIconGlyph]) take their size at the call site with `copy(fontSize = …)`.
 */
@Immutable
data class StashTypography(
    /** Playfair 32/32 italic: "Stash" on Home. */
    val wordmark: TextStyle,
    /** Lora 24/30 700: Settings, Archive, Widget, streak sheet titles. */
    val screenTitle: TextStyle,
    /** Lora 22/28 700: Add a link, empty states. */
    val sheetTitle: TextStyle,
    /** Lora 600, 15/21 (S 14/20, L 17/24) from the Text size setting; compact cards use size − 1, large-image size + 2. */
    val cardTitle: TextStyle,
    /** Lora 13/16 600, +1.5 tracking: date groups. Upper-case it at the call site. */
    val section: TextStyle,
    /** Lora 12/16 600, +1.5 tracking: settings sections. */
    val sectionSettings: TextStyle,
    /** Inter 15/22 400: rows, inputs. */
    val body: TextStyle,
    /** Inter 15/22 500: menu items, settings row titles, the search field. */
    val bodyStrong: TextStyle,
    /** Inter 15/20 600: buttons, the FAB, notification titles. */
    val emphasis: TextStyle,
    /** Inter 14/20 400: undo message. */
    val callout: TextStyle,
    /** Inter 14/20 600: swipe labels, the reader title, the archive link title, small buttons. */
    val calloutStrong: TextStyle,
    /** Inter 13/19 400: empty-state bodies, help lines, notification bodies. */
    val bodySmall: TextStyle,
    /** Inter 13/18 500: toasts, field labels, error messages. */
    val bodySmallStrong: TextStyle,
    /** Inter 13/18 600: chips, buttons in toasts and banners (`type-label`). */
    val label: TextStyle,
    /** Inter 13/18 700: the streak count. */
    val count: TextStyle,
    /** Inter 12/16 600: segmented options, pills, option-card labels. */
    val labelSmall: TextStyle,
    /** Inter 12/16 700: the widget's streak count. */
    val countSmall: TextStyle,
    /** Inter 12/17 400: site · time · minutes, helper text (`type-meta`). */
    val meta: TextStyle,
    /** Inter 11/14 400: widget meta. */
    val caption: TextStyle,
    /** Inter 11/14 600: week-strip letters, badges, app-icon names. */
    val captionStrong: TextStyle,
    /** Inter 12/16 600, +1.5 tracking: the reader kicker. Upper-case it at the call site. */
    val kicker: TextStyle,
    /** Inter 13/18 600, +2 tracking: the celebration caption. */
    val eyebrow: TextStyle,
    /** Lora 15/20 600: item titles in sheets and the widget. */
    val itemTitle: TextStyle,
    /** Lora 13/17 600: item titles in the medium widget. */
    val itemTitleSmall: TextStyle,
    /** Lora 20/28 600: the celebration message. */
    val celebrationMessage: TextStyle,
    /** Lora 700 for tile monograms; set the size from the tile. */
    val monogram: TextStyle,
    /** Playfair 15/20 italic: the reader "Aa" toggle and the small widget's wordmark. */
    val glyph: TextStyle,
    /** Playfair 17/20 italic: the medium widget's wordmark. */
    val widgetWordmark: TextStyle,
    /** Playfair 40/44 italic: the small widget's unread count. */
    val widgetCount: TextStyle,
    /** Playfair italic for the app-icon "S"; set the size from the tile. */
    val appIconGlyph: TextStyle,
    /** Reader headline and body, one per text step (0..3). */
    val readerHead: List<TextStyle>,
    val readerBody: List<TextStyle>,
    /** Lora 600 for the reader's A− / A+ buttons (14 and 19). */
    val readerSizeButton: TextStyle,
    /** Playfair 68/76 italic: the celebration counter. */
    val streakNumber: TextStyle,
)

/** Card title size and line height for a Text size step. */
fun cardTitleSize(size: TextSize): Pair<TextUnit, TextUnit> = when (size) {
    TextSize.Small -> 14.sp to 20.sp
    TextSize.Medium -> 15.sp to 21.sp
    TextSize.Large -> 17.sp to 24.sp
}

/** Reader headline and body size / line height per text step. */
private val readerSteps = listOf(
    intArrayOf(22, 29, 15, 23), intArrayOf(24, 31, 17, 26), intArrayOf(27, 34, 19, 29), intArrayOf(30, 37, 21, 32),
)

const val READER_TEXT_STEPS = 4

fun stashTypography(fonts: StashFonts, textSize: TextSize): StashTypography {
    fun style(family: FontFamily, size: Int, line: Int?, weight: Int, italic: Boolean = false, tracking: Float = 0f) = TextStyle(
        fontFamily = family, fontWeight = FontWeight(weight), fontSize = size.sp,
        lineHeight = line?.sp ?: TextUnit.Unspecified,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.sp,
    )
    val (titleSize, titleLine) = cardTitleSize(textSize)
    val i = fonts.inter
    val l = fonts.lora
    val p = fonts.playfair
    return StashTypography(
        wordmark = style(p, 32, 32, 700, italic = true),
        screenTitle = style(l, 24, 30, 700),
        sheetTitle = style(l, 22, 28, 700),
        cardTitle = TextStyle(fontFamily = l, fontWeight = FontWeight.SemiBold, fontSize = titleSize, lineHeight = titleLine),
        section = style(l, 13, 16, 600, tracking = 1.5f),
        sectionSettings = style(l, 12, 16, 600, tracking = 1.5f),
        body = style(i, 15, 22, 400),
        bodyStrong = style(i, 15, 22, 500),
        emphasis = style(i, 15, 20, 600),
        callout = style(i, 14, 20, 400),
        calloutStrong = style(i, 14, 20, 600),
        bodySmall = style(i, 13, 19, 400),
        bodySmallStrong = style(i, 13, 18, 500),
        label = style(i, 13, 18, 600),
        count = style(i, 13, 18, 700),
        labelSmall = style(i, 12, 16, 600),
        countSmall = style(i, 12, 16, 700),
        meta = style(i, 12, 17, 400),
        caption = style(i, 11, 14, 400),
        captionStrong = style(i, 11, 14, 600),
        kicker = style(i, 12, 16, 600, tracking = 1.5f),
        eyebrow = style(i, 13, 18, 600, tracking = 2f),
        itemTitle = style(l, 15, 20, 600),
        itemTitleSmall = style(l, 13, 17, 600),
        celebrationMessage = style(l, 20, 28, 600),
        monogram = style(l, 24, null, 700),
        glyph = style(p, 15, 20, 700, italic = true),
        widgetWordmark = style(p, 17, 20, 700, italic = true),
        widgetCount = style(p, 40, 44, 700, italic = true),
        appIconGlyph = style(p, 28, null, 700, italic = true),
        readerHead = readerSteps.map { style(l, it[0], it[1], 700) },
        readerBody = readerSteps.map { style(l, it[2], it[3], 400) },
        readerSizeButton = style(l, 14, null, 600),
        streakNumber = style(p, 68, 76, 700, italic = true),
    )
}

/** Material 3 type roles from the Stash tokens, so M3 components (dialogs, snackbars, text fields) use Inter and Lora. */
fun materialTypography(t: StashTypography) = Typography(
    displayLarge = t.streakNumber,
    displayMedium = t.widgetCount,
    displaySmall = t.wordmark,
    headlineLarge = t.screenTitle,
    headlineMedium = t.screenTitle,
    headlineSmall = t.sheetTitle,
    titleLarge = t.sheetTitle,
    titleMedium = t.itemTitle,
    titleSmall = t.calloutStrong,
    bodyLarge = t.body,
    bodyMedium = t.callout,
    bodySmall = t.meta,
    labelLarge = t.label,
    labelMedium = t.labelSmall,
    labelSmall = t.captionStrong,
)
