package com.techgv.stash.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.techgv.stash.App
import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.data.StashSync
import com.techgv.stash.core.database.StashDatabase
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.domain.ManualLink
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.network.createStashHttpClient
import com.techgv.stash.core.preferences.createStashDataStore
import com.techgv.stash.di.appModule
import com.techgv.stash.di.fakeCapabilitiesModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import okio.FileSystem
import org.koin.compose.KoinIsolatedContext
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.random.Random

/** Phone width (the handoff's 390 dp design width) and an expanded window, for the width-class journeys. */
internal val PhoneWidth = 390.dp
internal val TabletWidth = 1000.dp
internal val PhoneHeight = 844.dp

/** Every page the mock network serves: the title is the URL's last path segment, the body an article reader mode keeps. */
internal fun articleHtml(title: String) = buildString {
    append("<html><head><title>$title</title></head><body><article><h1>$title</h1>")
    repeat(5) { n ->
        append("<p>Paragraph ${n + 1} of $title. ")
        repeat(6) { append("A slow reader keeps a shelf of links and returns to them one evening at a time. ") }
        append("</p>")
    }
    append("</article></body></html>")
}

/**
 * The app as it ships ([App] over [appModule]): the real data layer on an in-memory database and a fresh preferences
 * file, the `core:testing` capability fakes, and a mock network that answers [articleHtml]. [back] sends system back.
 */
@OptIn(ExperimentalTestApi::class)
internal class Journey(
    val test: ComposeUiTest,
    val koin: Koin,
    val back: DirectNavigationEventInput,
    /** The activity's view model store. */
    val viewModels: ViewModelStoreOwner,
    width: Dp,
    height: Dp,
) {
    val bus: CommandBus get() = koin.get()

    /** The window's size; set it to resize the window (a rotation or a split-screen change). */
    var width by mutableStateOf(width)
    var height by mutableStateOf(height)

    /** A rotation: the window turns to [width] × [height] and the activity is recreated, as on Android. */
    fun rotate(width: Dp, height: Dp) {
        test.runOnIdle {
            this.width = width
            this.height = height
        }
        recreate()
    }

    internal var generation by mutableIntStateOf(0)
    internal var composed by mutableStateOf(true)
    internal var saved: Map<String, List<Any?>> = emptyMap()
    internal var registry: SaveableStateRegistry? = null

    /**
     * An activity recreation: saves what the composition saves, disposes it and composes it again from that state.
     * View models survive, as they do on a configuration change. (The library's StateRestorationTester is not
     * implemented on iOS.)
     */
    fun recreate() {
        test.runOnIdle {
            saved = checkNotNull(registry).performSave()
            composed = false
        }
        test.waitForIdle()
        // Same place in the composition, so the saved keys match; a fresh registry holds what was saved.
        test.runOnIdle {
            generation++
            composed = true
        }
        test.waitForIdle()
    }

    /** Saves [url] as the share target would and returns the new item's id. */
    suspend fun save(url: String): ItemId {
        val capture = (LinkCapturePolicy.validateManual(url) as ManualLink.Valid).capture
        return checkNotNull((bus.dispatch(Command.SaveLink(capture, null)) as DispatchResult.Done).itemId)
    }

    /** Saves [url] and fetches its details, so the card has the page's title. */
    suspend fun saveFetched(url: String): ItemId = save(url).also { koin.get<StashSync>().fetchMetadata(it) }

    suspend fun settings(transform: (UserPreferences) -> UserPreferences) {
        bus.dispatch(Command.ChangeSetting(transform))
    }

    fun pressBack() = test.runOnIdle { back.backCompleted() }

    /** Waits up to 5 s for a node matching [matcher] (the data layer answers on its own threads); fails with the screen. */
    fun waitFor(matcher: SemanticsMatcher) {
        try {
            test.waitUntil(timeoutMillis = 5_000) { test.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("No node ${matcher.description} on screen:\n${test.onRoot().printToString()}", e)
        }
    }

    fun waitForText(text: String, substring: Boolean = false) = waitFor(hasText(text, substring))

    fun waitGone(matcher: SemanticsMatcher) = test.waitUntil(timeoutMillis = 5_000) {
        test.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
    }

    fun cardMatcher(id: ItemId) = SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "item-${id.value}")

    fun card(id: ItemId) = test.onNode(cardMatcher(id))
}

internal fun labelled(text: String) = hasContentDescription(text, substring = true)

/**
 * Runs [block] against the whole app laid out [width] × [height] (the layout follows), after [seed] has written to the
 * data layer. Starts in Parchment with reduce motion on, so the state under test is never mid-animation. [overrides]
 * replaces bindings of the graph (capability fakes with behaviour of their own).
 */
@OptIn(ExperimentalTestApi::class)
internal fun journeyTest(
    width: Dp = PhoneWidth,
    height: Dp = PhoneHeight,
    overrides: Module = module {},
    seed: suspend Journey.() -> Unit = {},
    block: suspend Journey.() -> Unit,
) = runComposeUiTest {
    val appJob = SupervisorJob()
    val engine = MockEngine { request ->
        val title = request.url.encodedPath.substringAfterLast('/').ifEmpty { "Home page" }
        respond(articleHtml(title), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
    }
    val preferencesFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "journey-${Random.nextLong().toULong()}.preferences_pb"
    val app = koinApplication {
        modules(
            appModule,
            fakeCapabilitiesModule,
            module {
                single<StashDatabase> { inMemoryStashDatabase() }
                single { createStashDataStore(preferencesFile.toString()) }
                single<HttpClient> { createStashHttpClient(engine) }
                single(APPLICATION_SCOPE) { CoroutineScope(appJob + Dispatchers.Default) }
            },
            overrides,
        )
    }
    val back = DirectNavigationEventInput()
    // Outside the content: like an activity's, the store outlives a recreation.
    val viewModels = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    var showApp by mutableStateOf(true)
    val journey = Journey(this, app.koin, back, viewModels, width, height)
    try {
        journey.settings { it.copy(theme = ThemeMode.Parchment, reduceMotion = true) }
        journey.seed()
        setContent {
            // The activity's part: back input, window, view models and the saved-state registry outlive a recreation.
            val owner = rememberNavigationEventDispatcherOwner(parent = null)
            remember(owner) { owner.navigationEventDispatcher.addInput(back) }
            val density = LocalDensity.current
            val window = remember(density) {
                object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize get() = with(density) { IntSize(journey.width.roundToPx(), journey.height.roundToPx()) }
                }
            }
            val parentRegistry = LocalSaveableStateRegistry.current
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner,
                LocalViewModelStoreOwner provides viewModels,
                LocalWindowInfo provides window,
            ) {
                val registry = remember(journey.generation) {
                    SaveableStateRegistry(journey.saved) { parentRegistry?.canBeSaved(it) ?: true }.also { journey.registry = it }
                }
                if (showApp && journey.composed) {
                    CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                        KoinIsolatedContext(app) {
                            Box(Modifier.width(journey.width).heightIn(max = journey.height).fillMaxHeight()) { App() }
                        }
                    }
                }
            }
        }
        journey.block()
    } finally {
        // Down in order: the UI and its view models, the app scope, the database, Koin. A link a failed test left
        // pending would open in the next one.
        DeepLinks.pending.value?.let(DeepLinks::consumed)
        showApp = false
        waitForIdle()
        viewModels.viewModelStore.clear()
        appJob.cancelAndJoin()
        app.koin.getOrNull<StashDatabase>()?.close()
        app.close()
    }
}
