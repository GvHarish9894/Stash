package com.techgv.stash.core.domain

import com.techgv.stash.core.domain.SwipeDirection.End
import com.techgv.stash.core.domain.SwipeDirection.Start
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class SwipeActionResolverTest {
    private val unread = item()
    private val read = item(readAt = T0)
    private val archived = item(readAt = T0 - 2.days, archivedAt = T0)
    private val deleted = item(deletedAt = T0)

    @Test
    fun shelfUsesTheConfiguredActionPerDirection() {
        val table = listOf(
            SwipeAction.MarkRead to SwipeOutcome.MarkRead,
            SwipeAction.Archive to SwipeOutcome.Archive,
            SwipeAction.Share to SwipeOutcome.Share,
            SwipeAction.Remove to SwipeOutcome.Remove,
            SwipeAction.Nothing to SwipeOutcome.RubberBand,
        )
        for ((action, outcome) in table) {
            val endOnly = UserPreferences(swipeEndAction = action, swipeStartAction = SwipeAction.Nothing)
            assertEquals(outcome, SwipeActionResolver.resolve(unread, End, endOnly), "end $action")
            assertEquals(SwipeOutcome.RubberBand, SwipeActionResolver.resolve(unread, Start, endOnly), "start Nothing")
            val startOnly = UserPreferences(swipeStartAction = action, swipeEndAction = SwipeAction.Nothing)
            assertEquals(outcome, SwipeActionResolver.resolve(unread, Start, startOnly), "start $action")
            assertEquals(SwipeOutcome.RubberBand, SwipeActionResolver.resolve(unread, End, startOnly), "end Nothing")
        }
    }

    @Test
    fun defaultsAreEndMarkReadAndStartRemove() {
        val defaults = UserPreferences()
        assertEquals(SwipeOutcome.MarkRead, SwipeActionResolver.resolve(unread, End, defaults))
        assertEquals(SwipeOutcome.Remove, SwipeActionResolver.resolve(unread, Start, defaults))
    }

    @Test
    fun markReadOnAReadItemTogglesToUnread() {
        for (direction in SwipeDirection.entries) {
            val prefs = UserPreferences(swipeEndAction = SwipeAction.MarkRead, swipeStartAction = SwipeAction.MarkRead)
            assertEquals(SwipeOutcome.MarkUnread, SwipeActionResolver.resolve(read, direction, prefs))
            assertEquals(SwipeOutcome.Archive, SwipeActionResolver.resolve(read, direction, UserPreferences(swipeEndAction = SwipeAction.Archive, swipeStartAction = SwipeAction.Archive)))
        }
    }

    @Test
    fun archiveIsFixedEndRestoreStartDeleteWhateverTheSettings() {
        for (action in SwipeAction.entries) {
            val prefs = UserPreferences(swipeEndAction = action, swipeStartAction = action)
            assertEquals(SwipeOutcome.Restore, SwipeActionResolver.resolve(archived, End, prefs), "$action")
            assertEquals(SwipeOutcome.Delete, SwipeActionResolver.resolve(archived, Start, prefs), "$action")
        }
    }

    @Test
    fun deletedItemsOnlyRubberBand() {
        for (direction in SwipeDirection.entries) {
            assertEquals(SwipeOutcome.RubberBand, SwipeActionResolver.resolve(deleted, direction, UserPreferences()))
        }
    }
}
