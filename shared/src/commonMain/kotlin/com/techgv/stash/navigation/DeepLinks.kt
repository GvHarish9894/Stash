package com.techgv.stash.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hand-over point for `stash://reader/{id}` links (notification taps, widget rows).
 *
 * The platform shell calls [open] with the item id; the navigation host observes [pending], opens the reader with
 * Home underneath, and calls [consumed] with the id it handled. Process-wide because a link can arrive before the UI
 * exists. Android calls [open] from `onNewIntent`, and from `onCreate` only when `savedInstanceState == null`
 * (otherwise every rotation reopens the reader). The id may no longer exist (a widget row can outlive its item): the
 * reader handles that.
 */
object DeepLinks {
    private val state = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = state

    fun open(itemId: String) {
        state.value = itemId
    }

    /** Clears [itemId] if it is still the pending link; a newer link that arrived meanwhile stays. */
    fun consumed(itemId: String) {
        state.compareAndSet(itemId, null)
    }

    /** The item id in a `stash://reader/{id}` URL, or null for anything else. */
    fun itemIdOf(url: String): String? =
        url.removePrefix("stash://reader/").takeIf { it != url }
            ?.substringBefore('?')?.substringBefore('#')
            ?.takeIf { it.isNotEmpty() && '/' !in it }
}
