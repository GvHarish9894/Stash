package com.techgv.stash.core.network

import io.ktor.client.HttpClient
import org.koin.dsl.module
import org.koin.dsl.onClose

val networkModule = module {
    single<HttpClient> { createStashHttpClient() } onClose { it?.close() }
    // Not singleOf: the fetchers' dispatcher parameter keeps its default rather than being resolved from the graph.
    single { MetadataFetcher(get()) }
    single { ReaderFetcher(get()) }
}
