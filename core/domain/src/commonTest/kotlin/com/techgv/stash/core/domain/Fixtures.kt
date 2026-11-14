package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.SavedItem
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

val T0: Instant = Instant.parse("2026-11-12T10:00:00Z")

val KOLKATA = TimeZone.of("Asia/Kolkata")
val LOS_ANGELES = TimeZone.of("America/Los_Angeles")
val KIRITIMATI = TimeZone.of("Pacific/Kiritimati")
val NEW_YORK = TimeZone.of("America/New_York")

/** The instant of a local wall-clock time, for example `local("2026-11-12T21:30", NEW_YORK)`. */
fun local(dateTime: String, zone: TimeZone): Instant = LocalDateTime.parse(dateTime).toInstant(zone)

fun item(
    id: String = "i1",
    addedAt: Instant = T0,
    readAt: Instant? = null,
    archivedAt: Instant? = null,
    deletedAt: Instant? = null,
    sourceKey: String = "example.com",
    title: String = "Title $id",
    siteName: String = sourceKey,
    readingMinutes: Int? = null,
    fetchStatus: FetchStatus = FetchStatus.Fetched,
) = SavedItem(
    id = ItemId(id),
    url = "https://$siteName/$id",
    normalizedUrl = "https://$siteName/$id",
    title = title,
    description = "About $id",
    imageUrl = null,
    faviconUrl = null,
    siteName = siteName,
    domain = siteName,
    sourceKey = sourceKey,
    contentType = ContentType.Article,
    sourceApp = null,
    readingMinutes = readingMinutes,
    addedAt = addedAt,
    readAt = readAt,
    archivedAt = archivedAt,
    deletedAt = deletedAt,
    fetchStatus = fetchStatus,
    updatedAt = addedAt,
)

/** Ids `op-1`, `op-2`, ... so decisions are deterministic. */
class SequentialIds(private val prefix: String = "op") : IdGenerator {
    private var n = 0
    override fun next(): String = "$prefix-${++n}"
}
