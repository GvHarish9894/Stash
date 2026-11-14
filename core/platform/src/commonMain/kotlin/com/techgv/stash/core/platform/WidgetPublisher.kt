package com.techgv.stash.core.platform

import com.techgv.stash.core.model.WidgetSnapshot

interface WidgetPublisher {
    /** Replaces the snapshot the home-screen widgets show and asks them to refresh. */
    suspend fun publish(snapshot: WidgetSnapshot)
}
