# Stash — backlog of minor findings

Minor findings from reviews and verifications that are deliberately not fixed now (operator decision, 13 Nov 2026).
Critical and Major findings are never parked here: they are fixed and re-verified in `docs/IMPLEMENTATION.md`.

Each item names where it was found. Review reports are kept in `Stash/.orchestration/briefs/` (outside the repo).
Add new minor findings at the end of the matching section; remove an item in the commit that fixes it.

## Domain and data

| # | Item | Found in |
|---|---|---|
| D-1 | Search matcher and the FTS index disagree on a few exotic inputs: Turkish `İ`, letters outside the Basic Multilingual Plane, superscripts / fractions / Roman numerals, decomposed (NFD) accents. Documented in `SearchMatcher`. | T1.2 verification 2 |
| D-2 | `capTitle` and other length caps cut in UTF-16 units and can split a combining mark, a flag emoji, or a Tamil / Hindi cluster. | T1.2 review 2; T2.3 review 2 |
| D-3 | An IDN host and its punycode form produce different dedupe keys. | T1.2 re-review |
| D-4 | Search result summary: the "· N archived" part can appear a moment late, because `SearchChips` carries no shelf hit count. | T3.1c author report |
| D-5 | A 22,000-item "Clear read" holds the database writer for one transaction (0.8 s JVM, 4.3 s iOS simulator). Chunked transactions would release it sooner. | T2.2 fix round 1 |
| D-6 | Pull-to-refresh at the same moment as an overlapping foreground catch-up can attempt an item twice; a cancelled fetching catch-up leaves a requested re-run for the next catch-up. | T2.2 fix round 3 |
| D-7 | By-source sort re-sorts all filtered rows on every page load (about 350 ms JVM / 240 ms iOS simulator for the first page at 50,000 items). Documented where the SQL is built. | T2.2a review 1 |
| D-8 | `Connectivity` KDoc still says the data layer checks only after a failure; it checks before and after. | T2.2 fix round 3 |
| D-9 | Hindi plural wording for zero ("0 item"); clock times are fixed strings, not locale-formatted. | T3.1a review 1 |
| D-10 | Reminder that passes its slot while the app re-plans: covered by the planner grace period once T3.2 fix 3 lands — re-check the nudge banner level uses the same rule. | T3.2 verification |

## Networking and reader extraction

| # | Item | Found in |
|---|---|---|
| N-1 | Reader mode drops inline SVG and MathML; tables are flattened to text; no inline-code span; list items carry no nesting depth; `<ol start>` and `<br>` inside `<pre>` are not honoured. | T2.3 reviews; verification 4 |
| N-2 | `div.newsletter-issue` style roots and list-only articles are not extracted. | T2.3 verification 3–4 |
| N-3 | Reading time is approximate (within about 15% on most fixtures, higher on pages with unusual chrome; low for Chinese, Japanese, Thai). | T2.3 verification 4 |
| N-4 | No cookies across a redirect chain; loopback and LAN addresses are fetched; TLS errors are reported as "offline". | T2.3 review 1 |
| N-5 | Big5 byte 0x8840 decodes to a private-use code point on iOS. | T2.3 verification 3 |
| N-6 | Lazy-image attribute `data-lazy-src`, `<noscript>` and `<picture>` sources are not read. | T2.3 verification 2 |

## Android

| # | Item | Found in |
|---|---|---|
| A-1 | Cold share still misses the 500 ms target: about 0.76 s on the realme phone before the startup trim, 575–801 ms on the emulator after it (debug builds); warm is 37–49 ms. 330–520 ms pass before app code starts. Measure a release build with a baseline profile. | T3.2 verification on device |
| A-3 | The widget always uses the Leather accent (`WidgetSnapshot` has no accent field); only the two unread strings are translated; below Android 13 an in-app language change does not redraw it; a real placed widget redrawing after a language change is not verified. | T3.2 verification N6; review 1 |
| A-5 | Reminders are not re-armed before the first unlock after a reboot (documented limit). | T3.2 fix round 2 |
| A-6 | Launcher icon "S" is scaled to the visible 72 dp tile, not the prototype's 28/48 ratio; on API 24–25 it is drawn small on a rounded square. Icons are provisional art. | T3.2 follow-up; verification |
| A-7 | Widget picker previews were not checked visually; below API 31 the picker shows the app icon. | T3.2 fix round 2 |
| A-8 | No test for the orphaned-alarm ordering (old ∪ new written before scheduling). | T3.2 fix round 2 |
| A-9 | The system "16 KB page-size compatible" dialog lists DataStore's and Compose graphics' native libraries; newer artifacts may be needed before release. | T3.0 author report |

## iOS

| # | Item | Found in |
|---|---|---|
| I-1 | Widget midnight entry can be dated in the past on a 25-hour (fall-back) day in zones with daylight saving. Fix: next day's start of day via `Calendar.date(byAdding:)`. | T3.3 verification |
| I-2 | Stale comments in `StashWidget.swift` ("one entry") and the privacy manifest comment (ICU not named). | T3.3 verification |
| I-3 | A warm foreground right after a share logs two catch-ups (inbox notification plus scene activation). Harmless. | T3.3 verification |
| I-4 | The widget always uses the Leather accent, shows monograms instead of thumbnails, and has no late-evening "at risk" look; the snapshot would need `accent`, `atRiskAt` and a per-item `tint`. | T3.3 author reports |
| I-5 | The Share Extension cannot report the sharing app (`sourceApp` is always null) or detect duplicates; its sheet sits inside the system card. | T3.3 author report; review 3 |
| I-6 | XPC-misuse fault reports are logged for the widget and extension processes on this simulator build, from Apple frameworks only. Re-check on a device. | T3.3 fix round 1; review 3 |

## Design system and UI

| # | Item | Found in |
|---|---|---|
| U-2 | Hero and detail animations not built: page tear on remove, card settle after the reader closes, undo fly-back, the shared-element search bar; removed source chips fade instead of shrinking; switching to or from large-image cards re-lays out without animating. | T3.1a reports; T3.1b report |
| U-3 | Keyboard focus ring and the per-card "Options" button; sheets do not trap focus or return it to the opener; the add-link error is not announced when Save is tapped. | T3.1a report; T3.1b report |
| U-4 | Right-to-left: direction-implying icons are not mirrored. | T3.1a report |
| U-5 | Phone landscape: the header does not collapse and the nudge banner does not hide; on the operator's phone the sideways layout is the tablet two-pane one (width 941 dp). In Milestone 4 (T4.3, D29). | T3.1a review 3 |
| U-6 | At very large text two side-by-side buttons can end up with different heights. | T3.1a fix round 2 |
| U-7 | Reader: turning reader mode off reloads the web page; the iOS load bar moves in three fixed steps; every load failure says "You're offline". | T3.1d author report |
| U-8 | Archive: the two-column grid for large-image cards at medium width is not built. | T3.1c author report |
| U-9 | The deprecated `BackHandler` is used (its replacement needs a library that is not in the catalog); no UI test sends a system back event. | T3.1b / T3.1e reports |
| U-11 | iOS dark theme: status-bar icons stay white over the reader's light top bar. | T3.1f author report |
| U-12 | At the largest iOS text size the Settings option-card labels break mid-word ("Parchmen-t"). | T3.1f author report |
| U-14 | Archive's back label says "Back to shelf" even when opened from Settings; the "Marked as read" toast and the loaded web page are lost when a rotation moves the reader between the pane and full screen. | T3.1f author report |
| U-15 | 11 strings in `core:resources` are unused after the old UI was removed (`common_read_item_count`, `nav_share_demo`, `search_open_item`, `reader_web_note`, `reader_body_note`, `settings_reset_today`, `settings_reset_today_body`, `toast_share_opened`, `toast_today_reset`, `archive_rule_off_rtl`, `archive_rule_days_rtl`). | T3.1f author report |
| U-16 | On the realme phone a real Chrome share opens the quick-save sheet in a scaled-down ColorOS floating window (`am start` gives a normal bottom sheet). | Phone journey |
| U-17 | Add-link field garbles very fast input (value round-trips through the ViewModel); quick repeated A− taps in the reader are dropped (step updates after the setting is saved). | Phone journey |
| U-18 | The shelf filter, search query and reader mode are not saved across process death; the restored reader route replays the "Marked as read" toast. | Phone journey; app review 1 |
| U-19 | The 4×2 widget clips its third row on the realme launcher; "Link copied" duplicates Android 13+'s own clipboard confirmation; the selected source chip can end up off screen after chips re-sort; the missing-item screen has light status-bar icons on a light background. | Phone journey |
| U-20 | Effects whose callback suspends lose their answer if the composition is disposed mid-call (rotating during the permission dialog leaves the reminder off although access was granted; clipboard peek too). | App review 1 |
| U-22 | Quick-save sheet: rotation restarts the auto-close timer and replays the animation; process death mid-save re-saves and shows "Already on your shelf". The Coil HTTP client has no timeouts or size limits. Unused feature dependencies (quick-save: lifecycle-viewmodel, koin, navigation; coil in shelf / search / archive / widget preview). `SearchState.query` is never set. | App review 1 |
| U-23 | Settings: "picking a grouping leaves the by-source sort" lives in the ViewModel; a 7 PM daily reminder previewed under "Evening" with the saver off renders the Daily copy; the recent-searches header uses a locale-default `uppercase()`. | App review 1 |
| U-24 | A deep link pops Search without saving the typed query to recents. A selected source that fell back to All silently comes back when that source regains a second item. | App review 2 |
| U-25 | In Search and Archive a rejected Undo (item changed elsewhere) is silent; the shelf says "That didn't work". The browser result is ignored for Settings "Read now", the GitHub link and the reader's "Open in browser", so a failure shows no toast. | App review 2 |
| U-26 | "Not yet" says read items move "during the next daily check", but they move on the next app foreground. | App review 2 |
| U-27 | Test gaps: no test catches an archive open passing markedRead = false; `anExpandedWindowSelectsIntoTheReaderPane…` failed once and passed on re-run (possible flake). | App review 2 |
| U-28 | Sheets cannot be dragged down to close; the handle is a 64×20 dp tap target. Reader-mode text cannot be selected or copied. | App review 3 |
| U-29 | iOS: text-size changes apply only after a relaunch; the launch screen is white in both themes; in a narrow iPad window the web view keeps desktop layout and the page is cut off on the right. | App review 3 |
| U-30 | Failed-metadata card at default size on iPhone: the domain wins and "Couldn't load details" is cut. More mid-word breaks at the largest text sizes (streak sheet "Remind-ers", add-link "Canc-el"). | App review 3 |
| U-31 | Expanded width: the item and add-link sheets and their scrim cover only the shelf pane (the reader pane stays tappable and in the accessibility tree); the item open in the pane has no selected state in the list. | App review 3 |
| U-32 | Missing `Role.Button` on streak "Reminders", add-link "Cancel", reader "Mark unread" and the item-sheet rows; text-size radios announce "S/M/L". Language and notification rows say "phone" on tablets. | App review 3 |
| U-33 | After notifications are denied for good, tapping the Daily reminder or Streak saver switch does nothing visible; only the "Open notification settings" row below leads anywhere. | Fix-round verification |
| U-34 | A share reaching an already-open quick-save sheet is saved 1–2 s after it is sent (the system holds the intent until the sheet's opening animation ends); the sheet shows the previous outcome meanwhile. | Fix-round verification |
| U-35 | `ShareReceiverActivityTest.aSharedLinkIsSavedOnceAndASecondShareKeepsOneItem` asserts a save under 500 ms and fails consistently on the phone emulator (712–1,851 ms; also at the base commit). Nothing tests that the navigation host passes `notificationsPermitted` to the streak sheet. | Fix-round verification |
| U-36 | The "asked for notifications" flag is written before the prompt: a rotation in that gap means the app never asks on its own (a switch still prompts). After a process kill the tablet pane replays "Marked as read". | Fix-round verification |
| U-37 | After returning from the reader to Search, the first Back does not leave Search (keyboard not showing); a second Back reaches the shelf. Reproduced twice on the physical phone. | Phone re-check |
| U-38 | After `am force-stop` a WorkManager job restarts Stash's process within 0.3–2.5 s (half of the cold-start runs came back warm). Check which job and whether it is needed that early. | Phone re-check |
| U-39 | Seven design-system DEVICE tests fail on Android and pass on iOS; the features work in the real app (verified on the tablet emulator). The tests are wrong for Android: three thumbnail tests and two focus-ring tests capture the test window's background instead of the component; the RTL-icon test counts every opaque pixel as ink; the reader text-selection test relies on a stand-in toolbar Android does not use. | Milestone 4 verification |
| U-40 | The chip shrink-out is not pinned by its test (removing `shrinkOut` still passes); while a chip leaves, the next chip briefly draws over "Unread". | Milestone 4 verification; phone check |
| U-41 | Phone landscape with the keyboard up: add-link Cancel / Save need two or three scrolls to be fully visible (the keyboard's ✓ submits); Search shows about 30 dp of results until the chips scroll away. | Milestone 4 verification |
| U-42 | Reader mode: long-pressing a link opens it instead of selecting text; Back with text selected clears the selection and closes the reader in one press. While search results scroll, the chip row slides under the search field. | Phone check |
| U-43 | A swipe on Search results opened a card (three sightings on the phone emulator, not reproducible on demand). "Isn't responding" dialogs on loaded emulators after a rotation with a reader open — not attributable to the app yet. | Milestone 4 verification |
| U-44 | The mark-read "check pop" (handoff motion table) is not built: there is no check mark on the card in the design. Waiting on a design decision. While a sheet is dragged its scrim stays fully dark. | T4.1; T4.2 |
| U-45 | The first auto-archive catch-up with a very large backlog (about 21,000 items due at 50,000) runs as one write transaction: 1.4 s on the JVM, 7.4 s on the iOS simulator; user writes wait behind it. Decide whether to split it. | T5.2 |
| U-46 | Paging keeps at most 240 items (8 pages); not yet confirmed on a device that the list does not jump when pages drop off the top. Image downloads have timeouts but no size limit. | T5.2 |
| U-47 | **Performance target not met:** scroll jank on the physical phone is 1.1–2.4 % with the release build and baseline profile (target < 1 %). Next step: a frame trace of the shelf scroll on a real device to find the long frames. Cold start (225 ms) and share-to-save (178–199 ms cold) meet their targets. | Phone performance run |
| U-48 | Seeded shelf: the count dropped by one (30,845 → 30,844) with no action; with sort "By source" the first group differs between All and Unread. Not investigated. The baseline profile is only `verify` as installed until the system compiles it. | Phone performance run |
| U-10 | The widget preview is drawn at font scale 1.0 by decision (it pictures a fixed-size widget). Revisit if the real widgets scale. | T3.1a fix round 2 |

## Build and tooling

| # | Item | Found in |
|---|---|---|
| B-1 | `checkModuleGraph` cannot see dependencies added or substituted when a classpath resolves, nor data-side libraries arriving transitively (both noted as `shortcut:` in the build file). | T1.1 verification |
| B-2 | `androidx.compose.ui:ui-test-manifest` is used by the feature convention without a catalog entry. | T3.1a fix round 1 |
| B-3 | An occasional lint / KSP file race fails `check` when many tasks run at once; a rerun passes. | Orchestrator, 12 Nov |
| B-4 | Kotlin/Native warning: the Shared framework bundle id cannot be inferred. | T1.1 author report |
| B-5 | The Android launcher-icon generator needs `fonttools` (pinned in `requirements.txt`). | T3.2 fix round 2 |

## Waiting on a decision or an asset (not engineering backlog)

- `android:allowBackup` is now `false` (operator decision, 14 Nov). Still open: device-to-device transfer on Android 12+ (`dataExtractionRules`), and iOS device backups include the app's data.
- Dark theme with the Oxblood accent: the reader toolbar's site line is 4.31:1, below the handoff's 4.5:1.
- App icons are provisional art from the prototype; the handoff lists the final wordmark as open.
- Paper texture tile, pull-to-refresh quill animation, and es / hi / ta translations beyond the 34 existing strings.
- Apple developer account for device signing: the physical iPad, real background refresh, App Group on device,
  extension memory limits and alternate icons are Not Verified until then.

## Not verified yet (verification gaps, tracked here so they are not lost)

- Held until all Major work is done (operator decision, 14 Nov) — Milestone 4 on a real device: the phone-landscape layout (D29) on the physical phone (seen on the phone-sized emulator only); the card settle after the reader closes (captures too slow to see it); why `example.com` test cards show "Couldn't load details" while Wikipedia items load; TalkBack / VoiceOver speech; the focus ring with a real hardware keyboard; the iPad simulator at full size; scroll jank and cold start before / after Milestone 4 (the emulator was too noisy to compare: 98–100 % janky frames on both builds) — measure on the physical phone with a release build in Milestone 5; arrow keys in the Settings radio groups with a real keyboard; iOS motion and sheet drag on the simulator.
- Android below API 33: per-app language through AppCompat, reminder language in a process started without an
  activity (no system image installed).
- Android phone (debug build, realme RMX5108): cold start 1.10 s median; quick-save sheet 1.05–1.12 s when Stash is not running, 0.28 s when it is; shelf scroll 4.7–15.8% janky frames over 35 items; memory 173 MB on the shelf, 270–290 MB in the reader. Release-build numbers with a baseline profile are still to be measured against the targets (< 1 s cold start, < 1% jank, < 500 ms save).
- Fix round: the iPad simulator (the tablet emulator was used instead); Android 12 and below for the notification ask (code read only); the iOS web view console; the quick-save sheet's appearance during rotation. The fixes were re-run on the physical phone at `fd562b3` and hold (thumbnails, reader opening in both themes, reader-mode icons, no page logging, double tap, three quick opens, search keyboard, a second share while the sheet is open, the 31-share burst, the one-time notification prompt); rotation on the phone is still untested (it needs a person to turn the phone).
- iOS edge-swipe back and Esc; VoiceOver spoken output; the celebration on tablets by hand (a journey test covers it).
- Android: the daily maintenance worker in a cold process; device-wide fault injection on a physical phone (clock,
  reboot, airplane mode) is covered by instrumented tests only.
- iOS: `BGTaskScheduler` refresh (the simulator refuses requests), time-zone change, a scheduled reminder firing
  while the app is open, the 1,000,000-character share cap at run time, live HTTPS from the simulator test runner.
