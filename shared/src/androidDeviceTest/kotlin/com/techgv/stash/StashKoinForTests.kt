package com.techgv.stash

import androidx.test.platform.app.InstrumentationRegistry
import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.testing.FakeBackgroundJobs
import com.techgv.stash.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Runs [block] with the real app graph started globally (workers and activities read the global Koin), on this test
 * APK's own database. Background jobs are a fake so a test never starts a real metadata fetch. Shuts down in order:
 * app scope, database, Koin.
 */
suspend fun withStashKoin(block: suspend (Koin) -> Unit) {
    val appJob = SupervisorJob()
    val koin = startKoin {
        androidContext(InstrumentationRegistry.getInstrumentation().targetContext)
        modules(
            appModule,
            module {
                single(APPLICATION_SCOPE) { CoroutineScope(appJob + Dispatchers.Default) }
                single<BackgroundJobs> { FakeBackgroundJobs() }
            },
        )
    }.koin
    try {
        block(koin)
    } finally {
        appJob.cancelAndJoin()
        koin.getOrNull<StashDatabase>()?.close()
        stopKoin()
    }
}
