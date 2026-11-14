package com.techgv.stash.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [UserPreferences] over DataStore, one key per field. Enums are stored by constant name; a missing or unknown value
 * (a removed constant, a newer version's value after a downgrade) or a value of the wrong type reads as that field's
 * default and leaves the other fields alone. [update] writes only the fields that changed, so such a value survives
 * until the user changes it. Trade-off: choosing the default for a field that holds an unknown value is no change and
 * writes nothing, so after an upgrade that understands the stored value, that older value comes back.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    override val preferences: Flow<UserPreferences> = dataStore.data.map { it.toUserPreferences() }

    /**
     * Applies [transform] atomically (decision D12): concurrent updates each see the result of the one before. Only the
     * command bus calls this; everything else reads through [SettingsRepository].
     */
    suspend fun update(transform: (UserPreferences) -> UserPreferences): UserPreferences =
        dataStore.updateData { stored ->
            val current = stored.toUserPreferences()
            stored.toMutablePreferences().apply { writeChanges(current, transform(current)) }
        }.toUserPreferences()
}

private object Keys {
    val theme = stringPreferencesKey("theme")
    val accent = stringPreferencesKey("accent")
    val cardLayout = stringPreferencesKey("card_layout")
    val textSize = stringPreferencesKey("text_size")
    val reduceMotion = booleanPreferencesKey("reduce_motion")
    val markReadOnOpen = booleanPreferencesKey("mark_read_on_open")
    val readerModeDefault = booleanPreferencesKey("reader_mode_default")
    val openLinksIn = stringPreferencesKey("open_links_in")
    val dailyReminder = booleanPreferencesKey("daily_reminder")
    val reminderTime = stringPreferencesKey("reminder_time")
    val streakSaver = booleanPreferencesKey("streak_saver")
    val swipeStartAction = stringPreferencesKey("swipe_start_action")
    val swipeEndAction = stringPreferencesKey("swipe_end_action")
    val sort = stringPreferencesKey("sort")
    val group = stringPreferencesKey("group")
    val autoArchive = stringPreferencesKey("auto_archive")
    val appIcon = stringPreferencesKey("app_icon")
    val language = stringPreferencesKey("language")
    val widgetSize = stringPreferencesKey("widget_size")
    val gestureTipDismissed = booleanPreferencesKey("gesture_tip_dismissed")
    val readerTextStep = intPreferencesKey("reader_text_step")
    val notificationPermissionAsked = booleanPreferencesKey("notification_permission_asked")
}

private val READER_TEXT_STEPS = 0..3

// `Preferences.get` casts unchecked, so a value of another type under the key would throw where it is used.
private inline fun <reified T> Preferences.valueOr(key: Preferences.Key<T>, default: T): T = asMap()[key] as? T ?: default

private inline fun <reified E : Enum<E>> Preferences.enumOr(key: Preferences.Key<String>, default: E): E =
    (asMap()[key] as? String)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

internal fun Preferences.toUserPreferences(): UserPreferences {
    val default = UserPreferences()
    return UserPreferences(
        theme = enumOr(Keys.theme, default.theme),
        accent = enumOr(Keys.accent, default.accent),
        cardLayout = enumOr(Keys.cardLayout, default.cardLayout),
        textSize = enumOr(Keys.textSize, default.textSize),
        reduceMotion = valueOr(Keys.reduceMotion, default.reduceMotion),
        markReadOnOpen = valueOr(Keys.markReadOnOpen, default.markReadOnOpen),
        readerModeDefault = valueOr(Keys.readerModeDefault, default.readerModeDefault),
        openLinksIn = enumOr(Keys.openLinksIn, default.openLinksIn),
        dailyReminder = valueOr(Keys.dailyReminder, default.dailyReminder),
        reminderTime = enumOr(Keys.reminderTime, default.reminderTime),
        streakSaver = valueOr(Keys.streakSaver, default.streakSaver),
        swipeStartAction = enumOr(Keys.swipeStartAction, default.swipeStartAction),
        swipeEndAction = enumOr(Keys.swipeEndAction, default.swipeEndAction),
        sort = enumOr(Keys.sort, default.sort),
        group = enumOr(Keys.group, default.group),
        autoArchive = enumOr(Keys.autoArchive, default.autoArchive),
        appIcon = enumOr(Keys.appIcon, default.appIcon),
        language = enumOr(Keys.language, default.language),
        widgetSize = enumOr(Keys.widgetSize, default.widgetSize),
        gestureTipDismissed = valueOr(Keys.gestureTipDismissed, default.gestureTipDismissed),
        readerTextStep = valueOr(Keys.readerTextStep, default.readerTextStep).takeIf { it in READER_TEXT_STEPS } ?: default.readerTextStep,
        notificationPermissionAsked = valueOr(Keys.notificationPermissionAsked, default.notificationPermissionAsked),
    )
}

private fun MutablePreferences.writeChanges(old: UserPreferences, new: UserPreferences) {
    fun <T> put(key: Preferences.Key<T>, value: T, previous: T) {
        if (value != previous) this[key] = value
    }
    put(Keys.theme, new.theme.name, old.theme.name)
    put(Keys.accent, new.accent.name, old.accent.name)
    put(Keys.cardLayout, new.cardLayout.name, old.cardLayout.name)
    put(Keys.textSize, new.textSize.name, old.textSize.name)
    put(Keys.reduceMotion, new.reduceMotion, old.reduceMotion)
    put(Keys.markReadOnOpen, new.markReadOnOpen, old.markReadOnOpen)
    put(Keys.readerModeDefault, new.readerModeDefault, old.readerModeDefault)
    put(Keys.openLinksIn, new.openLinksIn.name, old.openLinksIn.name)
    put(Keys.dailyReminder, new.dailyReminder, old.dailyReminder)
    put(Keys.reminderTime, new.reminderTime.name, old.reminderTime.name)
    put(Keys.streakSaver, new.streakSaver, old.streakSaver)
    put(Keys.swipeStartAction, new.swipeStartAction.name, old.swipeStartAction.name)
    put(Keys.swipeEndAction, new.swipeEndAction.name, old.swipeEndAction.name)
    put(Keys.sort, new.sort.name, old.sort.name)
    put(Keys.group, new.group.name, old.group.name)
    put(Keys.autoArchive, new.autoArchive.name, old.autoArchive.name)
    put(Keys.appIcon, new.appIcon.name, old.appIcon.name)
    put(Keys.language, new.language.name, old.language.name)
    put(Keys.widgetSize, new.widgetSize.name, old.widgetSize.name)
    put(Keys.gestureTipDismissed, new.gestureTipDismissed, old.gestureTipDismissed)
    put(Keys.readerTextStep, new.readerTextStep, old.readerTextStep)
    put(Keys.notificationPermissionAsked, new.notificationPermissionAsked, old.notificationPermissionAsked)
}
