package com.techgv.stash.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun platformHttpEngineFactory(): HttpClientEngineFactory<*> = Darwin
