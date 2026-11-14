package com.techgv.stash.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import okio.Path.Companion.toPath

internal const val STASH_PREFERENCES_FILE = "stash.preferences_pb"

/**
 * One instance per file per process: DataStore fails if two instances own the same file. A file that cannot be read is
 * replaced with the defaults, so a corrupt settings file never stops the app from starting.
 */
fun createStashDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { path.toPath() },
    )
