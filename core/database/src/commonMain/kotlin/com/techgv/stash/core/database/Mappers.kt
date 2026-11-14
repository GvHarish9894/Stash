package com.techgv.stash.core.database

import com.techgv.stash.core.domain.ChipCounts
import com.techgv.stash.core.domain.SearchChips
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.SourceCount
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.RecentSearch
import com.techgv.stash.core.model.ReminderCopy
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.StreakDay

// Entity <-> model mappers. DAOs return entities; the data layer maps them with these.

fun SavedItemEntity.toModel() = SavedItem(
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

fun SavedItem.toEntity() = SavedItemEntity(
    id = id.value,
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

fun OperationEntity.toModel() = Operation(opId, ItemId(itemId), type, OperationPayloadJson.decode(payloadJson), createdAt)

fun Operation.toEntity() = OperationEntity(opId, itemId.value, type, OperationPayloadJson.encode(payload), createdAt)

fun StreakDayEntity.toModel() = StreakDay(localDate, firstReadAt)

fun StreakDay.toEntity() = StreakDayEntity(date, firstReadAt)

fun PlannedNotificationEntity.toModel() =
    PlannedNotification(id, fireAt, level, ItemId(itemId), ReminderCopy(streak, unreadCount, itemTitle, minutes))

fun PlannedNotification.toEntity() =
    PlannedNotificationEntity(id, fireAt, level, itemId.value, copy.streak, copy.unreadCount, copy.itemTitle, copy.minutes)

fun RecentSearchEntity.toModel() = RecentSearch(query, usedAt)

fun RecentSearch.toEntity() = RecentSearchEntity(query, usedAt)

/** [ChipCounts] from [SavedItemDao.observeShelfCounts] and [SavedItemDao.observeShelfSources]. */
fun chipCounts(counts: ShelfCounts, sources: List<SourceCount>) = ChipCounts(
    all = counts.total,
    unread = counts.unread,
    sources = sources.take(ShelfQuery.MAX_SOURCE_CHIPS),
    more = sources.drop(ShelfQuery.MAX_SOURCE_CHIPS),
)

/** [SearchChips] from [SavedItemDao.observeSearchSources], with the visibility rules of `ShelfQuery.search`. */
fun searchChips(sources: List<SearchSourceCount>): SearchChips {
    val all = sources.sumOf { it.count }
    val archived = sources.sumOf { it.archived }
    return SearchChips(
        all = all,
        sources = sources.map { SourceCount(it.sourceKey, it.count) }.takeIf { it.size > 1 }.orEmpty(),
        archived = archived.takeIf { it > 0 && all > archived },
    )
}
