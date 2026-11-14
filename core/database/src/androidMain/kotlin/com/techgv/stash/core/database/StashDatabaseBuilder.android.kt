package com.techgv.stash.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun stashDatabaseBuilder(context: Context): RoomDatabase.Builder<StashDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<StashDatabase>(appContext, appContext.getDatabasePath(STASH_DATABASE_NAME).absolutePath)
}
