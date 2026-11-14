package com.techgv.stash.core.domain

import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.OperationPayload
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.lifecycleStamps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CommandDeciderTest {
    private val decider = CommandDecider(SequentialIds())
    private val now = T0
    private val unread = item(addedAt = T0 - 3.days)
    private val read = item(addedAt = T0 - 3.days, readAt = T0 - 2.days)
    private val archived = item(addedAt = T0 - 3.days, readAt = T0 - 2.days, archivedAt = T0 - 1.days)
    private val deleted = item(addedAt = T0 - 3.days, deletedAt = T0 - 1.days)

    private val capture = LinkCapture.Captured(
        url = "https://example.com/a?x=1#top",
        normalizedUrl = "https://example.com/a?x=1",
        title = "Shared title",
        domain = "example.com",
        siteName = "example.com",
        sourceKey = "example.com",
        contentType = ContentType.Article,
    )

    private fun applied(decision: Decision): Decision.Applied = assertIs<Decision.Applied>(decision)

    private fun rejection(decision: Decision): Rejection = assertIs<Decision.Rejected>(decision).rejection

    @Test
    fun itemCommandsMapToTheirOperationAndLogBeforeAndAfter() {
        val cases = listOf(
            Triple(unread, Command.MarkRead(unread.id), OperationType.MarkRead),
            Triple(read, Command.MarkUnread(read.id), OperationType.MarkUnread),
            Triple(unread, Command.Archive(unread.id), OperationType.Archive),
            Triple(archived, Command.Restore(archived.id), OperationType.Restore),
            Triple(read, Command.Remove(read.id), OperationType.Remove),
            Triple(archived, Command.Delete(archived.id), OperationType.Delete),
        )
        for ((before, command, type) in cases) {
            val result = applied(CommandDecider(SequentialIds()).decide(before, command, now))
            val after = result.items.single()
            val op = result.operations.single()
            assertEquals(Operation("op-1", before.id, type, OperationPayload.Lifecycle(before.lifecycleStamps, after.lifecycleStamps), now), op)
            assertEquals(now, after.updatedAt)
        }
    }

    @Test
    fun invalidCommandsAreRejectedWithTheirStateAndNeverThrow() {
        val cases = listOf(
            Triple(unread, Command.MarkUnread(unread.id), OperationType.MarkUnread),
            Triple(archived, Command.Archive(archived.id), OperationType.Archive),
            Triple(unread, Command.Restore(unread.id), OperationType.Restore),
            Triple(archived, Command.Remove(archived.id), OperationType.Remove),
            Triple(read, Command.Delete(read.id), OperationType.Delete),
            Triple(deleted, Command.MarkRead(deleted.id), OperationType.MarkRead),
            Triple(deleted, Command.Delete(deleted.id), OperationType.Delete),
        )
        for ((item, command, type) in cases) {
            assertEquals(Rejection.InvalidTransition(item.lifecycle, type), rejection(decider.decide(item, command, now)))
        }
    }

    @Test
    fun readingAnAlreadyReadItemChangesNothingButIsNotRejected() {
        val nothing = Decision.Applied(emptyList(), emptyList())
        assertEquals(nothing, decider.decide(read, Command.MarkRead(read.id), now))
        assertEquals(nothing, decider.decide(archived, Command.MarkRead(archived.id), now))
        assertEquals(DispatchResult.Done(affected = 0), DispatchResult.of(nothing))
    }

    @Test
    fun openingMarksReadOnlyWhenTheSettingIsOnAndTheItemIsUnread() {
        val opened = applied(decider.open(unread, markReadOnOpen = true, now))
        assertEquals(applied(decider.decide(unread, Command.MarkRead(unread.id), now)).items, opened.items, "same transition as MarkRead")
        assertEquals(OperationType.MarkRead, opened.operations.single().type)

        val nothing = Decision.Applied(emptyList(), emptyList())
        assertEquals(nothing, decider.open(unread, markReadOnOpen = false, now), "setting off")
        assertEquals(nothing, decider.open(read, markReadOnOpen = true, now), "already read")
        assertEquals(nothing, decider.open(archived, markReadOnOpen = true, now), "archived and read")

        val archivedUnread = item(addedAt = T0 - 3.days, archivedAt = T0 - 1.days)
        val archivedOpened = applied(decider.open(archivedUnread, markReadOnOpen = true, now)).items.single()
        assertEquals(ItemLifecycle.Archived, archivedOpened.lifecycle, "the archived MarkRead rule")
        assertEquals(now, archivedOpened.readAt)

        assertEquals(Rejection.InvalidTransition(ItemLifecycle.Deleted, OperationType.MarkRead), rejection(decider.open(deleted, true, now)))
        assertEquals(Rejection.InvalidTransition(ItemLifecycle.Deleted, OperationType.MarkRead), rejection(decider.open(deleted, false, now)))
        assertEquals(Rejection.NotFound, rejection(decider.open(null, true, now)))
    }

    @Test
    fun missingItemIsNotFound() {
        assertEquals(Rejection.NotFound, rejection(decider.decide(null, Command.MarkRead(ItemId("gone")), now)))
    }

    @Test
    fun saveLinkCreatesAPendingUnreadItemFromTheCapture() {
        val result = applied(decider.saveLink(null, Command.SaveLink(capture, sourceApp = "com.android.chrome"), now))
        val saved = result.items.single()
        assertEquals(ItemId("op-1"), saved.id)
        assertEquals(capture.url, saved.url)
        assertEquals(capture.normalizedUrl, saved.normalizedUrl)
        assertEquals("Shared title", saved.title)
        assertEquals("com.android.chrome", saved.sourceApp)
        assertEquals(FetchStatus.Pending, saved.fetchStatus)
        assertEquals(ItemLifecycle.Unread, saved.lifecycle)
        assertEquals(now, saved.addedAt)
        val op = result.operations.single()
        assertEquals(OperationType.Save, op.type)
        assertEquals(OperationPayload.Lifecycle(null, saved.lifecycleStamps), op.payload)
        assertEquals("op-2", op.opId)
    }

    @Test
    fun reshareOfUnreadReadArchivedAndDeletedReturnsToTheTopAsUnread() {
        for (existing in listOf(unread, read, archived, deleted)) {
            val result = applied(decider.saveLink(existing, Command.SaveLink(capture, "app"), now))
            val item = result.items.single()
            assertEquals(existing.id, item.id, "no second item")
            assertEquals(ItemLifecycle.Unread, item.lifecycle, "${existing.lifecycle}")
            assertEquals(now, item.addedAt)
            assertEquals(OperationType.Reshare, result.operations.single().type)
        }
    }

    @Test
    fun undoRestoresTheExactPriorItemForEachUndoableCommand() {
        val cases = listOf(
            unread to Command.Archive(unread.id),
            read to Command.Archive(read.id),
            archived to Command.Restore(archived.id),
            unread to Command.Remove(unread.id),
            read to Command.Remove(read.id),
            archived to Command.Delete(archived.id),
        )
        for ((prior, command) in cases) {
            val done = applied(decider.decide(prior, command, now))
            val undoAt = now + 5.minutes
            val undone = applied(decider.undo(done.operations.single(), done.items.single(), undoAt))
            val restored = undone.items.single()
            assertEquals(prior.copy(updatedAt = undoAt), restored, "$command")
            val op = undone.operations.single()
            assertEquals(OperationType.Undo, op.type)
            assertEquals(
                OperationPayload.Undo(done.operations.single().opId, done.items.single().lifecycleStamps, prior.lifecycleStamps),
                op.payload,
            )
        }
    }

    @Test
    fun undoRejectsCommandsThatAreNotUndoable() {
        val notUndoable = listOf(
            decider.decide(unread, Command.MarkRead(unread.id), now),
            decider.decide(read, Command.MarkUnread(read.id), now),
            decider.saveLink(null, Command.SaveLink(capture, null), now),
            decider.saveLink(read, Command.SaveLink(capture, null), now),
            decider.clearRead(listOf(read), now),
            decider.applyAutoArchive(listOf(item(readAt = T0 - 40.days)), AutoArchivePeriod.Days30, now),
            decider.decide(item(fetchStatus = FetchStatus.Failed), Command.RetryMetadata(unread.id), now),
            decider.decide(archived, Command.MarkUnread(archived.id), now),
        )
        for (decision in notUndoable) {
            val result = applied(decision)
            assertEquals(Rejection.NotUndoable, rejection(decider.undo(result.operations.single(), result.items.single(), now)))
        }
    }

    @Test
    fun undoRejectsAnOperationForAnotherItem() {
        val done = applied(decider.decide(unread, Command.Archive(unread.id), now))
        val other = done.items.single().copy(id = ItemId("other"))
        assertEquals(Rejection.NotFound, rejection(decider.undo(done.operations.single(), other, now)))
    }

    @Test
    fun undoSurvivesMillisecondStorage() {
        // The database keeps milliseconds; a nanosecond clock must not make the stored stamps differ from the logged ones.
        val precise = Instant.fromEpochSeconds(T0.epochSeconds, 123_456_789)
        val done = applied(decider.decide(unread, Command.Archive(unread.id), precise))
        fun Instant?.stored() = this?.let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }
        val storedItem = done.items.single().let {
            it.copy(archivedAt = it.archivedAt.stored(), updatedAt = it.updatedAt.stored()!!)
        }
        assertEquals(done.items.single(), storedItem, "decider output is already at millisecond precision")
        val undone = applied(decider.undo(done.operations.single(), storedItem, precise + 1.minutes))
        assertEquals(unread.lifecycleStamps, undone.items.single().lifecycleStamps)
        assertEquals(Instant.fromEpochMilliseconds((precise + 1.minutes).toEpochMilliseconds()), undone.items.single().updatedAt)
    }

    @Test
    fun markReadAndUnreadOnAnArchivedItemOnlyChangeReadAt() {
        val archivedUnread = item(addedAt = T0 - 3.days, archivedAt = T0 - 1.days)
        val read = applied(decider.decide(archivedUnread, Command.MarkRead(archivedUnread.id), now)).items.single()
        assertEquals(ItemLifecycle.Archived, read.lifecycle)
        assertEquals(now, read.readAt)
        val restored = applied(decider.decide(read, Command.Restore(read.id), now)).items.single()
        assertEquals(ItemLifecycle.Read, restored.lifecycle, "an archived item opened in the reader comes back read")
        val unreadAgain = applied(decider.decide(read, Command.MarkUnread(read.id), now)).items.single()
        assertEquals(archivedUnread.copy(updatedAt = now), unreadAgain)
        assertEquals(Decision.Applied(emptyList(), emptyList()), decider.decide(read, Command.MarkRead(read.id), now), "already read")
        assertEquals(
            Rejection.InvalidTransition(ItemLifecycle.Archived, OperationType.MarkUnread),
            rejection(decider.decide(archivedUnread, Command.MarkUnread(archivedUnread.id), now)),
        )
    }

    @Test
    fun bulkResultSumsChunks() {
        val r2 = read.copy(id = ItemId("r2"))
        val r3 = read.copy(id = ItemId("r3"))
        val chunks = listOf(
            decider.clearRead(listOf(read, r2), now),
            decider.clearRead(listOf(unread), now),
            decider.clearRead(listOf(r3, archived), now),
        )
        assertEquals(DispatchResult.Done(affected = 3), DispatchResult.ofBulk(chunks))
        // A single chunk with a single item is still a bulk result: no item id and no undo.
        assertEquals(DispatchResult.Done(affected = 1), DispatchResult.ofBulk(listOf(decider.clearRead(listOf(read), now))))
        assertEquals(
            DispatchResult.Rejected(Rejection.NothingToApply),
            DispatchResult.ofBulk(listOf(decider.clearRead(listOf(unread), now), decider.clearRead(emptyList(), now))),
        )
        assertEquals(DispatchResult.Rejected(Rejection.NothingToApply), DispatchResult.ofBulk(emptyList()))
        assertEquals(
            DispatchResult.Rejected(Rejection.NotFound),
            DispatchResult.ofBulk(listOf(Decision.Rejected(Rejection.NothingToApply), Decision.Rejected(Rejection.NotFound))),
        )
    }

    @Test
    fun dispatchResultReportsWhatTheUiNeeds() {
        val archive = applied(decider.decide(unread, Command.Archive(unread.id), now))
        assertEquals(
            DispatchResult.Done(affected = 1, undoOpId = archive.operations.single().opId, itemId = unread.id),
            DispatchResult.of(archive),
        )
        val markRead = decider.decide(unread, Command.MarkRead(unread.id), now)
        assertEquals(DispatchResult.Done(affected = 1, itemId = unread.id), DispatchResult.of(markRead))
        val reshare = decider.saveLink(read, Command.SaveLink(capture, null), now)
        assertEquals(DispatchResult.Done(affected = 1, itemId = read.id, reshared = true), DispatchResult.of(reshare))
        val saved = applied(decider.saveLink(null, Command.SaveLink(capture, null), now))
        assertEquals(DispatchResult.Done(affected = 1, itemId = saved.items.single().id), DispatchResult.of(saved))
        val bulk = decider.clearRead(listOf(read, read.copy(id = ItemId("r2"))), now)
        assertEquals(DispatchResult.Done(affected = 2), DispatchResult.of(bulk))
        assertEquals(DispatchResult.Rejected(Rejection.NotFound), DispatchResult.of(decider.decide(null, Command.Archive(unread.id), now)))
    }

    @Test
    fun undoRejectsMissingOperationOrItem() {
        val done = applied(decider.decide(unread, Command.Archive(unread.id), now))
        assertEquals(Rejection.NotFound, rejection(decider.undo(null, unread, now)))
        assertEquals(Rejection.NotFound, rejection(decider.undo(done.operations.single(), null, now)))
    }

    @Test
    fun undoRejectsWhenTheItemChangedSinceOrWasAlreadyUndone() {
        val archivedOp = applied(decider.decide(unread, Command.Archive(unread.id), now))
        val restored = applied(decider.decide(archivedOp.items.single(), Command.Restore(unread.id), now + 1.minutes))
        assertEquals(Rejection.UndoConflict, rejection(decider.undo(archivedOp.operations.single(), restored.items.single(), now)))

        val undone = applied(decider.undo(archivedOp.operations.single(), archivedOp.items.single(), now))
        assertEquals(Rejection.UndoConflict, rejection(decider.undo(archivedOp.operations.single(), undone.items.single(), now)))
    }

    @Test
    fun clearReadRemovesOnlyReadShelfItems() {
        val items = listOf(unread, read.copy(id = ItemId("r2")), read, archived, deleted)
        val result = applied(decider.clearRead(items, now))
        assertEquals(listOf(ItemId("r2"), read.id), result.items.map { it.id })
        assertEquals(setOf(ItemLifecycle.Deleted), result.items.map { it.lifecycle }.toSet())
        assertEquals(listOf(OperationType.ClearRead, OperationType.ClearRead), result.operations.map { it.type })
        assertEquals(Rejection.NothingToApply, rejection(decider.clearRead(listOf(unread, archived), now)))
    }

    @Test
    fun applyAutoArchiveMovesOnlyDueReadItems() {
        val due = item(id = "due", readAt = now - 30.days)
        val fresh = item(id = "fresh", readAt = now - 29.days)
        val result = applied(decider.applyAutoArchive(listOf(due, fresh, unread, archived), AutoArchivePeriod.Days30, now))
        assertEquals(listOf(ItemId("due")), result.items.map { it.id })
        assertEquals(now, result.items.single().archivedAt)
        assertEquals(OperationType.AutoArchive, result.operations.single().type)
        assertEquals(Rejection.NothingToApply, rejection(decider.applyAutoArchive(listOf(due), AutoArchivePeriod.Off, now)))
    }

    @Test
    fun retryMetadataOnlyRetriesFailedItemsThatStillExist() {
        val failed = item(fetchStatus = FetchStatus.Failed)
        val result = applied(decider.decide(failed, Command.RetryMetadata(failed.id), now))
        assertEquals(FetchStatus.Pending, result.items.single().fetchStatus)
        assertEquals(failed.lifecycle, result.items.single().lifecycle)
        assertEquals(OperationPayload.Metadata(FetchStatus.Failed, FetchStatus.Pending), result.operations.single().payload)

        assertEquals(Rejection.NothingToApply, rejection(decider.decide(unread, Command.RetryMetadata(unread.id), now)))
        val gone = failed.copy(deletedAt = now)
        assertEquals(
            Rejection.InvalidTransition(ItemLifecycle.Deleted, OperationType.RetryMetadata),
            rejection(decider.decide(gone, Command.RetryMetadata(gone.id), now)),
        )
    }

    @Test
    fun operationsCarryTheDecisionTime() {
        val at = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals(at, applied(decider.decide(unread, Command.MarkRead(unread.id), at)).operations.single().createdAt)
    }
}
