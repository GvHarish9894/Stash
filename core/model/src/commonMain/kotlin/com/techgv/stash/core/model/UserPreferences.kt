package com.techgv.stash.core.model

/** Every user setting plus the small persisted UI flags. Defaults match the prototype. */
data class UserPreferences(
    val theme: ThemeMode = ThemeMode.Auto,
    val accent: Accent = Accent.Leather,
    val cardLayout: CardLayout = CardLayout.Comfortable,
    val textSize: TextSize = TextSize.Medium,
    val reduceMotion: Boolean = false,
    val markReadOnOpen: Boolean = true,
    val readerModeDefault: Boolean = false,
    val openLinksIn: OpenLinksIn = OpenLinksIn.InApp,
    val dailyReminder: Boolean = true,
    val reminderTime: ReminderTime = ReminderTime.Morning,
    val streakSaver: Boolean = true,
    /** Swipe toward the start edge (left in LTR). */
    val swipeStartAction: SwipeAction = SwipeAction.Remove,
    /** Swipe toward the end edge (right in LTR). */
    val swipeEndAction: SwipeAction = SwipeAction.MarkRead,
    val sort: ShelfSort = ShelfSort.Newest,
    val group: ShelfGrouping = ShelfGrouping.Day,
    val autoArchive: AutoArchivePeriod = AutoArchivePeriod.Days30,
    val appIcon: AppIcon = AppIcon.Leather,
    val language: AppLanguage = AppLanguage.System,
    val widgetSize: WidgetSize = WidgetSize.Medium,
    val gestureTipDismissed: Boolean = false,
    /** Reader-mode text size step, 0..3. */
    val readerTextStep: Int = 1,
    /** The app asked for notification permission on its own, once (decision D27); a denial is never asked again. */
    val notificationPermissionAsked: Boolean = false,
)

enum class ThemeMode { Auto, Parchment, Night }
enum class Accent { Leather, Oxblood, Forest, InkBlue }
enum class CardLayout { Comfortable, Compact, LargeImages }
enum class TextSize { Small, Medium, Large }
enum class OpenLinksIn { InApp, Browser }

/** Daily reminder at 9 AM or 7 PM local time. */
enum class ReminderTime(val hour: Int) { Morning(9), Evening(19) }

/** The configurable shelf swipe actions. */
enum class SwipeAction { MarkRead, Archive, Share, Remove, Nothing }

enum class ShelfSort { Newest, Oldest, BySource }
enum class ShelfGrouping { Day, Week, Month, None }

enum class AutoArchivePeriod(val days: Int?) { Off(null), Days7(7), Days30(30), Days90(90) }

enum class AppIcon { Leather, Night, Parchment, Ink }

/** [tag] is the BCP 47 language tag; null follows the phone. */
enum class AppLanguage(val tag: String?) { System(null), English("en"), Spanish("es"), Hindi("hi"), Tamil("ta") }

enum class WidgetSize { Small, Medium }
