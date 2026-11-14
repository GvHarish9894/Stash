package com.techgv.stash.feature.widgetpreview

import com.techgv.stash.core.model.WidgetSize

sealed interface WidgetPreviewIntent {
    data class SetSize(val size: WidgetSize) : WidgetPreviewIntent

    /** "Add to home screen"; [canPin] is whether the launcher can pin a widget (`WidgetPinner.canPin`). */
    data class AddToHomeScreen(val canPin: Boolean) : WidgetPreviewIntent

    /** Shared's answer to [WidgetPreviewEffect.PinWidget]: whether the system dialog was shown. */
    data class PinResult(val dialogShown: Boolean) : WidgetPreviewIntent
    data object Back : WidgetPreviewIntent
}
