package com.techgv.stash.core.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Makes item and operation ids. */
fun interface IdGenerator {
    fun next(): String
}

/** UUIDv7 text from the stdlib generator: time-ordered and monotonic within the process. */
@OptIn(ExperimentalUuidApi::class)
object UuidV7Generator : IdGenerator {
    override fun next(): String = Uuid.generateV7().toString()
}
