package com.techgv.stash.navigation

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/** Sends back for an Esc key that nothing consumed, and only while something would close; else Esc stays unhandled. */
private class EscapeInput : NavigationEventInput() {
    private var anyHandler = false

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        anyHandler = hasEnabledHandlers
    }

    fun escape(): Boolean = anyHandler.also { if (it) dispatchOnBackCompleted() }
}

// Android has no Esc-to-back of its own: an Esc no view consumed reaches the activity and is dropped.
@Composable
internal actual fun EscapeGoesBack() {
    val view = LocalView.current
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    DisposableEffect(view, dispatcher) {
        val input = EscapeInput()
        dispatcher.addInput(input)
        val listener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && input.escape()
        }
        ViewCompat.addOnUnhandledKeyEventListener(view, listener)
        onDispose {
            ViewCompat.removeOnUnhandledKeyEventListener(view, listener)
            dispatcher.removeInput(input)
        }
    }
}
