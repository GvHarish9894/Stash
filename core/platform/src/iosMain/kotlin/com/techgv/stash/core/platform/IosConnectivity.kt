package com.techgv.stash.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.Volatile

/** One `NWPathMonitor`, started with the app; answers with the last status it reported (online until the first one). */
@OptIn(ExperimentalForeignApi::class)
class IosConnectivity : Connectivity {
    @Volatile
    private var online = true

    private val monitor = nw_path_monitor_create().also { monitor ->
        nw_path_monitor_set_update_handler(monitor) { path -> online = nw_path_get_status(path) == nw_path_status_satisfied }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.techgv.stash.connectivity", null))
        nw_path_monitor_start(monitor)
    }

    override fun isOnline(): Boolean = online
}
