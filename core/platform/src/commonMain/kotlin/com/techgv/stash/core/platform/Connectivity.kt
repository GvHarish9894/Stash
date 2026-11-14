package com.techgv.stash.core.platform

/**
 * Whether the device has a usable network connection right now (Android `ConnectivityManager`, iOS `NWPathMonitor`).
 * The data layer asks after a metadata fetch failed as offline, to tell a dead link from a device without network
 * (decision D20). Must be cheap and never throw.
 */
interface Connectivity {
    fun isOnline(): Boolean
}
