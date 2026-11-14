package com.techgv.stash.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout

/** OkHttp on Android, Darwin on iOS. */
expect fun platformHttpEngineFactory(): HttpClientEngineFactory<*>

/** The client creates and owns its platform engine, so closing the client closes the engine. */
fun createStashHttpClient(): HttpClient = HttpClient(platformHttpEngineFactory()) { stashDefaults() }

/** For tests: the caller owns [engine]. */
fun createStashHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) { stashDefaults() }

private fun HttpClientConfig<*>.stashDefaults() {
    // The fetchers follow redirects themselves: they bound the count, upgrade http to https and check every hop.
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 15_000
    }
}
