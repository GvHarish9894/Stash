package com.techgv.stash.core.data

import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.domain.RecentSearchRepository
import com.techgv.stash.core.model.RecentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

internal class StashRecentSearchRepository(database: StashDatabase, private val clock: Clock) : RecentSearchRepository {
    private val searches = database.recentSearchDao()

    override fun observe(): Flow<List<RecentSearch>> = searches.observe().map { list -> list.map { it.toModel() } }

    override suspend fun add(query: String) = searches.add(query, clock.now())
}
