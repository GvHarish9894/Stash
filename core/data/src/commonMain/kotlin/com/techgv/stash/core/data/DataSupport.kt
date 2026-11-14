package com.techgv.stash.core.data

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.StreakDayDao
import com.techgv.stash.core.database.StreakDayEntity
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.domain.StreakEngine
import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.model.StreakDay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** Runs [block] in one read transaction, so the queries in it see one consistent state. */
internal suspend fun <R> StashDatabase.readTransaction(block: suspend () -> R): R =
    useReaderConnection { connection -> connection.deferredTransaction { block() } }

/**
 * Runs a side-effect step; its failure must not stop the caller or the steps after it. The next run retries it.
 * A [CancellationException] is rethrown only when the caller itself is cancelled: one thrown by a step (a platform
 * timeout, say) is that step's failure.
 */
internal suspend inline fun isolated(block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
    } catch (e: Exception) {
        // Swallowed on purpose: every step is idempotent and runs again on the next command or foreground.
    }
}

// One row per day, so every row since any plausible first use stays small; StreakEngine needs the whole chain.
private val FIRST_DAY = LocalDate(1970, 1, 1)
private val LAST_DAY = LocalDate(9999, 12, 31)

internal suspend fun StreakDayDao.all(): List<StreakDay> = between(FIRST_DAY, LAST_DAY).map { it.toModel() }

/** `unreadZeroSince` as the engine may use it: a stored value is never later than now (decision D13). */
internal fun Instant?.cappedAt(now: Instant): Instant? = this?.let { minOf(it, now) }

internal class StreakNow(val state: StreakState, val unreadCount: Int)

/** The streak and unread count now; call inside a transaction so both come from one state. */
internal suspend fun StashDatabase.streakNow(now: Instant, zone: TimeZone): StreakNow {
    val unread = savedItemDao().unreadCount()
    val since = appStateDao().unreadZeroSince().cappedAt(now)
    return StreakNow(StreakEngine.state(streakDayDao().all(), since, unread, now, zone), unread)
}

/**
 * Keeps `unreadZeroSince` (decision D13) after a write that may have changed the unread count from [unreadBefore]:
 * more → 0 sets it to now; 0 → more first stores the rest days it implies, then clears it. Call inside the write
 * transaction. On a fresh install it is null: no streak can exist before the first save, so no rest day is lost.
 */
internal suspend fun StashDatabase.trackUnreadZero(unreadBefore: Int, now: Instant, zone: TimeZone) {
    val unreadAfter = savedItemDao().unreadCount()
    when {
        unreadBefore > 0 && unreadAfter == 0 -> appStateDao().setUnreadZeroSince(now)
        unreadBefore == 0 && unreadAfter > 0 -> {
            val since = appStateDao().unreadZeroSince().cappedAt(now) ?: return
            val today = StreakEngine.dayOf(now, zone)
            val days = streakDayDao().between(StreakEngine.dayOf(since, zone).minus(1, DateTimeUnit.DAY), today).map { it.toModel() }
            streakDayDao().upsert(StreakEngine.restDays(days, since, today, zone).map { StreakDayEntity(it, firstReadAt = null) })
            appStateDao().setUnreadZeroSince(null)
        }
    }
}
