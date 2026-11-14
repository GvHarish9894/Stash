package com.techgv.stash.core.testing

import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.platform.A11yPrefs
import com.techgv.stash.core.platform.AppIconSwitcher
import com.techgv.stash.core.platform.AppLocale
import com.techgv.stash.core.platform.BrowserLauncher
import com.techgv.stash.core.platform.Clipboard
import com.techgv.stash.core.platform.ClipboardLink
import com.techgv.stash.core.platform.LanguageSwitching
import com.techgv.stash.core.platform.ShareSheet
import com.techgv.stash.core.platform.WidgetPinner
import com.techgv.stash.core.model.WidgetSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

/** Records every [open]; [opens] scripts whether a browser could show the page. */
class FakeBrowserLauncher(var opens: Boolean = true) : BrowserLauncher {
    data class Opened(val url: String, val toolbarColorArgb: Int, val darkTheme: Boolean)

    val opened = mutableListOf<Opened>()

    override fun open(url: String, toolbarColorArgb: Int, darkTheme: Boolean): Boolean {
        opened += Opened(url, toolbarColorArgb, darkTheme)
        return opens
    }
}

class FakeShareSheet : ShareSheet {
    /** Every share as url to title. */
    val shared = mutableListOf<Pair<String, String>>()

    override fun share(url: String, title: String) {
        shared += url to title
    }
}

/** [url] is what [readUrl] returns; [copied] records [copy]. */
class FakeClipboard(var url: String? = null) : Clipboard {
    var readCount = 0
        private set
    val copied = mutableListOf<String>()

    /** Set false to model iOS, where peeking only reveals that a link is there. */
    var peekShowsUrl = true

    override suspend fun peekLink(): ClipboardLink? =
        url?.let { if (peekShowsUrl) ClipboardLink.Url(it) else ClipboardLink.Hidden }

    override suspend fun readUrl(): String? {
        readCount++
        return url
    }

    override fun copy(text: String) {
        copied += text
    }
}

/** [succeeds] scripts the result of [set]; [current] is the icon last set successfully. */
class FakeAppIconSwitcher(
    override val isSupported: Boolean = true,
    var succeeds: Boolean = true,
) : AppIconSwitcher {
    var current: AppIcon = AppIcon.Leather
        private set
    val requested = mutableListOf<AppIcon>()

    override suspend fun set(icon: AppIcon): Boolean {
        requested += icon
        val applied = isSupported && succeeds
        if (applied) current = icon
        return applied
    }
}

class FakeAppLocale(
    override val switching: LanguageSwitching = LanguageSwitching.InApp,
    override var current: String? = null,
) : AppLocale {
    /** Every [set] call, including null for the phone's language. */
    val requested = mutableListOf<String?>()

    override fun set(languageTag: String?) {
        requested += languageTag
        if (switching == LanguageSwitching.InApp) current = languageTag
    }
}

/** [canPin] models launcher support; [pinned] records each request. */
class FakeWidgetPinner(override var canPin: Boolean = true) : WidgetPinner {
    val pinned = mutableListOf<WidgetSize>()

    override fun requestPin(size: WidgetSize): Boolean {
        if (canPin) pinned += size
        return canPin
    }
}

/** Set [reduceMotion]'s value to flip the OS setting; [recommended] maps the default timeout (identity by default). */
class FakeA11yPrefs(
    reduceMotion: Boolean = false,
    var recommended: (Duration) -> Duration = { it },
) : A11yPrefs {
    override val reduceMotion = MutableStateFlow(reduceMotion)

    override fun recommendedTimeout(default: Duration): Duration = recommended(default)
}

/** [granted] is the current state; [grantsOnRequest] scripts the user's answer to the prompt. */
class FakeNotificationPermission(var granted: Boolean = false, var grantsOnRequest: Boolean = true) :
    com.techgv.stash.core.platform.NotificationPermission {
    var requestCount = 0
        private set

    override suspend fun isGranted() = granted

    override suspend fun request(): Boolean {
        requestCount++
        granted = grantsOnRequest
        return granted
    }

    var settingsOpened = 0
        private set

    override fun openSettings() {
        settingsOpened++
    }
}
