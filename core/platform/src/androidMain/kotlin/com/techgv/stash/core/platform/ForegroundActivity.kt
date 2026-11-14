package com.techgv.stash.core.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

/**
 * The activity the user sees, for capabilities that need one (the permission prompt, starting the browser or the
 * share sheet in the app's task). Every activity registers through the application's lifecycle callbacks once
 * [install] ran. Held weakly and cleared when the activity pauses, so it never keeps an activity alive.
 */
object ForegroundActivity : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var resumed: WeakReference<ComponentActivity>? = null

    val current: ComponentActivity? get() = resumed?.get()

    /**
     * True from when the app opened another app's screen over its own (a Custom Tab, the share sheet, notification
     * settings, the permission prompt) until one of its activities resumes again: the app stopping meanwhile is not the
     * user leaving it.
     */
    @Volatile
    var openedOtherApp = false
        private set

    /** Call right after starting another app's activity. */
    fun startedOtherApp() {
        openedOtherApp = true
    }

    /** Call once from `Application.onCreate`; calling it again does not register twice. */
    fun install(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        openedOtherApp = false
        resumed = (activity as? ComponentActivity)?.let(::WeakReference)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
