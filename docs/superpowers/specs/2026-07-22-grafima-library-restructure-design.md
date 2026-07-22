# Grafima — Library + Example App Restructure

**Date:** 2026-07-22
**Status:** Approved design (pre-implementation)
**Author:** Kyriakos Georgiopoulos (with Claude)

## Goal

Turn the current single-module `:app` demo project into a **publishable Android
charting library** plus a **sample app** that consumes it. Target distribution is
**Maven Central** under a dedicated `io.grafima` namespace.

No chart features, visuals, or behavior change in this pass. This is a
structural / packaging / API-hygiene reorganization only.

## Decisions (locked)

| Topic | Decision |
|---|---|
| Distribution | Maven Central (Central Portal) |
| Module split | Single library module |
| Library module path | `:library` → `Grafima/library/` |
| Sample module path | `:sample` → `Grafima/sample/` (renamed from `:app`) |
| Maven coordinates | `io.grafima:grafima:1.0.0` |
| Kotlin package | Rename `com.grafima.*` → `io.grafima.*` |
| Publishing tooling | Vanniktech `com.vanniktech.maven.publish` |
| explicitApi() | Enabled (strict) on the library |
| `open class ChartEntry` | Removed; `BarEntry` becomes standalone |

## Target Repository Layout

```
Grafima/
├── settings.gradle.kts              # include(":library", ":sample")
├── build.gradle.kts                 # plugin aliases (apply false)
├── gradle/libs.versions.toml        # + android-library, vanniktech-publish plugins
├── library/                         # com.android.library → io.grafima:grafima
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml       # minimal, no launcher/application
│       └── java/io/grafima/charts/
│           ├── BarChart.kt   PieChart.kt   LineChart.kt
│           ├── GaugeChart.kt  RadarChart.kt
│           └── (charts + data models + config + selection
│                renderers + animation engines + internal helpers ONLY)
└── sample/                          # com.android.application
    ├── build.gradle.kts             # implementation(project(":library"))
    └── src/main/
        ├── AndroidManifest.xml       # launcher Activity
        ├── java/io/grafima/sample/
        │   ├── MainActivity.kt              # renders ChartsDemoScreen()
        │   ├── ChartsDemoScreen.kt          # moved from charts pkg
        │   ├── BarChartDemoScreen.kt        # extracted from BarChart.kt
        │   ├── PieChartDemoScreen.kt        # extracted from PieChart.kt
        │   ├── LineChartDemoScreen.kt       # extracted from LineChart.kt
        │   ├── GaugeChartDemoScreen.kt      # extracted from GaugeChart.kt (+ GaugePreset)
        │   ├── RadarChartDemoScreen.kt      # extracted from RadarChart.kt
        │   └── ui/theme/  (Color.kt, Theme.kt, Type.kt)
        └── res/  (mipmap/drawable icons, values/strings, themes, xml/*)
```

## Component Responsibilities

### `:library` (published artifact)
- **What it does:** Provides the five public chart composables and their public
  configuration surface. Pure Compose Canvas rendering, no app scaffolding.
- **Public API per chart:** the `@Composable` entry point (`BarChart`, `PieChart`,
  `LineChart`, `GaugeChart`, `RadarChart`), its `@Immutable` data models + config
  data classes, the `fun interface *SelectionRenderer` and stock implementations,
  and the `@Stable *AnimationEngine` classes. `private`/`internal` helpers stay hidden.
- **Depends on:** Compose BOM, and `foundation`, `ui`, `ui-graphics`, `ui-text`,
  `animation`, `runtime`. Compose types that appear in public signatures
  (`Color`, `Brush`, `TextStyle`, `Dp`, `PathEffect`, `DrawScope`, …) are exposed
  via `api(...)`; the rest via `implementation(...)`.
- **Does NOT depend on:** `material3`, `activity-compose` (demo-only).

### `:sample` (example app, not published)
- **What it does:** Demonstrates every chart. Owns the app theme, launcher,
  navigation (`HorizontalPager` tab bar), and all `*DemoScreen` composables.
- **Depends on:** `project(":library")`, `material3`, `activity-compose`,
  `lifecycle-runtime-ktx`, Compose tooling (debug).

## Dependency / API Boundary Detail

Move list (library → sample):
- Every `*DemoScreen()` composable currently living at the bottom of each chart file.
- `ChartsDemoScreen.kt` (currently in `com.grafima.charts`) → `io.grafima.sample`.
- Demo-only helpers: `GaugePreset` data class + `GaugePresets` list (GaugeChart.kt).
- `ui/theme/*` and all `res/*` (they are app resources).
- `MainActivity.kt` — rewritten to call `ChartsDemoScreen()` instead of the
  leftover `Greeting("Android")` template.

Import fix-up: after demos move to `io.grafima.sample`, they reference charts via
`import io.grafima.charts.*`.

## Gradle / Build Configuration

### `settings.gradle.kts`
- `rootProject.name = "Grafima"`
- `include(":library", ":sample")`
- Keep existing `pluginManagement` / `dependencyResolutionManagement` blocks.

### `gradle/libs.versions.toml`
- Add plugin: `android-library = { id = "com.android.library", version.ref = "agp" }`
- Add plugin: `vanniktech-maven-publish = { id = "com.vanniktech.maven.publish", version.ref = "vanniktechPublish" }` (pin `vanniktechPublish` to the current release at implementation time).
- Existing `material3` / `activity-compose` library entries stay; they are now referenced by `:sample` only.

### `library/build.gradle.kts`
- plugins: `android.library`, `kotlin.compose`, `vanniktech.maven.publish`
- `android { namespace = "io.grafima.charts"; compileSdk 36; defaultConfig { minSdk 24 }; buildFeatures { compose = true } }` — the Vanniktech plugin configures the `release` publication and sources/javadoc jars automatically, so no manual `publishing { singleVariant(...) }` block is needed.
- `consumerProguardFiles("consumer-rules.pro")`
- `kotlin { explicitApi() }`
- `mavenPublishing { coordinates("io.grafima", "grafima", "1.0.0"); pom { name, description, url, licenses(Apache-2.0), developers, scm }; publishToMavenCentral(); signAllPublications() }`

### `sample/build.gradle.kts`
- plugins: `android.application`, `kotlin.compose`
- `namespace = "io.grafima.sample"`, `applicationId = "io.grafima.sample"`
- `dependencies { implementation(project(":library")); material3; activity-compose; … }`

### Credentials (out of git — user-provided)
Read from `~/.gradle/gradle.properties` or env:
`mavenCentralUsername`, `mavenCentralPassword`, and signing keys
(`signingInMemoryKey`, `signingInMemoryKeyPassword`).

## API-Hygiene Changes

- **`explicitApi()`**: annotate public declarations with explicit visibility and
  return types where the compiler flags them (data classes, top-level funcs,
  extension `Color.toSliceBrush()`, engine classes, etc.).
- **Remove `open class ChartEntry`**: make `BarEntry` a standalone
  `@Immutable data class` with its own `id`/`xLabel`/`y` properties. No behavior change.

## Out of Scope

- Per-chart / multi-module split.
- New chart types, features, or visual changes.
- A new automated test suite. Existing template tests
  (`ExampleUnitTest`, `ExampleInstrumentedTest`) move to `:sample` unchanged.
- The Maven Central account, `grafima.io` DNS TXT verification, and GPG key
  generation — these are user/ops steps. The build wiring and a README section
  documenting them are in scope.

## Risks / Notes

- Package rename is repo-wide but mostly mechanical (per-file `package` header +
  source directory move `com/grafima` → `io/grafima`; intra-package refs are
  unqualified so need no change until demos are split out).
- The repo is **not currently a git repository**, so the design doc cannot be
  committed. Recommend `git init` before implementation so changes are tracked.
- Compose BOM `2026.02.01` + AGP `9.2.1` + Kotlin `2.2.10` are retained as-is.

## Verification Strategy

- `./gradlew :library:assembleRelease` compiles the library with `explicitApi()`.
- `./gradlew :sample:assembleDebug` builds the example against `project(":library")`.
- `./gradlew :library:publishToMavenLocal` produces the artifact + POM + sources +
  javadoc jars locally (validates publishing config without touching Central).
- Manual: run `:sample`, confirm all five demos render and interact as before.
