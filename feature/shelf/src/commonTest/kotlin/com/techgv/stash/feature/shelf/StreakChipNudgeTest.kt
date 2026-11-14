package com.techgv.stash.feature.shelf

import com.techgv.stash.core.domain.StreakState
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.testing.FIXTURE_NOW
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Phone landscape (decision D29): the hidden nudge's colours move to the streak chip; its label keeps the real status. */
class StreakChipNudgeTest {
    private fun state(count: Int, status: StreakStatus, level: ReminderLevel?) = ShelfState(
        now = FIXTURE_NOW,
        zone = TimeZone.UTC,
        streak = StreakState(count, readToday = status == StreakStatus.Lit, status = status, week = emptyList()),
        nudge = level?.let { ShelfNudge(it, count, ItemId("a"), "Title a", 5) },
    )

    @Test
    fun anEveningLateOrLastCallNudgeColoursAnUnlitChipInLandscape() {
        for (level in listOf(ReminderLevel.Evening, ReminderLevel.Late, ReminderLevel.LastCall)) {
            assertEquals(level, streakChipNudge(state(4, StreakStatus.Unlit, level), phoneLandscape = true))
        }
    }

    @Test
    fun anAtRiskChipKeepsItsDangerLookInLandscape() {
        for (level in listOf(ReminderLevel.Late, ReminderLevel.LastCall)) {
            assertNull(streakChipNudge(state(4, StreakStatus.AtRisk, level), phoneLandscape = true))
        }
    }

    @Test
    fun portraitKeepsTheChipsOwnLook() {
        assertNull(streakChipNudge(state(4, StreakStatus.Unlit, ReminderLevel.LastCall), phoneLandscape = false))
    }

    @Test
    fun aDailyNudgeLeavesTheChipUnchanged() {
        assertNull(streakChipNudge(state(4, StreakStatus.Unlit, ReminderLevel.Daily), phoneLandscape = true))
    }

    @Test
    fun withNoStreakToLoseOrALitChipTheChipKeepsItsOwnLook() {
        assertNull(streakChipNudge(state(0, StreakStatus.Unlit, ReminderLevel.LastCall), phoneLandscape = true))
        assertNull(streakChipNudge(state(4, StreakStatus.Lit, ReminderLevel.LastCall), phoneLandscape = true))
        assertNull(streakChipNudge(state(4, StreakStatus.Unlit, level = null), phoneLandscape = true))
    }
}
