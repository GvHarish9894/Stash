package com.techgv.stash.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebPolicyTest {
    @Test
    fun httpsLoadsAsItIs() {
        assertEquals("https://example.com/a?b=c#d", httpsUrl("https://example.com/a?b=c#d"))
        assertEquals("HTTPS://example.com/", httpsUrl("HTTPS://example.com/"))
    }

    @Test
    fun httpIsUpgradedKeepingTheRest() {
        assertEquals("https://example.com/a?b=http://x", httpsUrl("http://example.com/a?b=http://x"))
        assertEquals("https://example.com/", httpsUrl("HTTP://example.com/"))
    }

    @Test
    fun otherSchemesAreNeverFollowed() {
        listOf(
            "mailto:someone@example.com", "tel:+15551234", "sms:123", "intent://scan#Intent;scheme=zxing;end",
            "javascript:alert(1)", "file:///etc/hosts", "content://contacts/1", "data:text/html,hi", "about:blank",
            "twitter://user?id=1", "market://details?id=x", "httpx://example.com", "example.com", "",
        ).forEach { assertNull(httpsUrl(it), it) }
    }

    @Test
    fun readerLinksGoToTheBrowserOnlyWhenTheyAreWebLinks() {
        assertEquals("http://example.com", browserLink("http://example.com"))
        assertEquals("https://example.com", browserLink("https://example.com"))
        assertNull(browserLink("mailto:a@example.com"))
        assertNull(browserLink("javascript:void(0)"))
    }

    @Test
    fun loadBarRunsFromEightPercentToFull() {
        assertEquals(0.08f, loadBarFraction(0f))
        assertEquals(1f, loadBarFraction(1f))
        assertEquals(0.54f, loadBarFraction(0.5f), 0.0001f)
        assertEquals(0.08f, loadBarFraction(-1f))
        assertEquals(1f, loadBarFraction(2f))
    }

    @Test
    fun cancelledNavigationsAreNotFailures() {
        assertTrue(isCancelledNavigation("NSURLErrorDomain", -999))
        assertTrue(isCancelledNavigation("WebKitErrorDomain", 102))
        assertFalse(isCancelledNavigation("NSURLErrorDomain", -1009)) // not connected to the internet
        assertFalse(isCancelledNavigation("NSURLErrorDomain", -1200)) // TLS failure, e.g. an http-only site upgraded
        assertFalse(isCancelledNavigation("WebKitErrorDomain", 101))
    }
}
