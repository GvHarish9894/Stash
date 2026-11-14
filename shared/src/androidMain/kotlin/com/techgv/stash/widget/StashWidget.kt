package com.techgv.stash.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.techgv.stash.core.designsystem.theme.StashColors
import com.techgv.stash.core.designsystem.theme.stashColors
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.WidgetItem
import com.techgv.stash.core.model.WidgetSnapshot
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.app_wordmark
import com.techgv.stash.core.resources.streak_chip_lit
import com.techgv.stash.core.resources.streak_chip_unlit
import com.techgv.stash.core.resources.widget_all_caught_up
import com.techgv.stash.core.resources.widget_all_caught_up_short
import com.techgv.stash.core.resources.widget_unread_count
import com.techgv.stash.core.resources.widget_unread_label
import com.techgv.stash.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString

/** The home-screen widget (handoff `StashWidget`): small 2×2 and medium 4×2, light and dark. */
class StashWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = WidgetSnapshotStore.updates(context)
            .map { snapshot ->
                val thumbnails = snapshot.items.mapNotNull { item -> item.imageUrl?.let(WidgetThumbnails::load)?.let { item.id to it } }.toMap()
                Triple(snapshot, thumbnails, widgetText(snapshot))
            }
            .flowOn(Dispatchers.IO)
        val first = content.first()
        provideContent {
            val (snapshot, thumbnails, text) = content.collectAsState(first).value
            StashWidgetContent(snapshot, thumbnails, text)
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
    }
}

/** The medium (4×2) provider. */
class StashWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StashWidget()
}

/** The small (2×2) provider: the same widget, placed at 2×2 by default (see [GlanceWidgetPinner]). */
class StashSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StashWidget()
}

/** The widget's text, from `core:resources` in the app language (Compose resources follow `Locale.getDefault()`). */
data class WidgetText(
    val wordmark: String,
    val unreadCount: String,
    val unreadLabel: String,
    val allCaughtUp: String,
    val allCaughtUpShort: String,
    /** The candle's description, the same as the in-app streak chip's. */
    val streak: String,
)

suspend fun widgetText(snapshot: WidgetSnapshot) = WidgetText(
    wordmark = getString(Res.string.app_wordmark),
    unreadCount = getString(Res.string.widget_unread_count, snapshot.unreadCount),
    unreadLabel = getString(Res.string.widget_unread_label),
    allCaughtUp = getString(Res.string.widget_all_caught_up),
    allCaughtUpShort = getString(Res.string.widget_all_caught_up_short),
    streak = getString(if (snapshot.readToday) Res.string.streak_chip_lit else Res.string.streak_chip_unlit, snapshot.streak),
)

// The design-system tokens, light ("Parchment") and dark ("Night library").
// shortcut: always the Leather accent, because WidgetSnapshot carries no accent; pass the user's accent once it does.
private val Light = stashColors(dark = false, accent = Accent.Leather)
private val Dark = stashColors(dark = true, accent = Accent.Leather)

private fun token(pick: StashColors.() -> Color) = ColorProvider(day = Light.pick(), night = Dark.pick())

private val Surface = token { surface }
private val Background = token { bg }
private val Variant = token { variant }
private val Ink = token { ink }
private val Faded = token { inkFaded }
private val Brass = token { brass }
private val Primary = token { primary }
private val OnPrimary = token { onPrimary }

private fun style(size: TextUnit, color: ColorProvider, weight: FontWeight = FontWeight.Normal, serif: Boolean = false, italic: Boolean = false) =
    TextStyle(
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = if (serif) FontFamily.Serif else null,
    )

/** The widget's content for [snapshot]; rows and the next title open `stash://reader/{id}`, the rest opens the app. */
@Composable
fun StashWidgetContent(snapshot: WidgetSnapshot, thumbnails: Map<ItemId, Bitmap>, text: WidgetText) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().background(Surface).cornerRadius(24.dp).padding(14.dp).clickable(actionStartActivity(openAppIntent(context)))) {
        if (LocalSize.current.width >= StashWidget.MEDIUM.width) Medium(snapshot, thumbnails, text) else Small(snapshot, text)
    }
}

@Composable
private fun Medium(snapshot: WidgetSnapshot, thumbnails: Map<ItemId, Bitmap>, text: WidgetText) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(22.dp).background(Primary).cornerRadius(7.dp), contentAlignment = Alignment.Center) {
            Text("S", style = style(14.sp, OnPrimary, FontWeight.Bold, serif = true, italic = true))
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text.wordmark,
            GlanceModifier.defaultWeight(),
            style(17.sp, Primary, FontWeight.Bold, serif = true, italic = true),
        )
        Streak(snapshot, text)
        Spacer(GlanceModifier.width(6.dp))
        Text(text.unreadCount, style = style(12.sp, Faded))
    }
    if (snapshot.allCaughtUp) {
        Text(
            text.allCaughtUp,
            GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 18.dp),
            TextStyle(color = Faded, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center),
        )
    }
    snapshot.items.take(3).forEach { item ->
        Spacer(GlanceModifier.height(6.dp))
        ItemRow(item, thumbnails[item.id])
    }
}

@Composable
private fun ItemRow(item: WidgetItem, thumbnail: Bitmap?) {
    val context = LocalContext.current
    Row(
        GlanceModifier.fillMaxWidth().background(Background).cornerRadius(14.dp).padding(6.dp).clickable(actionStartActivity(readerIntent(context, item.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) {
            Image(ImageProvider(thumbnail), null, GlanceModifier.size(34.dp).cornerRadius(8.dp), contentScale = ContentScale.Crop)
        } else {
            Box(GlanceModifier.size(34.dp).background(Variant).cornerRadius(8.dp), contentAlignment = Alignment.Center) {
                Text(monogram(item.siteName), style = style(15.sp, Ink, FontWeight.Bold, serif = true))
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(item.title, style = style(13.sp, Ink, FontWeight.Medium, serif = true), maxLines = 1)
            val meta = item.readingMinutes?.let { context.getString(R.string.widget_meta_minutes, item.siteName, it) } ?: item.siteName
            Text(meta, style = style(11.sp, Faded), maxLines = 1)
        }
    }
}

@Composable
private fun ColumnScope.Small(snapshot: WidgetSnapshot, text: WidgetText) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.wordmark,
            GlanceModifier.defaultWeight(),
            style(15.sp, Primary, FontWeight.Bold, serif = true, italic = true),
        )
        Streak(snapshot, text)
    }
    Text("${snapshot.unreadCount}", style = style(40.sp, Ink, FontWeight.Bold, serif = true, italic = true))
    Text(text.unreadLabel, style = style(11.sp, Faded))
    Spacer(GlanceModifier.defaultWeight())
    val next = snapshot.items.firstOrNull()
    if (next == null) {
        Text(text.allCaughtUpShort, style = style(12.sp, Ink, FontWeight.Medium, serif = true), maxLines = 2)
    } else {
        Text(
            next.title,
            GlanceModifier.clickable(actionStartActivity(readerIntent(context, next.id))),
            style(12.sp, Ink, FontWeight.Medium, serif = true),
            maxLines = 2,
        )
    }
}

/** Candle and streak count: brass once the user read today, faded while the streak still waits for today's read. */
@Composable
private fun Streak(snapshot: WidgetSnapshot, text: WidgetText) {
    val color = if (snapshot.readToday) Brass else Faded
    Image(
        ImageProvider(R.drawable.widget_candle),
        text.streak,
        GlanceModifier.size(14.dp),
        colorFilter = ColorFilter.tint(color),
    )
    Spacer(GlanceModifier.width(3.dp))
    Text("${snapshot.streak}", style = style(12.sp, color, FontWeight.Bold))
}

/**
 * Opens the app at `MainActivity` by class: Glance bakes this intent into the widget, and the launcher alias that
 * `getLaunchIntentForPackage` would name is disabled by the next app-icon change, while `MainActivity` never is.
 */
fun openAppIntent(context: Context): Intent =
    Intent(Intent.ACTION_MAIN).setClassName(context.packageName, "com.techgv.stash.MainActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

private fun monogram(siteName: String) = siteName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"

private fun readerIntent(context: Context, id: ItemId) =
    Intent(Intent.ACTION_VIEW, Uri.parse("stash://reader/${id.value}"))
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
