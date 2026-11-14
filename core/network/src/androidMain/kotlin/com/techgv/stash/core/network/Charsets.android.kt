package com.techgv.stash.core.network

import java.nio.charset.Charset

internal actual fun decodeWithCharset(bytes: ByteArray, name: String): String? {
    // WHATWG's x-mac-cyrillic is Java's x-MacUkraine (Ґ at 0xA2, ґ at 0xB6) except 0xFF, which WHATWG decodes as €.
    if (name == "x-mac-cyrillic") return decodeWithCharset(bytes, "x-MacUkraine")?.replace('\u00A4', '€')
    return runCatching { Charset.forName(name) }.getOrNull()?.let { String(bytes, it) }
}

internal actual fun isOutOfMemory(error: Throwable): Boolean = error is OutOfMemoryError
