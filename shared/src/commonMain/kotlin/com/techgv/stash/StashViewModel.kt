package com.techgv.stash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techgv.stash.model.AutoArchive
import com.techgv.stash.model.Item
import com.techgv.stash.model.OpenIn
import com.techgv.stash.model.Prefs
import com.techgv.stash.model.RemindTime
import com.techgv.stash.model.Screen
import com.techgv.stash.model.ShareSample
import com.techgv.stash.model.SwipeAction
import com.techgv.stash.model.UndoKind
import com.techgv.stash.model.sampleItems
import com.techgv.stash.model.shareSamples
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Notif(val time: String, val title: String, val body: String)
data class Undo(val id: String, val kind: UndoKind, val stamp: Int)

data class StashState(
    val items: List<Item> = sampleItems,
    val prefs: Prefs = Prefs(),
    val screen: Screen = Screen.Home,
    val returnTo: Screen = Screen.Home,
    val filter: String = "all",
    val query: String = "",
    val sFilter: String = "all",
    val recent: List<String> = listOf("compose", "room", "typography"),
    val archQuery: String = "",
    val undo: Undo? = null,
    val addOpen: Boolean = false,
    val addUrl: String = "",
    val addError: String = "",
    val readerId: String? = null,
    val readerToast: String = "",
    val readerClean: Boolean = false,
    val readerFs: Int = 1,
    val menuId: String? = null,
    val hinted: Boolean = false,
    val homeToast: String = "",
    val settingsToast: String = "",
    val streak: Int = 12,
    val oldStreak: Int = 12,
    val readToday: Boolean = false,
    val nudgeLevel: Int = 1,
    val lastPreview: Int? = null,
    val notif: Notif? = null,
    val celebrate: Boolean = false,
    val celeMsg: String = "",
    val streakOpen: Boolean = false,
    val clearConfirm: Boolean = false,
    val archPending: AutoArchive? = null,
    val saveItem: ShareSample? = null,
    val firstLoad: Boolean = true,
    /** Item whose read strike-through should animate in. */
    val drawId: String? = null,
    /** Item that just landed on the shelf (flutter-in). */
    val addedId: String? = null,
) {
    fun find(id: String?) = items.firstOrNull { it.id == id }
}

class StashViewModel : ViewModel() {
    var state by mutableStateOf(StashState())
        private set

    private var nextId = 100
    private var shareIdx = 0
    private var undoStamp = 0
    private var pendingCele = false
    private var pendingDraw = false
    private var pendingSave: ShareSample? = null
    private val jobs = mutableMapOf<String, Job>()

    init {
        later("firstLoad", 1500) { set { copy(firstLoad = false) } }
    }

    private inline fun set(f: StashState.() -> StashState) {
        state = state.f()
    }

    /** Runs [block] after [ms]; a new call with the same [key] replaces the pending one. */
    private fun later(key: String, ms: Long, block: () -> Unit) {
        jobs[key]?.cancel()
        jobs[key] = viewModelScope.launch {
            delay(ms)
            block()
        }
    }

    private fun patch(id: String, f: Item.() -> Item) =
        set { copy(items = items.map { if (it.id == id) it.f() else it }) }

    // ---- navigation ------------------------------------------------------

    fun go(screen: Screen) = set { copy(screen = screen, firstLoad = false) }

    fun openSearch() = set { copy(screen = Screen.Search, query = "", sFilter = "all", firstLoad = false) }
    fun closeSearch() {
        pushRecent(state.query)
        set { copy(screen = Screen.Home, query = "") }
    }

    fun openSettings() = go(Screen.Settings)

    fun openArchive() = set { copy(screen = Screen.Archive, archQuery = "", firstLoad = false) }
    fun closeArchive() = set { copy(screen = Screen.Home, archQuery = "") }

    /** System back. Returns false when there is nothing left to close. */
    fun back(): Boolean {
        val s = state
        when {
            s.celebrate -> set { copy(celebrate = false) }
            s.clearConfirm -> set { copy(clearConfirm = false) }
            s.archPending != null -> set { copy(archPending = null) }
            s.menuId != null -> closeMenu()
            s.addOpen -> closeAdd()
            s.streakOpen -> set { copy(streakOpen = false) }
            s.notif != null -> hideNotif()
            s.screen == Screen.Reader -> closeReader()
            s.screen == Screen.Save -> finishSave()
            s.screen == Screen.Search -> closeSearch()
            s.screen == Screen.Widget -> go(Screen.Settings)
            s.screen == Screen.Settings || s.screen == Screen.Archive -> closeArchive()
            else -> return false
        }
        return true
    }

    // ---- shelf -----------------------------------------------------------

    fun setFilter(key: String) = set { copy(filter = key, firstLoad = false) }
    fun dismissHint() = set { copy(hinted = true) }

    fun toast(text: String) {
        set { copy(homeToast = text) }
        later("homeToast", 1600) { if (state.homeToast == text) set { copy(homeToast = "") } }
    }

    fun settingsToast(text: String) {
        set { copy(settingsToast = text) }
        later("settingsToast", 1800) { if (state.settingsToast == text) set { copy(settingsToast = "") } }
    }

    private fun drawStrike(id: String) {
        set { copy(drawId = id) }
        later("draw", 900) { set { copy(drawId = null) } }
    }

    fun toggleRead(id: String) {
        val it = state.find(id) ?: return
        if (it.read) {
            patch(id) { copy(read = false) }
            return
        }
        patch(id) { copy(read = true) }
        drawStrike(id)
        noteRead(defer = false)
    }

    private fun noteRead(defer: Boolean) {
        if (state.readToday) return
        set { copy(readToday = true, oldStreak = streak, streak = streak + 1) }
        pendingCele = true
        if (state.notif != null) hideNotif()
        if (!defer) later("cele", 800) { showCele() }
    }

    private fun showCele() {
        if (!pendingCele) return
        pendingCele = false
        val n = state.streak
        val milestones = mapOf(
            7 to "A full week of reading. That is a habit taking root.",
            14 to "Two weeks straight. Your shelf has noticed.",
            30 to "Thirty days. Reading is part of your day now.",
            50 to "Fifty days of showing up. Remarkable.",
            100 to "One hundred days. You have read your way into a habit.",
        )
        val pool = listOf(
            "One page at a time. That is how shelves get read.",
            "You showed up today. That is the whole trick.",
            "Small reads, every day. Your future self is taking notes.",
            "Another candle lit. Keep the flame going tomorrow.",
            "Today’s read is in the books.",
        )
        set { copy(celebrate = true, celeMsg = milestones[n] ?: pool[n % pool.size]) }
    }

    fun closeCelebration() = set { copy(celebrate = false) }

    /** Applies a completed swipe. Remove/Archive/Restore/Delete arrive after the card has flown off. */
    fun swipe(id: String, action: SwipeAction) {
        set { copy(hinted = true) }
        when (action) {
            SwipeAction.Read -> toggleRead(id)
            SwipeAction.Share -> toast("Share sheet opened")
            SwipeAction.Remove -> change(id, UndoKind.Remove) { copy(removed = true) }
            SwipeAction.Delete -> change(id, UndoKind.Delete) { copy(removed = true) }
            SwipeAction.Archive -> change(id, UndoKind.Archive) { archivedNow() }
            SwipeAction.Restore -> change(id, UndoKind.Restore) { copy(archived = false) }
            SwipeAction.None -> Unit
        }
    }

    private fun Item.archivedNow() = copy(archived = true, archLabel = "Archived just now", archMonth = "This month")

    private fun change(id: String, kind: UndoKind, f: Item.() -> Item) {
        patch(id, f)
        val stamp = ++undoStamp
        set { copy(undo = Undo(id, kind, stamp)) }
        later("undo", 10_000) { if (state.undo?.stamp == stamp) set { copy(undo = null) } }
    }

    fun archive(id: String) = change(id, UndoKind.Archive) { archivedNow() }
    fun restore(id: String) = change(id, UndoKind.Restore) { copy(archived = false) }
    fun remove(id: String) {
        val wasArch = state.find(id)?.archived == true
        change(id, if (wasArch) UndoKind.Delete else UndoKind.Remove) { copy(removed = true) }
    }

    fun undo() {
        val u = state.undo ?: return
        when (u.kind) {
            UndoKind.Archive -> patch(u.id) { copy(archived = false) }
            UndoKind.Restore -> patch(u.id) { copy(archived = true) }
            UndoKind.Remove, UndoKind.Delete -> patch(u.id) { copy(removed = false) }
        }
        set { copy(undo = null) }
    }

    fun dismissUndo() = set { copy(undo = null) }

    fun openMenu(id: String) = set { copy(menuId = id, hinted = true) }
    fun closeMenu() = set { copy(menuId = null) }

    // ---- reading ---------------------------------------------------------

    fun open(id: String) {
        val it = state.find(id) ?: return
        val wasUnread = !it.read && state.prefs.autoRead
        if (state.prefs.openIn == OpenIn.Browser) {
            noteRead(defer = false)
            if (wasUnread) {
                patch(id) { copy(read = true) }
                drawStrike(id)
            }
            toast("Opened in your browser")
            return
        }
        if (wasUnread) patch(id) { copy(read = true) }
        noteRead(defer = true)
        if (state.screen == Screen.Search) pushRecent(state.query)
        val from = state.screen
        set {
            copy(
                returnTo = if (from == Screen.Search || from == Screen.Archive) from else Screen.Home,
                screen = Screen.Reader,
                readerClean = prefs.readerDefault,
                readerId = id,
                readerToast = if (wasUnread) "Marked as read" else "",
                firstLoad = false,
            )
        }
        later("readerToast", 2200) { set { copy(readerToast = "") } }
        pendingDraw = wasUnread
    }

    fun closeReader() {
        val id = state.readerId
        set { copy(screen = returnTo) }
        if (pendingDraw && id != null) drawStrike(id)
        pendingDraw = false
        if (pendingCele) later("cele", 650) { showCele() }
    }

    fun readerUnread() {
        state.readerId?.let { id -> patch(id) { copy(read = false) } }
        pendingDraw = false
        set { copy(readerToast = "Back on your unread list") }
        later("readerToast", 1800) { set { copy(readerToast = "") } }
    }

    fun toggleClean() = set { copy(readerClean = !readerClean) }
    fun readerSize(delta: Int) = set { copy(readerFs = (readerFs + delta).coerceIn(0, 3)) }

    fun readShort() {
        val it = shortestUnread(state.items) ?: return
        open(it.id)
    }

    // ---- search ----------------------------------------------------------

    fun setQuery(q: String) = set { copy(query = q, sFilter = "all") }
    fun setSearchFilter(key: String) = set { copy(sFilter = key) }
    fun setArchQuery(q: String) = set { copy(archQuery = q) }

    private fun pushRecent(q: String) {
        val v = q.trim()
        if (v.isEmpty()) return
        set { copy(recent = (listOf(v) + recent.filter { !it.equals(v, ignoreCase = true) }).take(5)) }
    }

    // ---- adding ----------------------------------------------------------

    fun openAdd() = set { copy(addOpen = true) }
    fun closeAdd() = set { copy(addOpen = false, addUrl = "", addError = "") }
    fun setAddUrl(v: String) = set { copy(addUrl = v, addError = "") }

    private fun addItem(title: String, site: String, src: String, tile: Long, mono: String, meta: String) {
        val id = "n${nextId++}"
        val item = Item(id, title, site, src, meta, tile, mono)
        set { copy(items = listOf(item) + items, filter = "all", addedId = id) }
        later("added", 800) { set { copy(addedId = null) } }
    }

    fun saveUrl() {
        val raw = state.addUrl.trim()
        urlError(raw)?.let { err -> set { copy(addError = err) }; return }
        val host = hostOf(raw)
        closeAdd()
        addItem(raw, host, host.substringBefore('.'), 0xFF6E5D49, host.take(1).uppercase(), "just now · fetching details…")
    }

    fun useClip() {
        closeAdd()
        addItem("compose/samples: official Jetpack Compose sample apps", "github.com", "GitHub", 0xFF3B2F23, "C", "just now · repository")
    }

    /** Demo of the system share sheet: "shares" the next sample page into Stash. */
    fun share() {
        val d = shareSamples[shareIdx++ % shareSamples.size]
        pendingSave = d
        set { copy(screen = Screen.Save, saveItem = d, firstLoad = false) }
        later("save", 4500) { finishSave() }
    }

    fun finishSave() {
        val d = pendingSave ?: return
        pendingSave = null
        jobs["save"]?.cancel()
        set { copy(screen = Screen.Home) }
        later("saveAdd", 300) { addItem(d.title, d.site, d.src, d.tile, d.mono, d.meta) }
    }

    // ---- streak and reminders -------------------------------------------

    fun openStreak() = set { copy(streakOpen = true) }
    fun closeStreak() = set { copy(streakOpen = false) }

    fun hideNotif() = set { copy(notif = null) }

    fun previewNudge(level: Int) {
        val s = state
        val p = s.prefs
        set { copy(lastPreview = level) }
        if (level == 0 && !p.remind) return settingsToast("Daily reminder is off")
        if (level >= 2 && !p.saver && !(level == 2 && p.remindTime == RemindTime.Evening && p.remind)) {
            return settingsToast("Streak saver is off: no evening nudges")
        }
        if (s.readToday) return settingsToast("No reminder: you already read today")
        val it = shortestUnread(s.items) ?: return settingsToast("No reminder: nothing unread on your shelf")
        val n = s.streak
        val m = minutesOf(it).let { m -> if (m == 99) 5 else m }
        val t = if (it.title.length > 42) it.title.take(40) + "…" else it.title
        val u = s.items.onShelf().count { i -> !i.read }
        val notif = when (level) {
            0 -> Notif(
                if (p.remindTime == RemindTime.Morning) "9:00 AM" else "7:00 PM", "Your shelf is waiting",
                "$u unread. “$t” takes $m min and keeps your $n-day streak going.",
            )
            2 -> Notif("7:00 PM", "Keep your $n-day streak going", "You have not read today. One $m-minute read is all it takes.")
            3 -> Notif("9:30 PM", "Your $n-day streak ends in 2½ hours", "“$t” is $m min. Done before bed.")
            else -> Notif("11:00 PM", "Last call: $n days on the line", "Your streak ends at midnight. Tap to read “$t” ($m min).")
        }
        set { copy(notif = notif, nudgeLevel = if (level == 0) 1 else level) }
        later("notif", 12_000) { if (state.notif == notif) hideNotif() }
    }

    fun resetToday() {
        set {
            val st = if (readToday) streak - 1 else streak
            copy(readToday = false, streak = st, oldStreak = st, nudgeLevel = 1, lastPreview = null)
        }
        settingsToast("Today reset. Preview a reminder.")
    }

    // ---- settings --------------------------------------------------------

    fun prefs(f: Prefs.() -> Prefs) = set { copy(prefs = prefs.f()) }

    fun showTipAgain() {
        set { copy(hinted = false) }
        settingsToast("The tip is back on your shelf")
    }

    private fun dueForArchive(a: AutoArchive) =
        if (a == AutoArchive.Off) emptyList() else state.items.onShelf().filter { it.read && it.readAgo >= a.days }

    fun pickAutoArchive(a: AutoArchive) {
        prefs { copy(autoArchive = a) }
        if (dueForArchive(a).isNotEmpty()) set { copy(archPending = a) } else {
            set { copy(archPending = null) }
            applyAuto(a)
        }
    }

    fun archPendingCount() = state.archPending?.let { dueForArchive(it).size } ?: 0

    fun archNow() {
        val a = state.archPending ?: return
        set { copy(archPending = null) }
        applyAuto(a)
    }

    fun archLater() {
        set { copy(archPending = null) }
        settingsToast("They will move during the next daily check")
    }

    private fun applyAuto(a: AutoArchive) {
        if (a == AutoArchive.Off) return settingsToast("Read items will stay on your shelf")
        val ids = dueForArchive(a).map { it.id }.toSet()
        if (ids.isEmpty()) return settingsToast("Read items will move after ${a.days} days")
        set { copy(items = items.map { if (it.id in ids) it.archivedNow() else it }) }
        settingsToast("Moved ${plural(ids.size, "read item", "read items")} to Archive")
    }

    fun clearRead() {
        if (state.items.onShelf().none { it.read }) return settingsToast("Nothing to clear")
        set { copy(clearConfirm = true) }
    }

    fun cancelClear() = set { copy(clearConfirm = false) }

    fun confirmClear() {
        val n = state.items.onShelf().count { it.read }
        set { copy(clearConfirm = false, items = items.map { if (it.read && !it.removed && !it.archived) it.copy(removed = true) else it }) }
        settingsToast("Cleared ${plural(n, "read item", "read items")}")
    }
}
