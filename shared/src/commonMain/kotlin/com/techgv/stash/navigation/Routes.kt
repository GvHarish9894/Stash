package com.techgv.stash.navigation

import kotlinx.serialization.Serializable

// The navigation host's destinations. Sheets, the streak sheet and the celebration are state, not routes.
// Enum arguments travel as their names: plain strings keep the saved back stack independent of nav-type support.

@Serializable
data object Shelf

@Serializable
data object Search

@Serializable
data object Archive

/**
 * [from] is a `ReaderOrigin` name; [markedRead] is whether the opener's `OpenItem` marked the item read; [readerMode] is
 * the reader pane's mode when a resize moves its item here, null for the setting's default.
 */
@Serializable
data class Reader(val id: String, val from: String, val markedRead: Boolean, val readerMode: Boolean? = null)

/** [section] is the `SettingsSection` name to scroll to (the streak sheet's "Reminders"), or null for the top. */
@Serializable
data class Settings(val section: String? = null)

@Serializable
data object WidgetPreview
