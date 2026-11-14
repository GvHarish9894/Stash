# Feature modules: conventions

Every `feature:*` module follows these rules. Sources: the architecture plan ("UI and state"), the KDoc on
`shared/src/commonMain/kotlin/com/techgv/stash/di/PlatformModule.kt` (effects and parameters), and the design-system
API in `core:designsystem`. `./gradlew checkModuleGraph` enforces the dependency rule: a feature sees only
`core:model`, `core:domain`, `core:designsystem`, `core:resources` (and `core:testing` in tests), never another feature,
`core:data` or `core:platform`.

## Layout

Package `com.techgv.stash.feature.<name>`, one file per role in `src/commonMain/kotlin/com/techgv/stash/feature/<name>/`:

| File | Role |
|---|---|
| `<Name>State.kt` | `@Immutable data class <Name>State`: everything the screen draws |
| `<Name>Intent.kt` | `sealed interface <Name>Intent`: every user action |
| `<Name>Effect.kt` | `sealed interface <Name>Effect`: one-off actions `shared` performs (navigate, open in browser, share, copy, ...) |
| `<Name>ViewModel.kt` | lifecycle `ViewModel`: `state: StateFlow<<Name>State>`, `effects: Flow<<Name>Effect>`, `fun onIntent(intent)` |
| `<Name>Screen.kt` | stateless composable: `(state, onIntent)`, design-system components only |
| `<Name>Route.kt` | gets the ViewModel from Koin, collects state, forwards effects to callbacks from `shared` |
| `<Name>FeatureModule.kt` | `val <name>FeatureModule = module { viewModelOf(::<Name>ViewModel) }` |

```kotlin
@Immutable
data class ShelfState(val chips: ChipCounts? = null, val layout: CardLayout = CardLayout.Comfortable, val undoOpId: String? = null)

sealed interface ShelfIntent {
    data class Open(val id: ItemId) : ShelfIntent
    data class Swipe(val id: ItemId, val outcome: SwipeOutcome) : ShelfIntent
    data object UndoLast : ShelfIntent
}

sealed interface ShelfEffect {
    data class OpenReader(val id: ItemId) : ShelfEffect
    data class OpenInBrowser(val url: String) : ShelfEffect
}
```

## ViewModel

- Depends only on `core:domain` interfaces (`ItemRepository`, `CommandBus`, `SettingsRepository`, `StreakRepository`,
  `ReaderRepository`, `RecentSearchRepository`, `ReminderRepository`), plus `kotlin.time.Clock` and `ZoneProvider` when
  it needs "now". Every write is `commandBus.dispatch(...)`.
- Business rules live in the domain: call `ShelfQuery`, `SwipeActionResolver`, `ReminderPlanner`, `LinkCapturePolicy`
  and friends from the ViewModel, never from a composable.
- State is a `StateFlow` built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` or a
  `MutableStateFlow` updated with `update { }`.
- Effects go through a `Channel`: no replay, and nothing is dropped (a full buffer suspends the sender).
- Paged lists are a separate `Flow<PagingData<...>>` property, `cachedIn(viewModelScope)`, not part of the state.

```kotlin
class ShelfViewModel(
    private val items: ItemRepository,
    private val bus: CommandBus,
    settings: SettingsRepository,
) : ViewModel() {
    private val effectChannel = Channel<ShelfEffect>(Channel.BUFFERED)
    val effects: Flow<ShelfEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<ShelfState> = combine(items.observeChipCounts(), settings.preferences) { chips, prefs ->
        ShelfState(chips = chips, layout = prefs.cardLayout)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfState())

    val rows: Flow<PagingData<ShelfRow>> = items.observeShelf(ShelfQuerySpec()).cachedIn(viewModelScope)

    fun onIntent(intent: ShelfIntent) {
        viewModelScope.launch {
            when (intent) {
                is ShelfIntent.Open -> {
                    bus.dispatch(Command.OpenItem(intent.id))
                    effectChannel.send(ShelfEffect.OpenReader(intent.id))
                }
                // ...
            }
        }
    }
}
```

A ViewModel that needs an argument (the reader's item id) takes it as a constructor parameter:
`viewModel { (id: ItemId) -> ReaderViewModel(id, get(), get()) }` and `koinViewModel { parametersOf(id) }`.

## Screen and Route

- **Screen:** stateless. It takes `state`, paged items if any, and `onIntent`. It never sees a repository or a
  ViewModel, so previews and UI tests drive it directly.
- **Route:** the only composable `shared` calls. Its parameters are:
  - navigation callbacks (`onOpenReader: (ItemId) -> Unit`, `onBack: () -> Unit`, ...);
  - capability-backed callbacks (`onOpenInBrowser: (String) -> Unit`, `onShare: (url, title) -> Unit`, ...);
  - capability-backed values (`clipboardUrl: String?`, `systemReduceMotion: Boolean`, `undoTimeoutMillis: Int?`, ...).

  These are plain Kotlin types, never a `core:platform` type. `shared` reads the capability and passes the value or
  calls it from the callback.
- **UI feedback:** haptics and announcements stay in the Screen, through `LocalHapticFeedback` and semantics
  (`liveRegion`, `error()`, `paneTitle`). A confirm / reject haptic that depends on a command result arrives as an
  effect, and the Route or Screen performs it.

```kotlin
@Composable
fun ShelfRoute(onOpenReader: (ItemId) -> Unit, onOpenInBrowser: (String) -> Unit, undoTimeoutMillis: Int?) {
    val viewModel = koinViewModel<ShelfViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ShelfEffect.OpenReader -> onOpenReader(effect.id)
                is ShelfEffect.OpenInBrowser -> onOpenInBrowser(effect.url)
            }
        }
    }
    ShelfScreen(state, rows, undoTimeoutMillis, viewModel::onIntent)
}
```

`collectAsStateWithLifecycle` comes from `libs.androidx.lifecycle.runtimeCompose`; add it to the feature's `commonMain`
if the build does not already resolve it.

## UI rules

- **Strings:** only from `core:resources` (`Res.string.*` / `Res.plurals.*`, or the design system's `UiText`). There is
  no literal user-visible text, and no string concatenation; use placeholders and plurals.
- **Components:** use the design-system components and tokens (`Stash.colors`, `Stash.type`, `StashMotion`, ...).
  Do not use raw colours or hand-made duplicates of a design-system component.
- **Stable state:** `kotlinx-collections-immutable` is not in the catalog. Use read-only `List` / `Map` in
  `@Immutable` (or `@Stable`) state classes, and always replace the whole collection, never mutate it. Keep lambdas in
  Routes stable (`viewModel::onIntent`).
- **Lists:** paged lists use `collectAsLazyPagingItems()` with
  `items(count, key = rows.itemKey { it.key }, contentType = rows.itemContentType { it.type })`, where the key is the
  item id (or the header label for headers). Unpaged lists use `key = { it.id.value }`.

## Tests

- **Test dependency:** add `commonTest.dependencies { implementation(project(":core:testing")) }`. It brings
  coroutines-test, Turbine, paging-testing (`asSnapshot`), the fixtures (`savedItem`, `TestClock`,
  `FixedZoneProvider`, `SequenceIdGenerator`) and the repository fakes:
  - `FakeItemRepository` (pure-policy shelf, archive, search and counts);
  - `FakeCommandBus`, which applies commands with the real `CommandDecider`, records the streak day and returns the
    real `DispatchResult`;
  - `FakeStreakRepository`, `FakeSettingsRepository`, `FakeReaderRepository`, `FakeRecentSearchRepository`,
    `FakeReminderRepository` and `RecordingCommandBus`.
- **ViewModel tests** go in `commonTest` and run on the JVM host, Android host and iOS simulator in `check`.
  - `viewModelScope` uses `Dispatchers.Main`: call `Dispatchers.setMain(StandardTestDispatcher())` in `@BeforeTest`
    and `Dispatchers.resetMain()` in `@AfterTest`, and pass the same scheduler to `runTest`.
  - Test intent → state → effect sequences with Turbine (`state.test { }`, `effects.test { }`).
  - Read paged rows with `viewModel.rows.asSnapshot()` (it loads the first 90 rows; pass `{ scrollTo(n) }` for more).

```kotlin
@Test
fun swipingToArchiveOffersUndo() = runTest {
    val bus = FakeCommandBus(FakeItemRepository(listOf(savedItem("a"))))
    val viewModel = ShelfViewModel(bus.items, bus, bus.settings)
    viewModel.state.test {
        skipItems(1)
        viewModel.onIntent(ShelfIntent.Swipe(ItemId("a"), SwipeOutcome.Archive))
        assertNotNull(awaitItem().undoOpId)
    }
    assertEquals(listOf(Command.Archive(ItemId("a"))), bus.dispatched)
}
```

- **Compose UI tests** drive the Screen with `runComposeUiTest` and assert behaviour and semantics (labels, roles,
  actions, live regions).
  - They need a window, so they live in `src/composeUiTest/kotlin` and run on the iOS simulator (in `check`) and as
    `androidDeviceTest` on an emulator. They do not run as Android host tests.
  - The `stash.kmp.feature` convention already wires this (device-test builder, both source dirs, the test runner and
    the test activity manifest): just put the tests in `src/composeUiTest/kotlin`. Do not copy any of it into the module.
  - Run the Android side with `ANDROID_SERIAL=<your emulator> ./gradlew :feature:<name>:connectedAndroidDeviceTest`.
- **Done** for a feature means:
  - every behaviour the handoff spec and prototype define for its screens is implemented;
  - there are ViewModel tests for each intent and its state / effect;
  - there are UI tests for the interactive parts and their accessibility semantics;
  - `./gradlew check` passes, including `checkModuleGraph`;
  - the device tests pass on the assigned emulator.

## How `shared` consumes a feature

- Each destination is one `<Name>Route` composable. `shared` owns the `NavHost`, the type-safe route classes
  (`Shelf`, `Search`, `Archive`, `Reader(id, from)`, `Settings`, `WidgetPreview`) and deep links. Features never
  reference each other's routes: they ask for navigation through a callback, which `shared` maps to a route.
- `shared` adds every `<name>FeatureModule` to the Koin graph next to `appModule`.
- `shared` turns each effect callback into a capability call and passes the capability values in.
