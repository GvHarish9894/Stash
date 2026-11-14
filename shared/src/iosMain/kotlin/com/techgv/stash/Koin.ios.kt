package com.techgv.stash

import com.techgv.stash.di.WIDGET_RELOAD
import org.koin.dsl.module

/**
 * Starts the graph once per process, before any UI, and registers the background refresh task (iOS requires that
 * before launch finishes). [reloadWidgets] is `WidgetCenter.shared.reloadAllTimelines()` from the Swift app.
 */
fun initKoin(reloadWidgets: () -> Unit) {
    startStashKoin { modules(module { single(WIDGET_RELOAD) { reloadWidgets } }) }
    registerRefreshTask()
}
