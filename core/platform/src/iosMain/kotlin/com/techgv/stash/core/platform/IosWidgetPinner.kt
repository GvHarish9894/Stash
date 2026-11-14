package com.techgv.stash.core.platform

import com.techgv.stash.core.model.WidgetSize

/** iOS has no API to pin a widget: the user adds it from the home screen, which the UI explains. */
class IosWidgetPinner : WidgetPinner {
    override val canPin: Boolean = false

    override fun requestPin(size: WidgetSize): Boolean = false
}
