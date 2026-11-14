package com.techgv.stash.core.testing

import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.SharedPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.minutes

class TestSupportTest {
    @Test
    fun clockAdvances() {
        val clock = TestClock()

        clock.advanceBy(90.minutes)

        assertEquals(FIXTURE_NOW + 90.minutes, clock.now())
    }

    @Test
    fun inboxHandsOverInOrderAndRemovesOnlyWhatWasSaved() = runTest {
        val inbox = FakeShareInbox()
        val first = SharedPayload("https://a.example", null, "Chrome", FIXTURE_NOW)
        val second = SharedPayload("https://b.example", null, null, FIXTURE_NOW + 1.minutes)
        val late = SharedPayload("https://c.example", null, null, FIXTURE_NOW + 2.minutes)
        inbox.share(first, second)
        val saved = mutableListOf<SharedPayload>()

        assertFails { inbox.drain { if (it == second) error("storage failed") else saved += it } }
        assertEquals(listOf(first), saved)
        assertEquals(listOf(second), inbox.waiting, "a failed save keeps its entry")

        inbox.dieAfterNextSave()
        assertFails { inbox.drain { saved += it } }
        assertEquals(listOf(second), inbox.waiting, "saved but not removed: delivered again")

        inbox.shareDuringNextSave(late)
        inbox.drain { saved += it }
        assertEquals(listOf(first, second, second, late), saved)
        assertEquals(emptyList(), inbox.waiting)
        assertEquals(3, inbox.drainCount)
    }

    @Test
    fun busRecordsCommandsAndReturnsScriptedResults() = runTest {
        val rejected = DispatchResult.Rejected(Rejection.NotFound)
        val bus = RecordingCommandBus { if (it is Command.Delete) rejected else DispatchResult.Done(affected = 1) }

        val results = listOf(bus.dispatch(Command.MarkRead(ItemId("a"))), bus.dispatch(Command.Delete(ItemId("b"))))

        assertEquals(listOf<DispatchResult>(DispatchResult.Done(affected = 1), rejected), results)
        assertEquals(listOf<Command>(Command.MarkRead(ItemId("a")), Command.Delete(ItemId("b"))), bus.dispatched)
    }

    @Test
    fun savedItemVariantsHaveTheirLifecycle() {
        ItemLifecycle.entries.forEach { lifecycle ->
            assertEquals(lifecycle, savedItem(lifecycle = lifecycle).lifecycle)
        }
    }

    @Test
    fun recentSearchesKeepNewestFirst() = runTest {
        val clock = TestClock()
        val recent = FakeRecentSearchRepository(clock)

        recent.add("compose")
        clock.advanceBy(1.minutes)
        recent.add("room")

        assertEquals(listOf("room", "compose"), recent.observe().value.map { it.query })
    }

    @Test
    fun iconSwitcherAppliesOnlyWhenSupportedAndAllowed() = runTest {
        val unsupported = FakeAppIconSwitcher(isSupported = false)
        val refused = FakeAppIconSwitcher(succeeds = false)
        val working = FakeAppIconSwitcher()

        assertEquals(listOf(false, false, true), listOf(unsupported, refused, working).map { it.set(AppIcon.Night) })
        assertEquals(listOf(AppIcon.Leather, AppIcon.Leather, AppIcon.Night), listOf(unsupported, refused, working).map { it.current })
    }
}
