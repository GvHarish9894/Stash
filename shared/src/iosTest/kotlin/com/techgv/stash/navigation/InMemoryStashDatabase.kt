package com.techgv.stash.navigation

import androidx.room.Room
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.buildStashDatabase

/** The journey tests' database (`src/composeUiTest`); Android builds it with a Context. */
internal fun inMemoryStashDatabase(): StashDatabase = Room.inMemoryDatabaseBuilder<StashDatabase>().buildStashDatabase()
