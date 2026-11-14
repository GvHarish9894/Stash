package com.techgv.stash.core.designsystem.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.ItemMenuAction
import com.techgv.stash.core.designsystem.component.StreakSheetStatus
import com.techgv.stash.core.designsystem.component.itemMenuActions
import com.techgv.stash.core.designsystem.component.streakSheetStatus
import com.techgv.stash.core.designsystem.component.swipeDisplacement
import com.techgv.stash.core.designsystem.component.swipeOutcomeOnRelease
import com.techgv.stash.core.designsystem.widthClassOf
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.GroupLabel
import com.techgv.stash.core.domain.SearchFilter
import com.techgv.stash.core.domain.ShelfFilter
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.OperationType
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.auto_archive_days
import com.techgv.stash.core.resources.auto_archive_off
import com.techgv.stash.core.resources.card_archived_at
import com.techgv.stash.core.resources.card_minutes_read
import com.techgv.stash.core.resources.card_minutes_video
import com.techgv.stash.core.resources.card_repository
import com.techgv.stash.core.resources.card_video
import com.techgv.stash.core.resources.celebration_message_3
import com.techgv.stash.core.resources.celebration_milestone_7
import com.techgv.stash.core.resources.chip_all
import com.techgv.stash.core.resources.chip_archived
import com.techgv.stash.core.resources.chip_unread
import com.techgv.stash.core.resources.group_earlier
import com.techgv.stash.core.resources.group_last_week
import com.techgv.stash.core.resources.group_month_year
import com.techgv.stash.core.resources.group_this_month
import com.techgv.stash.core.resources.group_this_week
import com.techgv.stash.core.resources.group_today
import com.techgv.stash.core.resources.group_yesterday
import com.techgv.stash.core.resources.month_march
import com.techgv.stash.core.resources.month_short_november
import com.techgv.stash.core.resources.time_date
import com.techgv.stash.core.resources.time_date_year
import com.techgv.stash.core.resources.time_days_long
import com.techgv.stash.core.resources.time_days_short
import com.techgv.stash.core.resources.time_hours_long
import com.techgv.stash.core.resources.time_hours_short
import com.techgv.stash.core.resources.time_just_now
import com.techgv.stash.core.resources.time_minutes_long
import com.techgv.stash.core.resources.time_minutes_short
import com.techgv.stash.core.resources.tip_both
import com.techgv.stash.core.resources.tip_left
import com.techgv.stash.core.resources.tip_none
import com.techgv.stash.core.resources.tip_right
import com.techgv.stash.core.resources.tip_verb_archive
import com.techgv.stash.core.resources.tip_verb_mark_read
import com.techgv.stash.core.resources.tip_verb_remove
import com.techgv.stash.core.resources.undo_archived
import com.techgv.stash.core.resources.undo_removed
import com.techgv.stash.core.resources.weekday_tuesday
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

val NOW: Instant = Instant.parse("2026-11-12T15:00:00Z")

fun savedItem(
    title: String = "Designing for slow reading in a fast feed",
    site: String = "medium.com",
    source: String = "Medium",
    type: ContentType = ContentType.Article,
    minutes: Int? = 7,
    age: Duration = 2.hours,
    readAt: Instant? = null,
    archivedAt: Instant? = null,
    fetchStatus: FetchStatus = FetchStatus.Fetched,
    imageUrl: String? = null,
) = SavedItem(
    id = ItemId("a"), url = "https://$site/slow-reading", normalizedUrl = "https://$site/slow-reading", title = title,
    description = null, imageUrl = imageUrl, faviconUrl = null, siteName = site, domain = site, sourceKey = source,
    contentType = type, sourceApp = null, readingMinutes = minutes, addedAt = NOW - age, readAt = readAt,
    archivedAt = archivedAt, deletedAt = null, fetchStatus = fetchStatus, updatedAt = NOW,
)

class SavedItemPresentationTest {
    private val utc = TimeZone.UTC

    private fun ago(d: Duration) = relativeTime(NOW - d, NOW, utc)

    @Test
    fun relativeTimeBoundaries() {
        assertEquals(RelativeTime.JustNow, ago(0.seconds))
        assertEquals(RelativeTime.JustNow, ago(59.seconds))
        assertEquals(RelativeTime.JustNow, ago((-5).minutes), "a future time counts as just now")
        assertEquals(RelativeTime.Minutes(1), ago(60.seconds))
        assertEquals(RelativeTime.Minutes(59), ago(59.minutes + 59.seconds))
        assertEquals(RelativeTime.Hours(1), ago(60.minutes))
        assertEquals(RelativeTime.Hours(23), ago(23.hours + 59.minutes))
        assertEquals(RelativeTime.Days(1), ago(24.hours))
        assertEquals(RelativeTime.Days(6), ago(6.days + 23.hours))
        assertEquals(RelativeTime.OnDate(LocalDate(2026, 11, 5), showYear = false), ago(7.days))
        assertEquals(RelativeTime.OnDate(LocalDate(2025, 12, 31), showYear = true), relativeTime(Instant.parse("2025-12-31T23:00:00Z"), NOW, utc))
    }

    @Test
    fun relativeTimeShortAndSpokenForms() {
        assertEquals(text(Res.string.time_just_now), RelativeTime.JustNow.short())
        assertEquals(text(Res.string.time_minutes_short, 5), RelativeTime.Minutes(5).short())
        assertEquals(text(Res.string.time_hours_short, 2), RelativeTime.Hours(2).short())
        assertEquals(text(Res.string.time_days_short, 3), RelativeTime.Days(3).short())
        assertEquals(plural(Res.plurals.time_minutes_long, 1), RelativeTime.Minutes(1).long())
        assertEquals(plural(Res.plurals.time_hours_long, 2), RelativeTime.Hours(2).long())
        assertEquals(plural(Res.plurals.time_days_long, 6), RelativeTime.Days(6).long())
        val nov = shortMonthName(Month.NOVEMBER)
        assertEquals(text(Res.string.month_short_november), nov)
        assertEquals(text(Res.string.time_date, nov, 2), RelativeTime.OnDate(LocalDate(2026, 11, 2), false).short())
        assertEquals(text(Res.string.time_date_year, nov, 2, 2025), RelativeTime.OnDate(LocalDate(2025, 11, 2), true).long())
    }

    @Test
    fun lengthByContentType() {
        assertEquals(text(Res.string.card_minutes_read, 7), lengthText(ContentType.Article, 7))
        assertNull(lengthText(ContentType.Article, null))
        assertEquals(text(Res.string.card_minutes_video, 18), lengthText(ContentType.Video, 18))
        assertEquals(text(Res.string.card_video), lengthText(ContentType.Video, null))
        assertEquals(text(Res.string.card_repository), lengthText(ContentType.Repository, 3))
    }

    @Test
    fun tintIsStablePerSourceAndCaseInsensitive() {
        // Pinned values: the same on every platform this test runs on, and across releases.
        assertEquals(listOf(1, 3, 5, 6), listOf("Medium", "YouTube", "dev.to", "GitHub").map(::tileIndex))
        assertEquals(tileIndex("medium"), tileIndex("MEDIUM"))
        assertEquals(tileTint("Medium"), tileTint("medium"))
        assertEquals(TilePalette[1], tileTint("Medium"))
        assertEquals(Color(0xFF3B2F23), monogramInk(Color(0xFFB8893B)), "brass tiles take dark ink")
        assertEquals(Color(0xFFF4ECD8), monogramInk(TilePalette[0]))
    }

    @Test
    fun monogramIsSiteInitialOrPlay() {
        assertEquals("M", monogram("medium.com", "medium.com", ContentType.Article))
        assertEquals("E", monogram("www.example.com", "example.com", ContentType.Article))
        assertEquals("D", monogram("", "dev.to", ContentType.Repository))
        assertEquals(PLAY_MONOGRAM, monogram("youtube.com", "youtube.com", ContentType.Video))
    }

    @Test
    fun lifecycleAndMetadataAreSeparate() {
        assertEquals(CardLifecycle.Unread, cardLifecycle(ItemLifecycle.Unread))
        assertEquals(CardLifecycle.Read, cardLifecycle(ItemLifecycle.Read))
        assertEquals(CardLifecycle.Archived, cardLifecycle(ItemLifecycle.Archived))
        assertEquals(MetadataState.Ready, metadataState(FetchStatus.Fetched))
        assertEquals(MetadataState.Pending, metadataState(FetchStatus.Pending))
        assertEquals(MetadataState.Failed, metadataState(FetchStatus.Failed))
    }

    @Test
    fun archivedWithFailedDetailsKeepsTheArchiveActionsAndDate() {
        val card = savedItem(age = 20.days, readAt = NOW - 15.days, archivedAt = NOW - 10.days, fetchStatus = FetchStatus.Failed)
            .toCardUi(NOW, utc)
        assertEquals(CardLifecycle.Archived, card.lifecycle)
        assertEquals(MetadataState.Failed, card.metadata)
        assertEquals(listOf(SwipeOutcome.Restore, SwipeOutcome.Share, SwipeOutcome.Delete), cardActions(card))
        assertEquals(ItemMenuAction.Restore, itemMenuActions(card).first())
        assertEquals(ItemMenuAction.Delete, itemMenuActions(card).last())
        assertEquals(text(Res.string.card_archived_at, RelativeTime.OnDate(LocalDate(2026, 11, 2), false).short()), card.time)
        assertEquals("https://medium.com/slow-reading", card.title)
    }

    @Test
    fun readWithPendingDetailsStaysRead() {
        val card = savedItem(readAt = NOW - 1.hours, fetchStatus = FetchStatus.Pending).toCardUi(NOW, utc)
        assertEquals(CardLifecycle.Read, card.lifecycle)
        assertEquals(MetadataState.Pending, card.metadata)
        assertEquals(SwipeOutcome.MarkUnread, cardActions(card).first())
        assertEquals(ItemMenuAction.MarkUnread, itemMenuActions(card).first())
        val label = cardAccessibilityLabel(card, UiText.Plain(", ")) as UiText.Res
        assertEquals(lifecycleText(CardLifecycle.Read), label.args[0])
        assertEquals<Any?>(metadataText(MetadataState.Pending), label.args[1])
    }

    @Test
    fun archivedWithPendingDetailsKeepsTheArchiveActions() {
        val card = savedItem(age = 3.days, readAt = null, archivedAt = NOW - 1.days, fetchStatus = FetchStatus.Pending).toCardUi(NOW, utc)
        assertEquals(CardLifecycle.Archived, card.lifecycle)
        assertEquals(MetadataState.Pending, card.metadata)
        assertEquals(true, card.unread)
        assertEquals(listOf(SwipeOutcome.Restore, SwipeOutcome.Share, SwipeOutcome.Delete), cardActions(card))
        assertEquals(text(Res.string.card_archived_at, RelativeTime.Days(1).short()), card.time)
    }

    @Test
    fun cardImageIsHttpsOnly() {
        assertEquals("https://cdn.example/a.jpg", savedItem(imageUrl = "https://cdn.example/a.jpg").toCardUi(NOW, utc).imageUrl)
        listOf(null, "", "  ", "http://cdn.example/a.jpg", "data:image/png;base64,AAAA", "file:///a.jpg").forEach {
            assertNull(savedItem(imageUrl = it).toCardUi(NOW, utc).imageUrl, "image $it")
        }
    }

    @Test
    fun mapsAnUnreadArticle() {
        val card = savedItem().toCardUi(NOW, utc)
        assertEquals("a", card.id)
        assertEquals("Designing for slow reading in a fast feed", card.title)
        assertEquals("medium.com", card.siteName)
        assertEquals("M", card.monogram)
        assertEquals(TilePalette[1], card.tint)
        assertEquals(text(Res.string.time_hours_short, 2), card.time)
        assertEquals(plural(Res.plurals.time_hours_long, 2), card.timeSpoken)
        assertEquals(text(Res.string.card_minutes_read, 7), card.length)
        assertEquals(CardLifecycle.Unread, card.lifecycle)
        assertEquals(MetadataState.Ready, card.metadata)
        assertEquals(true, card.unread)
    }

    @Test
    fun archivedShowsWhenItWasArchived() {
        val card = savedItem(age = 20.days, readAt = NOW - 15.days, archivedAt = NOW - 10.days).toCardUi(NOW, utc)
        assertEquals(CardLifecycle.Archived, card.lifecycle)
        assertEquals(false, card.unread)
        val date = RelativeTime.OnDate(LocalDate(2026, 11, 2), false)
        assertEquals(text(Res.string.card_archived_at, date.short()), card.time)
        assertEquals(text(Res.string.card_archived_at, date.long()), card.timeSpoken)
    }

    @Test
    fun failedShowsTheUrlAndNoLength() {
        val card = savedItem(title = "Provisional", fetchStatus = FetchStatus.Failed).toCardUi(NOW, utc)
        assertEquals(MetadataState.Failed, card.metadata)
        assertEquals("https://medium.com/slow-reading", card.title)
        assertNull(card.length)
        val pending = savedItem(title = "", fetchStatus = FetchStatus.Pending).toCardUi(NOW, utc)
        assertEquals(MetadataState.Pending, pending.metadata)
        assertEquals("https://medium.com/slow-reading", pending.title, "a pending item without a title shows the URL")
    }

    @Test
    fun accessibilityLabelAndActions() {
        val card = savedItem().toCardUi(NOW, utc)
        val separator = UiText.Plain(", ")
        val label = cardAccessibilityLabel(card, separator) as UiText.Res
        assertEquals(lifecycleText(CardLifecycle.Unread), label.args[0])
        assertEquals(UiText.Plain(card.title), label.args[1])
        assertEquals(UiText.Joined(listOf(UiText.Plain("medium.com"), card.timeSpoken!!, card.length!!), separator), label.args[2])
        assertEquals(listOf(SwipeOutcome.MarkRead, SwipeOutcome.Archive, SwipeOutcome.Share, SwipeOutcome.Remove), cardActions(card))
        assertEquals(SwipeOutcome.MarkUnread, cardActions(card.copy(lifecycle = CardLifecycle.Read, unread = false)).first())
        assertEquals(listOf(SwipeOutcome.Restore, SwipeOutcome.Share, SwipeOutcome.Delete), cardActions(card.copy(lifecycle = CardLifecycle.Archived)))
        assertEquals(ItemMenuAction.MarkRead, itemMenuActions(card).first())
        assertEquals(
            listOf(ItemMenuAction.Restore, ItemMenuAction.Open, ItemMenuAction.Share, ItemMenuAction.CopyLink, ItemMenuAction.Delete),
            itemMenuActions(card.copy(lifecycle = CardLifecycle.Archived)),
        )
    }

    @Test
    fun everyGroupLabelHasText() {
        assertEquals(text(Res.string.group_today), groupLabelText(GroupLabel.Today, 2026))
        assertEquals(text(Res.string.group_yesterday), groupLabelText(GroupLabel.Yesterday, 2026))
        assertEquals(text(Res.string.weekday_tuesday), groupLabelText(GroupLabel.Weekday(DayOfWeek.TUESDAY), 2026))
        assertEquals(text(Res.string.group_this_week), groupLabelText(GroupLabel.ThisWeek, 2026))
        assertEquals(text(Res.string.group_last_week), groupLabelText(GroupLabel.LastWeek, 2026))
        assertEquals(text(Res.string.group_earlier), groupLabelText(GroupLabel.Earlier, 2026))
        assertEquals(text(Res.string.group_this_month), groupLabelText(GroupLabel.ThisMonth, 2026))
        assertEquals(text(Res.string.month_march), groupLabelText(GroupLabel.Month(2026, Month.MARCH), 2026))
        assertEquals(
            text(Res.string.group_month_year, text(Res.string.month_march), 2025),
            groupLabelText(GroupLabel.Month(2025, Month.MARCH), 2026),
        )
        assertEquals(UiText.Plain("YouTube"), groupLabelText(GroupLabel.Source("YouTube"), 2026))
        // Every weekday and month resolves to a distinct resource.
        assertEquals(7, DayOfWeek.entries.map(::weekdayName).toSet().size)
        assertEquals(12, Month.entries.map(::monthName).toSet().size)
    }

    @Test
    fun chipLabels() {
        assertEquals(text(Res.string.chip_all), shelfFilterLabel(ShelfFilter.All))
        assertEquals(text(Res.string.chip_unread), shelfFilterLabel(ShelfFilter.Unread))
        assertEquals(UiText.Plain("Medium"), shelfFilterLabel(ShelfFilter.Source("Medium")))
        assertEquals(text(Res.string.chip_archived), searchFilterLabel(SearchFilter.Archived))
    }

    @Test
    fun pluralsCarryTheCount() {
        assertEquals(UiText.Plural(Res.plurals.auto_archive_days, 7, listOf(7)), AutoArchivePeriod.Days7.label)
        assertEquals(plural(Res.plurals.auto_archive_days, 30), AutoArchivePeriod.Days30.label)
        assertEquals(text(Res.string.auto_archive_off), AutoArchivePeriod.Off.label)
    }

    @Test
    fun undoTipAndCelebrationCopy() {
        assertEquals(Res.string.undo_removed, undoMessage(OperationType.Remove))
        assertEquals(Res.string.undo_archived, undoMessage(OperationType.AutoArchive))
        assertNull(undoMessage(OperationType.MarkRead))
        assertEquals(
            text(Res.string.tip_both, text(Res.string.tip_verb_remove), text(Res.string.tip_verb_mark_read)),
            gestureTipText(SwipeAction.Remove, SwipeAction.MarkRead, LayoutDirection.Ltr),
        )
        assertEquals(
            text(Res.string.tip_both, text(Res.string.tip_verb_mark_read), text(Res.string.tip_verb_remove)),
            gestureTipText(SwipeAction.Remove, SwipeAction.MarkRead, LayoutDirection.Rtl),
            "in RTL the start swipe is the right one",
        )
        assertEquals(text(Res.string.tip_left, text(Res.string.tip_verb_archive)), gestureTipText(SwipeAction.Archive, SwipeAction.Nothing, LayoutDirection.Ltr))
        assertEquals(text(Res.string.tip_right, text(Res.string.tip_verb_archive)), gestureTipText(SwipeAction.Archive, SwipeAction.Nothing, LayoutDirection.Rtl))
        assertEquals(text(Res.string.tip_right, text(Res.string.tip_verb_archive)), gestureTipText(SwipeAction.Nothing, SwipeAction.Archive, LayoutDirection.Ltr))
        assertEquals(text(Res.string.tip_none), gestureTipText(SwipeAction.Nothing, SwipeAction.Nothing, LayoutDirection.Ltr))
        assertEquals(Res.string.celebration_milestone_7, celebrationMessage(Celebration(6, 7, 7)))
        assertEquals(Res.string.celebration_message_3, celebrationMessage(Celebration(11, 12, null)), "12 mod 5 picks the third message")
    }

    @Test
    fun swipeThresholdAndRubberBand() {
        val threshold = 110f
        assertEquals(SwipeOutcome.MarkRead, swipeOutcomeOnRelease(110f, threshold, SwipeOutcome.MarkRead), "handoff: release at ≥ 110 dp acts")
        assertNull(swipeOutcomeOnRelease(109.9f, threshold, SwipeOutcome.MarkRead), "just short of the threshold springs back")
        assertEquals(SwipeOutcome.Delete, swipeOutcomeOnRelease(-110f, threshold, SwipeOutcome.Delete))
        assertNull(swipeOutcomeOnRelease(500f, threshold, SwipeOutcome.RubberBand))
        assertEquals(SwipeOutcome.Remove, swipeOutcomeOnRelease(-200f, threshold, SwipeOutcome.Remove))
        assertNull(swipeOutcomeOnRelease(300f, threshold, null))
        assertEquals(100f, swipeDisplacement(100f, hasAction = true))
        assertEquals(22f, swipeDisplacement(100f, hasAction = false), 0.001f)
    }

    @Test
    fun widthClassesAndStreakStatus() {
        assertEquals(WidthClass.Compact, widthClassOf(599.dp))
        assertEquals(WidthClass.Medium, widthClassOf(600.dp))
        assertEquals(WidthClass.Medium, widthClassOf(839.dp))
        assertEquals(WidthClass.Expanded, widthClassOf(840.dp))
        assertEquals(StreakSheetStatus.DoneToday, streakSheetStatus(readToday = true, hasUnread = true))
        assertEquals(StreakSheetStatus.NotYet, streakSheetStatus(readToday = false, hasUnread = true))
        assertEquals(StreakSheetStatus.NothingUnread, streakSheetStatus(readToday = false, hasUnread = false))
    }

    @Test
    fun veryLongSiteNamesAreShortenedInTheMiddle() {
        assertEquals("medium.com", middleEllipsis("medium.com"))
        val long = "blog.averyveryveryverylongdomainnameforsure.com"
        val short = middleEllipsis(long)
        assertEquals(40, short.length)
        assertTrue(short.startsWith("blog.averyvery"))
        assertTrue(short.endsWith("….com"))
        assertEquals(40, middleEllipsis("x".repeat(60)).length)
    }
}
