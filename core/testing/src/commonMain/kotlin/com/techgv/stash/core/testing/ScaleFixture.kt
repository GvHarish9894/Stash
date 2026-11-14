package com.techgv.stash.core.testing

import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.SavedItem
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** The scale target of the architecture plan: 50,000 items per user. */
const val SCALE_ITEM_COUNT = 50_000

/** How many [scaleItems] titles contain "zebracorn": a rare search word with a known hit count. */
const val SCALE_ZEBRACORN_COUNT = 37

/** The 40 sources of [scaleItems]; the first ones are much bigger than the last (a few big sources, a long tail). */
val scaleSources: List<String> = listOf(
    "medium.com", "youtube.com", "github.com", "nytimes.com", "theverge.com", "arstechnica.com", "substack.com",
    "wikipedia.org", "theguardian.com", "bbc.co.uk", "wired.com", "economist.com", "newyorker.com", "atlantic.com",
    "nature.com", "smashingmagazine.com", "css-tricks.com", "dev.to", "hackernoon.com", "martinfowler.com",
    "android-developers.googleblog.com", "kotlinlang.org", "developer.apple.com", "swiftbysundell.com", "jakewharton.com",
    "quantamagazine.org", "aeon.co", "longreads.com", "eater.com", "seriouseats.com", "bonappetit.com", "lonelyplanet.com",
    "outsideonline.com", "runnersworld.com", "nautil.us", "spectrum.ieee.org", "lwn.net", "stratechery.com",
    "paulgraham.com", "danluu.com",
)

private val scaleWords = listOf(
    "swift", "kotlin", "compose", "android", "design", "paging", "room", "search", "guide", "notes", "weekly", "digest",
    "review", "deep", "dive", "tips", "news", "history", "science", "music", "travel", "food", "finance", "health",
    "climate", "space", "code", "data", "privacy", "security", "swimming", "sweden", "the", "of", "a", "how", "why",
    "what", "we", "learned", "building", "slow", "reading", "modern", "small", "teams", "future", "city", "night",
    "garden", "recipe", "bread", "running", "mountain", "ocean", "library", "letters", "essay", "interview", "year",
)

/**
 * [count] items for scale tests, the same on every run: added over the two years before [FIXTURE_NOW]; 30 % unread,
 * the rest read; 25 % archived and 5 % of those deleted; spread over [scaleSources] with a few big sources; titles of
 * 6 to 13 words (about 60 characters, [SCALE_ZEBRACORN_COUNT] of them with "zebracorn"); a description, an image for most;
 * 2 % pending and 1 % failed metadata; some videos and repositories; ids not in time order.
 */
fun scaleItems(count: Int = SCALE_ITEM_COUNT): List<SavedItem> {
    val random = Random(SCALE_ITEM_COUNT)
    val zebracornEvery = count / SCALE_ZEBRACORN_COUNT
    return List(count) { n ->
        val addedAt = FIXTURE_NOW - random.nextLong(0, 2 * 365 * 24 * 60).minutes
        val roll = random.nextInt(100)
        val site = scaleSources[(random.nextDouble().pow(3) * scaleSources.size).toInt()]
        val words = List(random.nextInt(6, 14)) { scaleWords[random.nextInt(scaleWords.size)] }
        val zebracorn = zebracornEvery > 0 && n % zebracornEvery == 0 && n / zebracornEvery < SCALE_ZEBRACORN_COUNT
        val title = (words + listOfNotNull("zebracorn".takeIf { zebracorn })).joinToString(" ").replaceFirstChar { it.uppercase() } + " #$n"
        // A multiplicative hash spreads ids over the key space, so id order is not time order.
        val id = ((n.toLong() * 2_654_435_761L) and 0xFFFFFFFFL).toString(16).padStart(8, '0')
        val metadataRoll = random.nextInt(100)
        savedItem(
            id = id,
            addedAt = addedAt,
            readAt = if (roll >= 30) addedAt + random.nextInt(1, 72).hours else null,
            archivedAt = if (roll >= 75) addedAt + random.nextInt(1, 90).days else null,
            deletedAt = if (roll >= 95) addedAt + random.nextInt(90, 120).days else null,
            siteName = site,
            url = "https://$site/${words.take(4).joinToString("-")}-$n",
            title = title,
            description = words.shuffled(random).joinToString(" ") + ".",
            imageUrl = if (random.nextInt(5) == 0) null else "https://img.$site/$id.jpg",
            contentType = when (site) {
                "youtube.com" -> ContentType.Video
                "github.com" -> ContentType.Repository
                else -> ContentType.Article
            },
            readingMinutes = if (random.nextInt(5) == 0) null else random.nextInt(1, 40),
            fetchStatus = when {
                metadataRoll < 2 -> FetchStatus.Pending
                metadataRoll < 3 -> FetchStatus.Failed
                else -> FetchStatus.Fetched
            },
        )
    }
}
