package com.techgv.stash.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformHttpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
