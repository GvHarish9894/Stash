package com.techgv.stash.core.designsystem.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.card_a11y_label
import com.techgv.stash.core.resources.card_a11y_label_status
import com.techgv.stash.core.resources.card_archived_at
import com.techgv.stash.core.resources.card_minutes_read
import com.techgv.stash.core.resources.card_minutes_video
import com.techgv.stash.core.resources.card_repository
import com.techgv.stash.core.resources.card_state_archived
import com.techgv.stash.core.resources.card_state_failed
import com.techgv.stash.core.resources.card_state_pending
import com.techgv.stash.core.resources.card_state_read
import com.techgv.stash.core.resources.card_state_unread
import com.techgv.stash.core.resources.card_video
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Where the item is in its lifecycle. Drives the actions, the item menu, the time line and the read styling. */
enum class CardLifecycle { Unread, Read, Archived }

/** Whether the page details arrived. Only changes the title, thumbnail and meta treatment, never the actions. */
enum class MetadataState { Ready, Pending, Failed }

/** Video items draw this monogram as a triangle (iOS would render "▶" as an emoji). */
const val PLAY_MONOGRAM = "▶"

/**
 * Everything a card, the item menu header, a search result or a widget row shows for one item.
 * [time] is the card form ("2h ago", or "Archived Nov 2"); [timeSpoken] the accessibility form ("2 hours ago").
 */
@Immutable
data class SavedItemCardUi(
    val id: String,
    val title: String,
    val siteName: String,
    val monogram: String,
    val tint: Color,
    val monogramInk: Color,
    val time: UiText?,
    val timeSpoken: UiText?,
    /** "7 min read", "18 min video", "video", "repository"; null when unknown. Dropped first when the meta line is too long. */
    val length: UiText?,
    val lifecycle: CardLifecycle,
    val metadata: MetadataState,
    /** Never read: the unread dot, and Mark read (not Mark unread) in the actions. Also set for archived items. */
    val unread: Boolean,
    /** The item's https image for the thumbnail; null shows the monogram tile. */
    val imageUrl: String? = null,
)

/** Monogram tile colours from the prototype; brass takes dark ink. */
val TilePalette: List<Color> = listOf(
    0xFF7A4E2D, 0xFF3F5E4A, 0xFFB8893B, 0xFF8B2E2A, 0xFF3B2F23, 0xFF5E3A20, 0xFF2F4A6B, 0xFF6E5D49,
).map { Color(it) }

private val brassTile = Color(0xFFB8893B)
private val lightInk = Color(0xFFF4ECD8)
private val darkInk = Color(0xFF3B2F23)

/**
 * Tile colour for a source: FNV-1a (32 bit) over the case-folded [sourceKey]'s UTF-16 units, high half XOR-ed into the
 * low half (FNV's low bits alone spread badly over 8 slots), modulo the palette size. Written out instead of
 * `String.hashCode` so it is the same on every platform and release.
 */
fun tileIndex(sourceKey: String): Int {
    var hash = 0x811C9DC5.toInt()
    for (ch in sourceKey.lowercase()) {
        hash = hash xor ch.code
        hash *= 0x01000193
    }
    val folded = (hash xor (hash ushr 16)).toUInt()
    return (folded % TilePalette.size.toUInt()).toInt()
}

fun tileTint(sourceKey: String): Color = TilePalette[tileIndex(sourceKey)]

fun monogramInk(tint: Color): Color = if (tint == brassTile) darkInk else lightInk

/** First letter or digit of the site name (then the domain), upper-cased; [PLAY_MONOGRAM] for videos. */
fun monogram(siteName: String, domain: String, contentType: ContentType): String {
    if (contentType == ContentType.Video) return PLAY_MONOGRAM
    val source = siteName.removePrefix("www.").ifBlank { domain.removePrefix("www.") }
    return source.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
}

/** Deleted items are not shown; if one is, it is treated like an archived one. */
fun cardLifecycle(lifecycle: ItemLifecycle): CardLifecycle = when (lifecycle) {
    ItemLifecycle.Unread -> CardLifecycle.Unread
    ItemLifecycle.Read -> CardLifecycle.Read
    ItemLifecycle.Archived, ItemLifecycle.Deleted -> CardLifecycle.Archived
}

fun metadataState(fetchStatus: FetchStatus): MetadataState = when (fetchStatus) {
    FetchStatus.Fetched -> MetadataState.Ready
    FetchStatus.Pending -> MetadataState.Pending
    FetchStatus.Failed -> MetadataState.Failed
}

/** "7 min read" / "18 min video" / "video" / "repository"; null for an article of unknown length. */
fun lengthText(contentType: ContentType, readingMinutes: Int?): UiText? = when (contentType) {
    ContentType.Article -> readingMinutes?.let { text(Res.string.card_minutes_read, it) }
    ContentType.Video -> readingMinutes?.let { text(Res.string.card_minutes_video, it) } ?: text(Res.string.card_video)
    ContentType.Repository -> text(Res.string.card_repository)
}

/** Maps a saved item for display at [now] in the user's [zone]. Archived items show when they were archived. */
fun SavedItem.toCardUi(now: Instant, zone: TimeZone): SavedItemCardUi {
    val life = cardLifecycle(lifecycle)
    val meta = metadataState(fetchStatus)
    val archivedAt = archivedAt
    val (time, spoken) = if (life == CardLifecycle.Archived && archivedAt != null) {
        val ago = relativeTime(archivedAt, now, zone)
        text(Res.string.card_archived_at, ago.short()) to text(Res.string.card_archived_at, ago.long())
    } else {
        val ago = relativeTime(addedAt, now, zone)
        ago.short() to ago.long()
    }
    val tint = tileTint(sourceKey)
    return SavedItemCardUi(
        id = id.value,
        // Handoff: a failed fetch shows the URL; a pending one shows the provisional title or the URL.
        title = if (meta == MetadataState.Failed) url else title.ifBlank { url },
        siteName = siteName.ifBlank { domain },
        monogram = monogram(siteName, domain, contentType),
        tint = tint,
        monogramInk = monogramInk(tint),
        time = time,
        timeSpoken = spoken,
        length = if (meta == MetadataState.Failed) null else lengthText(contentType, readingMinutes),
        lifecycle = life,
        metadata = meta,
        unread = readAt == null,
        // D28: https only; the image loader refuses anything else too.
        imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
    )
}

fun lifecycleText(lifecycle: CardLifecycle): UiText = text(
    when (lifecycle) {
        CardLifecycle.Unread -> Res.string.card_state_unread
        CardLifecycle.Read -> Res.string.card_state_read
        CardLifecycle.Archived -> Res.string.card_state_archived
    },
)

/** "Loading details" / "Couldn't load details"; null when the details are there. */
fun metadataText(metadata: MetadataState): UiText? = when (metadata) {
    MetadataState.Ready -> null
    MetadataState.Pending -> text(Res.string.card_state_pending)
    MetadataState.Failed -> text(Res.string.card_state_failed)
}

/**
 * The card's spoken label (handoff Semantics table): "Unread. Designing for slow reading. medium.com, 2 hours ago,
 * 7 min read.", with the details status after the lifecycle while they are loading or failed ("Archived. Couldn't load
 * details. …"). [separator] joins the details (", " in English).
 */
fun cardAccessibilityLabel(card: SavedItemCardUi, separator: UiText): UiText {
    val details = UiText.Joined(listOfNotNull(UiText.Plain(card.siteName), card.timeSpoken, card.length), separator)
    val status = metadataText(card.metadata)
    return if (status == null) {
        text(Res.string.card_a11y_label, lifecycleText(card.lifecycle), UiText.Plain(card.title), details)
    } else {
        text(Res.string.card_a11y_label_status, lifecycleText(card.lifecycle), status, UiText.Plain(card.title), details)
    }
}

/**
 * The card's accessibility actions besides "More options" (handoff): shelf items Mark read or unread, Archive, Share,
 * Remove; archived items Restore, Share, Delete.
 */
fun cardActions(card: SavedItemCardUi): List<SwipeOutcome> = if (card.lifecycle == CardLifecycle.Archived) {
    listOf(SwipeOutcome.Restore, SwipeOutcome.Share, SwipeOutcome.Delete)
} else {
    listOf(if (card.unread) SwipeOutcome.MarkRead else SwipeOutcome.MarkUnread, SwipeOutcome.Archive, SwipeOutcome.Share, SwipeOutcome.Remove)
}

/**
 * A very long site name shortened in the middle, keeping its ending ("blog.verylongdo….com"); the handoff's display rule
 * for domains over about 40 characters. Accessibility labels keep the full name.
 */
fun middleEllipsis(text: String, max: Int = 40): String {
    if (text.length <= max) return text
    val ending = text.substringAfterLast('.', "").let { if (it.isNotEmpty() && it.length <= 10) ".$it" else text.takeLast(6) }
    return text.take((max - 1 - ending.length).coerceAtLeast(1)) + "…" + ending
}
