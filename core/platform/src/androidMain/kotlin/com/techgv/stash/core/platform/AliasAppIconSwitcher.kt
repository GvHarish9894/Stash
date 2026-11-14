package com.techgv.stash.core.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.techgv.stash.core.model.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One launcher `<activity-alias>` per [AppIcon], declared in the app manifest as `com.techgv.stash.Launcher<Name>`
 * (Leather enabled by default). Disabling the alias a task was launched through finishes that task, so [set] only
 * records the choice and [applyPending] swaps the aliases once the user left the app (and at a background process
 * start, if a swap was still pending): not while the app itself shows another app's screen in its task, such as a
 * Custom Tab, which the swap would close ([ForegroundActivity.openedOtherApp]). The swap enables the chosen alias before disabling the others, so the
 * app never has no launcher entry, and keeps the process running (`DONT_KILL_APP`).
 */
class AliasAppIconSwitcher(private val context: Context) : AppIconSwitcher {
    private val store = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override val isSupported: Boolean = true

    override suspend fun set(icon: AppIcon): Boolean = withContext(Dispatchers.IO) {
        store.edit().putString(KEY_PENDING, icon.name).commit()
    }

    /** Swaps the launcher aliases to the icon [set] recorded, if any. Call only while no activity of the app is visible. */
    fun applyPending() {
        val name = store.getString(KEY_PENDING, null) ?: return
        val icon = AppIcon.entries.firstOrNull { it.name == name }
        try {
            val packages = context.packageManager
            // An alias already in the requested state is left alone by the package manager: no change, no broadcast.
            if (icon != null) {
                packages.setComponentEnabledSetting(alias(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                AppIcon.entries.filter { it != icon }.forEach {
                    packages.setComponentEnabledSetting(alias(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
            store.edit().remove(KEY_PENDING).commit()
        } catch (e: RuntimeException) {
            // Never throws: the OS refused; the choice stays pending and the next background tries again.
        }
    }

    /** The launcher alias for [icon]. */
    fun alias(icon: AppIcon) = ComponentName(context.packageName, "com.techgv.stash.Launcher${icon.name}")

    private companion object {
        const val PREFERENCES = "stash_app_icon"
        const val KEY_PENDING = "pending_icon"
    }
}
