package com.techgv.stash.core.testing

import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ReaderRepository
import com.techgv.stash.core.domain.ReaderResult
import com.techgv.stash.core.domain.RecentSearchRepository
import com.techgv.stash.core.domain.RecentSearches
import com.techgv.stash.core.domain.ReminderRepository
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.RecentSearch
import com.techgv.stash.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

/** Set [preferences]' value to change settings. */
class FakeSettingsRepository(initial: UserPreferences = UserPreferences()) : SettingsRepository {
    override val preferences = MutableStateFlow(initial)
}

class FakeRecentSearchRepository(private val clock: Clock = TestClock()) : RecentSearchRepository {
    private val searches = MutableStateFlow<List<RecentSearch>>(emptyList())

    override fun observe(): StateFlow<List<RecentSearch>> = searches

    override suspend fun add(query: String) {
        searches.value = RecentSearches.add(searches.value, query, clock.now())
    }
}

/** Records every command; [result] scripts the reply per command (default: one item changed). */
class RecordingCommandBus(
    var result: (Command) -> DispatchResult = { DispatchResult.Done(affected = 1) },
) : CommandBus {
    val dispatched = mutableListOf<Command>()

    override suspend fun dispatch(command: Command): DispatchResult {
        dispatched += command
        return result(command)
    }
}

/** [results] scripts the answer per item, [default] the rest; [requested] records every call. */
class FakeReaderRepository(var default: ReaderResult = ReaderResult.Unavailable) : ReaderRepository {
    val results = mutableMapOf<ItemId, ReaderResult>()
    val requested = mutableListOf<ItemId>()

    override suspend fun article(id: ItemId): ReaderResult {
        requested += id
        return results[id] ?: default
    }
}

/** [plan] scripts what [replan] returns; [replanCount] counts calls. */
class FakeReminderRepository(var plan: List<PlannedNotification> = emptyList()) : ReminderRepository {
    var replanCount = 0
        private set

    override suspend fun replan(): List<PlannedNotification> {
        replanCount++
        return plan
    }
}
