package com.techgv.stash.core.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

class NetworkModuleVerifyTest {
    // The fetchers' dispatcher parameter has a default, so Koin verification must not require a binding for it.
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun everyDefinitionHasItsDependencies() {
        // HttpClient is built by createStashHttpClient(), not by Koin calling its constructor.
        networkModule.verify(extraTypes = listOf(HttpClientEngine::class, HttpClientConfig::class))
    }
}
