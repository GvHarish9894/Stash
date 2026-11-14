package com.techgv.stash

import android.app.Application

class StashApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startStash(this)
    }
}
