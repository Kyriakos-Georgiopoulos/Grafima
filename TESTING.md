# Testing

Grafima's tests live in `:library` and run on two platforms from a single
`commonTest` source set, plus a shared UI suite.

[MANUAL_TESTING.md](MANUAL_TESTING.md) covers what automation can't reach —
screen-reader announcements, animation feel, and readability on real displays.

| Source set | What lives there | Runs on |
|---|---|---|
| `library/src/commonTest` | Chart math, animation engines, accessibility string defaults | JVM **and** iOS simulator |
| `library/src/uiTest` | Compose UI tests (`runComposeUiTest`): semantics, accessibility actions, pixel regression | iOS simulator **and** Android device/emulator |

`src/uiTest` isn't a Kotlin source set of its own — the directory is added to
both `iosTest` and `androidDeviceTest`, so one suite compiles into both test
targets.

## Running

```bash
# JVM (fast — runs everything in commonTest)
./gradlew :library:testAndroid

# iOS simulator (commonTest + the UI suite)
./gradlew :library:iosSimulatorArm64Test

# Android instrumented UI tests (needs a connected device or running emulator)
./gradlew :library:connectedAndroidDeviceTest

# Everything, plus lint (what CI runs)
./gradlew build
```

Requirements: JDK 17+ (Android Studio's bundled JBR works); a Mac with Xcode
and an iOS simulator runtime for the iOS tasks; a device with USB debugging or
a running emulator for `connectedAndroidDeviceTest`.

The iOS test task defaults to the **iPhone 17 Pro** simulator. If your machine
doesn't have that device, pass any available one:

```bash
./gradlew :library:iosSimulatorArm64Test -PiosSimulatorDevice="iPhone 16"
# list available devices:
xcrun simctl list devices available
```

## Filtering

```bash
# One class on the JVM
./gradlew :library:testAndroid --tests "io.grafima.charts.bar.BarChartAnimationEngineTest"
```

## Reports

HTML reports land in:

- `library/build/reports/tests/testAndroidHostTest/index.html`
- `library/build/reports/tests/iosSimulatorArm64Test/index.html`

Test names are plain-English sentences, so the report reads as a behavior
spec — e.g. *"a cancelled scope never applies stale values after a dataset
swap"*.

## Conventions

- **Test names are sentences.** The name must be a claim the test actually
  proves. `commonTest` uses backticked names with spaces; `uiTest` uses
  `underscore_separated` names instead — those tests are dexed for Android
  instrumentation, and D8 rejects spaces in class names below DEX version 040
  (Kotlin derives lambda class names from the enclosing function, and the
  library targets minSdk 24).
- **No wall-clock time.** Animation tests run on virtual time via
  `runTest` + a hand-pumped `BroadcastFrameClock` (see `AnimationTestHarness`).
- **Accessibility defaults are pinned as exact strings** — for screen-reader
  text, silent drift is a bug.
- **Accessibility is tested at three levels**: exact screen-reader strings and
  WCAG contrast ratios in `commonTest`; per-chart semantics and actions in
  `uiTest`; and cross-chart contracts plus large font scales, RTL, and empty
  datasets in `AccessibilityContractTest` / `AccessibilityEnvironmentTest`.
- Engine tests assert on `internal` state deliberately; the UI suite covers
  the public surface through semantics.

## Public API

`library/api/library.klib.api` records Grafima's public API. CI fails if the
code drifts from it, so API changes show up as a reviewable diff rather than
slipping through unnoticed.

```bash
# Did I change the public API?
./gradlew :library:checkKotlinAbi

# Yes, and it was intentional — update the dump and commit it.
./gradlew :library:updateKotlinAbi
```

The dump covers the klib targets, which is the entire public surface:
`androidMain` contains no public API, only an internal `actual`.
