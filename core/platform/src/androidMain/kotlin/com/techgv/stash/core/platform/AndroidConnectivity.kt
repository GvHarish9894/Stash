package com.techgv.stash.core.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Online means the active network has internet that Android has validated (not a captive portal or a dead Wi-Fi). */
class AndroidConnectivity(context: Context) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean = try {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: RuntimeException) {
        // Offline is the safe answer: a failed fetch then does not count toward the item's attempts (D20).
        false
    }
}
