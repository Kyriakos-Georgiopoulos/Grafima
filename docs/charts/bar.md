# Bar chart

Vertical or horizontal bars with tap selection, animated entry, and RTL support.

![A bar chart of monthly revenue, five bars labelled Jan to May with their values above each bar.](../assets/charts/bar.png)

```kotlin
val data = BarDataSet(
    entries = listOf(
        BarEntry(id = "jan", xLabel = "Jan", y = 45f),
        BarEntry(id = "feb", xLabel = "Feb", y = 80f)
    ),
    contentDescription = "Monthly revenue"
)

BarChart(dataSet = data, modifier = Modifier.height(300.dp))
```

## Selection

Selection is hoisted — you own it. The chart tells you what was tapped and
renders whatever you pass back:

```kotlin
var selected by remember { mutableStateOf<BarEntry?>(null) }

BarChart(
    dataSet = data,
    selectedEntry = selected,
    onBarSelected = { selected = it }
)
```

`onBarSelected` fires with the tapped entry, or `null` when the user taps the
same bar again or taps empty space. If you pass a `selectedEntry` that isn't in
`dataSet`, the chart clears it for you.

## Ids matter

`BarEntry.id` is how the chart tracks a bar across data changes. Keep ids stable
and a bar animates from its old value to the new one. Change the id and the
chart treats it as a new bar that grows from zero.

```kotlin
// Same id: Jan animates 45 → 60
BarEntry(id = "jan", xLabel = "Jan", y = 60f)
```

## Groups and stacks

Give entries a `seriesId` to compare several measures over the same categories.
Neighbouring entries that share an `xLabel` become one category:

```kotlin
BarChart(
    dataSet = BarDataSet(
        entries = listOf(
            BarEntry("q1-rev",  "Q1", 45f, seriesId = "rev",  seriesLabel = "Revenue"),
            BarEntry("q1-cost", "Q1", 30f, seriesId = "cost", seriesLabel = "Cost"),
            BarEntry("q2-rev",  "Q2", 80f, seriesId = "rev",  seriesLabel = "Revenue"),
            BarEntry("q2-cost", "Q2", 52f, seriesId = "cost", seriesLabel = "Cost")
        ),
        mode = BarGroupMode.Grouped
    )
)
```

`BarGroupMode.Grouped` sets a category's bars side by side, which compares series
against each other. `BarGroupMode.Stacked` piles them into one bar, which compares
each series against the category total. Stack only when the parts genuinely sum to
something: stacking unrelated measures produces a total that means nothing.

Three rules are worth knowing:

- **Only neighbours group.** A category ends as soon as the `xLabel` changes, so a
  label reused further down the list opens a new category rather than reaching back.
  Build the list category by category.
- **An entry with no `seriesId` is always its own bar.** A dataset that sets none
  behaves exactly as it did before groups existed, in either `mode`.
- **The axis clears the stack, not the segment.** In `Stacked` the y-axis is scaled
  to the tallest total, so segments are never clipped. `BarEntry.y` must be positive;
  a negative value is not drawn.

Colors are per entry, as they always were, so a series gets its color by giving
every entry of that series the same `gradientColors`. Spacing within a group is
`ChartStyle.groupSpacingFactor`; set it to `0f` to make the bars of a group touch.

Each bar keeps its own selection and its own accessibility action, labelled with
both the category and the series, so `Select Q1, Revenue` and `Select Q1, Cost`
are distinct. Give every series a `seriesLabel`, or screen-reader users hear the
raw `seriesId`.

There is no bar legend. A grouped chart needs a key, so draw one from the same
data — the labels are already on the entries:

```kotlin
dataSet.entries
    .filter { it.seriesId != null }
    .distinctBy { it.seriesId }
    .mapNotNull { entry ->
        entry.spokenSeriesLabel?.to(
            entry.gradientColors ?: dataSet.defaultGradientColors
        )
    }
```

`gradientColors` is null on any bar that falls back to the dataset default, so read
through to `defaultGradientColors`. A bar setting `colorStops` overrides both.

Screen-reader users do not need it: the chart's description counts the bars and the
groups, and every action names its own series.

## Legend

A grouped or stacked chart draws two or more bars per category and tells them apart
by colour alone. The chart names the series in its tooltip and to a screen reader,
but only once a bar is selected — at rest, or in a screenshot, a sighted reader has
nothing to read the colours against.

```kotlin
Column {
    BarLegend(dataSet = data)
    BarChart(dataSet = data, modifier = Modifier.fillMaxWidth().height(300.dp))
}
```

It is a separate composable rather than part of the drawing, so you place it above,
beside or below and the plot keeps its full height.

```kotlin
BarLegend(
    dataSet = data,
    orientation = LegendOrientation.Vertical,
    textStyle = MaterialTheme.typography.labelMedium
)
```

Each series is listed once, in the order it first appears, with a swatch taking that
series' colours from its first bar. A series that sets none of its own is given a
gradient from `BarDataSet.seriesPalette`, so grouped and stacked bars are tellable
apart without the caller choosing colours at all; set that palette to an empty list
to go back to one `defaultGradientColors` for every series.

A dataset whose entries carry no `seriesId` draws nothing at all — those bars are
named by the axis already, and a key repeating those names says nothing new. That
means the same `BarLegend` call can sit above a chart that switches between single,
grouped and stacked without needing to be conditional.

`Horizontal` wraps onto further lines when the entries do not fit. `Vertical` takes
an `entryAlignment` for the edge the entries line up on.

A screen reader reaches the legend as one item naming every series, rather than a
stop each. Unlike the line chart, the bar chart's own description carries counts
rather than series names, so this key is the only place a listener meets them before
selecting a bar — pass `describe` to reword or translate what the item is called.

## Orientation

```kotlin
BarChart(dataSet = data, orientation = BarOrientation.Horizontal)
```

Horizontal puts category labels on the left and values along the bottom. In RTL
layouts both orientations mirror, including which end of the bar is rounded.

## Colors

Per-bar gradient, falling back to the dataset default:

```kotlin
BarEntry(
    id = "feb",
    xLabel = "Feb",
    y = 80f,
    gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
)
```

For control over where colors sit along the gradient, use `colorStops` instead —
it takes priority over `gradientColors`:

```kotlin
colorStops = listOf(
    ColorStop(0f, Color.Green),
    ColorStop(0.8f, Color.Yellow),
    ColorStop(1f, Color.Red)
)
```

## Axis and grid

```kotlin
BarChart(
    dataSet = data,
    axisConfig = AxisConfig(
        yAxisSteps = 5,
        showGridLines = true,
        gridDashPattern = null  // solid grid lines
    )
)
```

`dashEffect` is deprecated and removed in 2.0. It is no longer the way to get a
solid grid: `null` now means *unset* and falls through to `gridDashPattern`. If you
passed `dashEffect = null`, pass `gridDashPattern = null` instead — the compiler
will not warn you, because a deprecated constructor property warns where it is
read, not where it is set.

The y-axis maximum is computed for you: 20% headroom above the tallest bar,
rounded up to a round number so the labels stay readable.

## Animation

Bars stagger in on first appearance and morph on value changes.

```kotlin
animationConfig = AnimationConfig(
    initialEntrySpec = tween(600),
    staggerDelayMs = 60L
)
```

If the user has animations turned off at the OS level, every spec collapses to
`snap()` automatically. See [Accessibility](../ACCESSIBILITY.md) to override that.

## Tooltips

The default tooltip shows the value above the selected bar. Swap it by passing
your own `BarChartSelectionRenderer` — it's a `fun interface`, so a lambda works:

```kotlin
selectionRenderer = TooltipSelectionRenderer(
    backgroundColor = Color(0xFF1F2937),
    cornerRadius = 12.dp
)
```

## Reference

| Parameter | Purpose |
|---|---|
| `dataSet` | Bars, grouping mode, and the chart's accessibility description |
| `orientation` | `Vertical` (default) or `Horizontal` |
| `style` | Bar shape, spacing, text styles, floating values |
| `axisConfig` | Y-axis labels, grid lines, step count |
| `animationConfig` | Entry, morph, and selection timing |
| `a11yConfig` | Screen-reader text builders |
| `selectionRenderer` | Draws the selection indicator |
| `selectedEntry` / `onBarSelected` | Hoisted selection state |
| `selectionHaptic` | Haptic on select; `null` to disable |
