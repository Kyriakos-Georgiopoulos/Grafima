# Grafima Library Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the single-module `:app` demo into a publishable Android charting library (`:library` → `io.grafima:grafima`) plus a `:sample` app that consumes it, and wire it for Maven Central.

**Architecture:** Two Gradle modules in one repo. `:library` (`com.android.library`, namespace `io.grafima.charts`) holds only the five chart composables and their public API. `:sample` (`com.android.application`, namespace `io.grafima.sample`) holds the theme, launcher, navigation, and all demo screens, depending on `project(":library")`. Publishing via the Vanniktech Maven Publish plugin targeting the Central Portal.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, Compose BOM 2026.02.01, Material3 (sample only), Vanniktech `com.vanniktech.maven.publish`.

## Global Constraints

- Maven coordinates: `io.grafima:grafima:1.0.0` (artifactId stays `grafima` even though the module path is `:library`).
- Kotlin package root: `io.grafima.*` (charts → `io.grafima.charts`, app → `io.grafima.sample`, theme → `io.grafima.sample.ui.theme`). No `com.grafima.*` may remain.
- `minSdk = 24`, `compileSdk = 37`, `targetSdk = 37` (sample only). Java 11 source/target. Kotlin code style `official`. (compileSdk/targetSdk were bumped 36→37 in a preliminary baseline fix — `androidx.core:core:1.19.0` and `androidx.lifecycle:…:2.11.0` require API 37.)
- Library depends on Compose `foundation`, `ui`, `ui-graphics`, `ui-text`, `animation`, `runtime` only — **never `material3` or `activity-compose`**. Compose types in public signatures use `api(...)`.
- `kotlin { explicitApi() }` on the library.
- No chart feature/visual/behavior changes. No new chart types.
- Host OS is macOS (`darwin`): `sed -i ''` form required. Shell is zsh. Build via `./gradlew`.
- Platform: run every `sed`/`git mv`/`mkdir` from the repo root `/Users/kgeorgiopoulos/AndroidStudioProjects/Grafima`.

---

### Task 1: Initialize git baseline

The repo is not yet under version control; per-task commits require it.

**Files:**
- Modify: none (VCS init only)

**Interfaces:**
- Consumes: nothing
- Produces: a git repo with a clean baseline commit of the current `:app` project.

- [ ] **Step 1: Confirm not already a git repo**

Run: `git -C /Users/kgeorgiopoulos/AndroidStudioProjects/Grafima rev-parse --is-inside-work-tree 2>&1 || echo "NOT A REPO"`
Expected: prints `NOT A REPO`.

- [ ] **Step 2: Initialize and create baseline commit**

```bash
cd /Users/kgeorgiopoulos/AndroidStudioProjects/Grafima
git init
git add -A
git commit -m "chore: baseline before library restructure

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Verify the tree is clean**

Run: `git status --short`
Expected: no output (working tree clean).

- [ ] **Step 4: Sanity-build the current project**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Establishes a known-good starting point.)

---

### Task 2: Rename module `:app` → `:sample` and package `com.grafima.*` → `io.grafima.*`, wire the launcher to the charts

After this task the project is still one module, but named `:sample`, fully on the `io.grafima` package root, and — for the first time — the app actually shows `ChartsDemoScreen()` instead of the leftover "Hello Android" template.

**Files:**
- Rename dir: `app/` → `sample/`
- Move: `sample/src/main/java/com/grafima/charts/*.kt` → `sample/src/main/java/io/grafima/charts/`
- Move: `sample/src/main/java/com/grafima/MainActivity.kt` → `sample/src/main/java/io/grafima/sample/MainActivity.kt`
- Move: `sample/src/main/java/com/grafima/charts/ChartsDemoScreen.kt` → `sample/src/main/java/io/grafima/sample/ChartsDemoScreen.kt`
- Move: `sample/src/main/java/com/grafima/ui/theme/*.kt` → `sample/src/main/java/io/grafima/sample/ui/theme/`
- Move: test files under `com/grafima` → `io/grafima/sample`
- Modify: `settings.gradle.kts`, `sample/build.gradle.kts`, package headers, `MainActivity.kt` body, instrumented test assertion.

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: module path `:sample`; package `io.grafima.charts` (charts + embedded per-chart demos), `io.grafima.sample` (`MainActivity`, `ChartsDemoScreen`), `io.grafima.sample.ui.theme` (`GrafimaTheme`, `Typography`, color vals). `ChartsDemoScreen()` is `public @Composable fun` with no params.

- [ ] **Step 1: Rename the module directory**

```bash
cd /Users/kgeorgiopoulos/AndroidStudioProjects/Grafima
git mv app sample
```

- [ ] **Step 2: Create the new package directories and move sources**

```bash
cd /Users/kgeorgiopoulos/AndroidStudioProjects/Grafima
mkdir -p sample/src/main/java/io/grafima/charts
mkdir -p sample/src/main/java/io/grafima/sample/ui/theme
mkdir -p sample/src/test/java/io/grafima/sample
mkdir -p sample/src/androidTest/java/io/grafima/sample

# ChartsDemoScreen is app navigation -> io.grafima.sample
git mv sample/src/main/java/com/grafima/charts/ChartsDemoScreen.kt sample/src/main/java/io/grafima/sample/ChartsDemoScreen.kt
# Remaining chart files (with their embedded demos, for now) -> io.grafima.charts
git mv sample/src/main/java/com/grafima/charts/BarChart.kt   sample/src/main/java/io/grafima/charts/BarChart.kt
git mv sample/src/main/java/com/grafima/charts/PieChart.kt   sample/src/main/java/io/grafima/charts/PieChart.kt
git mv sample/src/main/java/com/grafima/charts/LineChart.kt  sample/src/main/java/io/grafima/charts/LineChart.kt
git mv sample/src/main/java/com/grafima/charts/GaugeChart.kt sample/src/main/java/io/grafima/charts/GaugeChart.kt
git mv sample/src/main/java/com/grafima/charts/RadarChart.kt sample/src/main/java/io/grafima/charts/RadarChart.kt

git mv sample/src/main/java/com/grafima/MainActivity.kt sample/src/main/java/io/grafima/sample/MainActivity.kt
git mv sample/src/main/java/com/grafima/ui/theme/Color.kt sample/src/main/java/io/grafima/sample/ui/theme/Color.kt
git mv sample/src/main/java/com/grafima/ui/theme/Theme.kt sample/src/main/java/io/grafima/sample/ui/theme/Theme.kt
git mv sample/src/main/java/com/grafima/ui/theme/Type.kt  sample/src/main/java/io/grafima/sample/ui/theme/Type.kt

git mv sample/src/test/java/com/grafima/ExampleUnitTest.kt sample/src/test/java/io/grafima/sample/ExampleUnitTest.kt
git mv sample/src/androidTest/java/com/grafima/ExampleInstrumentedTest.kt sample/src/androidTest/java/io/grafima/sample/ExampleInstrumentedTest.kt

# Drop now-empty old package dirs
rm -rf sample/src/main/java/com sample/src/test/java/com sample/src/androidTest/java/com
```

- [ ] **Step 3: Rewrite package headers**

```bash
cd /Users/kgeorgiopoulos/AndroidStudioProjects/Grafima
# charts (5 files still carry the demo section; package unchanged root)
for f in sample/src/main/java/io/grafima/charts/*.kt; do
  sed -i '' 's/^package com\.grafima\.charts$/package io.grafima.charts/' "$f"
done
# theme
for f in sample/src/main/java/io/grafima/sample/ui/theme/*.kt; do
  sed -i '' 's/^package com\.grafima\.ui\.theme$/package io.grafima.sample.ui.theme/' "$f"
done
# ChartsDemoScreen moved to sample package
sed -i '' 's/^package com\.grafima\.charts$/package io.grafima.sample/' sample/src/main/java/io/grafima/sample/ChartsDemoScreen.kt
# tests
sed -i '' 's/^package com\.grafima$/package io.grafima.sample/' sample/src/test/java/io/grafima/sample/ExampleUnitTest.kt
sed -i '' 's/^package com\.grafima$/package io.grafima.sample/' sample/src/androidTest/java/io/grafima/sample/ExampleInstrumentedTest.kt
sed -i '' 's/"com\.grafima"/"io.grafima.sample"/' sample/src/androidTest/java/io/grafima/sample/ExampleInstrumentedTest.kt
```

- [ ] **Step 4: Point `ChartsDemoScreen` at the chart-package demos**

`ChartsDemoScreen.kt` now lives in `io.grafima.sample` but calls `BarChartDemoScreen()` etc. which are still in `io.grafima.charts`. Add a wildcard import so it resolves. Edit `sample/src/main/java/io/grafima/sample/ChartsDemoScreen.kt` — immediately after the `package io.grafima.sample` line, add:

```kotlin
import io.grafima.charts.BarChartDemoScreen
import io.grafima.charts.GaugeChartDemoScreen
import io.grafima.charts.LineChartDemoScreen
import io.grafima.charts.PieChartDemoScreen
import io.grafima.charts.RadarChartDemoScreen
```

- [ ] **Step 5: Rewrite `MainActivity.kt` to render the charts**

Replace the entire contents of `sample/src/main/java/io/grafima/sample/MainActivity.kt` with:

```kotlin
package io.grafima.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.grafima.sample.ui.theme.GrafimaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrafimaApp()
        }
    }
}

@Composable
fun GrafimaApp() {
    GrafimaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChartsDemoScreen()
        }
    }
}
```

- [ ] **Step 6: Update `settings.gradle.kts`**

In `/Users/kgeorgiopoulos/AndroidStudioProjects/Grafima/settings.gradle.kts`, change the last include line from `include(":app")` to:

```kotlin
include(":sample")
```

- [ ] **Step 7: Update the module Gradle namespace/applicationId**

In `sample/build.gradle.kts`, change the two identifiers:

```kotlin
android {
    namespace = "io.grafima.sample"
    // ...
    defaultConfig {
        applicationId = "io.grafima.sample"
        // ...
    }
}
```
Leave everything else (compileSdk, minSdk, dependencies) unchanged for now.

- [ ] **Step 8: Build the renamed module**

Run: `./gradlew :sample:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If a chart file reports an unresolved reference, confirm its `package` line reads `io.grafima.charts`.

- [ ] **Step 9: Verify no `com.grafima` references remain**

Run: `grep -rn "com\.grafima" sample/src settings.gradle.kts sample/build.gradle.kts || echo "CLEAN"`
Expected: `CLEAN`.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: rename :app to :sample and package to io.grafima, show charts

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Extract the `:library` module

Move the five chart *core* implementations into a new `com.android.library` module, split each embedded `*DemoScreen()` into its own file under `:sample`, and make `:sample` depend on `project(":library")`.

**Files:**
- Create: `library/build.gradle.kts`
- Create: `library/src/main/AndroidManifest.xml`
- Create: `library/consumer-rules.pro`
- Move: `sample/src/main/java/io/grafima/charts/*.kt` → `library/src/main/java/io/grafima/charts/`
- Create: `sample/src/main/java/io/grafima/sample/{Bar,Pie,Line,Gauge,Radar}ChartDemoScreen.kt`
- Modify: each `library/.../<Chart>.kt` (delete demo section + `material3` imports)
- Modify: `settings.gradle.kts`, `sample/build.gradle.kts`, `gradle/libs.versions.toml`, `sample/.../ChartsDemoScreen.kt`

**Interfaces:**
- Consumes: `io.grafima.charts` package from Task 2.
- Produces: library artifact source set at `io.grafima.charts`; the public chart entry points `BarChart`, `PieChart`, `LineChart`, `GaugeChart`, `RadarChart` (unchanged signatures). Sample demo functions `BarChartDemoScreen()`, `PieChartDemoScreen()`, `LineChartDemoScreen()`, `GaugeChartDemoScreen()`, `RadarChartDemoScreen()` now live in `io.grafima.sample`.

- [ ] **Step 1: Add the `android-library` plugin alias to the version catalog**

In `gradle/libs.versions.toml`, under `[plugins]`, add this line after the `android-application` line:

```toml
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [ ] **Step 2: Create the library module directories and move chart sources**

```bash
cd /Users/kgeorgiopoulos/AndroidStudioProjects/Grafima
mkdir -p library/src/main/java/io/grafima/charts
git mv sample/src/main/java/io/grafima/charts/BarChart.kt   library/src/main/java/io/grafima/charts/BarChart.kt
git mv sample/src/main/java/io/grafima/charts/PieChart.kt   library/src/main/java/io/grafima/charts/PieChart.kt
git mv sample/src/main/java/io/grafima/charts/LineChart.kt  library/src/main/java/io/grafima/charts/LineChart.kt
git mv sample/src/main/java/io/grafima/charts/GaugeChart.kt library/src/main/java/io/grafima/charts/GaugeChart.kt
git mv sample/src/main/java/io/grafima/charts/RadarChart.kt library/src/main/java/io/grafima/charts/RadarChart.kt
rmdir sample/src/main/java/io/grafima/charts
```

- [ ] **Step 3: Create `library/src/main/AndroidManifest.xml`**

A library manifest has no application/launcher:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 4: Create `library/consumer-rules.pro`**

The charts use no reflection, so consumer rules are empty by design:

```proguard
# Grafima is pure Compose with no reflection or serialization.
# No consumer keep rules are required at this time.
```

- [ ] **Step 5: Create `library/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.grafima.charts"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    // Compose types appear in Grafima's public API -> exposed via `api`.
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
```

- [ ] **Step 6: Add the missing Compose catalog aliases**

`ui`, `ui-graphics`, `ui-tooling`, and `ui-tooling-preview` already exist in `gradle/libs.versions.toml`. Add `foundation`, `runtime`, and `animation` under `[libraries]`:

```toml
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }
androidx-compose-animation = { group = "androidx.compose.animation", name = "animation" }
```

- [ ] **Step 7: Register the module in `settings.gradle.kts`**

Change the include line to:

```kotlin
include(":library", ":sample")
```

- [ ] **Step 8: Extract each chart's demo section into `:sample`**

For each chart, cut the demo block (the `// N. DEMO IMPLEMENTATION` divider comment and everything below it to end-of-file) out of the library file and paste it into a new sample file. Give each new file this header, then the pasted demo code:

```kotlin
package io.grafima.sample

import io.grafima.charts.*
```
followed by the **exact import block copied verbatim from the top of the original chart file** (this guarantees every Compose/Material3/kotlin symbol the demo uses resolves; unused imports are warnings, not errors, and get cleaned in Step 11).

Do this for all five:

| Cut from (library) | Demo divider to cut at | Also cut | Paste into (sample) |
|---|---|---|---|
| `library/.../BarChart.kt` | `// 5. DEMO IMPLEMENTATION` → EOF (`fun BarChartDemoScreen`) | — | `sample/.../BarChartDemoScreen.kt` |
| `library/.../PieChart.kt` | `// 6. DEMO IMPLEMENTATION` → EOF (`fun PieChartDemoScreen`) | — | `sample/.../PieChartDemoScreen.kt` |
| `library/.../LineChart.kt` | `// 5. DEMO` → EOF (`fun LineChartDemoScreen`) | — | `sample/.../LineChartDemoScreen.kt` |
| `library/.../GaugeChart.kt` | `// 4. DEMO IMPLEMENTATION` → EOF | `private data class GaugePreset` + `private val GaugePresets` (they sit in the demo block) | `sample/.../GaugeChartDemoScreen.kt` |
| `library/.../RadarChart.kt` | `// 6. DEMO IMPLEMENTATION` → EOF (`fun RadarChartDemoScreen`) | — | `sample/.../RadarChartDemoScreen.kt` |

- [ ] **Step 9: Remove the now-unresolvable `material3` imports from the library files**

The library has no `material3` dependency; those imports were used only by the demos just removed. In each of the five `library/.../<Chart>.kt` files delete these three import lines:

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
```

Run to confirm none remain in the library:
Run: `grep -rn "androidx.compose.material3" library/src || echo "CLEAN"`
Expected: `CLEAN`.

- [ ] **Step 10: Wire `:sample` to depend on `:library` and drop the charts-package import in `ChartsDemoScreen`**

In `sample/build.gradle.kts`, add to the `dependencies { }` block:

```kotlin
implementation(project(":library"))
```

In `sample/src/main/java/io/grafima/sample/ChartsDemoScreen.kt`, the five `import io.grafima.charts.*DemoScreen` lines added in Task 2 Step 4 now point at functions that live in the same `io.grafima.sample` package. Delete those five import lines.

- [ ] **Step 11: Build the library in isolation, then the sample**

Run: `./gradlew :library:assembleRelease`
Expected: `BUILD SUCCESSFUL`. (Proves the library compiles with no `material3`/demo code.)

Run: `./gradlew :sample:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Optimize imports (optional cleanup)**

Unused imports left in the sample demo files are warnings only. Optionally remove them; the build already passes without doing so.

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "refactor: extract :library module, move demos into :sample

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: API hygiene — `explicitApi()` + remove `ChartEntry`

**Files:**
- Modify: `library/build.gradle.kts` (add `kotlin { explicitApi() }`)
- Modify: `library/src/main/java/io/grafima/charts/BarChart.kt` (drop `ChartEntry`)
- Modify: all five `library/.../<Chart>.kt` (add explicit `public` + return types as the compiler flags them)

**Interfaces:**
- Consumes: the library source set from Task 3.
- Produces: `BarEntry` as a standalone `@Immutable data class` (properties `id: String`, `xLabel: String`, `y: Float`, `gradientColors: List<Color>?`, `colorStops: List<Pair<Float, Color>>?`); all public declarations carry explicit visibility. No signature/behavior change to any chart entry point.

- [ ] **Step 1: Remove the `ChartEntry` base class**

In `library/src/main/java/io/grafima/charts/BarChart.kt`, replace:

```kotlin
@Stable
open class ChartEntry(open val id: String, open val xLabel: String, open val y: Float)

/**
 * A single bar in the chart.
 * ...
 */
@Immutable
data class BarEntry(
    override val id: String,
    override val xLabel: String,
    override val y: Float,
    val gradientColors: List<Color>? = null,
    val colorStops: List<Pair<Float, Color>>? = null
) : ChartEntry(id, xLabel, y)
```

with (keep the existing KDoc block above `BarEntry`):

```kotlin
/**
 * A single bar in the chart.
 * ...
 */
@Immutable
data class BarEntry(
    val id: String,
    val xLabel: String,
    val y: Float,
    val gradientColors: List<Color>? = null,
    val colorStops: List<Pair<Float, Color>>? = null
)
```

- [ ] **Step 2: Verify the class still builds before turning on strict mode**

Run: `./gradlew :library:assembleRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Enable `explicitApi()` in the library**

In `library/build.gradle.kts`, add a top-level `kotlin { }` block after the `android { }` block:

```kotlin
kotlin {
    explicitApi()
}
```

- [ ] **Step 4: Compile and collect the violations**

Run: `./gradlew :library:compileReleaseKotlin`
Expected: FAIL — the compiler prints one error per public declaration lacking an explicit visibility modifier or (for functions/properties) an explicit return type, e.g.
`Visibility must be specified in explicit API mode` and
`Return type must be specified in explicit API mode`.

- [ ] **Step 5: Apply the mechanical fixes the compiler asks for**

For every reported declaration, apply the rule the message states. The transformations are mechanical:

- Top-level/public composables and functions get an explicit `public`:
  `fun BarChart(` → `public fun BarChart(`
- Public classes / data classes / interfaces get `public`:
  `class ChartAnimationEngine` → `public class ChartAnimationEngine`,
  `data class BarEntry(` → `public data class BarEntry(`,
  `fun interface BarChartSelectionRenderer` → `public fun interface BarChartSelectionRenderer`
- Public extension/factory funcs need an explicit return type where missing:
  `fun Color.toSliceBrush() = SliceBrush.Solid(this)` → `public fun Color.toSliceBrush(): SliceBrush = SliceBrush.Solid(this)` (this one already declares its type — leave as-is).
- Declarations that should NOT be part of the API get `private`/`internal` instead of `public` — the `private fun`/`internal val` helpers already in the files keep their modifiers; only genuinely public API becomes `public`.

Re-run `./gradlew :library:compileReleaseKotlin` after each pass, fixing the newly-topmost errors, until it reports `BUILD SUCCESSFUL`. Do not add `public` to anything already marked `private`/`internal`.

- [ ] **Step 6: Full library + sample build**

Run: `./gradlew :library:assembleRelease :sample:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Confirms the sample still resolves every public symbol after the visibility pass.)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(library): enable explicitApi and drop ChartEntry base

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Maven Central publishing config + docs

**Files:**
- Modify: `gradle/libs.versions.toml` (add Vanniktech plugin)
- Modify: `build.gradle.kts` (root — plugin alias `apply false`)
- Modify: `library/build.gradle.kts` (apply plugin + `mavenPublishing { }`)
- Create: `README.md` (usage + publishing prerequisites)

**Interfaces:**
- Consumes: the finished library from Task 4.
- Produces: `./gradlew :library:publishToMavenLocal` emitting `io.grafima:grafima:1.0.0` with `.aar`, `.pom`, `-sources.jar`, `-javadoc.jar` into `~/.m2/repository/io/grafima/grafima/1.0.0/`.

- [ ] **Step 1: Add the Vanniktech plugin to the version catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:

```toml
vanniktechPublish = "0.34.0"
```
(Pin to the current release; check https://plugins.gradle.org/plugin/com.vanniktech.maven.publish and bump if a newer one exists.)

Add under `[plugins]`:

```toml
vanniktech-maven-publish = { id = "com.vanniktech.maven.publish", version.ref = "vanniktechPublish" }
```

- [ ] **Step 2: Declare the plugin at the root (apply false)**

In the root `build.gradle.kts`, extend the `plugins { }` block:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}
```

- [ ] **Step 3: Apply and configure publishing in the library**

In `library/build.gradle.kts`, add to the `plugins { }` block:

```kotlin
alias(libs.plugins.vanniktech.maven.publish)
```

At the end of the file, add:

```kotlin
mavenPublishing {
    coordinates("io.grafima", "grafima", "1.0.0")

    pom {
        name.set("Grafima")
        description.set("A performant, animated Jetpack Compose charts library (bar, pie, line, gauge, radar).")
        url.set("https://github.com/kgeorgiopoulos/Grafima") // set to the real repo URL
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("kgeorgiopoulos")
                name.set("Kyriakos Georgiopoulos")
            }
        }
        scm {
            url.set("https://github.com/kgeorgiopoulos/Grafima")
            connection.set("scm:git:git://github.com/kgeorgiopoulos/Grafima.git")
            developerConnection.set("scm:git:ssh://git@github.com/kgeorgiopoulos/Grafima.git")
        }
    }

    publishToMavenCentral() // Central Portal is the default target in current plugin versions
    signAllPublications()
}
```

- [ ] **Step 4: Verify publishing wiring against the local Maven repo**

Signing is skipped for local publishing, so this needs no keys.

Run: `./gradlew :library:publishToMavenLocal -Dorg.gradle.internal.publish.checksums.insecure=true`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Confirm the artifacts landed**

Run: `ls ~/.m2/repository/io/grafima/grafima/1.0.0/`
Expected: lists `grafima-1.0.0.aar`, `grafima-1.0.0.pom`, `grafima-1.0.0-sources.jar`, `grafima-1.0.0-javadoc.jar` (and `.module`).

- [ ] **Step 6: Create `README.md`**

Create `/Users/kgeorgiopoulos/AndroidStudioProjects/Grafima/README.md`:

````markdown
# Grafima

A performant, animated charts library for Jetpack Compose — bar, pie/donut, line, gauge, and radar. Pure `Canvas` rendering, allocation-free draw loops, RTL + accessibility support.

## Install

```kotlin
dependencies {
    implementation("io.grafima:grafima:1.0.0")
}
```

## Usage

See the `:sample` app (`io.grafima.sample`) for a runnable demo of every chart.

```kotlin
BarChart(
    dataSet = BarDataSet(
        entries = listOf(
            BarEntry("jan", "Jan", 45f),
            BarEntry("feb", "Feb", 80f),
        )
    ),
    modifier = Modifier.fillMaxWidth().height(300.dp),
)
```

## Building

- `./gradlew :sample:assembleDebug` — build the demo app.
- `./gradlew :library:assembleRelease` — build the library.
- `./gradlew :library:publishToMavenLocal` — publish to `~/.m2` for local testing.

## Publishing to Maven Central (maintainers)

One-time prerequisites (performed outside this repo):

1. **Central Portal account** at https://central.sonatype.com.
2. **Namespace verification** for `io.grafima` — add the TXT record the Portal
   provides to the `grafima.io` domain's DNS.
3. **GPG signing key** — generate, then publish the public key to a keyserver.
4. **Credentials** in `~/.gradle/gradle.properties` (never commit these):

   ```properties
   mavenCentralUsername=<central-portal-token-user>
   mavenCentralPassword=<central-portal-token-password>
   signingInMemoryKey=<ascii-armored-private-key>
   signingInMemoryKeyPassword=<key-password>
   ```

Then release with:

```bash
./gradlew :library:publishAndReleaseToMavenCentral --no-configuration-cache
```

## License

Apache 2.0 © Kyriakos Georgiopoulos
````

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "build: configure Maven Central publishing and add README

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Post-Plan Manual Verification

- [ ] Run the `:sample` app on a device/emulator; swipe through all five tabs (Bar, Pie, Radar, Gauge, Line). Confirm each chart animates in, selection/crosshair works, and "Update"/"Randomize" buttons morph data — i.e. identical behavior to before the restructure.
- [ ] In a throwaway consumer project, add `mavenLocal()` and `implementation("io.grafima:grafima:1.0.0")`; confirm `BarChart(...)` resolves and compiles against the published-locally artifact.
