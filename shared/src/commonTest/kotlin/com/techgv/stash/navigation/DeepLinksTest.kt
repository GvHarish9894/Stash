package com.techgv.stash.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinksTest {
    @Test
    fun readsTheItemIdFromAReaderLink() {
        assertEquals("0192-abc", DeepLinks.itemIdOf("stash://reader/0192-abc"))
        assertNull(DeepLinks.itemIdOf("stash://reader/"))
        assertNull(DeepLinks.itemIdOf("stash://settings"))
        assertNull(DeepLinks.itemIdOf("https://example.com/stash://reader/x"))
        assertNull(DeepLinks.itemIdOf("stash://reader/a/b"))
        assertEquals("id1", DeepLinks.itemIdOf("stash://reader/id1?from=widget#x"))
    }

    @Test
    fun aLinkWaitsUntilItIsConsumed() {
        DeepLinks.open("a")
        assertEquals("a", DeepLinks.pending.value)
        DeepLinks.consumed("a")
        assertNull(DeepLinks.pending.value)
    }

    @Test
    fun consumingAnOlderLinkKeepsANewerOne() {
        DeepLinks.open("a")
        DeepLinks.open("b")
        DeepLinks.consumed("a")
        assertEquals("b", DeepLinks.pending.value)
        DeepLinks.consumed("b")
        assertNull(DeepLinks.pending.value)
    }
}
