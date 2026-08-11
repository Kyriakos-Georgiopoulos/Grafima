# Accessibility

Every chart ships accessible by default. This page covers what you get for free
and what you can change.

## What each chart exposes

A chart is a single merged node — a screen reader announces it once rather than
walking its internals. Each one declares `Role.Image`, matching the convention
for data visualizations on the web.

`LineLegend` is also a single merged node, so it costs one stop rather than one
per series. It carries no role or description of its own: what it adds is the
colour beside each name, which a screen reader cannot use, and the chart's
description already names every series.

The description is built from your data:

> "Bar Chart representing Monthly revenue. Jan value is 45. Feb value is 80."

Selection is exposed separately as a state description, so selecting a bar
announces only *"Currently selected: Feb, 80."* instead of re-reading the whole
chart. Charts are polite live regions, so data changes are announced without the
user re-focusing.

The gauge is the exception: it has nothing to select, so it exposes progress
semantics instead of a live region.

## Navigating without touch

Every interactive chart publishes one custom action per item — bar, slice, series
or point — plus a clear action when something is selected. Screen reader users
reach these through the actions menu; they don't need to hit a target.

The one exception is a line chart with a pinned x range: points outside it are not
drawn and get no action, because selecting one would move the crosshair somewhere
nothing is visible. See [the line chart guide](charts/line.md).

Give your entries distinct labels. Two bars both labelled "Q1" and carrying no
`seriesId` produce two identical actions and one of them becomes unreachable.
Grouped bars are the exception: they share an `xLabel` by design, and the default
`A11yConfig.selectActionLabel` adds the series so the actions stay distinct.

A line chart's `xAxisTitle` and `yAxisTitle` are appended to its description, so
the numbers reach a screen reader with the unit they are in. Set neither and the
description is exactly what the builder produced. The wording around them is
`axisTitleDescriptionBuilder`, so it translates with the rest.

## Changing the wording

Every chart takes an `a11yConfig` with builders for its text. Override any of
them:

```kotlin
BarChart(
    dataSet = data,
    a11yConfig = A11yConfig(
        chartDescriptionBuilder = { "Revenue for ${it.contentDescription}" },
        selectedStateDescription = { entry ->
            entry?.let { "${it.xLabel}: ${it.y.toInt()} thousand euro" }
                ?: "No bar selected."
        }
    )
)
```

Worth reading yours out loud. `"45.0"` is fine on screen and awkward spoken.

A line chart's reference lines are announced through
`LineA11yConfig.referenceLineDescriptionBuilder`. It is handed only the lines that
are actually drawn — one outside the axis range, or any line on a chart with no
series, is left out, so a listener is never told about a threshold nobody can see.
Each is named by `ReferenceLine.spokenLabel`: its `contentDescription`, or its
drawn `label` when it has none. Give a line neither and it is drawn but not
announced.

Value labels add nothing to the description. A listener already reaches any value
by selecting its point, and reading all of them out up front would bury the
summary the description opens with.

The chart's own description stays a summary — a count, not a reading of every
item. It sits on a live region, so anything in it is repeated on every selection.
Per-item values belong in `selectedStateDescription`, which is spoken only when
the selection actually changes.

## Reduced motion

When the OS reports reduced motion, animations collapse to instant. You don't
have to do anything.

On desktop that is the one thing you do have to do yourself. The JVM exposes no
portable reduce-motion setting, so the chart assumes motion is wanted; read the
platform preference in your app and provide `LocalReduceMotion`.

To control it yourself — a preview, a screenshot test, or your own in-app
toggle — provide `LocalReduceMotion`:

```kotlin
CompositionLocalProvider(LocalReduceMotion provides true) {
    BarChart(dataSet = data)
}
```

`null` (the default) defers to the platform.

One limitation: the OS setting is read once when the chart enters composition,
so toggling it takes effect on the next launch rather than immediately.
`LocalReduceMotion` applies on the next recomposition.

## Contrast

The default palette meets WCAG AA against a light surface, and this is checked
in CI. If you restyle a chart, check your own colors — the label grey sits close
to the 4.5:1 threshold and a small change can drop below it.

## Testing

The library's accessibility semantics are covered by automated tests on both
platforms. What tests can't check is whether an announcement actually makes
sense out loud — for that, see [MANUAL_TESTING.md](MANUAL_TESTING.md).
