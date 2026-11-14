package com.techgv.stash.core.domain

import androidx.paging.PagingData
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.RecentSearch
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

// Interfaces the data layer implements. Each must match the pure policies in this module.
//
// Time: implementations take a `kotlin.time.Clock` and a [ZoneProvider] by constructor and never call `Clock.System`
// or `TimeZone.currentSystemDefault()` directly, so tests control "now" and the user's zone.

/** Reads items. Every write goes through [CommandBus]. */
interface ItemRepository {
    /** Shelf rows for [spec], ordered like [ShelfQuery.comparator], with [ShelfQuery.groupLabel] headers between them. */
    fun observeShelf(spec: ShelfQuerySpec): Flow<PagingData<ShelfRow>>

    /** Archived rows like [ShelfQuery.archive], matching [searchText] with [SearchMatcher]. */
    fun observeArchive(searchText: String): Flow<PagingData<ShelfRow>>

    /** Shelf search results like `ShelfQuery.search(...).shelf`, newest first, after [ShelfQuery.effectiveSearchFilter]. */
    fun observeShelfSearch(text: String, filter: SearchFilter): Flow<PagingData<SearchHit>>

    /** Archive search results like `ShelfQuery.search(...).archive`. */
    fun observeArchiveSearch(text: String, filter: SearchFilter): Flow<PagingData<SearchHit>>

    /** Search chips like `ShelfQuery.search(...).chips`, counted in SQL. */
    fun observeSearchChips(text: String): Flow<SearchChips>

    /** Chip counts like [ShelfQuery.chipCounts], counted in SQL. */
    fun observeChipCounts(): Flow<ChipCounts>

    fun observeArchiveCount(): Flow<Int>

    fun observe(id: ItemId): Flow<SavedItem?>

    suspend fun get(id: ItemId): SavedItem?

    /** Like [ReminderPlanner.shortestUnread]; for nudges and "Read something short". */
    suspend fun shortestUnread(): SavedItem?

    /** Unread items newest first, for the widget. */
    suspend fun newestUnread(limit: Int): List<SavedItem>

    /** How many items [Command.ApplyAutoArchive] would move now, for the settings confirm. */
    suspend fun countAutoArchiveDue(period: AutoArchivePeriod): Int
}

/**
 * Validates, writes and logs a command in one transaction, then re-plans reminders and the widget.
 * Item commands run through [CommandDecider] and report [DispatchResult.of]; [Command.ChangeSetting] applies its
 * transform atomically to the stored preferences and reports `Done(affected = 0)`.
 * The same transaction keeps `unreadZeroSince` up to date (see [StreakRepository]), and for [Command.MarkRead] and
 * [Command.OpenItem] records the streak day with [StreakEngine.onRead] and reports its celebration (decision D21).
 */
interface CommandBus {
    suspend fun dispatch(command: Command): DispatchResult
}

/** Read-only; changes go through [Command.ChangeSetting] (decision D12). */
interface SettingsRepository {
    val preferences: Flow<UserPreferences>
}

/**
 * Stores [com.techgv.stash.core.model.StreakDay] rows and `unreadZeroSince: Instant?`, the moment the unread count last
 * became zero (null while something is unread), both updated in the command transaction (decision D13):
 * - unread count goes from more to 0: set `unreadZeroSince` to now. Any value stored is capped at now, never in the future.
 * - unread count goes from 0 to more: write [StreakEngine.restDays] as rest rows (`firstReadAt = null`), then set it to null.
 * Every read passes `unreadZeroSince` to [StreakEngine], which derives the rest days not yet written.
 */
interface StreakRepository {
    /**
     * [StreakEngine.state] for now; re-emits on every change and at each local midnight and 21:30. Reads are recorded by
     * [Command.MarkRead] and [Command.OpenItem] (decision D21).
     */
    fun observeStreak(): Flow<StreakState>
}

interface ReminderRepository {
    /** Re-plans with [ReminderPlanner], stores and applies the plan, and returns it. */
    suspend fun replan(): List<PlannedNotification>
}

sealed interface ReaderResult {
    data class Ready(val article: ReaderArticle) : ReaderResult

    /** Reader mode could not extract text; show the web view. */
    data object Unavailable : ReaderResult

    /** Not cached and no network. */
    data object Offline : ReaderResult
}

interface ReaderRepository {
    /** Cached reader text, or fetches, extracts and caches it. */
    suspend fun article(id: ItemId): ReaderResult
}

interface RecentSearchRepository {
    /** Newest first, at most [RecentSearches.MAX]. */
    fun observe(): Flow<List<RecentSearch>>

    /** Applies [RecentSearches.add]. */
    suspend fun add(query: String)
}
