package com.techgv.stash.core.database

import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * A few hundred varied items for the SQL-vs-domain parity tests (decision D4). Timestamps fall on whole days, so many
 * items tie on `added_at` / `archived_at` and the id tie-breaks matter.
 *
 * Excluded on purpose, as documented limits of `SearchMatcher` / SQLite: NFD text (no Unicode normalization), letters
 * whose lowercase changes length (`İ`), characters that Unicode 6.1 (SQLite's unicode61 tables) and the platform
 * classify differently, and source keys outside the BMP (SQLite orders text by UTF-8 bytes, Kotlin by UTF-16 units).
 */
object ParityFixture {
    val titles = listOf(
        "Swift UI tips and tricks",
        "SwiftUI layout guide",
        "Understanding Kotlin coroutines",
        "Room/FTS4 guide for Android",
        "The Oracle of order",
        "Near and far: field notes",
        "C++ templates in 2026",
        "Top 10 tips for 2026",
        "École du web: café crème",
        "ÉCOLE normale supérieure",
        "Καλημέρα κόσμε",
        "ΟΔΟΣ προς την Αθήνα",
        "Привет, мир!",
        "Новости дня",
        "हिंदी समाचार",
        "हम दोनों",
        "தமிழ் நாடு",
        "தமர் ழகரம்",
        "東京の天気",
        "北京烤鸭 recipe",
        "Weekly digest #42",
        "Don't panic: a guide",
    )

    /** Source key to site name. More than 12 sources, so the "More…" list is exercised. */
    private val sources = listOf(
        "medium.com" to "medium.com",
        "YouTube" to "youtube.com",
        "github.com" to "github.com",
        "example.com" to "example.com",
        "nytimes.com" to "nytimes.com",
        "dev.to" to "dev.to",
        "hindi.news" to "hindi.news",
        "tamil.blog" to "tamil.blog",
        "kathimerini.gr" to "kathimerini.gr",
        "habr.com" to "habr.com",
        "nhk.or.jp" to "nhk.or.jp",
        "lemonde.fr" to "lemonde.fr",
        "near.org" to "near.org",
        "or.example" to "or.example",
        "weekly.dev" to "weekly.dev",
        "bbc.co.uk" to "bbc.co.uk",
    )

    val items: List<SavedItem> = buildList {
        val random = Random(20261112)
        repeat(300) { n ->
            val (sourceKey, siteName) = sources[(random.nextDouble() * random.nextDouble() * sources.size).toInt()]
            val addedAt = FIXTURE_NOW - random.nextInt(0, 150).days
            val roll = random.nextInt(100)
            val readAt = if (roll in 40..99 && random.nextBoolean()) addedAt + random.nextInt(1, 48).hours else null
            val archivedAt = if (roll in 65..99) addedAt + random.nextInt(0, 20).days else null
            val deletedAt = if (roll >= 88) addedAt + random.nextInt(0, 30).days else null
            val suffix = if (random.nextInt(3) == 0) " part ${n % 7}" else ""
            add(
                savedItem(
                    // Ids are not in time order, so the id tie-break is visible.
                    id = "i" + ((n * 7919) % 1000).toString().padStart(3, '0'),
                    addedAt = addedAt,
                    readAt = readAt,
                    archivedAt = archivedAt,
                    deletedAt = deletedAt,
                    sourceKey = sourceKey,
                    siteName = siteName,
                    title = titles[random.nextInt(titles.size)] + suffix,
                    readingMinutes = if (random.nextInt(4) == 0) null else random.nextInt(1, 15),
                ),
            )
        }
        // Two sources tied on count and on newest item, so only the key orders them; and a single-item source.
        for (key in listOf("tie-b.example", "tie-a.example")) {
            for (d in 1..3) add(savedItem(id = "$key-$d", addedAt = FIXTURE_NOW - d.days, siteName = key))
        }
        add(savedItem(id = "solo", addedAt = FIXTURE_NOW - 2.days, siteName = "solo.example"))
    }

    val sourceKeys: List<String> = items.map { it.sourceKey }.distinct() + "missing.example"

    /** Prefix, multi-token, token order, infix (no match), punctuation, scripts, FTS operator words and empty. */
    val queries = listOf(
        "swi", "swift ui", "ui swift", "ift", "SwiftUI", "medium.com", "MEDIUM", "youtube", "kot co", "room fts4",
        "c++", "2026", "10 tips", "part 3", "don't", "OR", "or", "NEAR", "near", "AND", "NOT", "éco", "ÉCOLE", "ecole",
        "café", "καλη", "οδος", "ΟΔΟΣ", "прив", "новости", "हिं", "हिंदी", "ह", "தமி", "தமிழ்", "東京", "天気", "北京",
        "zzz", "", "  ...  ",
    )
}
