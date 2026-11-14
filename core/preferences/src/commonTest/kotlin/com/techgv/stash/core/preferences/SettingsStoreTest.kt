package com.techgv.stash.core.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.AppLanguage
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.model.WidgetSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreTest {
    private val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stash-settings-${Random.nextLong().toULong()}.preferences_pb").toString()

    @AfterTest
    fun deleteFile() = FileSystem.SYSTEM.delete(path.toPath())

    /** Every field away from its default. */
    private val changed = UserPreferences(
        theme = ThemeMode.Night,
        accent = Accent.InkBlue,
        cardLayout = CardLayout.LargeImages,
        textSize = TextSize.Large,
        reduceMotion = true,
        markReadOnOpen = false,
        readerModeDefault = true,
        openLinksIn = OpenLinksIn.Browser,
        dailyReminder = false,
        reminderTime = ReminderTime.Evening,
        streakSaver = false,
        swipeStartAction = SwipeAction.Archive,
        swipeEndAction = SwipeAction.Share,
        sort = ShelfSort.BySource,
        group = ShelfGrouping.Month,
        autoArchive = AutoArchivePeriod.Off,
        appIcon = AppIcon.Ink,
        language = AppLanguage.Tamil,
        widgetSize = WidgetSize.Small,
        gestureTipDismissed = true,
        readerTextStep = 3,
        notificationPermissionAsked = true,
    )

    @Test
    fun anEmptyStoreReadsTheDefaults() = runTest {
        assertEquals(UserPreferences(), SettingsStore(createStashDataStore(path)).preferences.first())
    }

    @Test
    fun everyFieldSurvivesTheFile() = runTest {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val first = SettingsStore(PreferenceDataStoreFactory.createWithPath(scope = scope) { path.toPath() })
        assertEquals(changed, first.update { changed })
        scope.coroutineContext[Job]!!.cancelAndJoin() // releases the file, so a new instance may own it

        assertEquals(changed, SettingsStore(createStashDataStore(path)).preferences.first())
    }

    @Test
    fun aCorruptFileIsReplacedWithTheDefaults() = runTest {
        FileSystem.SYSTEM.write(path.toPath()) { write(ByteArray(64) { 0xFF.toByte() }) }
        val store = SettingsStore(createStashDataStore(path))

        assertEquals(UserPreferences(), store.preferences.first())
        assertEquals(ThemeMode.Night, store.update { it.copy(theme = ThemeMode.Night) }.theme)
    }

    @Test
    fun concurrentUpdatesBothLand() = runTest {
        val store = SettingsStore(createStashDataStore(path))

        val jobs = List(20) { i ->
            launch(Dispatchers.Default) {
                store.update { if (i % 2 == 0) it.copy(theme = ThemeMode.Night) else it.copy(accent = Accent.Forest) }
            }
        } + launch(Dispatchers.Default) { store.update { it.copy(reduceMotion = true) } }
        jobs.forEach { it.join() }

        assertEquals(UserPreferences(theme = ThemeMode.Night, accent = Accent.Forest, reduceMotion = true), store.preferences.first())
    }

    @Test
    fun anUnknownStoredValueReadsAsTheDefaultAndSurvivesOtherChanges() = runTest {
        val dataStore = createStashDataStore(path)
        dataStore.edit {
            it[stringPreferencesKey("theme")] = "Sepia" // from a newer version, or a removed constant
            it[stringPreferencesKey("accent")] = Accent.Forest.name
            it[intPreferencesKey("reader_text_step")] = 9
        }
        val store = SettingsStore(dataStore)

        assertEquals(UserPreferences(accent = Accent.Forest), store.preferences.first())

        store.update { it.copy(reduceMotion = true) }
        assertEquals("Sepia", dataStore.data.first()[stringPreferencesKey("theme")])
        assertEquals(UserPreferences(accent = Accent.Forest, reduceMotion = true), store.preferences.first())
    }

    @Test
    fun anUnknownValueFallsBackToThatFieldsDefaultNotTheFirstConstant() = runTest {
        val dataStore = createStashDataStore(path)
        dataStore.edit {
            it[stringPreferencesKey("auto_archive")] = "Days14"
            it[stringPreferencesKey("swipe_start_action")] = "Snooze"
            it[intPreferencesKey("reader_text_step")] = 4
        }

        assertEquals(UserPreferences(), SettingsStore(dataStore).preferences.first())
    }

    @Test
    fun aValueOfTheWrongTypeReadsAsThatFieldsDefault() = runTest {
        val dataStore = createStashDataStore(path)
        dataStore.edit {
            it[intPreferencesKey("theme")] = 3
            it[stringPreferencesKey("reduce_motion")] = "yes"
            it[stringPreferencesKey("reader_text_step")] = "2"
            it[stringPreferencesKey("accent")] = Accent.Forest.name
        }
        val store = SettingsStore(dataStore)

        assertEquals(UserPreferences(accent = Accent.Forest), store.preferences.first())
        assertEquals(UserPreferences(theme = ThemeMode.Night, accent = Accent.Forest), store.update { it.copy(theme = ThemeMode.Night) })
    }

    @Test
    fun theReaderTextStepKeepsItsWholeRange() = runTest {
        val store = SettingsStore(createStashDataStore(path))
        for (step in 0..3) assertEquals(step, store.update { it.copy(readerTextStep = step) }.readerTextStep)
    }

    @Test
    fun concurrentUpdatesEachSeeThePreviousResult() = runTest {
        // On one thread the two updates interleave at every suspension: a transform applied to a snapshot read before
        // updateData would see the same value twice and lose one increment.
        val store = SettingsStore(PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) { path.toPath() })
        store.update { it.copy(readerTextStep = 0) }

        List(3) { launch { store.update { it.copy(readerTextStep = it.readerTextStep + 1) } } }.forEach { it.join() }

        assertEquals(3, store.preferences.first().readerTextStep)
    }

    @Test
    fun theFlowEmitsOnChange() = runTest {
        val store = SettingsStore(createStashDataStore(path))

        store.preferences.test {
            assertEquals(UserPreferences(), awaitItem())
            store.update { it.copy(sort = ShelfSort.Oldest) }
            assertEquals(UserPreferences(sort = ShelfSort.Oldest), awaitItem())
            store.update { it.copy(gestureTipDismissed = true) }
            assertEquals(UserPreferences(sort = ShelfSort.Oldest, gestureTipDismissed = true), awaitItem())
        }
    }
}
