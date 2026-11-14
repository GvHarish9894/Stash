package com.techgv.stash

import com.techgv.stash.core.domain.ReminderPlanner
import com.techgv.stash.core.domain.ReminderText
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.PlannedNotification
import com.techgv.stash.core.model.ReminderCopy
import com.techgv.stash.core.model.ReminderLevel
import kotlin.test.assertEquals
import kotlin.time.Instant

private suspend fun render(level: ReminderLevel, streak: Int = 12, title: String = "Room FTS", minutes: Int? = 6) =
    ResourceReminderTextRenderer.render(
        PlannedNotification("n", Instant.parse("2026-11-12T13:30:00Z"), level, ItemId("i"), ReminderCopy(streak, 5, title, minutes)),
    )

/**
 * The English reminder copy for every level. Compose resources read the system language, which an Android host test
 * cannot provide, so the iOS simulator and Android device tests call this with the system language set to English.
 */
suspend fun assertEnglishReminderTexts() {
    assertEquals(
        listOf(
            ReminderText("Your shelf is waiting", "5 unread. “Room FTS” takes 6 min and keeps your 12-day streak going."),
            ReminderText("Keep your 12-day streak going", "You have not read today. One 6-minute read is all it takes."),
            ReminderText("Your 12-day streak ends in 2½ hours", "“Room FTS” is 6 min. Done before bed."),
            ReminderText("Last call: 12 days on the line", "Your streak ends at midnight. Tap to read “Room FTS” (6 min)."),
        ),
        ReminderLevel.entries.map { render(it) },
    )
    assertEquals(
        listOf(
            "5 unread. “Room FTS” keeps your 12-day streak going.",
            "You have not read today. One short read is all it takes.",
            "Read “Room FTS” before bed.",
            "Your streak ends at midnight. Tap to read “Room FTS”.",
        ),
        ReminderLevel.entries.map { render(it, minutes = null).body },
        "without minutes",
    )
    assertEquals("Last call: 1 day on the line", render(ReminderLevel.LastCall, streak = 1).title)
    // Only the daily reminder is planned with no streak yet; it must not say "0-day".
    assertEquals(
        listOf(
            "5 unread. “Room FTS” takes 6 min.",
            "5 unread. “Room FTS” is waiting.",
            "5 unread. “Room FTS” takes 6 min and keeps your 1-day streak going.",
            "5 unread. “Room FTS” keeps your 1-day streak going.",
        ),
        listOf(0, 1).flatMap { streak -> listOf(6, null).map { render(ReminderLevel.Daily, streak = streak, minutes = it).body } },
        "daily at streak 0 and 1",
    )
    val capped = ReminderPlanner.capTitle("A very long article title that keeps going well past the cap")
    assertEquals("“$capped” is 6 min. Done before bed.", render(ReminderLevel.Late, title = capped).body, "capped title")
}
