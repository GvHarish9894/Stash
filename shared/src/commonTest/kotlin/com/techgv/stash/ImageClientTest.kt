package com.techgv.stash

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/** Backlog U-22: Coil's client gives up on an image server that does not answer. */
class ImageClientTest {
    @Test
    fun aStalledImageRequestTimesOut() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                // Virtual time: the test does not wait for the timeout.
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    delay(60.seconds)
                    respondOk()
                }
            }
            imageClientDefaults()
        }
        assertFailsWith<HttpRequestTimeoutException> { client.get("https://img.example.com/a.jpg") }
        assertTrue(currentTime <= 20_000, "gave up after $currentTime ms")
    }
}
