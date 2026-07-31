# Concepts

A few things hold across every chart, so they are explained once here instead of
five times.

**Selection is hoisted.** No chart owns its selection state. You pass the current
selection in and receive changes through a callback, which means you can drive
selection from anywhere: a list, a deep link, a test.

**Ids drive animation.** `BarEntry.id`, `PieEntry.id`, `RadarSeries.id`,
`LineSeries.id`. Keep an id stable across a data change and the chart animates
from the old value to the new one. Change it and the element restarts from zero.

**RTL works.** Every chart mirrors when the layout direction is right to left,
including the details, down to which end of a bar is rounded and which way slices
sweep.

**Reduced motion is respected.** When the OS asks for less motion, animations
become instant. See [Accessibility](ACCESSIBILITY.md) to override it.

**Charts fill their modifier.** Give them a size. `Modifier.height(300.dp)` for
bar and line, a square for pie, radar and gauge.
