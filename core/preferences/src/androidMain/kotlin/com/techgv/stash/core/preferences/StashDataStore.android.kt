package com.techgv.stash.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createStashDataStore(context: Context): DataStore<Preferences> =
    createStashDataStore(context.filesDir.resolve(STASH_PREFERENCES_FILE).absolutePath)
