package com.techgv.stash.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * The view models of the expanded window's reader pane, which sits outside the navigation host: the selected item's
 * reader gets its own store, cleared when another item is selected or the pane empties. This holder lives in the
 * screen's store, so the selection's store survives a recreation (rotation).
 */
internal class ReaderPaneStore : ViewModel() {
    private var itemId: String? = null

    /** The store of the selected item's reader; null while the pane is empty. */
    var store: ViewModelStore? = null
        private set

    /** The owner for [itemId]'s reader, clearing the previous selection's store first; null for an empty pane. */
    fun ownerFor(itemId: String?): ViewModelStoreOwner? {
        if (itemId != this.itemId) {
            store?.clear()
            store = itemId?.let { ViewModelStore() }
            this.itemId = itemId
        }
        val current = store ?: return null
        return object : ViewModelStoreOwner {
            override val viewModelStore = current
        }
    }

    override fun onCleared() {
        store?.clear()
    }
}
