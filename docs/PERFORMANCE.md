# Performance: release build, baseline profile, benchmarks (Android)

Targets (architecture plan): cold start under 1 s to first frame, under 1 % janky frames, share-to-save under 500 ms,
50,000 items per user.

## Builds

- `./gradlew :androidApp:assembleRelease`: R8 and resource shrinking; signed only where the signing values exist (see `docs/RELEASE.md`), otherwise unsigned.
  No keep rules were added: the libraries' consumer rules were enough (see "R8 smoke test").
- `./gradlew :androidApp:assembleBenchmark`: the release build signed with the debug key (installs over a debug
  build and keeps its data), `profileable`, plus the measurement seeder. Output:
  `androidApp/build/outputs/apk/benchmark/androidApp-benchmark.apk`.
- Both carry the committed baseline profile (`assets/dexopt/baseline.prof`; `profileinstaller` installs it on
  sideloaded builds).

## Seed and wipe (benchmark build only)

```
adb -s <serial> shell am broadcast -a com.techgv.stash.SEED -p com.techgv.stash --include-stopped-packages --ei count 50000
adb -s <serial> shell am broadcast -a com.techgv.stash.WIPE -p com.techgv.stash --include-stopped-packages
```

- `am broadcast` returns when done and prints e.g. `seeded 50000 in 12806 ms` (tablet emulator). Logs only counts and
  durations.
- SEED replaces earlier seeded items with exactly `count` new ones: 40 sources, two years of dates, about 60 % unread,
  25 % read, 15 % archived, metadata already fetched. Seeded items have a domain ending in `.seed.stash.invalid`
  (a reserved name no real link has); WIPE deletes exactly those rows, so the app's own items are kept.
- The receiver requires `android.permission.DUMP`: the shell can send to it, apps cannot. It exists only in
  `androidApp/src/benchmark` (decision D31); the release APK has no receiver, class or string of it.
- Each run re-plans reminders once. The widget is republished on the app's next foreground: bring the app up once
  before measuring the widget. The first foreground after seeding also runs auto-archive (read items older than the
  setting move to the archive).

## Benchmarks (`:benchmark`, by hand, never part of `check`)

```
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR   # emulator only
```

- Always pass `ANDROID_SERIAL`: a physical phone is attached to this machine. Without
  `leaveApksInstalledAfterRun=true` AGP uninstalls the app after the run, which deletes its data.
- One class: add `-Pandroid.testInstrumentationRunnerArguments.class=com.techgv.stash.benchmark.StartupBenchmark`.
- Results: `benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/*.json`
  (the directory is cleared by the next run; copy it first).
- Precondition: the app has already asked for the notification permission (open it once and answer), or its prompt
  covers the shelf. The share benchmark saves links on the seeded host and wipes them at the end.

## Regenerate the baseline profile (emulator only)

```
ANDROID_SERIAL=emulator-5556 ./gradlew :androidApp:generateBaselineProfile \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

The plugin reinstalls its `nonMinifiedRelease` build (the app's data is lost), grants the notification permission
and shares 12 links, so never run it on a phone with real data. It writes
`androidApp/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` (`mergeIntoMain`: one profile for every
variant); commit both. Versions: AndroidX Benchmark and the Baseline Profile Gradle plugin 1.5.0 (latest stable on
Google Maven on 14 Nov 2026; 1.6.0-alpha01 exists), profileinstaller 1.4.1, UiAutomator 2.4.1; they work with AGP 9.1.1.

Coverage (`BaselineProfileGenerator`, 5 runs on the tablet emulator, API 35): a share into the stopped app and the
quick-save sheet, cold start to the shelf, a shelf scroll, opening a card in the reader and closing it, Home → Search →
typing a query → back. 37,743 rules; every journey is also in the startup profile (`includeInStartupProfile = true`),
which puts more than startup code in the primary dex.

## Numbers: tablet emulator (Nav3_Pixel_Tablet_API35), benchmark build, Partial(baseline profile) unless noted

Emulator numbers are noisy and only compare builds on the same emulator; this host was heavily loaded during every
run (load average 10–28 on 11 cores, frames of several seconds). They say nothing about real devices.

| Benchmark | Result |
|---|---|
| `StartupBenchmark.coldStartBaselineProfile` | 10 runs: timeToInitialDisplay min 794 ms, median 7,012 ms, max 9,755 ms (first run, which also waited for a card) |
| `StartupBenchmark.coldStartNoCompilation` | NOT DONE: first run failed in the old "wait for a card" step (UiAutomator saw no nodes for 30 s); the step was removed; a second run passed but its results were lost (output directory cleared by the next run) |
| `ShareToSaveBenchmark.coldShare` (timeToInitialDisplay, sheetFirstFrameMs) | NOT DONE: one run passed but its results were lost (as above); an earlier run failed with Macrobenchmark's DROP_SHADER_CACHE broadcast timing out under load. Trace check by hand on 5 earlier runs: SEND to the sheet's first frame 655–5,821 ms |
| `ShelfScrollBenchmark.scroll40` | 10 runs: frameDurationCpu P50 560 ms, P90 1,018 ms; frameOverrun P50 861 ms, P90 1,503 ms |
| `ShelfScrollBenchmark.scroll50000` | 10 runs: frameDurationCpu P50 77 ms, P90 107 ms, P99 813 ms; frameOverrun P50 96 ms, P90 141 ms |

The two scroll results are from different runs under different load and are not comparable to each other. Real
numbers: run the commands above on the physical phone (with the benchmark build installed over the debug build).

## R8 smoke test (benchmark build, tablet emulator, by hand)

Launch, share a link (saved, metadata fetched through Ktor / OkHttp and parsed), open the reader (web page), reader
mode, search (FTS), settings (theme change), the widget (pinned, shows the shelf; a row opens the reader) and a
reminder notification (fired by moving the clock to the planned 09:00 slot) all worked. No crash, no keep rule needed.
