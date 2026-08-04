# Line chart

Multi-series line chart with smooth curves, area fills, and a drag crosshair.

![A line chart with one series rising from 42 to 90 across seven points, drawn as a smooth curve with a light area fill.](../assets/charts/line.png)

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
    yLabelFormatter = { "${it.toInt()}k" },
    xLabelFormatter = { "Day ${it.toInt()}" }
)
```

Tick values are rounded to readable numbers (1, 2, 5 × 10ⁿ) that fully contain
your data, so you won't get an axis labelled 3.7, 7.4, 11.1.

`includeZeroInYRange` forces the axis to start at zero. Without it the axis fits
the data, which exaggerates small changes — useful sometimes, misleading others.

`xLabelFormatter` only runs for points that left `label` empty. Set
`LineDataPoint.label` and it is used as it is.

## Pinning the range

Auto-scaling fits each chart to its own data, which is wrong when several charts
have to be read against each other. Pin the range and they share a scale:

```kotlin
axisConfig = LineAxisConfig(
    yMin = 0f,
    yMax = 10f,
    xMin = -1f,
    xMax = 25f
)
```

Each is independent — set one, two, or all four. An unset bound is still
computed from the data.

A pinned bound is used exactly as given. Nice-number rounding is off for that
axis, since rounding works by moving the bound outwards to reach a round step,
and the range is divided into `yTickCount` equal steps instead. Pin `0f..10f`
with the default five ticks and the labels are 0, 2, 4, 6, 8, 10. Pin `0f..7f`
and they are 0, 1.4, 2.8 and so on — pick bounds that divide, or format them
with `yLabelFormatter`.

`yMin` takes precedence over `includeZeroInYRange`.

A line that leaves a pinned range is cut where it crosses the bound and picks up
again where it comes back, leaving a gap. The gap is the point: a line flattened
along the top edge would read as a run of values sitting exactly at `yMax`, when
they are really above it and off the chart.

A point outside the range gets no dot, no x-label, no vertical grid line and no
crosshair, and it cannot be selected: touch snaps to the nearest point that is on
the axis, and no "Select …" action is published for it. The chart's own
description still counts it, because it is still in your data — override
`chartDescriptionBuilder` if you would rather it did not.

Setting `selectedPointIndex` yourself is not filtered, so a chart can still be
asked to select a point that is off the axis. Nothing is drawn for it.

`xMin` is the left edge in LTR and the right edge in RTL, and can be negative —
useful when the axis starts before zero, like a baseline day of `-1`.

A range that cannot work is ignored rather than obeyed: `yMax` below `yMin`, the
two equal, a bound below the data with the other left unset, or a `NaN`. Any of
those would divide by a span of zero or less and stack every point on one edge,
so the axis silently falls back to fitting the data. Both axes behave this way.

Colors sit on the same config:

```kotlin
axisConfig = LineAxisConfig(
    axisColor = Color(0xFF94A3B8),
    labelColor = Color(0xFF475569),
    gridColor = Color(0xFFF1F5F9),
    dashedGrid = true
)
```

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
