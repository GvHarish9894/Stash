package com.techgv.stash.model

/** A saved link on the shelf (or in the archive). Colours are ARGB longs. */
data class Item(
    val id: String,
    val title: String,
    val site: String,
    val src: String,
    val meta: String,
    val tile: Long,
    val mono: String,
    val ink: Long = 0xFFF4ECD8,
    val desc: String = "Saved from your share sheet.",
    val daysAgo: Int = 0,
    val read: Boolean = false,
    val readAgo: Int = 0,
    val archived: Boolean = false,
    val archLabel: String = "",
    val archMonth: String = "",
    val removed: Boolean = false,
)

enum class Screen { Home, Search, Archive, Settings, Widget, Reader, Save }
enum class ThemeMode(val label: String) { Auto("Auto"), Parchment("Parchment"), Night("Night library") }
enum class Accent(val label: String) { Leather("Leather"), Oxblood("Oxblood"), Forest("Forest"), Ink("Ink blue") }
enum class CardLayout(val label: String) { Comfortable("Comfortable"), Compact("Compact"), Large("Large images") }
enum class TextSize { S, M, L }
enum class OpenIn(val label: String) { InApp("In-app"), Browser("Browser") }
enum class RemindTime(val label: String) { Morning("9 AM"), Evening("7 PM") }
enum class SortBy(val label: String) { Newest("Newest"), Oldest("Oldest"), Source("By source") }
enum class GroupBy(val label: String) { Day("Day"), Week("Week"), Month("Month"), None("None") }
enum class AutoArchive(val label: String, val days: Int) { Off("Off", 0), D7("7 days", 7), D30("30 days", 30), D90("90 days", 90) }
enum class WidgetSize(val label: String) { Small("Small 2×2"), Medium("Medium 4×2") }
enum class Lang(val label: String) { Auto("Phone"), En("English"), Es("Español"), Hi("हिन्दी"), Ta("தமிழ்") }

enum class AppIcon(val label: String, val bg: Long, val fg: Long, val bd: Long) {
    Leather("Leather", 0xFF7A4E2D, 0xFFFBF6EA, 0xFF5E3A20),
    Night("Night", 0xFF1E1812, 0xFFE0B45C, 0xFF3D3226),
    Parchment("Parchment", 0xFFF4ECD8, 0xFF7A4E2D, 0xFFCBB894),
    Ink("Ink", 0xFF2F4A6B, 0xFFF4ECD8, 0xFF1F3550),
}

/** What a swipe does. Restore and Delete only happen in the archive. */
enum class SwipeAction(val label: String) {
    Read("Mark read"), Archive("Archive"), Share("Share"), Remove("Remove"), None("Nothing"),
    Restore("Restore"), Delete("Delete"),
}

enum class UndoKind(val text: String) {
    Remove("Removed from your shelf"), Archive("Moved to Archive"),
    Restore("Restored to your shelf"), Delete("Deleted from Archive"),
}

data class Prefs(
    val theme: ThemeMode = ThemeMode.Auto,
    val accent: Accent = Accent.Leather,
    val cardLayout: CardLayout = CardLayout.Comfortable,
    val textSize: TextSize = TextSize.M,
    val calm: Boolean = false,
    val autoRead: Boolean = true,
    val readerDefault: Boolean = false,
    val openIn: OpenIn = OpenIn.InApp,
    val remind: Boolean = true,
    val remindTime: RemindTime = RemindTime.Morning,
    val saver: Boolean = true,
    val swipeR: SwipeAction = SwipeAction.Read,
    val swipeL: SwipeAction = SwipeAction.Remove,
    val sortBy: SortBy = SortBy.Newest,
    val groupBy: GroupBy = GroupBy.Day,
    val autoArchive: AutoArchive = AutoArchive.D30,
    val appIcon: AppIcon = AppIcon.Leather,
    val lang: Lang = Lang.Auto,
    val widgetSize: WidgetSize = WidgetSize.Medium,
)

/** A page "shared" to Stash in the share demo. */
data class ShareSample(val title: String, val site: String, val src: String, val tile: Long, val mono: String, val meta: String)

val shareSamples = listOf(
    ShareSample("The quiet return of serif type on screens", "example-blog.com", "example-blog", 0xFF6E5D49, "T", "just now · 5 min read"),
    ShareSample("Kotlin coroutines: structured concurrency in practice", "medium.com", "Medium", 0xFF7A4E2D, "K", "just now · 12 min read"),
    ShareSample("Building a reading list app in a weekend", "youtube.com", "YouTube", 0xFF3F5E4A, "▶", "just now · 21 min video"),
)

// shortcut: in-memory demo shelf from the design; replace with persisted storage when the data layer lands.
val sampleItems = listOf(
    Item("a", "Designing for slow reading in a fast feed", "medium.com", "Medium", "2h ago · 7 min read", 0xFF7A4E2D, "D",
        desc = "Why the fastest feeds leave the least behind, and what calm interfaces do differently.", daysAgo = 0),
    Item("b", "Shared element transitions in Compose, explained", "youtube.com", "YouTube", "4h ago · 18 min video", 0xFF3F5E4A, "▶",
        desc = "A walkthrough of SharedTransitionLayout and sharedBounds, with the pitfalls to avoid.", daysAgo = 0),
    Item("c", "Room full-text search on Android: FTS4 vs FTS5", "dev.to", "dev.to", "6 min read", 0xFFB8893B, "R", ink = 0xFF3B2F23,
        desc = "When the newer engine is worth it, and when FTS4 is all a mobile app needs.", daysAgo = 1),
    Item("d", "Spring physics for Compose micro-interactions", "youtube.com", "YouTube", "11 min video", 0xFF8B2E2A, "▶",
        desc = "Tuning damping and stiffness so interactions feel physical without feeling slow.", daysAgo = 1),
    Item("e", "paper-grain: tileable textures for app backgrounds", "github.com", "GitHub", "repository", 0xFF3B2F23, "P",
        desc = "Seamless paper and grain textures sized for mobile backgrounds.", daysAgo = 5, read = true, readAgo = 12),
    Item("f", "Why we stopped shipping infinite scroll", "medium.com", "Medium", "9 min read", 0xFF5E3A20, "W",
        desc = "What happened to engagement and comprehension when a team gave its feed an ending.", daysAgo = 5),
    Item("g", "Material 3 colour roles, explained", "medium.com", "Medium", "8 min read", 0xFF2F4A6B, "M",
        desc = "How Material 3 assigns colour roles across light and dark themes.", daysAgo = 9, read = true,
        archived = true, archLabel = "Archived Nov 2", archMonth = "This month"),
    Item("h", "A field guide to WorkManager constraints", "dev.to", "dev.to", "10 min read", 0xFFB8893B, "W", ink = 0xFF3B2F23,
        desc = "Practical patterns for network, battery and idle constraints.", daysAgo = 24, read = true,
        archived = true, archLabel = "Archived Oct 21", archMonth = "Earlier"),
    Item("i", "Designing calm notifications", "youtube.com", "YouTube", "14 min video", 0xFF3F5E4A, "▶",
        desc = "How to design notifications people keep switched on.", daysAgo = 45, read = true,
        archived = true, archLabel = "Archived Sep 30", archMonth = "Earlier"),
)
