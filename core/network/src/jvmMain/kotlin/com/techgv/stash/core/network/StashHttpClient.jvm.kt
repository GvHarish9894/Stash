package com.techgv.stash.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

// Mirrors androidMain: the plain JVM target exists so modules above can run their tests on a JVM host.
actual fun platformHttpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
