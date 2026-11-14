package com.techgv.stash.feature.settings

import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.ItemId

/** One-off actions `shared` performs; [SettingsRoute] maps each to a callback. */
sealed interface SettingsEffect {
    data object Back : SettingsEffect
    data object OpenArchive : SettingsEffect
    data object OpenWidgetPreview : SettingsEffect

    /** Opens [url] outside the app: the GitHub link, or an item when "Open links in" is Browser. */
    data class OpenExternally(val url: String) : SettingsEffect

    /** Answered with [SettingsIntent.NotificationPermissionResult]. */
    data object RequestNotificationPermission : SettingsEffect
    data object OpenNotificationSettings : SettingsEffect

    /** Answered with [SettingsIntent.AppIconResult]; the setting is stored only when the icon changed. */
    data class SetAppIcon(val icon: AppIcon) : SettingsEffect

    /** [tag] is a BCP 47 tag, or null to follow the phone. */
    data class SetLanguage(val tag: String?) : SettingsEffect
    data object OpenLanguageSettings : SettingsEffect

    /** "Read now" on the reminder preview, after `OpenItem` was dispatched. */
    data class OpenReader(val id: ItemId, val markedRead: Boolean) : SettingsEffect

    /** That read was the first of the day. */
    data class Celebrate(val celebration: Celebration) : SettingsEffect
}
