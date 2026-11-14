package com.techgv.stash.di

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import kotlin.test.Test

// Room needs a Context on Android, so these run on a device or emulator: ./gradlew :shared:connectedAndroidDeviceTest
private val withContext: KoinApplication.() -> Unit = {
    androidContext(InstrumentationRegistry.getInstrumentation().targetContext)
}

class StashGraphDeviceTest {
    @Test
    fun everyBindingResolvesWithFakeCapabilities() = runTest {
        assertStashGraphResolves(withContext, fakeCapabilitiesModule)
    }
}

class RealPlatformModuleDeviceTest {
    @Test
    fun everyBindingResolvesWithTheRealPlatformModule() = runTest {
        assertStashGraphResolves(withContext)
    }
}
