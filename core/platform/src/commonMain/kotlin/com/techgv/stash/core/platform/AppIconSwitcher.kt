package com.techgv.stash.core.platform

import com.techgv.stash.core.model.AppIcon

/**
 * Changes the launcher icon: toggles `<activity-alias>` components on Android, `setAlternateIconName` on iOS.
 * Named `AppIconSwitcher` so it does not clash with the [AppIcon] setting it applies.
 */
interface AppIconSwitcher {
    /** Whether this device can change the icon; when false the UI hides or disables the app-icon row. */
    val isSupported: Boolean

    /**
     * Makes [icon] the launcher icon and returns whether the change was accepted (Android may apply it when the app
     * leaves the foreground). Setting the icon already shown returns true and does nothing (no iOS alert). Returns
     * false, never throws, when unsupported or the OS refused. iOS shows its own confirmation alert for a real
     * change. The caller stores the setting only on true. Safe to call from any thread.
     */
    suspend fun set(icon: AppIcon): Boolean
}
