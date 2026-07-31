# Accessibility

Every chart ships accessible by default. This page covers what you get for free
and what you can change.

## What each chart exposes

A chart is a single merged node — a screen reader announces it once rather than
walking its internals. Each one declares `Role.Image`, matching the convention
for data visualizations on the web.

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

Give your entries distinct labels. Two bars both labelled "Q1" produce two
identical actions and one of them becomes unreachable.

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

The chart's own description stays a summary — a count, not a reading of every
item. It sits on a live region, so anything in it is repeated on every selection.
Per-item values belong in `selectedStateDescription`, which is spoken only when
the selection actually changes.

## Reduced motion

When the OS reports reduced motion, animations collapse to instant. You don't
have to do anything.

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
