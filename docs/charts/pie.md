# Pie chart

Pie or donut with tap selection, per-slice gradients, and a center content slot.

```kotlin
val data = PieDataSet(
    entries = listOf(
        PieEntry(id = "design", label = "Design", value = 30f),
        PieEntry(id = "dev", label = "Development", value = 70f)
    ),
    contentDescription = "Team budget"
)

PieChart(dataSet = data, modifier = Modifier.size(280.dp))
```

Values don't need to add up to anything — each slice is drawn as its share of
the total.

## Donut

`donutRatio` is the size of the hole as a fraction of the radius. It defaults to
`0.45f`, so you get a donut unless you ask for a pie:

```kotlin
PieChart(dataSet = data, style = PieChartStyle(donutRatio = 0f))  // solid pie
```

With a hole, you can put something in it:

```kotlin
PieChart(dataSet = data) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("100", style = MaterialTheme.typography.headlineMedium)
        Text("total")
    }
}
```

The slot is ignored when `donutRatio` is 0.

## Selection

Hoisted, same as the other charts:

```kotlin
var selected by remember { mutableStateOf<PieEntry?>(null) }

PieChart(
    dataSet = data,
    selectedEntry = selected,
    onSliceSelected = { selected = it }
)
```

The selected slice scales up slightly and the rest dim. Tapping the hole or
outside the ring clears the selection.

## Fills

Every slice takes a `SliceBrush`, falling back to `PieDataSet.defaultBrush`:

```kotlin
PieEntry(
    id = "dev",
    label = "Development",
    value = 70f,
    brush = SliceBrush.Linear(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
        angleDegrees = 45f
    )
)
```

`SliceBrush.Solid` for a flat color, `Linear` for a gradient across the slice,
plus `Radial` and `Sweep`.

## Tiny slices

A slice worth 0.3% is invisible and untappable. `minSliceAngle` gives every
non-zero slice a floor, and the rest are scaled down so the total still reaches
360°:

```kotlin
style = PieChartStyle(minSliceAngle = 8f)
```

Zero-value slices stay at zero — the floor only applies to slices that exist.

## Tooltips

Two renderers ship with the library:

```kotlin
// Tooltip near the slice (default)
selectionRenderer = TooltipPieSelectionRenderer()

// Label on a leader line outside the chart
selectionRenderer = ElbowCalloutPieSelectionRenderer()
```

The elbow callout suits charts with long labels, since it has room to breathe
outside the ring.

## Layout

```kotlin
style = PieChartStyle(
    startAngle = -90f,       // -90 = 12 o'clock
    sliceSpacingAngle = 2f,  // gap between slices, in degrees
    fillFraction = 0.6f      // share of the canvas the chart occupies
)
```

Set `outerRadius` to a fixed `Dp` if you'd rather size it exactly than have it
fit the canvas.

In RTL layouts slices are laid out counter-clockwise.

## Reference

| Parameter | Purpose |
|---|---|
| `dataSet` | Slices, default brush, accessibility description |
| `style` | Donut ratio, angles, spacing, sizing, min slice angle |
| `animationConfig` | Entry, morph, and selection timing |
| `a11yConfig` | Screen-reader text builders |
| `selectionRenderer` | Tooltip or elbow callout |
| `selectedEntry` / `onSliceSelected` | Hoisted selection state |
| `selectionHaptic` | Haptic on select; `null` to disable |
| `centerContent` | Composable shown in the donut hole |
