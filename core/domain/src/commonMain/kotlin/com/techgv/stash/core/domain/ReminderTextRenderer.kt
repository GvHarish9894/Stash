package com.techgv.stash.core.domain

import com.techgv.stash.core.model.PlannedNotification

/** Localized notification text. */
data class ReminderText(val title: String, val body: String)

/**
 * Turns a planned reminder into localized text. The shared module implements it with string resources; the data layer
 * calls it before handing the plan to the platform's notification scheduler.
 */
fun interface ReminderTextRenderer {
    suspend fun render(notification: PlannedNotification): ReminderText
}
