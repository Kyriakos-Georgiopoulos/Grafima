# Line chart

Multi-series line chart with smooth curves, area fills, and a drag crosshair.

```kotlin
val data = LineDataSet(
    series = listOf(
        LineSeries(
            id = "revenue",
            label = "Revenue",
            points = listOf(
                LineDataPoint(x = 0f, y = 10f, label = "Jan"),
                LineDataPoint(x = 1f, y = 25f, label = "Feb"),
                LineDataPoint(x = 2f, y = 18f, label = "Mar")
            )
        )
    ),
    contentDescription = "Quarterly revenue"
)

LineChart(dataSet = data, modifier = Modifier.height(300.dp))
```

`x` is a real number, not an index — points can be unevenly spaced and the chart
positions them correctly.

## Multiple series

Add more entries to `series`. They share the axes and draw in order, so later
series sit on top:

```kotlin
series = listOf(
    LineSeries(id = "2025", label = "2025", points = lastYear, color = Color.Gray),
    LineSeries(id = "2026", label = "2026", points = thisYear, color = Color(0xFF6366F1))
)
```

## Curves

```kotlin
style = LineChartStyle(curveType = LineCurveType.MonotoneCubic)
```

`MonotoneCubic` smooths the line without inventing peaks — it never overshoots
above or below your actual data points, which matters when the line represents
something real. `Linear` connects points with straight segments.

## Area fill

```kotlin
LineSeries(
    id = "revenue",
    label = "Revenue",
    points = points,
    fillAlpha = 0.15f,
    fillGradientColors = listOf(Color(0xFF6366F1), Color.Transparent)
)
```

`fillAlpha` defaults to 0, so there's no fill until you ask for one.

## Crosshair

Press and drag across the chart and the crosshair snaps to the nearest point,
with a haptic tick each time it moves:

```kotlin
var selected by remember { mutableStateOf<Int?>(null) }

LineChart(
    dataSet = data,
    selectedPointIndex = selected,
    onPointSelected = { selected = it }
)
```

`onPointSelected` gives you the index while dragging and `null` on release.
Disable it with `crosshairConfig = LineCrosshairConfig(enabled = false)`.

The index is yours to hold, so it can outlive the data it pointed at. The chart
skips a stale index rather than crashing, but the crosshair disappears — clear
it yourself when the data changes if that matters to you.

## Axes

```kotlin
axisConfig = LineAxisConfig(
    yTickCount = 5,
    includeZeroInYRange = true,
    valueFormatter = { "${it.toInt()}k" }
)
```

Tick values are rounded to readable numbers (1, 2, 5 × 10ⁿ) that fully contain
your data, so you won't get an axis labelled 3.7, 7.4, 11.1.

`includeZeroInYRange` forces the axis to start at zero. Without it the axis fits
the data, which exaggerates small changes — useful sometimes, misleading others.

## Animation

Points animate up from the baseline, staggered along the line and across series:

```kotlin
animationConfig = LineAnimationConfig(
    entrySpec = tween(700),
    staggerMs = 12L,        // between points
    seriesStaggerMs = 120L  // between series
)
```

## Reference

| Parameter | Purpose |
|---|---|
| `dataSet` | Series and the accessibility description |
| `style` | Curve type, dots, stroke, minimum size |
| `axisConfig` | Ticks, grid, labels, value formatting |
| `crosshairConfig` | Crosshair and tooltip appearance; enable/disable |
| `animationConfig` | Entry and morph timing, stagger |
| `a11yConfig` | Screen-reader text builders |
| `selectedPointIndex` / `onPointSelected` | Hoisted crosshair position |
| `selectionHaptic` | Haptic per snap; `null` to disable |
