package com.techgv.stash.core.platform

import com.techgv.stash.core.model.WidgetSize

/** Pins a home-screen widget ("Add to home screen" on the widget preview). */
interface WidgetPinner {
    /**
     * Android: whether the launcher supports pinning (`isRequestPinAppWidgetSupported`). iOS: always false; the UI
     * then explains how to add the widget from the home screen.
     */
    val canPin: Boolean

    /** Asks the launcher to pin the [size] widget; returns whether the system dialog was shown. Main thread; never throws. */
    fun requestPin(size: WidgetSize): Boolean
}
