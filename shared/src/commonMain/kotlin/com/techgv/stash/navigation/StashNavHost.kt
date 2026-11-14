package com.techgv.stash.navigation

import androidx.compose.foundation.shape.RoundedCornerShape
import com.techgv.stash.core.designsystem.component.LocalSharedSearchBar
import androidx.compose.animation.SharedTransitionLayout
import kotlinx.coroutines.delay
import com.techgv.stash.core.designsystem.component.coveredByModal
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.techgv.stash.AppInfo
import com.techgv.stash.SystemBars
import com.techgv.stash.core.data.StashSync
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.ClipboardSuggestion
import com.techgv.stash.core.designsystem.component.DefaultUndoTimeout
import com.techgv.stash.core.designsystem.currentWidthClass
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.ReminderRepository
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.platform.A11yPrefs
import com.techgv.stash.core.platform.AppIconSwitcher
import com.techgv.stash.core.platform.AppLocale
import com.techgv.stash.core.platform.BrowserLauncher
import com.techgv.stash.core.platform.Clipboard
import com.techgv.stash.core.platform.ClipboardLink
import com.techgv.stash.core.platform.LanguageSwitching
import com.techgv.stash.core.platform.NotificationPermission
import com.techgv.stash.core.platform.ShareSheet
import com.techgv.stash.core.platform.WidgetPinner
import com.techgv.stash.feature.archive.ArchiveRoute
import com.techgv.stash.feature.reader.ReaderOrigin
import com.techgv.stash.feature.reader.ReaderRoute
import com.techgv.stash.feature.search.SearchRoute
import com.techgv.stash.feature.settings.SettingsRoute
import com.techgv.stash.feature.settings.SettingsSection
import com.techgv.stash.feature.shelf.ShelfRoute
import com.techgv.stash.feature.streak.CelebrationHost
import com.techgv.stash.feature.streak.StreakSheetRoute
import com.techgv.stash.feature.widgetpreview.WidgetPreviewRoute
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/** Where a full-screen reader keeps its current mode, for a resize that moves its item to the reader pane. */
private const val READER_MODE_KEY = "readerMode"

/** How long the card of a closed reader keeps its settle (prototype: 700 ms, longer than the 500 ms motion). */
private const val SETTLE_HOLD_MILLIS = 700L

/** Key of the shared search bar: Home's pill and the Search screen's field. */
private const val SEARCH_BAR_KEY = "search-bar"

/** Decision D29: a window shorter than this is a phone turned sideways, never list-detail. */
private val PhoneLandscapeMaxHeight = 480.dp

/**
 * A celebration handed over by a screen. While [readerKey] (the item id) is the item still open in a reader (the read
 * happened there), it waits; once that reader closes it shows (handoff streak rule 4). Keyed by item, not by screen, so
 * a resize that moves the item between the reader pane and the full-screen reader does not count as closing it.
 */
private data class HeldCelebration(val celebration: Celebration, val readerKey: String?)

private val HeldCelebrationSaver = listSaver<HeldCelebration?, Any>(
    save = { held ->
        if (held == null) emptyList()
        else with(held.celebration) { listOf(from, to, milestone ?: -1, held.readerKey.orEmpty()) }
    },
    restore = { saved ->
        if (saved.isEmpty()) null
        else HeldCelebration(
            Celebration(saved[0] as Int, saved[1] as Int, (saved[2] as Int).takeIf { it >= 0 }),
            (saved[3] as String).ifEmpty { null },
        )
    },
)

/**
 * Every screen, sheet and overlay of the app, and where feature effects meet the platform capabilities.
 *
 * Home is drawn underneath the [NavHost] (whose `Shelf` start destination is empty), so its scroll position, filter
 * and focus survive every other screen. Expanded windows show Home as a 400 dp pane beside a reader pane: opening an
 * item from Home selects it there instead of navigating. Back (and Esc, see [EscapeGoesBack]) closes, top first:
 * celebration, the streak sheet, a screen's own confirmation, sheet or preview, then the screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StashNavHost() {
    EscapeGoesBack()
    val navController = rememberNavController()
    val widthClass = layoutWidthClass()
    val isExpanded = widthClass == WidthClass.Expanded
    // Callbacks below outlive the composition that created them (routes capture them once), so they read State.
    val expanded by rememberUpdatedState(isExpanded)
    val colors by rememberUpdatedState(Stash.colors)
    val reduceMotion = Stash.reduceMotion

    val bus = koinInject<CommandBus>()
    val sync = koinInject<StashSync>()
    val browser = koinInject<BrowserLauncher>()
    val shareSheet = koinInject<ShareSheet>()
    val clipboard = koinInject<Clipboard>()
    val a11y = koinInject<A11yPrefs>()
    val appLocale = koinInject<AppLocale>()
    val iconSwitcher = koinInject<AppIconSwitcher>()
    val notificationPermission = koinInject<NotificationPermission>()
    val widgetPinner = koinInject<WidgetPinner>()
    val appInfo = koinInject<AppInfo>()
    val settings = koinInject<SettingsRepository>()
    val items = koinInject<ItemRepository>()
    val reminders = koinInject<ReminderRepository>()

    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailMarkedRead by rememberSaveable { mutableStateOf(false) }
    // The pane reader's mode, carried to the full-screen reader when a resize moves the item there.
    var detailReaderMode by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var streakOpen by rememberSaveable { mutableStateOf(false) }
    var held by rememberSaveable(stateSaver = HeldCelebrationSaver) { mutableStateOf<HeldCelebration?>(null) }
    val readerPane = viewModel { ReaderPaneStore() }
    val paneOwner = remember(detailId) { readerPane.ownerFor(detailId) }

    // The back stack starts with the graph's own entry; the screens follow it, Shelf first.
    val backStack by navController.currentBackStack.collectAsState()
    val screens = backStack.filterNot { it.destination is NavGraph }
    val cover = screens.getOrNull(1)?.layer()
    val top = screens.lastOrNull()
    val readerKey = top?.readerItemId() ?: detailId?.takeIf { isExpanded }

    /** The item open in a reader right now, read from the controller so a hand-over right after a navigation sees it. */
    fun currentReaderKey(): String? = navController.currentBackStackEntry?.readerItemId() ?: detailId?.takeIf { expanded }

    fun celebrate(celebration: Celebration) {
        held = HeldCelebration(celebration, currentReaderKey())
    }

    fun openReader(id: ItemId, from: ReaderOrigin, markedRead: Boolean) {
        if (from == ReaderOrigin.Link) navController.popBackStack<Shelf>(inclusive = false)
        // A second tap that lands before the first reader covers the list (a double tap) opens no second reader.
        if (navController.currentBackStackEntry?.layer() == Layer.Reader) return
        if (expanded && navController.currentBackStackEntry?.layer() == Layer.Home) {
            detailId = id.value
            detailMarkedRead = markedRead
            detailReaderMode = null
        } else {
            navController.navigate(Reader(id.value, from.name, markedRead))
        }
    }

    /** Pops [entry] only while it is on top, so a late or repeated callback never pops another screen. */
    fun close(entry: NavBackStackEntry) {
        if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
    }

    fun openInBrowser(url: String): Boolean = browser.open(url, colors.primary.toArgb(), colors.dark)
    val undoTimeout = remember(a11y) { { a11y.recommendedTimeout(DefaultUndoTimeout) } }

    // Notification taps and widget rows: opening is a read like any other (D21); an unknown id shows the missing state.
    LaunchedEffect(Unit) {
        DeepLinks.pending.filterNotNull().collect { id ->
            val done = bus.dispatch(Command.OpenItem(ItemId(id))) as? DispatchResult.Done
            streakOpen = false
            openReader(ItemId(id), ReaderOrigin.Link, markedRead = done?.affected == 1)
            done?.celebration?.let(::celebrate)
            DeepLinks.consumed(id)
        }
    }

    // Decision D27: reminders are on by default, so ask for notification permission once, the first time the shelf holds
    // an unread item while the app is in the foreground. Recorded before the prompt, so a denial is never asked again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        if (settings.preferences.first().notificationPermissionAsked) return@LaunchedEffect
        items.observeChipCounts().first { it.unread > 0 }
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        if (bus.dispatch(Command.ChangeSetting { it.copy(notificationPermissionAsked = true) }) !is DispatchResult.Done) return@LaunchedEffect
        // Reminders planned before the grant may not have reached the OS (iOS drops them): plan again.
        if (notificationPermission.request()) reminders.replan()
    }

    // A resize moves an item opened from Home between the reader pane and the full-screen reader.
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            val reader = navController.currentBackStackEntry?.takeIf { it.layer() == Layer.Reader } ?: return@LaunchedEffect
            val route = reader.toRoute<Reader>()
            val fromHome = route.from == ReaderOrigin.Home.name || route.from == ReaderOrigin.Link.name
            if (fromHome && navController.previousBackStackEntry?.layer() == Layer.Home) {
                navController.popBackStack()
                detailId = route.id
                detailMarkedRead = false
                detailReaderMode = reader.savedStateHandle.get<Boolean>(READER_MODE_KEY) ?: route.readerMode
            }
        } else {
            val id = detailId ?: return@LaunchedEffect
            // Under another screen the selection stays for the next expanded window: a reader pushed above that screen
            // would close back to it instead of Home.
            if (navController.currentBackStackEntry?.layer() != Layer.Home) return@LaunchedEffect
            detailId = null
            navController.navigate(Reader(id, ReaderOrigin.Home.name, markedRead = false, readerMode = detailReaderMode))
        }
    }

    // After the resize effect, which moves the open item first. Re-checked whenever the composition sees another reader; the controller is the truth (the composition's back
    // stack can lag one frame behind a navigation, which would release the celebration over the reader it waits for).
    LaunchedEffect(readerKey, held) {
        val waiting = held ?: return@LaunchedEffect
        if (waiting.readerKey != null && waiting.readerKey != currentReaderKey()) held = waiting.copy(readerKey = null)
    }

    // The item whose reader sits right on Home (the pane, or a full-screen reader over Home); when that reader closes, its
    // card settles (handoff motion), after the full-screen reader's close transition.
    val homeReaderItem = if (isExpanded) detailId else screens.takeIf { it.size == 2 }?.last()?.readerItemId()
    var settleItem by remember { mutableStateOf<String?>(null) }
    val lastHomeReader = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(homeReaderItem) {
        val closed = lastHomeReader[0]
        lastHomeReader[0] = homeReaderItem
        if (closed == null || closed == homeReaderItem) return@LaunchedEffect
        if (!expanded && !reduceMotion) delay(StashMotion.READER_CLOSE.toLong())
        settleItem = closed
        try {
            delay(SETTLE_HOLD_MILLIS)
        } finally {
            if (settleItem == closed) settleItem = null
        }
    }

    val shown = held?.takeIf { it.readerKey == null }?.celebration
    val overlayOpen = streakOpen || shown != null
    // The reader's bar is the primary colour, light in the dark theme: its icons follow onPrimary there.
    SystemBars(darkIcons = if (top?.layer() == Layer.Reader) colors.dark else !colors.dark, background = colors.bg)

    // Back goes to the top layer only: Home's handlers (its sheets, the detail pane's page history) are off while a
    // screen covers it, and the screens' handlers are off while the streak sheet or the celebration is open.
    val homeBack = rememberNavigationEventDispatcherOwner(enabled = cover == null && !overlayOpen)
    val screensBack = rememberNavigationEventDispatcherOwner(enabled = !overlayOpen)

    val density = LocalDensity.current
    // Home ↔ Search: the search pill and the search field are one shared element (handoff motion; none under Reduce motion).
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        // color-root shows behind a receding screen (handoff tokens).
        Box(Modifier.fillMaxSize().background(colors.root)) {
            val homeSearchBar = if (reduceMotion) Modifier else Modifier.sharedElementWithCallerManagedVisibility(
                rememberSharedContentState(SEARCH_BAR_KEY), visible = cover != Layer.Search,
                boundsTransform = { _, _ -> StashMotion.exit(StashMotion.SEARCH_CLOSE) },
            )
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides homeBack, LocalSharedSearchBar provides homeSearchBar) {
                ShelfRoute(
                    widthClass = if (isExpanded) WidthClass.Compact else widthClass,
                    undoTimeout = undoTimeout,
                    refreshFailedMetadata = sync::refetchFailedMetadata,
                    onOpenReader = { id, markedRead -> openReader(id, ReaderOrigin.Home, markedRead) },
                    onOpenInBrowser = ::openInBrowser,
                    onShare = shareSheet::share,
                    onCopy = clipboard::copy,
                    onOpenSearch = { navController.navigate(Search) },
                    onOpenArchive = { navController.navigate(Archive) },
                    onOpenSettings = { navController.navigate(Settings()) },
                    onOpenStreak = { streakOpen = true },
                    peekClipboard = {
                        when (val link = clipboard.peekLink()) {
                            is ClipboardLink.Url -> ClipboardSuggestion.Visible(link.url)
                            ClipboardLink.Hidden -> ClipboardSuggestion.Hidden
                            null -> null
                        }
                    },
                    readClipboard = clipboard::readUrl,
                    onCelebrate = ::celebrate,
                    // Under the streak sheet or the celebration: out of the accessibility tree and keyboard focus, and focus
                    // returns to the opener when it closes.
                    modifier = Modifier.fillMaxSize().recede(cover, reduceMotion).hiddenWhen(cover != null).coveredByModal(overlayOpen),
                    selectedItemId = detailId?.takeIf { isExpanded }?.let(::ItemId),
                    settleItemId = settleItem?.let(::ItemId),
                    detailPane = if (!isExpanded) null else {
                        {
                            @Suppress("DEPRECATION") // Like the shelf and settings; NavigationBackHandler's state API is more than this needs.
                            BackHandler(enabled = detailId != null) { detailId = null }
                            CompositionLocalProvider(LocalViewModelStoreOwner provides (paneOwner ?: checkNotNull(LocalViewModelStoreOwner.current))) {
                                ReaderRoute(
                                    itemId = detailId?.let(::ItemId),
                                    from = ReaderOrigin.Home,
                                    markedRead = detailMarkedRead,
                                    widthClass = widthClass,
                                    onClose = { detailId = null },
                                    onOpenInBrowser = { openInBrowser(it) },
                                    readerMode = detailReaderMode,
                                    onReaderModeChange = { detailReaderMode = it },
                                )
                            }
                        }
                    },
                )
            }

            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides screensBack) {
                NavHost(
                    navController = navController,
                    startDestination = Shelf,
                    modifier = Modifier.fillMaxSize().coveredByModal(overlayOpen),
                    enterTransition = { screenEnter(reduceMotion, density) },
                    exitTransition = { screenRecede(reduceMotion, density) },
                    popEnterTransition = { screenReturn(reduceMotion, density) },
                    popExitTransition = { screenClose(reduceMotion, density) },
                ) {
                    // Home is drawn underneath the host; this destination only marks the bottom of the stack.
                    composable<Shelf> {}
                    composable<Search> { entry ->
                        val searchBar = if (reduceMotion) Modifier else Modifier.sharedElement(
                            rememberSharedContentState(SEARCH_BAR_KEY), this,
                            boundsTransform = { _, _ -> StashMotion.enter(StashMotion.SEARCH_OPEN) },
                        )
                        ScreenSurface(searchBar) {
                            SearchRoute(
                                widthClass = widthClass,
                                undoTimeout = undoTimeout,
                                onOpenReader = { id, markedRead -> openReader(id, ReaderOrigin.Search, markedRead) },
                                onOpenInBrowser = ::openInBrowser,
                                onShare = shareSheet::share,
                                onCopy = clipboard::copy,
                                onCelebrate = ::celebrate,
                                onBack = { close(entry) },
                            )
                        }
                    }
                    composable<Archive> { entry ->
                        ScreenSurface {
                            ArchiveRoute(
                                widthClass = widthClass,
                                undoTimeout = undoTimeout,
                                onOpenReader = { id, markedRead -> openReader(id, ReaderOrigin.Archive, markedRead) },
                                onOpenInBrowser = ::openInBrowser,
                                onShare = shareSheet::share,
                                onCopy = clipboard::copy,
                                onCelebrate = ::celebrate,
                                onBack = { close(entry) },
                            )
                        }
                    }
                    composable<Reader> { entry ->
                        val route = entry.toRoute<Reader>()
                        ScreenSurface {
                            ReaderRoute(
                                itemId = ItemId(route.id),
                                from = ReaderOrigin.valueOf(route.from),
                                markedRead = route.markedRead,
                                widthClass = widthClass,
                                onClose = { close(entry) },
                                onOpenInBrowser = { openInBrowser(it) },
                                readerMode = route.readerMode,
                                onReaderModeChange = { entry.savedStateHandle[READER_MODE_KEY] = it },
                            )
                        }
                    }
                    composable<Settings> { entry ->
                        val route = entry.toRoute<Settings>()
                        // The OS owns the app language: read it again whenever this screen comes back to the front.
                        val lifecycleState by entry.lifecycle.currentStateAsState()
                        val languageTag = remember(lifecycleState) { appLocale.current }
                        // Also whether notifications are permitted: the user may have just changed it in the system settings.
                        val notificationsPermitted by produceState<Boolean?>(null, lifecycleState) { value = notificationPermission.isGranted() }
                        ScreenSurface {
                            SettingsRoute(
                                widthClass = widthClass,
                                canChangeIcon = iconSwitcher.isSupported,
                                languageInApp = appLocale.switching == LanguageSwitching.InApp,
                                currentLanguageTag = languageTag,
                                appVersion = appInfo.version,
                                recommendedTimeout = a11y::recommendedTimeout,
                                onBack = { close(entry) },
                                onOpenArchive = { navController.navigate(Archive) },
                                onOpenWidgetPreview = { navController.navigate(WidgetPreview) },
                                onOpenExternally = { openInBrowser(it) },
                                requestNotificationPermission = notificationPermission::request,
                                onOpenNotificationSettings = notificationPermission::openSettings,
                                notificationsPermitted = notificationsPermitted,
                                setAppIcon = iconSwitcher::set,
                                onSetLanguage = appLocale::set,
                                onOpenLanguageSettings = { appLocale.set(null) },
                                // The reader pops back to Settings; Home is the nearest origin the reader knows.
                                onOpenReader = { id, markedRead -> openReader(id, ReaderOrigin.Home, markedRead) },
                                onCelebrate = ::celebrate,
                                initialSection = route.section?.let(SettingsSection::valueOf),
                            )
                        }
                    }
                    composable<WidgetPreview> { entry ->
                        ScreenSurface {
                            WidgetPreviewRoute(
                                widthClass = widthClass,
                                canPinWidget = widgetPinner.canPin,
                                onBack = { close(entry) },
                                pinWidget = widgetPinner::requestPin,
                            )
                        }
                    }
                }
            }

            // Read each time the sheet opens: the user may have changed it in the system settings.
            val notificationsPermitted by produceState(true, streakOpen) { value = notificationPermission.isGranted() }
            StreakSheetRoute(
                visible = streakOpen,
                onDismiss = { streakOpen = false },
                onOpenReader = { id, markedRead -> openReader(id, ReaderOrigin.Home, markedRead) },
                onOpenInBrowser = ::openInBrowser,
                onOpenReminders = { navController.navigate(Settings(SettingsSection.Streak.name)) },
                onCelebrate = ::celebrate,
                notificationsPermitted = notificationsPermitted,
            )
            CelebrationHost(shown) { held = null }
        }
    }
}

/**
 * The window's width class for layout. Decision D29: list-detail needs width ≥ 840 dp AND height ≥ 480 dp; a shorter
 * window is a phone turned sideways and gets the single-pane medium-width rules, whatever its width.
 */
@Composable
private fun layoutWidthClass(): WidthClass {
    val widthClass = currentWidthClass()
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return if (widthClass == WidthClass.Expanded && height < PhoneLandscapeMaxHeight) WidthClass.Medium else widthClass
}

/** A hardware keyboard's Esc closes the top layer as back does (handoff, Accessibility notes). */
@Composable
internal expect fun EscapeGoesBack()

private fun NavBackStackEntry.readerItemId(): String? = if (layer() == Layer.Reader) toRoute<Reader>().id else null

/** An opaque screen above Home; it takes every touch so nothing reaches Home underneath. [searchBar]: see [LocalSharedSearchBar]. */
@Composable
private fun ScreenSurface(searchBar: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalSharedSearchBar provides searchBar) {
        Box(Modifier.fillMaxSize().background(Stash.colors.bg).pointerInput(Unit) {}, content = content)
    }
}

/** Takes a covered layer out of the accessibility tree and out of keyboard focus, so only the top layer is reachable. */
private fun Modifier.hiddenWhen(hidden: Boolean): Modifier =
    if (hidden) clearAndSetSemantics {}.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup() else this

/** Home's look while [cover] is on top of it: it scales, dims or shifts back as the screen above arrives. */
@Composable
private fun Modifier.recede(cover: Layer?, reduceMotion: Boolean): Modifier {
    val transition = updateTransition(cover, label = "home recede")
    val spec: @Composable Transition.Segment<Layer?>.() -> FiniteAnimationSpec<Float> = {
        val arriving = targetState
        when {
            reduceMotion -> snap()
            arriving != null -> StashMotion.enter(openMillis(arriving))
            else -> StashMotion.exit(closeMillis(initialState ?: Layer.Home))
        }
    }
    val scale by transition.animateFloat(spec, label = "scale") { recede(it).scale }
    val alpha by transition.animateFloat(spec, label = "alpha") { recede(it).alpha }
    val shift by transition.animateFloat(spec, label = "shift") { recede(it).shift }
    val lift by transition.animateFloat(spec, label = "lift") { recede(it).lift }
    val corner by transition.animateFloat(spec, label = "corner") { recede(it).corner }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
        translationX = shift * size.width * direction
        translationY = -lift.dp.toPx()
        if (corner > 0f) {
            shape = RoundedCornerShape(corner.dp)
            clip = true
        }
    }
}
