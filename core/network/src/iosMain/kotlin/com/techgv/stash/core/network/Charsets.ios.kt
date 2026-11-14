package com.techgv.stash.core.network

import com.fleeksoft.charset.Charsets
import com.fleeksoft.charset.decodeToString
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.CFStringConvertIANACharSetNameToEncoding
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingInvalidId
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSStringEncodingDetectionAllowLossyKey
import platform.Foundation.NSStringEncodingDetectionLossySubstitutionKey
import platform.Foundation.NSStringEncodingDetectionSuggestedEncodingsKey
import platform.Foundation.NSStringEncodingDetectionUseOnlySuggestedEncodingsKey
import platform.Foundation.create
import platform.Foundation.numberWithBool
import platform.Foundation.numberWithUnsignedLong
import platform.Foundation.stringEncodingForData

/**
 * The pure-Kotlin charset tables first (lenient: bad bytes become U+FFFD), then Foundation, which knows the legacy
 * CJK encodings (windows-31j, gb18030, Big5, …) those tables lack.
 */
internal actual fun decodeWithCharset(bytes: ByteArray, name: String): String? {
    val common = runCatching { if (Charsets.isSupported(name)) Charsets.forName(name) else null }.getOrNull()
    if (common != null) return bytes.decodeToString(common)
    return decodeWithFoundation(bytes, name)
}

/**
 * Foundation's plain conversion fails on a single byte outside the encoding, which would leave the whole page to the
 * UTF-8 fallback; the detection API restricted to this one encoding with lossy conversion allowed replaces only the
 * bad bytes with U+FFFD, as browsers do. Known limit: on tiny or heavily corrupted input Foundation's lossy conversion
 * can drop a valid character next to a bad byte (`93 FA FF 96 7B` gives `日\uFFFD`, losing 本).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun decodeWithFoundation(bytes: ByteArray, name: String): String? {
    val ianaName = CFStringCreateWithCString(null, name, kCFStringEncodingUTF8) ?: return null
    val cfEncoding = try {
        CFStringConvertIANACharSetNameToEncoding(ianaName)
    } finally {
        CFRelease(ianaName)
    }
    if (cfEncoding == kCFStringEncodingInvalidId) return null
    if (bytes.isEmpty()) return ""
    val encoding = CFStringConvertEncodingToNSStringEncoding(cfEncoding)
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    NSString.create(data = data, encoding = encoding)?.let { return it.toString() }
    return memScoped {
        val converted = alloc<ObjCObjectVar<String?>>()
        val options = mapOf<Any?, Any?>(
            NSStringEncodingDetectionSuggestedEncodingsKey to listOf(NSNumber.numberWithUnsignedLong(encoding)),
            NSStringEncodingDetectionUseOnlySuggestedEncodingsKey to NSNumber.numberWithBool(true),
            NSStringEncodingDetectionAllowLossyKey to NSNumber.numberWithBool(true),
            NSStringEncodingDetectionLossySubstitutionKey to "\uFFFD",
        )
        NSString.stringEncodingForData(data, options, converted.ptr, null)
        converted.value
    }
}

// Kotlin/Native aborts the process on out-of-memory; there is nothing to catch.
internal actual fun isOutOfMemory(error: Throwable): Boolean = false
