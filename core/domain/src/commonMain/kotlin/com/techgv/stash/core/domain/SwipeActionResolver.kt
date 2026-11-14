package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.UserPreferences

/** The edge the finger moves toward: [End] is a right swipe in LTR, a left swipe in RTL. */
enum class SwipeDirection { Start, End }

enum class SwipeOutcome { MarkRead, MarkUnread, Archive, Share, Remove, Restore, Delete, RubberBand }

object SwipeActionResolver {
    /**
     * Shelf cards use the user's start/end settings, and "mark read" on a read card marks it unread.
     * Archive cards are fixed (end = restore, start = delete) so a setting can never turn a restore into a delete.
     */
    fun resolve(item: SavedItem, direction: SwipeDirection, preferences: UserPreferences): SwipeOutcome =
        when (item.lifecycle) {
            ItemLifecycle.Archived -> if (direction == SwipeDirection.End) SwipeOutcome.Restore else SwipeOutcome.Delete
            ItemLifecycle.Deleted -> SwipeOutcome.RubberBand
            ItemLifecycle.Unread, ItemLifecycle.Read -> {
                val action = if (direction == SwipeDirection.End) preferences.swipeEndAction else preferences.swipeStartAction
                when (action) {
                    SwipeAction.MarkRead ->
                        if (item.lifecycle == ItemLifecycle.Read) SwipeOutcome.MarkUnread else SwipeOutcome.MarkRead
                    SwipeAction.Archive -> SwipeOutcome.Archive
                    SwipeAction.Share -> SwipeOutcome.Share
                    SwipeAction.Remove -> SwipeOutcome.Remove
                    SwipeAction.Nothing -> SwipeOutcome.RubberBand
                }
            }
        }
}
