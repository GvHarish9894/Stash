package com.techgv.stash.core.network

import io.ktor.client.HttpClient
import kotlinx.coroutines.isActive
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertFalse

class NetworkModuleTest {
    @Test
    fun providesFetchersOverOneClientAndClosesIt() {
        val koin = koinApplication { modules(networkModule) }

        koin.koin.get<MetadataFetcher>()
        koin.koin.get<ReaderFetcher>()
        val client = koin.koin.get<HttpClient>()
        koin.close()

        assertFalse(client.engine.isActive)
    }
}
