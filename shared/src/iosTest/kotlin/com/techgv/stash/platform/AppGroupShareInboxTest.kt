package com.techgv.stash.platform

import com.techgv.stash.core.model.SharedPayload
import com.techgv.stash.core.platform.AppGroupShareInbox
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class AppGroupShareInboxTest {
    private val directory = tempDirectory() + "/inbox"
    private val inbox = AppGroupShareInbox(directory)

    init {
        createDirectory(directory)
    }

    /** Writes a share the way the extension names it: `<epoch millis, 15 digits>-<UUID>.json`. */
    private fun share(text: String, millis: Long, uuid: String = NSUUID().UUIDString, subject: String? = null): String {
        val name = millis.toString().padStart(15, '0') + "-$uuid.json"
        writeText("$directory/$name", json(text, subject))
        return name
    }

    private fun json(text: String, subject: String? = null) =
        """{"text":"$text"${subject?.let { ""","subject":"$it"""" } ?: ""},"sourceApp":null,"at":"2026-11-12T10:00:00Z"}"""

    private fun names(): List<String> = listDirectory(directory)

    private suspend fun drainTexts(save: suspend (SharedPayload) -> Unit = {}): List<String?> {
        val saved = mutableListOf<String?>()
        inbox.drain { saved += it.text; save(it) }
        return saved
    }

    @Test
    fun deliversEveryShareInTheOrderSharedAndEmptiesTheInbox() = runTest {
        share("https://c.example", 3)
        share("https://a.example", 1)
        share("https://b.example", 2, subject = "Bee")
        val payloads = mutableListOf<SharedPayload>()
        inbox.drain { payloads += it }
        assertEquals(listOf("https://a.example", "https://b.example", "https://c.example"), payloads.map { it.text })
        assertEquals(SharedPayload("https://b.example", "Bee", null, Instant.parse("2026-11-12T10:00:00Z")), payloads[1])
        assertEquals(emptyList(), names())
    }

    @Test
    fun aShareIsStillInTheInboxWhileItsSaveRuns() = runTest {
        val a = share("https://a.example", 1)
        val b = share("https://b.example", 2)
        val seenDuringSave = mutableListOf<List<String>>()
        drainTexts { seenDuringSave += names() }
        assertEquals(listOf(listOf(a, b), listOf(b)), seenDuringSave)
    }

    @Test
    fun aThrowingSaveStopsTheDrainAndKeepsThatShareAndTheLaterOnes() = runTest {
        share("https://a.example", 1)
        val b = share("https://b.example", 2)
        val c = share("https://c.example", 3)
        assertFailsWith<IllegalStateException> {
            inbox.drain { if (it.text == "https://b.example") throw IllegalStateException("not stored") }
        }
        assertEquals(listOf(b, c), names())
        assertEquals(listOf("https://b.example", "https://c.example"), drainTexts())
    }

    @Test
    fun aShareArrivingDuringASaveIsDeliveredNowOrByTheNextDrain() = runTest {
        share("https://a.example", 10)
        share("https://b.example", 20)
        val saved = drainTexts {
            if (it.text == "https://b.example") {
                share("https://later.example", 30)
                share("https://clock-went-back.example", 5)
            }
        }
        assertEquals(listOf("https://a.example", "https://b.example", "https://later.example"), saved)
        assertEquals(listOf("https://clock-went-back.example"), drainTexts())
        assertEquals(emptyList(), names())
    }

    @Test
    fun filesThatAreNotAShareAreDeletedWithoutASave() = runTest {
        writeText("$directory/000000000000001-garbage.json", "not json")
        writeText("$directory/000000000000002-empty.json", "")
        writeText("$directory/000000000000003-array.json", "[1,2]")
        writeText("$directory/000000000000004-no-date.json", """{"text":"https://no-date.example"}""")
        writeText("$directory/000000000000005-bad-date.json", """{"text":"https://bad-date.example","at":"yesterday"}""")
        writeText("$directory/000000000000006-torn.json", """{"text":"https://tor""")
        share("https://a.example", 7)
        assertEquals(listOf("https://a.example"), drainTexts())
        assertEquals(emptyList(), names())
    }

    @Test
    fun tempFilesOfAShareBeingWrittenAreIgnored() = runTest {
        writeText("$directory/.0F3A.tmp", json("https://half-written.example"))
        share("https://a.example", 1)
        assertEquals(listOf("https://a.example"), drainTexts())
        assertEquals(listOf(".0F3A.tmp"), names())
    }

    @Test
    fun anUnreadableFileIsLeftForTheNextDrain() = runTest {
        createDirectory("$directory/000000000000001-unreadable.json")
        share("https://a.example", 2)
        assertEquals(listOf("https://a.example"), drainTexts())
        assertEquals(listOf("000000000000001-unreadable.json"), names())
    }

    @Test
    fun aMissingOrEmptyInboxDeliversNothing() = runTest {
        assertEquals(emptyList<String?>(), drainTexts())
        assertEquals(emptyList<String?>(), AppGroupShareInbox(tempDirectory() + "/missing").let { missing ->
            mutableListOf<String?>().also { saved -> missing.drain { saved += it.text } }
        })
    }

    @Test
    fun deliversAThousandSharesInOrder() = runTest {
        (999 downTo 0).forEach { share("https://example.com/$it", 1_000L + it) }
        assertEquals((0 until 1_000).map { "https://example.com/$it" }, drainTexts())
        assertEquals(emptyList(), names())
    }

    @Test
    fun sharesInTheSameMillisecondKeepAStableOrder() = runTest {
        share("https://second.example", 7, uuid = "B0000000-0000-0000-0000-000000000000")
        share("https://first.example", 7, uuid = "A0000000-0000-0000-0000-000000000000")
        assertEquals(listOf("https://first.example", "https://second.example"), drainTexts())
    }

    @Test
    fun aShareWhoseSaveWasInterruptedByACrashIsDeliveredAgain() = runTest {
        share("https://a.example", 1)
        val b = share("https://b.example", 2)
        val c = share("https://c.example", 3)
        // The process "dies" while b is being saved: the drain never returns and never deletes b.
        coroutineScope {
            val drain = launch { inbox.drain { if (it.text == "https://b.example") awaitCancellation() } }
            launch {
                while (names() != listOf(b, c)) yield()
                drain.cancel()
            }
        }
        assertEquals(listOf("https://b.example", "https://c.example"), drainTexts())
    }

    @Test
    fun readsTheFileTheSwiftWriterProduces() = runTest {
        // iosApp/StashTests/Fixtures/inbox-entry.json is checked against InboxWriter's output by InboxWriterTests.
        val fixture = checkNotNull(readText(swiftFixture("inbox-entry.json")))
        writeText("$directory/001762941600123-A0000000-0000-0000-0000-000000000000.json", fixture)
        val payloads = mutableListOf<SharedPayload>()
        inbox.drain { payloads += it }
        assertEquals(
            listOf(SharedPayload("Read this https://example.com/a?b=1", "A title — “quoted” ✂️", null, Instant.parse("2025-11-12T10:00:00.123Z"))),
            payloads,
        )
    }

    @Test
    fun aDrainDeletesStaleTempFilesAndNamesThatAreNotShares() = runTest {
        writeText("$directory/.stale.tmp", json("https://killed-mid-write.example"))
        setModified("$directory/.stale.tmp", secondsAgo = AppGroupShareInbox.STALE_TEMP_SECONDS + 60)
        writeText("$directory/.fresh.tmp", json("https://being-written.example"))
        writeText("$directory/notes.txt", "not a share")
        share("https://a.example", 1)
        assertEquals(listOf("https://a.example"), drainTexts())
        assertEquals(listOf(".fresh.tmp"), names())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun listDirectory(path: String): List<String> =
    NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null).orEmpty().filterIsInstance<String>().sorted()

@OptIn(ExperimentalForeignApi::class)
private fun setModified(path: String, secondsAgo: Double) {
    NSFileManager.defaultManager.setAttributes(mapOf(NSFileModificationDate to NSDate.dateWithTimeIntervalSinceNow(-secondsAgo)), path, null)
}
