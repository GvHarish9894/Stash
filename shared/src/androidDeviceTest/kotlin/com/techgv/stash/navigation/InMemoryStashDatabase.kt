package com.techgv.stash.navigation

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.buildStashDatabase

/** The journey tests' database (`src/composeUiTest`); iOS builds it without a Context. */
internal fun inMemoryStashDatabase(): StashDatabase =
    Room.inMemoryDatabaseBuilder<StashDatabase>(InstrumentationRegistry.getInstrumentation().targetContext).buildStashDatabase()
