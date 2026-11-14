package com.techgv.stash.core.testing

import androidx.paging.testing.asSnapshot
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandDecider
import com.techgv.stash.core.domain.Decision
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.domain.ManualLink
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.ShelfQuery
import com.techgv.stash.core.domain.ShelfQuerySpec
import com.techgv.stash.core.domain.ShelfRow
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class DataFakesTest {
    private val fixture = listOf(
        savedItem("a", addedAt = FIXTURE_NOW - 1.hours, siteName = "medium.com"),
        savedItem("b", addedAt = FIXTURE_NOW - 1.days, siteName = "medium.com", lifecycle = ItemLifecycle.Read),
        savedItem("c", addedAt = FIXTURE_NOW - 3.days, siteName = "youtube.com", readingMinutes = 2),
        savedItem("d", addedAt = FIXTURE_NOW - 40.days, lifecycle = ItemLifecycle.Archived),
        savedItem("e", addedAt = FIXTURE_NOW - 2.days, lifecycle = ItemLifecycle.Deleted),
    )

    @Test
    fun shelfPagesMatchShelfQuery() = runTest {
        val repository = FakeItemRepository(fixture)
        listOf(
            ShelfQuerySpec(),
            ShelfQuerySpec(filter = ShelfFilter.Unread, sort = ShelfSort.Oldest, group = ShelfGrouping.Week),
            ShelfQuerySpec(filter = ShelfFilter.Source("medium.com"), sort = ShelfSort.BySource),
            ShelfQuerySpec(searchText = "title c"),
        ).forEach { spec ->
            val expected = ShelfQuery.shelf(fixture, spec, FIXTURE_NOW, FixedZoneProvider().zone()).rows
            assertEquals(expected, repository.observeShelf(spec).asSnapshot(), "$spec")
        }
        assertEquals(ShelfQuery.archive(fixture, "", FIXTURE_NOW, FixedZoneProvider().zone()), repository.observeArchive("").asSnapshot())
        assertEquals(listOf("c"), repository.observeShelfSearch("youtube", SearchFilter.All).asSnapshot().map { it.item.id.value })
        assertEquals("c", repository.shortestUnread()?.id?.value)
        assertEquals(listOf("a", "c"), repository.newestUnread(5).map { it.id.value })
        assertEquals(1, repository.observeArchiveCount().first())
    }

    @Test
    fun shelfReEmitsWhenTimePasses() = runTest {
        val repository = FakeItemRepository(listOf(savedItem("a", addedAt = FIXTURE_NOW)))
        assertEquals(ShelfRow.Header(GroupLabel.Today), repository.observeShelf(ShelfQuerySpec()).asSnapshot().first())

        repository.clock.advanceBy(1.days)

        assertEquals(ShelfRow.Header(GroupLabel.Yesterday), repository.observeShelf(ShelfQuerySpec()).asSnapshot().first())
    }

    @Test
    fun busAppliesItemCommandsLikeTheDecider() = runTest {
        val bus = FakeCommandBus(FakeItemRepository(fixture + savedItem("f", fetchStatus = FetchStatus.Failed)), ids = SequenceIdGenerator("op"))
        val decider = CommandDecider(SequenceIdGenerator("op"))
        listOf(
            Command.MarkUnread(ItemId("b")), Command.Archive(ItemId("a")), Command.Restore(ItemId("d")),
            Command.Remove(ItemId("c")), Command.Archive(ItemId("b")), Command.Delete(ItemId("b")),
            Command.RetryMetadata(ItemId("f")), Command.Restore(ItemId("a")), Command.Delete(ItemId("a")),
        ).forEach { command ->
            val expected = decider.decide(bus.items.get(command.id), command, FIXTURE_NOW)
            val result = bus.dispatch(command)
            assertEquals(DispatchResult.of(expected), result, "$command")
            if (expected is Decision.Applied) {
                assertEquals(expected.items.single(), bus.items.get(command.id), "$command")
            }
        }
        assertEquals(9, bus.dispatched.size)
    }

    @Test
    fun undoRestoresWhatTheCommandChanged() = runTest {
        val bus = FakeCommandBus(FakeItemRepository(fixture))
        val before = bus.items.get(ItemId("a"))

        val archived = assertIs<DispatchResult.Done>(bus.dispatch(Command.Archive(ItemId("a"))))
        val undone = bus.dispatch(Command.Undo(assertNotNull(archived.undoOpId)))

        assertIs<DispatchResult.Done>(undone)
        assertEquals(before?.lifecycle, bus.items.get(ItemId("a"))?.lifecycle)
        assertEquals(DispatchResult.Rejected(Rejection.UndoConflict), bus.dispatch(Command.Undo(archived.undoOpId!!)))
    }

    @Test
    fun saveReshareAndReadsCountForTheStreak() = runTest {
        val bus = FakeCommandBus()
        val capture = (LinkCapturePolicy.validateManual("example.com/post") as ManualLink.Valid).capture

        val saved = assertIs<DispatchResult.Done>(bus.dispatch(Command.SaveLink(capture, null)))
        val reshared = assertIs<DispatchResult.Done>(bus.dispatch(Command.SaveLink(capture, null)))
        val firstRead = assertIs<DispatchResult.Done>(bus.dispatch(Command.OpenItem(saved.itemId!!)))
        val secondRead = assertIs<DispatchResult.Done>(bus.dispatch(Command.MarkRead(saved.itemId!!)))

        assertTrue(reshared.reshared)
        assertEquals(1, bus.items.items.value.size)
        assertEquals(1, firstRead.celebration?.to)
        assertNull(secondRead.celebration)
        assertEquals(ItemLifecycle.Read, bus.items.get(saved.itemId!!)?.lifecycle)
        assertEquals(1, bus.streak.observeStreak().first().count)
        assertEquals(FIXTURE_NOW, bus.streak.unreadZeroSince.value, "the last unread item was read")
    }

    @Test
    fun bulkSettingsAndScriptedRejections() = runTest {
        val clock = TestClock(FIXTURE_NOW + 40.days)
        val bus = FakeCommandBus(FakeItemRepository(fixture, clock))

        bus.rejectNext(Rejection.StorageFailure)
        assertEquals(DispatchResult.Rejected(Rejection.StorageFailure), bus.dispatch(Command.ClearRead))
        assertEquals(1, bus.items.countAutoArchiveDue(AutoArchivePeriod.Days30))
        assertEquals(DispatchResult.Done(affected = 1), bus.dispatch(Command.ApplyAutoArchive(AutoArchivePeriod.Days30)))
        assertEquals(DispatchResult.Rejected(Rejection.NothingToApply), bus.dispatch(Command.ClearRead))
        assertEquals(DispatchResult.Done(affected = 0), bus.dispatch(Command.ChangeSetting { it.copy(markReadOnOpen = false) }))
        assertEquals(false, bus.settings.preferences.value.markReadOnOpen)
    }
}
