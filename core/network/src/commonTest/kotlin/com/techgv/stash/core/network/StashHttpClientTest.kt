package com.techgv.stash.core.network

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StashHttpClientTest {
    @Test
    fun fetchesHtmlAndParsesTitle() = runTest {
        val engine = MockEngine {
            respond(
                content = "<html><head><title>A quiet shelf</title></head><body></body></html>",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }
        val html = createStashHttpClient(engine).use { it.get("https://example.com/post").bodyAsText() }

        assertEquals("A quiet shelf", Ksoup.parse(html).title())
    }

    @Test
    fun platformClientOwnsAndClosesItsEngine() {
        val client = createStashHttpClient()

        client.close()

        assertFalse(client.engine.isActive)
    }
}
