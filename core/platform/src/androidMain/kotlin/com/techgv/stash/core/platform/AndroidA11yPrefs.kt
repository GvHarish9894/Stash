package com.techgv.stash.core.platform

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** "Remove animations" is an animator duration scale of 0; a running touch-exploration service means a screen reader. */
class AndroidA11yPrefs(context: Context) : A11yPrefs {
    private val resolver = context.contentResolver
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private val motion = MutableStateFlow(animationsRemoved())

    // Process-long, like this single: the observer is never unregistered.
    init {
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    motion.value = animationsRemoved()
                }
            },
        )
    }

    override val reduceMotion: StateFlow<Boolean> = motion

    override fun recommendedTimeout(default: Duration): Duration = when {
        accessibility.isTouchExplorationEnabled -> Duration.INFINITE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            val flags = AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS
            maxOf(default, accessibility.getRecommendedTimeoutMillis(default.inWholeMilliseconds.toInt(), flags).milliseconds)
        }
        else -> default
    }

    private fun animationsRemoved() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
