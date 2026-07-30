# Grafima

Charts for Compose Multiplatform. Android and iOS, one codebase.

Five chart types — bar, line, pie, radar, gauge — drawn on `Canvas`, animated,
and accessible by default.

```kotlin
BarChart(
    dataSet = BarDataSet(
        entries = listOf(
            BarEntry(id = "jan", xLabel = "Jan", y = 45f),
            BarEntry(id = "feb", xLabel = "Feb", y = 80f)
        ),
        contentDescription = "Monthly revenue"
    ),
    modifier = Modifier.fillMaxWidth().height(300.dp)
)
```

## Documentation

Start with [docs/README.md](docs/README.md), or go straight to a chart:

[Bar](docs/charts/bar.md) · [Line](docs/charts/line.md) ·
[Pie](docs/charts/pie.md) · [Radar](docs/charts/radar.md) ·
[Gauge](docs/charts/gauge.md)

## What you get

**Accessibility that works.** Every chart is a single labelled node with a
description built from your data, selection announced as state, and a custom
action per entry so screen reader users don't have to hit a target. Contrast is
checked in CI.

**Animation that behaves.** Charts stagger in and morph between datasets.
Reduced motion is respected automatically.

**RTL throughout.** Not just mirrored coordinates — the details, down to which
end of a bar is rounded.

**Selection you control.** No chart owns its selection state. You pass it in and
get changes back.

## Requirements

- Android minSdk 24
- iOS: `iosArm64` and `iosSimulatorArm64` (Apple silicon simulators; Compose
  Multiplatform publishes no `iosX64`)
- Kotlin 2.3.21, Compose Multiplatform 1.11.1

## Running the sample

The `sample` module holds the demo UI shared by both apps.

```bash
./gradlew :androidApp:installDebug     # Android
open iosApp/iosApp.xcodeproj           # iOS, then run from Xcode
```

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md) covers the branching model and what a PR needs
to pass. [docs/TESTING.md](docs/TESTING.md) covers the test suites and how to run
them. If you're working with an AI assistant, point it at
[docs/AI_CONTRIBUTING.md](docs/AI_CONTRIBUTING.md) first — it has the
conventions and the checks that catch mistakes.

## License

Apache 2.0 — see [LICENSE](LICENSE).
