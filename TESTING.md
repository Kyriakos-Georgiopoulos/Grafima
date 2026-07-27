# Testing

Grafima's tests live in `:library` and run on two platforms from a single
`commonTest` source set, plus an iOS-only UI suite.

| Source set | What lives there | Runs on |
|---|---|---|
| `library/src/commonTest` | Chart math, animation engines, accessibility string defaults | JVM **and** iOS simulator |
| `library/src/iosTest` | Compose UI tests (`runComposeUiTest`): semantics, accessibility actions, pixel regression | iOS simulator only |

Compose UI tests are iOS-only on purpose: on Android they would need
instrumentation (an emulator), which this repo doesn't require for now.

## Running

```bash
# JVM (fast — runs everything in commonTest)
./gradlew :library:testAndroid

# iOS simulator (commonTest + the UI suite)
./gradlew :library:iosSimulatorArm64Test

# Everything, plus lint (what CI runs)
./gradlew build
```

Requirements: JDK 17+ (Android Studio's bundled JBR works), and for the iOS
tasks a Mac with Xcode and an iOS simulator runtime installed.

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

- **Test names are sentences** (backticked function names). The name must be a
  claim the test actually proves.
- **No wall-clock time.** Animation tests run on virtual time via
  `runTest` + a hand-pumped `BroadcastFrameClock` (see `AnimationTestHarness`).
- **Accessibility defaults are pinned as exact strings** — for screen-reader
  text, silent drift is a bug.
- Engine tests assert on `internal` state deliberately; the UI suite covers
  the public surface through semantics.
