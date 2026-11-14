package com.techgv.stash

import com.techgv.stash.di.appModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/** Builds the app's dependency graph; each platform calls its `initKoin` once per process, before any UI. */
internal fun startStashKoin(platformConfig: KoinAppDeclaration) {
    startKoin {
        platformConfig()
        modules(appModule)
    }
}
