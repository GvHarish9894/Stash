package com.techgv.stash.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import com.techgv.stash.core.domain.SearchMatcher
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.ReminderLevel
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

// Enums are stored by name; an unknown content type or fetch status reads as a default (see StashConverters). Instants
// are epoch milliseconds.

@Entity(
    tableName = "saved_item",
    indices = [
        Index("normalized_url", unique = true),
        // Shelf and archive lists, newest or oldest first. The id tie-break makes the order complete, so a page and
        // Room's COUNT(*) around the list query read the index in order instead of sorting the shelf.
        Index(
            "deleted_at", "archived_at", "added_at", "id",
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC, Index.Order.DESC],
        ),
        // A source's shelf or archive in order, and the by-source shelf's rows per source.
        Index(
            "source_key", "deleted_at", "archived_at", "added_at", "id",
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.ASC, Index.Order.DESC, Index.Order.DESC],
        ),
        // Shelf counts, the unread count, shortest unread and auto-archive's due items, read from the index alone.
        Index("deleted_at", "archived_at", "read_at"),
        // Pending and failed metadata, a small subset found on every foreground.
        Index("fetch_status"),
    ],
)
data class SavedItemEntity(
    @PrimaryKey val id: String,
    val url: String,
    @ColumnInfo(name = "normalized_url") val normalizedUrl: String,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "favicon_url") val faviconUrl: String?,
    @ColumnInfo(name = "site_name") val siteName: String,
    val domain: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "content_type") val contentType: ContentType,
    @ColumnInfo(name = "source_app") val sourceApp: String?,
    @ColumnInfo(name = "reading_minutes") val readingMinutes: Int?,
    @ColumnInfo(name = "added_at") val addedAt: Instant,
    @ColumnInfo(name = "read_at") val readAt: Instant?,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
    @ColumnInfo(name = "fetch_status") val fetchStatus: FetchStatus,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/**
 * Search index over title and site name (decisions D11, D16), kept in sync with `saved_item` by Room's content triggers.
 * The tokenizer splits words exactly like [SearchMatcher]: the Devanagari and Tamil marks are word characters.
 * `tokenchars` is belt-and-braces: SQLite's `unicode61` already treats those marks as token characters (D16).
 */
@Fts4(
    contentEntity = SavedItemEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=0", "tokenchars=" + SearchMatcher.WORD_MARKS],
)
@Entity(tableName = "saved_item_fts")
data class SavedItemFtsEntity(
    val title: String,
    @ColumnInfo(name = "site_name") val siteName: String,
)

/** Cached reader text. [htmlBytes] (UTF-8 size of the HTML) and [lastOpenedAt] drive LRU eviction. */
@Entity(
    tableName = "article_content",
    foreignKeys = [
        ForeignKey(entity = SavedItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ArticleContentEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "extracted_html") val extractedHtml: String,
    val standfirst: String?,
    @ColumnInfo(name = "text_length") val textLength: Int,
    @ColumnInfo(name = "extracted_at") val extractedAt: Instant,
    @ColumnInfo(name = "extractor_version") val extractorVersion: Int,
    @ColumnInfo(name = "html_bytes") val htmlBytes: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Instant,
)

/**
 * Metadata attempts of a pending item: [failedAttempts] counted toward the limit and [lastAttemptAt] of any attempt,
 * counted or not, so catch-up rotates through pending items. Kept here so both survive process death; the row is
 * deleted when the metadata resolves (fetched or failed), on a retry, and with the item when it is purged.
 */
@Entity(
    tableName = "metadata_attempt",
    foreignKeys = [
        ForeignKey(entity = SavedItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MetadataAttemptEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "failed_attempts") val failedAttempts: Int,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Instant,
)

/** Append-only command log; [payloadJson] is the typed payload (see OperationPayloadJson). */
@Entity(tableName = "operation")
data class OperationEntity(
    @PrimaryKey @ColumnInfo(name = "op_id") val opId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val type: OperationType,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/** A null [firstReadAt] is a rest day. */
@Entity(tableName = "streak_day")
data class StreakDayEntity(
    @PrimaryKey @ColumnInfo(name = "local_date") val localDate: LocalDate,
    @ColumnInfo(name = "first_read_at") val firstReadAt: Instant?,
)

/** The last applied reminder plan, with the copy parameters to re-render each notification. */
@Entity(tableName = "planned_notification")
data class PlannedNotificationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "fire_at") val fireAt: Instant,
    val level: ReminderLevel,
    @ColumnInfo(name = "item_id") val itemId: String,
    val streak: Int,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
    @ColumnInfo(name = "item_title") val itemTitle: String,
    val minutes: Int?,
)

@Entity(tableName = "recent_search")
data class RecentSearchEntity(
    @PrimaryKey @ColumnInfo(collate = ColumnInfo.NOCASE) val query: String,
    @ColumnInfo(name = "used_at") val usedAt: Instant,
)

/** Single row (id 0) of app state that must change in the same transaction as items. */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int,
    /** When the unread count last became zero; null while something is unread (decision D13). */
    @ColumnInfo(name = "unread_zero_since") val unreadZeroSince: Instant?,
)
