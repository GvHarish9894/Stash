package com.techgv.stash.feature.widgetpreview

import com.techgv.stash.core.model.WidgetSize

sealed interface WidgetPreviewEffect {
    data object Back : WidgetPreviewEffect

    /** Answered with [WidgetPreviewIntent.PinResult]. */
    data class PinWidget(val size: WidgetSize) : WidgetPreviewEffect
}
