# Stash — UI implementation plan (source of truth)

Design source: `Stash — App Screens & Prototype – 09 Interactive prototype.html`
(artifact `eb3ad3ed-6b90-4b9a-8b49-198a11ab9328`). The prototype is a 390×844 React
mock with in-memory demo data. This plan maps it onto this Compose Multiplatform
codebase (`shared/commonMain`, Android + iOS).

## Scope

Build every screen and overlay in the prototype with the same visuals, copy and
behaviour, backed by in-memory state like the prototype.

Out of scope for this pass (follow-ups):
- Persistence (Room/DataStore), real share-intent receiving, real web page loading,
  real notifications/widgets/app-icon switching, Google sign-in/Drive ("Phase 2" in design).
  These are shown as the prototype shows them: demo flows and toasts.

## Design tokens

Fonts (bundled in `composeResources/font`):
- **Inter** 400/500/600/700: UI text
- **Lora** 400/400i/600/700: titles, card titles, section headers
- **Playfair Display** 700 italic: "Stash" wordmark, app-icon "S", big streak number

Colours: light ("Parchment") / dark ("Night library"), theme Auto follows system.

| token | light | dark |
|---|---|---|
| root | #2A2219 | #0E0B08 |
| bg | #F4ECD8 | #1E1812 |
| surface | #FAF4E6 | #2A2219 |
| variant | #E8DCC0 | #3D3226 |
| outline | #CBB894 | #4A3D2E |
| ink | #3B2F23 | #EADFC8 |
| faded | #6E5D49 | #B8A88E |
| accent | #B8893B | #E0B45C |
| good | #3F5E4A | #8DB49A |
| danger | #8B2E2A | #D97A6F |
| toast / onToast | #3B2F23 / #F4ECD8 | #EADFC8 / #1E1812 |
| toastAction | #E0B45C | #7A4E2D |
| onPrimary | #FBF6EA | #1E1812 |
| onPrimaryMuted | #EADFC8 | #3B2F23 |
| switchOff / inputBd | #857158 | #8A7861 |

Accent picks `primary` (light / dark): Leather #7A4E2D/#D2A26A, Oxblood #8B2E2A/#D97A6F,
Forest #3F5E4A/#8DB49A, Ink blue #2F4A6B/#8FB0D6.

Text size S/M/L → card title 14/20, 15/21, 17/24 (sp/line-height).

Card layouts: Comfortable (60dp tile, 12dp pad, 3-line clamp), Compact (40dp tile, 1 line,
title −1), Large images (full-width 156dp tile on top, title +2).

Radii: cards 14, settings groups 16, sheets 28 top, chips 18 (36 tall), FAB 16 (56 tall),
search pill 24 (48 tall), icon buttons 40 round.

## Architecture

- `StashViewModel` (lifecycle-viewmodel, already a dependency) owns one `StashState`
  (`MutableStateFlow`) and all actions. Screen nav is a `Screen` enum in state;
  transitions via `AnimatedContent`/`AnimatedVisibility`.
- Pure logic in `ShelfLogic.kt` (filter chips, sort + group rows, search + highlight,
  URL validation, shortest unread, nudge copy). Unit tests in `commonTest`.
- Theme: `StashTheme` provides `StashColors` via `LocalStashColors` and maps onto
  `MaterialTheme.colorScheme` so M3 components (Switch, Text) pick it up.
- Icons: the prototype's SVG paths, turned into `ImageVector`s with `PathParser`.
- Strings: en / es / hi / ta for the strings the prototype localises (`Strings.kt`).

## File layout (`shared/src/commonMain/kotlin/com/techgv/stash/`)

```
App.kt                      root: StashTheme + StashApp
model/Item.kt               Item, ShareSample, sample data, settings enums
StashState.kt               state + StashViewModel (actions, timers)
ShelfLogic.kt               pure logic
ui/theme/Theme.kt           colours, fonts, StashTheme
ui/theme/Strings.kt         localised strings
ui/components/Icons.kt      SVG → ImageVector
ui/components/Common.kt     Chip, Segmented, SettingSwitch, SectionHeader, Tile, Toast, Sheet, CandleIcon
ui/components/ItemCard.kt   swipeable card + long-press, highlight, read strike, unread dot
ui/HomeScreen.kt            header, search pill, chips, nudge, hint, list, archive link, empty, FAB, add sheet
ui/SearchScreen.kt          query, recent searches, result chips, results incl. archive
ui/ArchiveScreen.kt         archive search, rule text, grouped cards (right=restore, left=delete)
ui/SettingsScreen.kt        all settings sections + inline confirms + toast
ui/WidgetScreen.kt          widget preview small/medium + add
ui/ReaderScreen.kt          web view mock / reader mode (Aa), text size, mark unread, toast
ui/SaveScreen.kt            share → "Tucked into your shelf" animation
ui/Overlays.kt              item menu sheet, streak sheet, celebration, notification banner, undo toast
```

## Screens and behaviour checklist

- [x] Theme, fonts, tokens, icons
- [x] Home: wordmark, streak chip (candle, lit/late states), unread count, Archive/Settings/Share buttons
- [x] Home: search pill → Search screen
- [x] Home: filter chips All/Unread/per source with counts
- [x] Home: streak nudge card (levels 1–4) with "Read now" → opens shortest unread
- [x] Home: dismissible gesture tip (text follows swipe settings)
- [x] Home: grouped list (Day/Week/Month/None; sort Newest/Oldest/By source)
- [x] Card: tap opens reader; swipe right/left with coloured action background (threshold 110dp);
      long-press → options sheet; read = faded title + strike-through + faded tile; unread dot
- [x] Undo toast (10s progress bar) for remove/archive/restore/delete
- [x] Archive link row + empty shelf state
- [x] Add link FAB → sheet: clipboard suggestion "Use", URL field with validation errors, Cancel/Save
- [x] Search: autofocus field, recent searches, results with highlights, source/archived chips, summary, no matches
- [x] Archive: header + count, search, rule text, This month/Earlier groups
- [x] Settings: Appearance (theme, accent, card layout, text size, reduce motion), Reading
      (mark read on open, reader mode default, open links in), Streak & reminders (status,
      daily reminder, time, streak saver, preview reminder, reset today), Swipe gestures,
      Shelf (sort, group, auto-archive + confirm, open archive, show tip again),
      Account & backup (Phase 2/Soon badges), Your data (clear read + confirm),
      Home screen & language (widget, app icon, language), About (GitHub link, privacy, version)
- [x] Widget preview (small 2×2 / medium 4×2) + "Add to home screen" toast
- [x] Reader: top bar in primary colour, Aa toggle, Mark unread, load bar, web mock / reader mode with A−/A+
- [x] Share demo → Save screen with book/tuck animation, auto-finishes after 4.5s, item flutters in
- [x] Streak sheet: week dots, status, Reminders / Read something short
- [x] Celebration overlay after first read of the day (number roll, embers, week)
- [x] Notification banner preview (from Settings) with Later / Read now
- [x] Reduce motion disables animations
- [x] System back closes overlays/screens in the same order as the prototype's Escape key

## Status and known gaps

All checklist items are built and checked on the Resizable_Experimental emulator (phone + tablet, light + dark).
Known gaps, kept as small follow-ups:
- In-memory data only (resets on process death); no real share intent, web content, notifications or widget.
- iOS edge-swipe back is not verified: XCTest/Maestro synthetic swipes do not trigger UIKit's edge-pan recognizer. Check by hand.
- Undo / notification timers do not pause while focused (the prototype pauses on hover/focus).
- The level-4 nudge pulse loops instead of stopping after three pulses.

## Verification

- `./gradlew :shared:testAndroidHostTest` (logic tests)
- `./gradlew :androidApp:assembleDebug`, install on the **Resizable_Experimental** emulator,
  check phone + tablet/foldable sizes, light + dark, against prototype screenshots.

### iOS simulator

Built with Xcode 27.2 (`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`) and run on an iPhone 17 Pro simulator (iOS 26.4).
The Maestro flows in `.maestro/` pass there: shelf swipe/undo/menu/archive, search, add link, share-to-save,
reader and reader mode, streak sheet and celebration, settings (theme, accent, layout, language) and the widget preview.

## Commit rules

- Commit with the repo's personal identity (`Harish G <50960154+GvHarish9894@users.noreply.github.com>`).
- No co-author trailers.
