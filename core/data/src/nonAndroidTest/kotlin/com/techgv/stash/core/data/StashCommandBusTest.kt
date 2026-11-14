package com.techgv.stash.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.techgv.stash.core.database.MetadataAttemptEntity
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.buildStashDatabase
import com.techgv.stash.core.database.toEntity
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.Rejection
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.OperationPayload
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.model.lifecycleStamps
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.testing.FIXTURE_NOW
import com.techgv.stash.core.testing.savedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import okio.IOException

class StashCommandBusTest {
    private val earlier = FIXTURE_NOW - 3.days

    @Test
    fun saveLinkStoresAPendingItemWithItsOperationAndSchedulesMetadata() = runTest {
        val data = TestData(this)

        val result = data.save("https://example.com/post?utm_source=x", text = "A provisional title")

        assertEquals(DispatchResult.Done(affected = 1, itemId = ItemId("id-1")), result)
        val item = data.item("id-1")!!
        assertEquals("https://example.com/post", item.url)
        assertEquals("A provisional title", item.title)
        assertEquals(FetchStatus.Pending, item.fetchStatus)
        assertEquals(ItemLifecycle.Unread, item.lifecycle)
        assertEquals(FIXTURE_NOW, item.addedAt)
        assertEquals(
            listOf(Operation("id-2", ItemId("id-1"), OperationType.Save, OperationPayload.Lifecycle(null, item.lifecycleStamps), FIXTURE_NOW)),
            data.operations(),
        )
        assertEquals(listOf<BackgroundJob>(BackgroundJob.FetchMetadata(ItemId("id-1"))), data.jobs.scheduled)
    }

    @Test
    fun everySingleItemCommandWritesTheRowItsOperationAndReportsUndo() = runTest {
        val now = FIXTURE_NOW
        val cases = listOf(
            Triple(savedItem("a", addedAt = earlier), Command.MarkRead(ItemId("a")), OperationType.MarkRead),
            Triple(savedItem("b", ItemLifecycle.Read, addedAt = earlier), Command.MarkUnread(ItemId("b")), OperationType.MarkUnread),
            Triple(savedItem("c", addedAt = earlier), Command.Archive(ItemId("c")), OperationType.Archive),
            Triple(savedItem("d", ItemLifecycle.Archived, addedAt = earlier), Command.Restore(ItemId("d")), OperationType.Restore),
            Triple(savedItem("e", ItemLifecycle.Read, addedAt = earlier), Command.Remove(ItemId("e")), OperationType.Remove),
            Triple(savedItem("f", ItemLifecycle.Archived, addedAt = earlier), Command.Delete(ItemId("f")), OperationType.Delete),
        )
        val expectedRows = mapOf(
            "a" to { i: SavedItem -> i.copy(readAt = now, updatedAt = now) },
            "b" to { i -> i.copy(readAt = null, updatedAt = now) },
            "c" to { i -> i.copy(archivedAt = now, updatedAt = now) },
            "d" to { i -> i.copy(archivedAt = null, updatedAt = now) },
            "e" to { i -> i.copy(deletedAt = now, updatedAt = now) },
            "f" to { i -> i.copy(deletedAt = now, updatedAt = now) },
        )
        for ((item, command, type) in cases) {
            val data = TestData(this)
            data.insert(item)

            val result = data.bus.dispatch(command)

            val expected = expectedRows.getValue(item.id.value)(item)
            assertEquals(expected, data.item(item.id.value), "$type row")
            val operation = data.operations().single()
            assertEquals(Operation("id-1", item.id, type, OperationPayload.Lifecycle(item.lifecycleStamps, expected.lifecycleStamps), now), operation)
            val undoable = type in setOf(OperationType.Archive, OperationType.Restore, OperationType.Remove, OperationType.Delete)
            val celebration = Celebration(0, 1, null).takeIf { type == OperationType.MarkRead }
            assertEquals(DispatchResult.Done(1, undoOpId = "id-1".takeIf { undoable }, itemId = item.id, celebration = celebration), result, "$type result")
            data.close()
        }
    }

    @Test
    fun retryMetadataSetsFailedBackToPendingAndResetsTheAttempts() = runTest {
        val data = TestData(this)
        data.insert(savedItem("a", fetchStatus = FetchStatus.Failed, addedAt = earlier))
        data.database.metadataAttemptDao().upsert(MetadataAttemptEntity("a", 2, FIXTURE_NOW))

        val result = data.bus.dispatch(Command.RetryMetadata(ItemId("a")))

        assertEquals(DispatchResult.Done(1, itemId = ItemId("a")), result)
        assertEquals(FetchStatus.Pending, data.item("a")!!.fetchStatus)
        assertEquals(OperationPayload.Metadata(FetchStatus.Failed, FetchStatus.Pending), data.operations().single().payload)
        assertEquals(0, data.database.metadataAttemptDao().failedAttempts("a"))
        assertEquals(listOf<BackgroundJob>(BackgroundJob.FetchMetadata(ItemId("a"))), data.jobs.scheduled)
    }

    @Test
    fun everyRejectionWritesNothing() = runTest {
        val data = TestData(this)
        data.insert(savedItem("unread", addedAt = earlier), savedItem("fetched", ItemLifecycle.Read, addedAt = earlier))
        data.bus.dispatch(Command.MarkRead(ItemId("unread")))
        val markRead = data.operations().single().opId
        val before = data.operations()

        val rejections = listOf(
            Command.MarkRead(ItemId("missing")) to Rejection.NotFound,
            Command.Delete(ItemId("unread")) to Rejection.InvalidTransition(ItemLifecycle.Read, OperationType.Delete),
            Command.Undo(markRead) to Rejection.NotUndoable,
            Command.Undo("no-such-op") to Rejection.NotFound,
            Command.RetryMetadata(ItemId("fetched")) to Rejection.NothingToApply,
            Command.ApplyAutoArchive(AutoArchivePeriod.Off) to Rejection.NothingToApply,
            Command.ApplyAutoArchive(AutoArchivePeriod.Days7) to Rejection.NothingToApply,
        )
        for ((command, rejection) in rejections) {
            assertEquals(DispatchResult.Rejected(rejection), data.bus.dispatch(command), "$command")
        }
        assertEquals(before, data.operations())
        // ClearRead takes the read items; a second run has nothing left.
        assertEquals(DispatchResult.Done(2), data.bus.dispatch(Command.ClearRead))
        assertEquals(DispatchResult.Rejected(Rejection.NothingToApply), data.bus.dispatch(Command.ClearRead))
    }

    @Test
    fun undoRestoresTheExactRowOnceAndNotAfterALaterChange() = runTest {
        val starts = listOf(
            Command.Archive(ItemId("x")) to savedItem("x", ItemLifecycle.Read, addedAt = earlier),
            Command.Restore(ItemId("x")) to savedItem("x", ItemLifecycle.Archived, addedAt = earlier),
            Command.Remove(ItemId("x")) to savedItem("x", addedAt = earlier),
            Command.Delete(ItemId("x")) to savedItem("x", ItemLifecycle.Archived, addedAt = earlier),
        )
        for ((command, item) in starts) {
            val data = TestData(this)
            data.insert(item)
            val opId = (data.bus.dispatch(command) as DispatchResult.Done).undoOpId!!
            data.clock.advanceBy(1.minutes)

            val undo = data.bus.dispatch(Command.Undo(opId))

            assertEquals(DispatchResult.Done(1, itemId = item.id), undo, "$command")
            assertEquals(item.copy(updatedAt = data.clock.now), data.item("x"), "$command: the row as it was")
            assertEquals(DispatchResult.Rejected(Rejection.UndoConflict), data.bus.dispatch(Command.Undo(opId)), "$command twice")
            data.close()
        }

        val data = TestData(this)
        data.insert(savedItem("x", addedAt = earlier))
        val opId = (data.bus.dispatch(Command.Archive(ItemId("x"))) as DispatchResult.Done).undoOpId!!
        data.bus.dispatch(Command.MarkRead(ItemId("x")))
        assertEquals(DispatchResult.Rejected(Rejection.UndoConflict), data.bus.dispatch(Command.Undo(opId)))
    }

    @Test
    fun reshareBringsAnyItemBackToTheTopUnread() = runTest {
        for (lifecycle in ItemLifecycle.entries) {
            val data = TestData(this)
            val url = "https://example.com/again"
            data.insert(savedItem("old", lifecycle, addedAt = earlier, url = url))

            val result = data.save("$url?utm_campaign=share")

            assertEquals(DispatchResult.Done(1, itemId = ItemId("old"), reshared = true), result, "$lifecycle")
            assertEquals(listOf("old"), data.itemIds())
            val item = data.item("old")!!
            assertEquals(ItemLifecycle.Unread, item.lifecycle, "$lifecycle")
            assertEquals(FIXTURE_NOW, item.addedAt)
            assertEquals(OperationType.Reshare, data.operations().single().type)
            data.close()
        }
    }

    @Test
    fun aDuplicateShareMakesNoSecondItem() = runTest {
        val data = TestData(this)

        data.save("https://Example.com/post/")
        data.save("https://example.com/post?fbclid=1")

        assertEquals(listOf("id-1"), data.itemIds())
        assertEquals(listOf(OperationType.Save, OperationType.Reshare), data.operations().map { it.type })
    }

    @Test
    fun aSettingChangeWritesNoOperationAndReplansOnlyForReminderSettings() = runTest {
        val data = TestData(this)
        data.save("https://example.com/a")
        data.settle()
        val applied = data.scheduler.applyCount

        assertEquals(DispatchResult.Done(0), data.bus.dispatch(Command.ChangeSetting { it.copy(theme = ThemeMode.Night) }))
        data.settle()
        assertEquals(applied, data.scheduler.applyCount, "theme does not change reminders")

        data.bus.dispatch(Command.ChangeSetting { it.copy(reminderTime = ReminderTime.Evening) })
        data.settle()
        assertEquals(applied + 1, data.scheduler.applyCount)
        assertEquals(ThemeMode.Night, data.settings.preferences.first().theme)
        assertEquals(ReminderTime.Evening, data.settings.preferences.first().reminderTime)
        assertEquals(1, data.operations().size)
    }

    @Test
    fun aStorageFailureMidTransactionLeavesNothingBehind() = runTest {
        val data = TestData(this)
        // The save takes id-1 for the item and id-2 for its operation; an operation id-2 already exists, so the
        // operation insert fails after the item row was written.
        val existing = Operation("id-2", ItemId("other"), OperationType.MarkRead, OperationPayload.Metadata(FetchStatus.Failed, FetchStatus.Pending), earlier)
        data.database.operationDao().insert(listOf(existing.toEntity()))

        val result = data.save("https://example.com/a")
        data.settle()

        assertEquals(DispatchResult.Rejected(Rejection.StorageFailure), result)
        assertEquals(emptyList(), data.itemIds())
        assertEquals(listOf(existing), data.operations())
        assertNull(data.database.appStateDao().unreadZeroSince())
        assertTrue(data.jobs.scheduled.isEmpty() && data.widget.published.isEmpty(), "no fan-out for a rejected command")
    }

    @Test
    fun bulkCommandsWorkThroughEveryChunkInOneTransaction() = runTest {
        val data = TestData(this)
        val read = List(1300) { savedItem("r" + "$it".padStart(4, '0'), ItemLifecycle.Read, addedAt = earlier - 40.days) }
        val unread = List(20) { savedItem("u" + "$it".padStart(2, '0'), addedAt = earlier) }
        data.insert(*(read + unread).toTypedArray())

        val result = data.bus.dispatch(Command.ClearRead)
        data.settle()

        assertEquals(DispatchResult.Done(1300), result)
        assertEquals(1300, data.operations().count { it.type == OperationType.ClearRead })
        assertEquals(FIXTURE_NOW, data.item("r1299")!!.deletedAt)
        assertEquals(unread.first(), data.item("u00"))
        assertEquals(1, data.scheduler.applyCount, "one fan-out for the whole command")
        assertEquals(1, data.widget.published.size)
    }

    @Test
    fun autoArchiveMovesOnlyItemsReadLongerThanThePeriod() = runTest {
        val data = TestData(this)
        val due = List(1300) { savedItem("old" + "$it".padStart(4, '0'), addedAt = FIXTURE_NOW - 60.days, readAt = FIXTURE_NOW - 31.days) }
        val recent = savedItem("recent", addedAt = FIXTURE_NOW - 60.days, readAt = FIXTURE_NOW - 29.days)
        val unread = savedItem("unread", addedAt = FIXTURE_NOW - 60.days)
        data.insert(*(due + recent + unread).toTypedArray())

        assertEquals(DispatchResult.Done(1300), data.bus.dispatch(Command.ApplyAutoArchive(AutoArchivePeriod.Days30)))

        assertEquals(FIXTURE_NOW, data.item("old0000")!!.archivedAt)
        assertEquals(1300, data.operations().count { it.type == OperationType.AutoArchive })
        assertEquals(recent, data.item("recent"))
        assertEquals(unread, data.item("unread"))
    }

    @Test
    fun concurrentCommandsNeverDecideOnStaleState() = runTest {
        repeat(20) { round ->
            // Real threads and the IO dispatcher, so the two commands genuinely race.
            val database = Room.inMemoryDatabaseBuilder<StashDatabase>().buildStashDatabase()
            val data = TestData(this, database)
            val url = "https://example.com/race-$round"

            val saves = withContext(Dispatchers.Default) { List(2) { async { data.save(url) } }.awaitAll() }

            assertEquals(1, data.itemIds().size, "one item for one link")
            assertEquals(setOf(false, true), saves.map { (it as DispatchResult.Done).reshared }.toSet())
            val id = data.itemIds().single()

            withContext(Dispatchers.Default) {
                listOf(async { data.bus.dispatch(Command.MarkRead(ItemId(id))) }, async { data.bus.dispatch(Command.Archive(ItemId(id))) }).awaitAll()
            }

            // Each operation was decided on the state the one before it left behind.
            val chain = data.operations().map { it.payload as OperationPayload.Lifecycle }
            chain.zipWithNext().forEach { (first, second) -> assertEquals(first.after, second.before, "round $round") }
            val item = data.item(id)!!
            assertEquals(chain.last().after, item.lifecycleStamps)
            assertEquals(ItemLifecycle.Archived, item.lifecycle)
            // Not closed: this round's fan-out may still be reading on Room's threads, and closing the in-memory
            // database under it crashes the native driver.
        }
    }

    @Test
    fun aCommittedSaveIsFollowedUpEvenWhenTheCallerIsCancelledMidway() = runTest {
        val data = TestData(this)
        lateinit var caller: Job
        // The decider takes an id inside the transaction: cancel the caller right there.
        data.onNextId = { caller.cancel(); data.onNextId = null }

        caller = launch { data.save("https://example.com/post") }
        caller.join()
        data.settle()

        assertEquals(listOf("id-1"), data.itemIds(), "the save committed")
        assertEquals(listOf<BackgroundJob>(BackgroundJob.FetchMetadata(ItemId("id-1"))), data.jobs.scheduled, "and was followed up")
        assertEquals(1, data.scheduler.applyCount)
    }

    @Test
    fun aBugIsThrownNotReportedAsAStorageFailure() = runTest {
        val data = TestData(this)
        data.onNextId = { error("a decider bug") }

        assertFailsWith<IllegalStateException> { data.save("https://example.com/post") }

        assertEquals(emptyList(), data.itemIds())
    }

    @Test
    fun aSettingsFileThatCannotBeReadOrWrittenIsAStorageFailure() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk") }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
        }
        val data = TestData(this, settingsFile = broken)
        data.insert(savedItem("a", addedAt = earlier))

        assertEquals(DispatchResult.Rejected(Rejection.StorageFailure), data.bus.dispatch(Command.ChangeSetting { it.copy(theme = ThemeMode.Night) }))
        assertEquals(DispatchResult.Rejected(Rejection.StorageFailure), data.open("a"))
        assertEquals(ItemLifecycle.Unread, data.item("a")!!.lifecycle)
    }

    @Test
    fun sharingAFailedLinkAgainRetriesItsMetadata() = runTest {
        val data = TestData(this)
        val url = "https://example.com/failed"
        data.insert(savedItem("old", ItemLifecycle.Read, addedAt = earlier, url = url, fetchStatus = FetchStatus.Failed))
        data.database.metadataAttemptDao().upsert(MetadataAttemptEntity("old", 2, earlier))

        val result = data.save(url)

        assertEquals(DispatchResult.Done(1, itemId = ItemId("old"), reshared = true), result)
        assertEquals(FetchStatus.Pending, data.item("old")!!.fetchStatus)
        assertEquals(0, data.database.metadataAttemptDao().failedAttempts("old"))
        assertEquals(listOf(OperationType.Reshare, OperationType.RetryMetadata), data.operations().map { it.type })
        assertEquals(listOf<BackgroundJob>(BackgroundJob.FetchMetadata(ItemId("old"))), data.jobs.scheduled)
    }

    @Test
    fun aSettingChangeWhoseCallerIsCancelledAfterTheWriteIsStillReplanned() = runTest {
        lateinit var caller: Job
        val file = PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
            (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stash-data-${Random.nextLong().toULong()}.preferences_pb").toString().toPath()
        }
        // The write commits, then the caller is cancelled while it still waits for DataStore's answer.
        val cancelledAfterTheWrite = object : DataStore<Preferences> by file {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                file.updateData(transform).also { caller.cancel(); yield() }
        }
        val data = TestData(this, settingsFile = cancelledAfterTheWrite)
        data.save("https://example.com/a")
        data.settle()
        val plans = data.scheduler.applyCount

        caller = launch { data.bus.dispatch(Command.ChangeSetting { it.copy(reminderTime = ReminderTime.Evening) }) }
        caller.join()
        data.settle()

        assertTrue(caller.isCancelled)
        assertEquals(ReminderTime.Evening, data.settings.preferences.first().reminderTime)
        assertEquals(plans + 1, data.scheduler.applyCount, "the committed change is re-planned")
    }
}
