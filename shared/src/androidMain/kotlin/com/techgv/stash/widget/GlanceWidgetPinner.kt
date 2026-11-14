package com.techgv.stash.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.techgv.stash.core.model.WidgetSize
import com.techgv.stash.core.platform.WidgetPinner

/**
 * Asks the launcher to pin a widget. A pin request cannot pass a size, so each size has its own provider whose default
 * size is that size ([StashSmallWidgetReceiver] 2×2, [StashWidgetReceiver] 4×2); both show [StashWidget], which lays
 * itself out for the size it is given.
 */
class GlanceWidgetPinner(private val context: Context) : WidgetPinner {
    // Null on devices without app widgets.
    private val widgets: AppWidgetManager? = AppWidgetManager.getInstance(context)

    override val canPin: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && widgets?.isRequestPinAppWidgetSupported == true

    override fun requestPin(size: WidgetSize): Boolean = try {
        val receiver = when (size) {
            WidgetSize.Small -> StashSmallWidgetReceiver::class.java
            WidgetSize.Medium -> StashWidgetReceiver::class.java
        }
        canPin && widgets!!.requestPinAppWidget(ComponentName(context, receiver), null, null)
    } catch (e: RuntimeException) {
        false
    }
}
