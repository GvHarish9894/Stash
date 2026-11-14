package com.techgv.stash.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.useReaderConnection
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.database.buildStashDatabase
import com.techgv.stash.core.database.toEntity
import com.techgv.stash.core.database.toModel
import com.techgv.stash.core.database.writeTransaction
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandDecider
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.LinkCapture
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.domain.ReminderText
import com.techgv.stash.core.domain.ReminderTextRenderer
import com.techgv.stash.core.domain.ZoneProvider
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.Operation
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.SharedPayload
import com.techgv.stash.core.model.WidgetSnapshot
import com.techgv.stash.core.platform.BackgroundJob
import com.techgv.stash.core.platform.BackgroundJobs
import com.techgv.stash.core.platform.NotificationScheduler
import com.techgv.stash.core.platform.ScheduledNotification
import com.techgv.stash.core.platform.ShareInbox
import com.techgv.stash.core.platform.WidgetPublisher
import com.techgv.stash.core.network.MetadataFetcher
import com.techgv.stash.core.network.ReaderFetcher
import com.techgv.stash.core.network.createStashHttpClient
import com.techgv.stash.core.preferences.SettingsStore
import com.techgv.stash.core.testing.FakeBackgroundJobs
import com.techgv.stash.core.testing.FakeConnectivity
import com.techgv.stash.core.testing.FakeNotificationScheduler
import com.techgv.stash.core.testing.FakeShareInbox
import com.techgv.stash.core.testing.FakeWidgetPublisher
import com.techgv.stash.core.testing.SequenceIdGenerator
import com.techgv.stash.core.testing.TestClock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random

/** Renders the copy parameters verbatim, so tests can see what reached the scheduler. */
val testRenderer = ReminderTextRenderer { ReminderText("${it.level} ${it.copy.streak}", "${it.copy.itemTitle} ${it.copy.unreadCount}") }

/**
 * The whole data layer over an in-memory database, wired like the Koin module but with test time, fakes and a scripted
 * network. Room, DataStore and the side-effect worker run on the test scheduler, so [settle] runs everything a command
 * started. The fetchers keep their real dispatcher: their 30 s bound must not expire in virtual time. [network] answers
 * every request (default: offline).
 *
 * The side effects run in [appScope], a child of the test's background scope. A test that closes the database calls
 * [close], which cancels and joins that scope first, so no refresh is still reading when the database closes.
 */
internal class TestData(
    private val scope: TestScope,
    val database: StashDatabase = inMemoryDatabase(scope),
    /** The user's zone; change it and call `sync.onTimeChanged()` to simulate travel. */
    var zone: TimeZone = TimeZone.UTC,
    val clock: TestClock = TestClock(),
    var network: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { throw IOException("offline") },
    settingsFile: DataStore<Preferences>? = null,
) {
    val zones = ZoneProvider { zone }
    val ids = SequenceIdGenerator()

    /** Runs whenever the decider takes an id: a hook into the middle of a command's transaction. */
    var onNextId: (() -> Unit)? = null

    /** Runs before each apply reaches the scheduler, for example to hold one apply back. */
    var beforeApply: (suspend () -> Unit)? = null
    val connectivity = FakeConnectivity()
    val appScope = CoroutineScope(scope.backgroundScope.coroutineContext + Job(scope.backgroundScope.coroutineContext.job))

    /** Names of capabilities that throw: "scheduler", "widget", "jobs", "inbox". */
    val failing = mutableSetOf<String>()
    val settings = SettingsStore(
        settingsFile ?: PreferenceDataStoreFactory.createWithPath(scope = scope.backgroundScope) {
            (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stash-data-${Random.nextLong().toULong()}.preferences_pb").toString().toPath()
        },
    )
    val requests = mutableListOf<String>()
    private val client = createStashHttpClient(MockEngine { request -> requests += request.url.toString(); network(request) })
    val scheduler = FakeNotificationScheduler()
    val widget = FakeWidgetPublisher()
    val jobs = FakeBackgroundJobs()
    private val schedulerOrFailure = object : NotificationScheduler {
        override suspend fun apply(plan: List<ScheduledNotification>) {
            beforeApply?.invoke()
            if ("scheduler" in failing) error("scheduler down") else scheduler.apply(plan)
        }
    }
    private val widgetOrFailure = object : WidgetPublisher {
        override suspend fun publish(snapshot: WidgetSnapshot) = if ("widget" in failing) error("widget down") else widget.publish(snapshot)
    }
    private val inboxOrFailure = object : ShareInbox {
        override suspend fun drain(save: suspend (SharedPayload) -> Unit) = if ("inbox" in failing) error("inbox unreadable") else inbox.drain(save)
    }
    private val jobsOrFailure = object : BackgroundJobs {
        override fun schedule(job: BackgroundJob) = if ("jobs" in failing) error("jobs down") else jobs.schedule(job)
    }
    val inbox = FakeShareInbox()
    val ticks = LocalTicks(clock, zones)
    val reminders = StashReminderRepository(database, settings, clock, zones, testRenderer, schedulerOrFailure)
    val effects = SideEffects(appScope, reminders, widgetOrFailure, jobsOrFailure, database, clock, zones, ticks)
    val bus = StashCommandBus(database, settings, CommandDecider { onNextId?.invoke(); ids.next() }, clock, zones, effects)
    val items = StashItemRepository(database, clock, zones, ticks)
    val streak = StashStreakRepository(database, clock, zones, ticks)
    val metadata = MetadataUpdater(database, MetadataFetcher(client), clock, effects, connectivity)
    val reader = StashReaderRepository(database, ReaderFetcher(client), clock, maxCacheBytes = 10_000)
    val recentSearches = StashRecentSearchRepository(database, clock)
    val sync = StashSync(inboxOrFailure, bus, metadata, database, settings, clock, effects, jobsOrFailure, ticks, maxCacheBytes = 10_000)

    /** Runs the work a command started on the test scheduler, including the fan-out after its coalescing window. */
    fun settle() {
        scope.runCurrent()
        scope.advanceTimeBy(SideEffects.COALESCE_WINDOW)
        scope.runCurrent()
    }

    /** Stops the side effects, waits for any running refresh, then closes the database. */
    suspend fun close() {
        appScope.coroutineContext.job.cancelAndJoin()
        database.close()
    }

    /** Opens [id] in the reader at the clock's time (decision D21). */
    suspend fun open(id: String): DispatchResult = bus.dispatch(Command.OpenItem(ItemId(id)))

    suspend fun markRead(id: String): DispatchResult = bus.dispatch(Command.MarkRead(ItemId(id)))

    suspend fun save(url: String, text: String? = null): DispatchResult {
        val capture = LinkCapturePolicy.capture(SharedPayload(listOfNotNull(text, url).joinToString(" "), null, null, clock.now()))
        return bus.dispatch(Command.SaveLink(capture as LinkCapture.Captured, sourceApp = null))
    }

    suspend fun insert(vararg items: SavedItem) =
        database.writeTransaction { database.savedItemDao().upsert(items.map { it.toEntity() }) }

    suspend fun item(id: String): SavedItem? = database.savedItemDao().get(id)?.toModel()

    /** Every operation row, oldest first. */
    suspend fun operations(): List<Operation> =
        strings("SELECT op_id FROM operation ORDER BY created_at, op_id").map { database.operationDao().get(it)!!.toModel() }

    suspend fun itemIds(): List<String> = strings("SELECT id FROM saved_item ORDER BY id")

    suspend fun strings(sql: String): List<String> = database.useReaderConnection { connection ->
        connection.usePrepared(sql) { statement -> buildList { while (statement.step()) add(statement.getText(0)) } }
    }

    companion object {
        fun inMemoryDatabase(scope: TestScope): StashDatabase =
            Room.inMemoryDatabaseBuilder<StashDatabase>().buildStashDatabase(StandardTestDispatcher(scope.testScheduler))

        fun html(body: String, head: String = "") = respondHtml("<html><head>$head</head><body>$body</body></html>")

        fun respondHtml(html: String): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
            { respond(html, HttpStatusCode.OK, headersOf("Content-Type", "text/html; charset=utf-8")) }

        fun status(code: HttpStatusCode): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
            { respondError(code) }
    }
}
