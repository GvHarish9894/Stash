package com.techgv.stash.core.testing

import com.techgv.stash.core.domain.IdGenerator
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * A [SavedItem] with defaults for every field. [lifecycle] fills the timestamps: Read sets [readAt] an hour after
 * [addedAt]; Archived also sets [archivedAt] a day after that; Deleted sets only [deletedAt] (removed from the shelf
 * unread). Pass a timestamp explicitly to override.
 */
fun savedItem(
    id: String = "item-1",
    lifecycle: ItemLifecycle = ItemLifecycle.Unread,
    addedAt: Instant = FIXTURE_NOW,
    readAt: Instant? = if (lifecycle == ItemLifecycle.Read || lifecycle == ItemLifecycle.Archived) addedAt + 1.hours else null,
    archivedAt: Instant? = if (lifecycle == ItemLifecycle.Archived) addedAt + 25.hours else null,
    deletedAt: Instant? = if (lifecycle == ItemLifecycle.Deleted) addedAt + 1.hours else null,
    siteName: String = "example.com",
    url: String = "https://$siteName/$id",
    normalizedUrl: String = url,
    title: String = "Title $id",
    description: String? = null,
    imageUrl: String? = null,
    faviconUrl: String? = null,
    domain: String = siteName,
    sourceKey: String = siteName,
    contentType: ContentType = ContentType.Article,
    sourceApp: String? = null,
    readingMinutes: Int? = 5,
    fetchStatus: FetchStatus = FetchStatus.Fetched,
    updatedAt: Instant = listOfNotNull(addedAt, readAt, archivedAt, deletedAt).max(),
): SavedItem = SavedItem(
    id = ItemId(id),
    url = url,
    normalizedUrl = normalizedUrl,
    title = title,
    description = description,
    imageUrl = imageUrl,
    faviconUrl = faviconUrl,
    siteName = siteName,
    domain = domain,
    sourceKey = sourceKey,
    contentType = contentType,
    sourceApp = sourceApp,
    readingMinutes = readingMinutes,
    addedAt = addedAt,
    readAt = readAt,
    archivedAt = archivedAt,
    deletedAt = deletedAt,
    fetchStatus = fetchStatus,
    updatedAt = updatedAt,
)

/** Ids `id-1`, `id-2`, ... in order. */
class SequenceIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var count = 0

    override fun next(): String = "$prefix-${++count}"
}
