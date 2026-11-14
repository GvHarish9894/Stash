package com.techgv.stash.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class IdGeneratorTest {
    @Test
    fun uuidV7IdsAreVersion7AndSortByCreation() {
        val ids = List(1_000) { UuidV7Generator.next() }
        ids.forEach {
            assertEquals(36, it.length)
            assertEquals('7', it[14], it)
        }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.size, ids.toSet().size)
    }
}
