package com.techgv.stash.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.disabled
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.accentColor
import com.techgv.stash.core.designsystem.theme.stashColors
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.app_icon_glyph
import org.jetbrains.compose.resources.stringResource

/** Uppercase Lora section title (a heading) over a surface group with an outline and `radius-lg`. */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Stash.colors
    val shape = RoundedCornerShape(Stash.radius.large)
    Column(modifier) {
        Txt(
            title.uppercase(), Stash.type.sectionSettings, c.primary,
            Modifier.padding(start = Stash.spacing.xxs, end = Stash.spacing.xxs, bottom = Stash.spacing.xs).semantics { heading() },
        )
        Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.outline, shape), content = content)
    }
}

/** 1 dp variant rule inset 16 dp between rows. */
@Composable
fun SettingsDivider() = Box(Modifier.padding(horizontal = Stash.spacing.md).fillMaxWidth().height(1.dp).background(Stash.colors.variant))

@Composable
fun SettingsLabels(title: String, description: String?, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    val c = Stash.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(title, Stash.type.bodyStrong, c.ink)
        if (!description.isNullOrEmpty()) Txt(description, Stash.type.meta, c.inkFaded)
        extra?.invoke()
    }
}

private val RowPadding @Composable get() = Modifier.heightIn(min = 60.dp).padding(horizontal = Stash.spacing.md, vertical = 10.dp)

/**
 * Row of min 60 dp: title, optional description and [extra] content, an optional [badge] ("Phase 2") and a trailing
 * control. [enabled] false draws the row at 55 % and marks it disabled.
 */
@Composable
fun SettingsRow(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    extra: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().then(RowPadding).dimmedWhenDisabled(enabled),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
    ) {
        SettingsLabels(title, description, Modifier.weight(1f), extra)
        badge?.invoke()
        trailing?.invoke(this)
    }
}

/**
 * A setting with a switch: the whole row is one toggleable node (role switch, named by [title]); the switch only draws.
 * [enabled] false dims the row to 55 % and ignores taps; [badge] shows next to the switch (the "Phase 2" badge).
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        // The group clips its rows, so their focus rings are drawn inside.
        modifier.fillMaxWidth().focusRing(inset = true)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .then(RowPadding).dimmedWhenDisabled(enabled),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
    ) {
        SettingsLabels(title, description, Modifier.weight(1f))
        badge?.invoke()
        StashSwitch(checked, onCheckedChange = null)
    }
}

/** Tappable row that navigates or acts; [chevron] marks navigation. [titleColor] is danger for destructive rows. */
@Composable
fun SettingsNavRow(
    title: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    chevron: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = if (chevron) StashIcons.chevronRight() else null,
    titleColor: Color = Stash.colors.ink,
) {
    val c = Stash.colors
    Row(
        modifier.fillMaxWidth().focusRing(inset = true).clickable(enabled = enabled, role = Role.Button, onClick = onClick).then(RowPadding).dimmedWhenDisabled(enabled),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, Stash.type.bodyStrong, titleColor)
            if (!description.isNullOrEmpty()) Txt(description, Stash.type.meta, c.inkFaded)
        }
        trailingIcon?.let { Icon(it, null, Modifier.size(18.dp), tint = c.inkFaded) }
    }
}

/** Title and description with a full-width control below (segmented controls, option cards, chips). */
@Composable
fun SettingsBlock(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    gap: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Stash.spacing.md, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        SettingsLabels(title, description)
        content()
    }
}

private fun Modifier.dimmedWhenDisabled(enabled: Boolean) =
    if (enabled) this else graphicsLayer { alpha = DisabledAlpha }.semantics { disabled() }

/**
 * Inline confirmation for destructive or bulk settings (Cancel / Clear, Not yet / Move now). With a [focusRequester] the
 * question takes focus as soon as the confirm appears, so keyboard and screen-reader users land on it.
 */
@Composable
fun InlineConfirm(
    question: String,
    detail: String?,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmColor: Color,
    background: Color,
    bordered: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val c = Stash.colors
    val r = Stash.radius.input
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(r)).background(background)
            .then(if (bordered) Modifier.border(1.dp, c.inputBorder, RoundedCornerShape(r)) else Modifier)
            .padding(Stash.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val questionModifier = if (focusRequester == null) Modifier else {
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
            Modifier.focusRing().focusRequester(focusRequester).focusable()
        }
        Txt(question, if (bordered) Stash.type.calloutStrong else Stash.type.emphasis, c.ink, questionModifier)
        detail?.let { Txt(it, Stash.type.meta, c.inkFaded) }
        RowOrStack(listOf(1f, 1f), gap = 10.dp) {
            OutlineButton(dismissLabel, onDismiss, height = 44.dp, radius = r, border = c.inputBorder, style = Stash.type.calloutStrong)
            PrimaryButton(confirmLabel, onConfirm, height = 44.dp, radius = r, bg = confirmColor, style = Stash.type.calloutStrong)
        }
    }
}

/**
 * A selectable card with a live mini-preview: 2 dp border (primary when selected), 14 dp radius, radio semantics.
 * Put several in a `RowOrStack(..., Modifier.selectableGroup())`, so at very large text they stack instead of breaking
 * their labels inside a word, and give each `Modifier.radioGroupOption(...)` for the arrow keys.
 */
@Composable
fun OptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    preview: @Composable () -> Unit,
) {
    val c = Stash.colors
    val shape = RoundedCornerShape(Stash.radius.card)
    Column(
        modifier.dimmedWhenDisabled(enabled).focusRing(shape).clip(shape).border(2.dp, if (selected) c.primary else c.outline, shape)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = Stash.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Stash.spacing.xs),
    ) {
        preview()
        Txt(label, Stash.type.labelSmall, c.ink, Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
}

/** Mini preview of a theme: header bar and two cards, split diagonally for Auto. */
@Composable
fun ThemePreview(theme: ThemeMode) {
    // The preview shows the other theme too, so it draws with that theme's Leather tokens, not the current colours.
    val p = stashColors(dark = theme == ThemeMode.Night, accent = Accent.Leather)
    val night = stashColors(dark = true, accent = Accent.Leather)
    val bg = p.bg
    val surface = p.surface
    val outline = p.outline
    val primary = p.primary
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(9.dp)).background(bg).border(1.dp, outline, RoundedCornerShape(9.dp))) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth(0.5f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(primary))
            repeat(2) { Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(surface).border(1.dp, outline, RoundedCornerShape(4.dp))) }
        }
        if (theme == ThemeMode.Auto) Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.5f).fillMaxHeight().background(night.bg))
    }
}

/** Mini preview of a card layout in the current colours. */
@Composable
fun CardLayoutPreview(layout: CardLayout) {
    val c = Stash.colors
    Column(
        Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(9.dp)).background(c.bg).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val card = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.surface).border(1.dp, c.outline, RoundedCornerShape(6.dp))
        when (layout) {
            CardLayout.Comfortable -> repeat(2) {
                Row(card.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(c.primary))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.fillMaxWidth(0.9f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.ink))
                        Box(Modifier.fillMaxWidth(0.55f).height(3.dp).clip(RoundedCornerShape(1.dp)).background(c.inkFaded))
                    }
                }
            }
            CardLayout.Compact -> repeat(4) {
                Row(card.padding(horizontal = 5.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(c.primary))
                    Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(1.dp)).background(c.ink))
                }
            }
            CardLayout.LargeImages -> Column(card) {
                Box(Modifier.fillMaxWidth().height(30.dp).background(c.primary))
                Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.ink))
                    Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(1.dp)).background(c.inkFaded))
                }
            }
        }
    }
}

/**
 * 36 dp accent swatch with a 48 dp touch target, a radio button named after the accent. In a `selectableGroup()` row,
 * give each `Modifier.radioGroupOption(...)` for the arrow keys.
 */
@Composable
fun AccentSwatch(accent: Accent, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Stash.colors
    Box(
        modifier.minimumInteractiveComponentSize().size(36.dp).focusRing(CircleShape).clip(CircleShape).border(2.dp, if (selected) c.ink else Color.Transparent, CircleShape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }.padding(3.dp),
    ) { Box(Modifier.fillMaxSize().clip(CircleShape).background(accentColor(accent, c.dark))) }
}

/** App icon colours: background, glyph and border. */
@Immutable
data class AppIconColors(val background: Color, val glyph: Color, val border: Color)

fun appIconColors(icon: AppIcon): AppIconColors = when (icon) {
    AppIcon.Leather -> AppIconColors(Color(0xFF7A4E2D), Color(0xFFFBF6EA), Color(0xFF5E3A20))
    AppIcon.Night -> AppIconColors(Color(0xFF1E1812), Color(0xFFE0B45C), Color(0xFF3D3226))
    AppIcon.Parchment -> AppIconColors(Color(0xFFF4ECD8), Color(0xFF7A4E2D), Color(0xFFCBB894))
    AppIcon.Ink -> AppIconColors(Color(0xFF2F4A6B), Color(0xFFF4ECD8), Color(0xFF1F3550))
}

/** The app icon: a rounded tile with the Playfair "S", sized from the tile (not from the font scale, so it never overflows). */
@Composable
fun AppIconTile(icon: AppIcon, size: Dp, radius: Dp, shadow: Boolean = true) {
    val colors = appIconColors(icon)
    Box(
        Modifier.size(size)
            .graphicsLayer { if (shadow) { shadowElevation = 6f; shape = RoundedCornerShape(radius) } }
            .clip(RoundedCornerShape(radius)).background(colors.background).border(1.dp, colors.border, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        val glyphSize = with(LocalDensity.current) { (size * 0.58f).toSp() }
        Txt(stringResource(Res.string.app_icon_glyph), Stash.type.appIconGlyph.copy(fontSize = glyphSize, lineHeight = glyphSize), colors.glyph)
    }
}

/** Icon shown next to a swipe action option; null for Nothing. */
fun swipeActionIcon(action: SwipeAction): ImageVector? = when (action) {
    SwipeAction.MarkRead -> StashIcons.check()
    SwipeAction.Archive -> StashIcons.archive(2f)
    SwipeAction.Share -> StashIcons.share(2f)
    SwipeAction.Remove -> StashIcons.torn()
    SwipeAction.Nothing -> null
}
