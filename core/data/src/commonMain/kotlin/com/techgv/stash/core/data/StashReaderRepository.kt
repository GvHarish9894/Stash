package com.techgv.stash.core.data

import com.techgv.stash.core.database.ArticleContentEntity
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.database.writeTransaction
import com.techgv.stash.core.domain.ReaderRepository
import com.techgv.stash.core.domain.ReaderResult
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.network.FetchFailure
import com.techgv.stash.core.network.FetchFailureKind
import com.techgv.stash.core.network.ReaderBlocks
import com.techgv.stash.core.network.ReaderExtractor
import com.techgv.stash.core.network.ReaderFetchResult
import com.techgv.stash.core.network.ReaderFetcher
import kotlin.time.Clock

/** Architecture plan: reader text is cached on demand and evicted least recently opened first beyond 200 MB. */
internal const val ARTICLE_CACHE_MAX_BYTES = 200L * 1024 * 1024

/**
 * Reader mode: the cached article when it was extracted by the current extractor, otherwise a fresh extraction. A cache
 * from an older extractor is still served when the page cannot be fetched again, so cached articles work offline.
 */
internal class StashReaderRepository(
    private val database: StashDatabase,
    private val fetcher: ReaderFetcher,
    private val clock: Clock,
    private val maxCacheBytes: Long = ARTICLE_CACHE_MAX_BYTES,
) : ReaderRepository {
    private val articles = database.articleContentDao()

    override suspend fun article(id: ItemId): ReaderResult {
        val item = database.savedItemDao().get(id.value)?.toModel() ?: return ReaderResult.Unavailable
        val cached = articles.get(id.value)
        if (cached != null && cached.extractorVersion >= ReaderExtractor.EXTRACTOR_VERSION) return opened(item, cached)
        return when (val result = fetcher.fetch(item.url)) {
            is ReaderFetchResult.Extracted -> {
                val now = clock.now()
                val article = result.article
                val entity = ArticleContentEntity(
                    itemId = id.value,
                    extractedHtml = article.extractedHtml,
                    standfirst = article.standfirst,
                    textLength = article.textLength,
                    extractedAt = now,
                    extractorVersion = ReaderExtractor.EXTRACTOR_VERSION,
                    htmlBytes = article.extractedHtml.encodeToByteArray().size.toLong(),
                    lastOpenedAt = now,
                )
                // A failed cache write still shows the article; it is fetched again next time.
                isolated {
                    database.writeTransaction {
                        articles.upsert(entity)
                        articles.evictBeyond(maxCacheBytes)
                    }
                }
                ready(item, entity)
            }
            ReaderFetchResult.NotExtractable -> cached?.let { opened(item, it) } ?: ReaderResult.Unavailable
            is FetchFailure -> when {
                cached != null -> opened(item, cached)
                result.kind == FetchFailureKind.Offline || result.kind == FetchFailureKind.Timeout -> ReaderResult.Offline
                else -> ReaderResult.Unavailable
            }
        }
    }

    private suspend fun opened(item: SavedItem, cached: ArticleContentEntity): ReaderResult {
        isolated { articles.touch(cached.itemId, clock.now()) }
        return ready(item, cached)
    }

    private suspend fun ready(item: SavedItem, content: ArticleContentEntity): ReaderResult {
        val blocks = ReaderBlocks.parse(content.extractedHtml)
        if (blocks.isEmpty()) return ReaderResult.Unavailable
        return ReaderResult.Ready(ReaderArticle(item.title, item.siteName, content.standfirst, item.readingMinutes, blocks))
    }
}
