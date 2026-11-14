package com.techgv.stash.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.techgv.stash.core.designsystem.theme.StashMotion
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.resolve
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.WidgetSize
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.app_wordmark
import com.techgv.stash.core.resources.card_meta_two
import com.techgv.stash.core.resources.widget_all_caught_up
import com.techgv.stash.core.resources.widget_all_caught_up_short
import com.techgv.stash.core.resources.widget_unread_count
import com.techgv.stash.core.resources.widget_unread_label
import org.jetbrains.compose.resources.stringResource

enum class EmptyIllustration { Book, Archive, None }

/** Centred empty state with 48 dp top padding; announced politely when it appears. */
@Composable
fun EmptyState(title: String, body: String, illustration: EmptyIllustration, modifier: Modifier = Modifier) {
    val c = Stash.colors
    val (side: Dp, gap: Dp) = when (illustration) {
        EmptyIllustration.Book -> 20.dp to 12.dp
        EmptyIllustration.Archive -> 16.dp to 10.dp
        EmptyIllustration.None -> 12.dp to 8.dp
    }
    Column(
        modifier.fillMaxWidth().padding(horizontal = side, vertical = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (illustration != EmptyIllustration.None) {
            // The illustration breathes once (1.03) when the state appears.
            val reduceMotion = Stash.reduceMotion
            val breath = remember { Animatable(0f) }
            LaunchedEffect(reduceMotion) {
                if (reduceMotion) return@LaunchedEffect
                breath.animateTo(1f, tween(StashMotion.EMPTY_BREATH / 2, easing = StashMotion.EaseInOut))
                breath.animateTo(0f, tween(StashMotion.EMPTY_BREATH / 2, easing = StashMotion.EaseInOut))
            }
            Box(Modifier.graphicsLayer { val k = 1f + 0.03f * breath.value; scaleX = k; scaleY = k }) {
                when (illustration) {
                    EmptyIllustration.Book -> OpenBook()
                    EmptyIllustration.Archive -> Icon(StashIcons.archive(1.4f), null, Modifier.size(44.dp), tint = c.outline)
                    EmptyIllustration.None -> Unit
                }
            }
        }
        Txt(title, Stash.type.sheetTitle, c.ink, align = TextAlign.Center)
        Txt(body, Stash.type.bodySmall, c.inkFaded, align = TextAlign.Center)
    }
}

/** Open book with a ribbon. */
@Composable
private fun OpenBook() {
    val c = Stash.colors
    Canvas(Modifier.size(140.dp, 90.dp)) {
        val k = size.width / 140f
        // Two pages skewed ±6° (tan 6° × 32 ≈ 3.4) so they dip towards the spine.
        fun page(x0: Float, lift: Float) = Path().apply {
            moveTo(x0 * k, (12 - lift) * k); lineTo((x0 + 64) * k, (12 + lift) * k)
            lineTo((x0 + 64) * k, (84 + lift) * k); lineTo(x0 * k, (84 - lift) * k); close()
        }
        listOf(page(6f, 3.4f), page(70f, -3.4f)).forEach {
            drawPath(it, c.surface)
            drawPath(it, c.outline, style = Stroke(1.dp.toPx()))
        }
        drawRoundRect(c.primary, Offset(66 * k, 6 * k), Size(8 * k, 82 * k), CornerRadius(4 * k))
    }
}

// The widget sits on a fixed wallpaper in both themes.
private val Wallpaper = Color(0xFF3A2A1E)
private val WallpaperGlow = Color(0x38E0B45C)
private val WallpaperShade = Color(0x8C7A4E2D)
private val DockIcon = Color(0x40F4ECD8)

/**
 * Home-screen widget preview on a wallpaper with a dock: Medium (header, up to 3 rows of [items]) or Small (unread
 * count, streak, next title). Empty [items] shows "All caught up".
 */
@Composable
fun WidgetPreview(
    widgetSize: WidgetSize,
    appIcon: AppIcon,
    streak: Int,
    status: StreakStatus,
    unread: Int,
    items: List<SavedItemCardUi>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The preview is a picture of a fixed-size home-screen widget on a fixed wallpaper, so its text keeps 100 % scale
    // instead of growing out of the drawing at large font scales.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        val c = Stash.colors
        val look = streakLook(status)
        Box(
            modifier.fillMaxWidth().height(470.dp).clip(RoundedCornerShape(26.dp))
                .background(Wallpaper)
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(WallpaperGlow, Color.Transparent), Offset(size.width * .2f, size.height * .15f), size.maxDimension * .45f))
                    drawRect(Brush.radialGradient(listOf(WallpaperShade, Color.Transparent), Offset(size.width * .85f, size.height * .8f), size.maxDimension * .55f))
                }
                .padding(start = 16.dp, end = 16.dp, top = 26.dp),
        ) {
            val card = Modifier.graphicsLayer { shadowElevation = 26f; shape = RoundedCornerShape(24.dp); clip = true }
                .background(c.surface).border(1.dp, c.outline, RoundedCornerShape(24.dp)).padding(14.dp)
            val wordmark = stringResource(Res.string.app_wordmark)
            if (widgetSize == WidgetSize.Medium) {
                Column(Modifier.fillMaxWidth().then(card), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIconTile(appIcon, 22.dp, 7.dp, shadow = false)
                        Txt(wordmark, Stash.type.widgetWordmark, c.primary, Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Candle(14.dp, c.inkFaded, look.flame, flicker = look.flicker)
                            Txt("$streak", Stash.type.countSmall, look.fg)
                        }
                        Txt(stringResource(Res.string.widget_unread_count, unread), Stash.type.meta, c.inkFaded)
                    }
                    items.forEach { i ->
                        Row(
                            Modifier.fillMaxWidth().focusRing(RoundedCornerShape(Stash.radius.card)).clip(RoundedCornerShape(Stash.radius.card)).background(c.bg)
                                .clickable(role = Role.Button) { onOpen(i.id) }.padding(Stash.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Like the Android widget's rows: the thumbnail when there is one.
                            ItemThumbnail(i, 15.sp, Modifier.size(34.dp).clip(RoundedCornerShape(Stash.radius.thumbnailCompact)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Txt(i.title, Stash.type.itemTitleSmall, c.ink, maxLines = 1)
                                val meta = i.length?.resolve()?.let { stringResource(Res.string.card_meta_two, i.siteName, it) } ?: i.siteName
                                Txt(meta, Stash.type.caption, c.inkFaded, maxLines = 1)
                            }
                        }
                    }
                    if (items.isEmpty()) {
                        Txt(
                            stringResource(Res.string.widget_all_caught_up), Stash.type.itemTitle, c.inkFaded,
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 18.dp), align = TextAlign.Center,
                        )
                    }
                }
            } else {
                Column(Modifier.size(160.dp).then(card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt(wordmark, Stash.type.glyph, c.primary, Modifier.weight(1f))
                        Candle(13.dp, c.inkFaded, look.flame, flicker = look.flicker)
                        Txt("$streak", Stash.type.countSmall, look.fg)
                    }
                    Txt("$unread", Stash.type.widgetCount, c.ink)
                    Txt(stringResource(Res.string.widget_unread_label), Stash.type.caption, c.inkFaded, Modifier.offset(y = (-4).dp))
                    Box(Modifier.weight(1f))
                    val next = items.firstOrNull()
                    Txt(
                        next?.title ?: stringResource(Res.string.widget_all_caught_up_short), Stash.type.itemTitleSmall, c.ink,
                        Modifier.focusRing().clickable(role = Role.Button) { next?.let { onOpen(it.id) } }, maxLines = 2,
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                repeat(4) { k ->
                    if (k == 2) AppIconTile(appIcon, 50.dp, 15.dp)
                    else Box(Modifier.size(50.dp).clip(CircleShape).background(DockIcon))
                }
            }
        }
    }
}
