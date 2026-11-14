package com.techgv.stash.core.data

import com.techgv.stash.core.database.buildStashDatabase
import com.techgv.stash.core.database.stashDatabaseBuilder
import com.techgv.stash.core.preferences.createStashDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDataModule: Module = module {
    single { stashDatabaseBuilder(androidContext()).buildStashDatabase() }
    single { createStashDataStore(androidContext()) }
}
