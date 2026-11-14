package com.techgv.stash.di

import com.techgv.stash.platform.tempDirectory
import kotlinx.coroutines.test.runTest
import org.koin.dsl.module
import kotlin.test.Test

class StashGraphIosTest {
    @Test
    fun everyBindingResolvesWithFakeCapabilities() = runTest {
        assertStashGraphResolves(extra = arrayOf(fakeCapabilitiesModule))
    }
}

class RealPlatformModuleTest {
    /**
     * The thirteen real iOS capabilities. The test runner has no App Group entitlement and no Swift app, so it binds the two
     * seams the module leaves for exactly that: [APP_GROUP_DIRECTORY] and [WIDGET_RELOAD].
     */
    @Test
    fun everyBindingResolvesWithTheRealPlatformModule() = runTest {
        assertStashGraphResolves(
            extra = arrayOf(module {
                single(APP_GROUP_DIRECTORY) { tempDirectory() }
                single(WIDGET_RELOAD) { {} }
            }),
        )
    }
}
