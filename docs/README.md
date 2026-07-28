# Grafima documentation

## Charts

- [Bar](charts/bar.md) — vertical or horizontal bars, tap selection
- [Line](charts/line.md) — multi-series lines, smooth curves, drag crosshair
- [Pie](charts/pie.md) — pie or donut with a center slot
- [Radar](charts/radar.md) — several series across shared axes
- [Gauge](charts/gauge.md) — one value on an arc, with zones

## Guides

- [Accessibility](ACCESSIBILITY.md) — what's built in, and how to change it
- [Testing](TESTING.md) — running the suites
- [Manual testing](MANUAL_TESTING.md) — the checks automation can't do
- [Working with an AI assistant](AI_CONTRIBUTING.md) — conventions and guardrails

## Common ground

A few things hold across every chart, so they're only explained once here.

**Selection is hoisted.** No chart owns its selection state. You pass the
current selection in and receive changes through a callback. That means you can
drive selection from anywhere — a list, a deep link, a test.

**Ids drive animation.** `BarEntry.id`, `PieEntry.id`, `RadarSeries.id`,
`LineSeries.id`: keep an id stable across a data change and the chart animates
from the old value to the new one. Change it and the element restarts from zero.

**RTL works.** Every chart mirrors when the layout direction is right-to-left,
including the details — which end of a bar is rounded, which way slices sweep.

**Reduced motion is respected.** When the OS asks for less motion, animations
become instant. See [Accessibility](ACCESSIBILITY.md) to override it.

**Charts fill their modifier.** Give them a size. `Modifier.height(300.dp)` for
bar and line; a square for pie, radar and gauge.
