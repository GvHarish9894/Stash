package com.techgv.stash.core.data

import com.techgv.stash.core.database.MetadataAttemptEntity
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.toEntity
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.database.writeTransaction
import com.techgv.stash.core.domain.LinkCapture
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.SharedPayload
import com.techgv.stash.core.network.FetchFailure
import com.techgv.stash.core.network.FetchFailureKind
import com.techgv.stash.core.network.MetadataFetcher
import com.techgv.stash.core.network.MetadataResult
import com.techgv.stash.core.network.PageMetadata
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.Connectivity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** What a metadata fetch did; a background job retries with backoff on [RetryLater] only. */
enum class MetadataFetchOutcome {
    Fetched,

    /** Gave up: a failure retrying cannot fix, or the third failed attempt. */
    Failed,

    /** Still pending, or another fetch of it is running; try again later. */
    RetryLater,

    /** Nothing to do: the item is gone, deleted, already fetched or failed. */
    Skipped,
}

/**
 * Fetches a pending item's metadata and applies it. The network call runs outside any transaction; the result is applied
 * to the row as it is at that moment, so a lifecycle change made meanwhile (archive, delete) is kept, and a deleted item
 * is left alone. Failed attempts are counted in the database, so the third failure is the third across process deaths.
 * One fetch per item at a time: a second caller for an item already in flight gets [MetadataFetchOutcome.RetryLater],
 * so a job that lost the race to a catch-up still retries if that fetch fails.
 */
internal class MetadataUpdater(
    private val database: StashDatabase,
    private val fetcher: MetadataFetcher,
    private val clock: Clock,
    private val effects: SideEffects,
    private val connectivity: Connectivity,
) {
    private val items = database.savedItemDao()
    private val attempts = database.metadataAttemptDao()
    private val inFlight = HashSet<String>()
    private val inFlightLock = Mutex()

    suspend fun fetch(id: ItemId): MetadataFetchOutcome {
        if (!inFlightLock.withLock { inFlight.add(id.value) }) return MetadataFetchOutcome.RetryLater
        try {
            return fetchOnce(id)
        } finally {
            withContext(NonCancellable) { inFlightLock.withLock { inFlight.remove(id.value) } }
        }
    }

    private suspend fun fetchOnce(id: ItemId): MetadataFetchOutcome {
        val item = items.get(id.value)?.toModel()
        if (item == null || !item.isPending()) return MetadataFetchOutcome.Skipped
        val onlineBefore = connectivity.isOnline()
        val result = fetcher.fetch(item.url)
        // Once the result is in, applying it and the refresh after it run to the end even if the caller is cancelled.
        return withContext(NonCancellable) { applyResult(id, result, onlineBefore) }
    }

    private suspend fun applyResult(id: ItemId, result: MetadataResult, onlineBefore: Boolean): MetadataFetchOutcome {
        val outcome = database.writeTransaction {
            val current = items.get(id.value)?.toModel()
            if (current == null || !current.isPending()) return@writeTransaction MetadataFetchOutcome.Skipped
            val now = clock.now()
            when (result) {
                is MetadataResult.Success -> {
                    items.upsert(listOf(current.withMetadata(result.metadata, now).toEntity()))
                    attempts.reset(id.value)
                    MetadataFetchOutcome.Fetched
                }
                is FetchFailure -> {
                    // Decision D20: DNS and TLS failures are reported as offline too, so an offline failure counts
                    // only if the device said it had network both before the request and after it failed.
                    val counts = result.kind != FetchFailureKind.Offline || (onlineBefore && connectivity.isOnline())
                    val failed = attempts.failedAttempts(id.value) + if (counts) 1 else 0
                    if (!result.retryable || failed >= BackgroundJob.FetchMetadata.MAX_ATTEMPTS) {
                        items.upsert(listOf(current.copy(fetchStatus = FetchStatus.Failed, updatedAt = now).toEntity()))
                        attempts.reset(id.value)
                        MetadataFetchOutcome.Failed
                    } else {
                        attempts.upsert(MetadataAttemptEntity(id.value, failed, lastAttemptAt = now))
                        MetadataFetchOutcome.RetryLater
                    }
                }
            }
        }
        // New titles and minutes show in the widget and in reminder text.
        if (outcome == MetadataFetchOutcome.Fetched || outcome == MetadataFetchOutcome.Failed) effects.requestRefresh()
        return outcome
    }

    private fun SavedItem.isPending() = deletedAt == null && fetchStatus == FetchStatus.Pending

    /**
     * Applies what the page says. Site fields come from the final URL after redirects (decision D18); the saved URL stays.
     * A field the page leaves out keeps its value, so a missing title keeps the provisional title or the URL.
     */
    private fun SavedItem.withMetadata(metadata: PageMetadata, now: Instant): SavedItem {
        val site = LinkCapturePolicy.capture(SharedPayload(metadata.finalUrl, subject = null, sourceApp = null, at = now)) as? LinkCapture.Captured
        return copy(
            title = metadata.title ?: title,
            description = metadata.description ?: description,
            imageUrl = metadata.imageUrl ?: imageUrl,
            faviconUrl = metadata.faviconUrl,
            readingMinutes = metadata.readingMinutes ?: readingMinutes,
            domain = site?.domain ?: domain,
            siteName = site?.siteName ?: siteName,
            sourceKey = site?.sourceKey ?: sourceKey,
            contentType = metadata.contentType ?: site?.contentType ?: contentType,
            fetchStatus = FetchStatus.Fetched,
            updatedAt = now,
        )
    }
}
