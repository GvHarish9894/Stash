package com.techgv.stash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.ReminderRepository
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.SavedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Measurement data, in the `benchmark` build type only (decision D31). Driven from the shell (the receiver requires
 * `android.permission.DUMP`, which the shell holds and apps cannot get):
 *
 *     adb shell am broadcast -a com.techgv.stash.SEED -p com.techgv.stash --ei count 50000
 *     adb shell am broadcast -a com.techgv.stash.WIPE -p com.techgv.stash
 *
 * `am broadcast` returns when the work is done. Seeding replaces earlier seeded items with `count` synthetic ones
 * ([seedItems]: 60 % unread, 25 % read, 15 % archived, 40 sources, two years of dates), inserted in chunks on Room's
 * writer connection; the FTS index follows through Room's triggers. Every seeded item's domain ends
 * in [SEED_HOST], a reserved `.invalid` name no real link has, and the wipe deletes exactly those rows. Each run re-plans
 * reminders once; the widget is republished on the app's next foreground (bring the app up once before measuring it).
 * Logs only counts and durations.
 */
class SeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.get()
                val database = koin.get<StashDatabase>()
                val started = SystemClock.elapsedRealtime()
                val message = when (intent.action) {
                    ACTION_SEED -> "seeded ${seed(database, intent.getIntExtra(EXTRA_COUNT, DEFAULT_COUNT))}"
                    ACTION_WIPE -> "wiped ${wipe(database)}"
                    else -> "ignored"
                }
                koin.get<ReminderRepository>().replan()
                val result = "$message in ${SystemClock.elapsedRealtime() - started} ms"
                Log.i(TAG, result)
                pending.resultData = result
            } catch (e: Exception) {
                Log.e(TAG, "failed: ${e::class.simpleName}")
                pending.resultData = "failed"
            } finally {
                pending.finish()
            }
        }
    }

    /** Replaces earlier seeded items with [count] new ones, so the seeded count is always exactly [count]. */
    private suspend fun seed(database: StashDatabase, count: Int): Int {
        wipe(database)
        // One prepared INSERT per chunk on Room's writer connection: going through the DAO's upsert (three statements
        // per item) took 154 s for 50,000 items on the tablet emulator, past the 60 s broadcast limit.
        seedItems(count, Clock.System.now()).chunked(CHUNK).forEach { chunk ->
            database.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    usePrepared(INSERT) { statement ->
                        chunk.forEach { item ->
                            statement.bindItem(item)
                            statement.step()
                            statement.reset()
                        }
                    }
                }
            }
        }
        database.invalidationTracker.refreshAsync()
        return count
    }

    private suspend fun wipe(database: StashDatabase): Int {
        val deleted = database.useWriterConnection { connection ->
            connection.usePrepared("DELETE FROM saved_item WHERE domain LIKE '%.$SEED_HOST'") { it.step() }
            connection.usePrepared("SELECT changes()") { it.step(); it.getInt(0) }
        }
        // Both writes run outside a DAO, so tell Room's observers (the paged lists, the counts) about them.
        database.invalidationTracker.refreshAsync()
        return deleted
    }

    private companion object {
        const val TAG = "StashSeed"
        const val ACTION_SEED = "com.techgv.stash.SEED"
        const val ACTION_WIPE = "com.techgv.stash.WIPE"
        const val EXTRA_COUNT = "count"
        const val DEFAULT_COUNT = 40
        const val CHUNK = 5_000

        // The saved_item columns (SavedItemEntity); Room stores instants as epoch milliseconds and enums by name.
        const val INSERT = "INSERT INTO saved_item (id, url, normalized_url, title, description, image_url, favicon_url, " +
            "site_name, domain, source_key, content_type, source_app, reading_minutes, added_at, read_at, archived_at, " +
            "deleted_at, fetch_status, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

        fun SQLiteStatement.bindItem(item: SavedItem) {
            fun text(index: Int, value: String?) = if (value == null) bindNull(index) else bindText(index, value)
            fun long(index: Int, value: Long?) = if (value == null) bindNull(index) else bindLong(index, value)
            text(1, item.id.value)
            text(2, item.url)
            text(3, item.normalizedUrl)
            text(4, item.title)
            text(5, item.description)
            text(6, item.imageUrl)
            text(7, item.faviconUrl)
            text(8, item.siteName)
            text(9, item.domain)
            text(10, item.sourceKey)
            text(11, item.contentType.name)
            text(12, item.sourceApp)
            long(13, item.readingMinutes?.toLong())
            long(14, item.addedAt.toEpochMilliseconds())
            long(15, item.readAt?.toEpochMilliseconds())
            long(16, item.archivedAt?.toEpochMilliseconds())
            long(17, item.deletedAt?.toEpochMilliseconds())
            text(18, item.fetchStatus.name)
            long(19, item.updatedAt.toEpochMilliseconds())
        }
    }
}

/** The reserved host every seeded item's domain ends in; [SeedReceiver]'s wipe deletes by it. */
internal const val SEED_HOST = "seed.stash.invalid"

private val SITE_WORDS = listOf(
    "Northwind", "Harbor", "Lantern", "Copper", "Meadow", "Atlas", "Signal", "Juniper", "Quarry", "Ember",
    "Granite", "Willow", "Beacon", "Orchard", "Cobalt", "Summit", "Riverbend", "Foundry", "Prairie", "Tidewater",
)
private val SITE_KINDS = listOf("Review", "Journal")
private val TITLE_WORDS = listOf(
    "design", "systems", "reading", "slow", "notes", "kotlin", "compose", "architecture", "gardens", "cities",
    "history", "memory", "attention", "craft", "typography", "maps", "rivers", "software", "habits", "light",
    "letters", "machines", "weather", "markets", "music", "language", "climate", "teams", "tools", "the", "of", "and",
)

/**
 * `count` synthetic items, the same for the same count: 40 sources, added over the two years before [now], about 60 %
 * unread, 25 % read, 15 % archived, titles of 4–14 words, metadata already fetched (nothing to download).
 */
internal fun seedItems(count: Int, now: Instant): List<SavedItem> {
    val random = Random(RANDOM_SEED)
    return List(count) { index ->
        val source = index % SOURCES
        val domain = "s$source.$SEED_HOST"
        val siteName = "${SITE_WORDS[source % SITE_WORDS.size]} ${SITE_KINDS[source / SITE_WORDS.size]}"
        val title = List(4 + random.nextInt(11)) { TITLE_WORDS[random.nextInt(TITLE_WORDS.size)] }
            .joinToString(" ").replaceFirstChar { it.uppercase() }
        val addedAt = now - random.nextLong(TWO_YEARS_MINUTES).minutes
        val doneAt = minOf(now, addedAt + random.nextLong(MONTH_MINUTES).minutes)
        val lifecycle = random.nextInt(100)
        val url = "https://$domain/item/$index"
        SavedItem(
            id = ItemId("seed-$index"),
            url = url,
            normalizedUrl = url,
            title = title,
            description = null,
            imageUrl = null,
            faviconUrl = null,
            siteName = siteName,
            domain = domain,
            sourceKey = domain,
            contentType = if (source % 8 == 0) ContentType.Video else ContentType.Article,
            sourceApp = null,
            readingMinutes = if (lifecycle % 7 == 0) null else 1 + random.nextInt(30),
            addedAt = addedAt,
            readAt = doneAt.takeIf { lifecycle >= 60 },
            archivedAt = doneAt.takeIf { lifecycle >= 85 },
            deletedAt = null,
            fetchStatus = FetchStatus.Fetched,
            updatedAt = if (lifecycle >= 60) doneAt else addedAt,
        )
    }
}

private const val RANDOM_SEED = 50_000
private const val SOURCES = 40
private val TWO_YEARS_MINUTES = (2 * 365).days.inWholeMinutes
private val MONTH_MINUTES = 30.days.inWholeMinutes
