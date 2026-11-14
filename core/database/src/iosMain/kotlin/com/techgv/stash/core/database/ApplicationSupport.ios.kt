package com.techgv.stash.core.database

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Decision D14: app files live in Application Support (not user-visible in Files), created on first use. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun applicationSupportPath(): String = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = error.ptr,
    )
    checkNotNull(url?.path) { "Application Support is unavailable: ${error.value?.localizedDescription}" }
}
