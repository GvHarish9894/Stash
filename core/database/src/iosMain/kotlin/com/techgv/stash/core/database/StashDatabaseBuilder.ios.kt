package com.techgv.stash.core.database

import androidx.room.Room
import androidx.room.RoomDatabase

fun stashDatabaseBuilder(): RoomDatabase.Builder<StashDatabase> =
    Room.databaseBuilder<StashDatabase>(name = applicationSupportPath() + "/$STASH_DATABASE_NAME")
