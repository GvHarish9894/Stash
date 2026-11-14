package com.techgv.stash.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createStashDataStore(): DataStore<Preferences> =
    createStashDataStore(applicationSupportPath() + "/$STASH_PREFERENCES_FILE")
