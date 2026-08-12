# Changelog

Notable changes to Grafima. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Grafima's public API is recorded in `library/api/library.klib.api` and
`library/api/jvm/library.api`. Any entry under **Changed** or **Removed** that
alters a signature has a matching diff in those files; entries that only change
behaviour do not.

## [Unreleased]

### Added

- Grouped and stacked bar charts. Give `BarEntry` a `seriesId` and neighbouring
  entries that share an `xLabel` become one category, drawn side by side or piled
  into a single bar according to `BarDataSet.mode`. Both orientations and RTL are
  handled, each bar keeps its own selection and accessibility action, and the
  y-axis clears the tallest stack rather than the tallest segment.
- `BarEntry.seriesId` and `BarEntry.seriesLabel`, `BarGroupMode`, `BarDataSet.mode`,
  `ChartStyle.groupSpacingFactor`, `BarChartSummary`, `A11yConfig.selectActionLabel`,
  `A11yConfig.clearSelectionLabel` and the `BarEntry.spokenSeriesLabel` extension. Every one is defaulted, so a
  dataset that sets no series behaves exactly as it did in 1.1.1.
- `LineSeries.dotRadius` sizes one series' dots on their own, so a "you are here"
  marker can outweigh the curve it marks instead of drawing at the same weight as
  the readings around it. `Dp.Unspecified` keeps `LineChartStyle.dotRadius`, matching
  how `outerRadius` defers on the pie and radar charts, and `0.dp` drops that series'
  dots while the rest of the chart keeps theirs. `showDots` still decides whether any
  dot is drawn at all. Dots are drawn after every series' fill, so a marker keeps its
  weight wherever it sits in the list.

### Changed

- The default `A11yConfig.selectedStateDescription` names the series when a bar has
  one, so the two bars of a group are told apart. Its signature is unchanged and a
  bar without a series is described exactly as before.
- **Source breaking.** `A11yConfig.barCountDescriptionBuilder: (Int) -> String` is
  removed and replaced by `countDescriptionBuilder: (BarChartSummary) -> String`, so
  one override covers a dataset whether or not it carries series. A count alone
  cannot describe grouping, and a second builder beside it would have left a chart
  that localised only the first reverting to English the day it gained a series. The
  default wording is unchanged for an ungrouped chart; ragged categories say
  "3 bars in 2 groups" rather than claiming a group size they do not share.
- **Behaviour breaking.** The line chart's crosshair reads each series at the x it
  stopped on rather than at the selected point's position in the list. This carries
  no api diff, so it is the one entry here you cannot catch by diffing `library/api/`.
  A one-point "you are here" marker used to be drawn at every x the first series had
  a point at, so hovering January put the marker's dot and tooltip line there while
  the marker sat in August; it is now named at its own x and nowhere else. The cost
  is that series which were index-aligned on *differing* x values — two periods laid
  side by side, say — lose the second series' crosshair dot, tooltip line and spoken
  value, because it has no reading at the x you stopped on. Give both series the same
  x values, or make the second a `ReferenceLine`. `selectedPointIndex` still indexes
  the first series and is unchanged. The default
  `LineA11yConfig.selectedPointDescriptionBuilder` follows the same rule, so a series
  with no point at the selected x is no longer announced; an override that indexes
  `points` by hand keeps its old behaviour and will disagree with what is drawn.
- **Binary incompatible.** `BarEntry`, `BarDataSet`, `ChartStyle`, `A11yConfig` and
  `LineSeries` each gained a defaulted constructor parameter. Apart from the builder rename above
  your code compiles unchanged, but Kotlin regenerates a data class's constructor and
  `copy` for the new arity rather than keeping the old one — the removed signatures
  are in the api diff.
  Anything compiled against 1.1.1 and run against this release without recompiling
  fails with `NoSuchMethodError`. Recompile dependents; publish no mixed set.

### Fixed

- A dataset replaced outright no longer draws the old items alongside the new ones.
  Departing items were threaded back into an axis they shared no ids with, so
  swapping a bar chart's months for quarters drew both sets on one axis and split
  the groups between them. Emptying a dataset is still an exit animation: everything
  leaving is all there is to draw. Affects the bar, pie, line and radar charts.
- Bar chart hit testing follows the bars while one is animating out. It measured
  against the dataset while the draw pass measured against what was on screen, so
  every tap landed on the wrong bar for the length of a removal.
- A removed bar's exit animation is no longer restarted from full duration by an
  unrelated data change, so a chart updating faster than the animation still lets
  its departing bars finish and be released.
- A bar removed while another is selected fades with the rest rather than staying
  at full opacity while it animates out.
- Bar chart touch handling followed `ChartStyle.bottomLabelSpace` and `topValueSpace`.
  Both were read when hit testing but neither restarted the gesture detector, so
  changing either left taps aimed at where the bars used to be.
- The horizontal bar chart's selection tooltip is drawn on the end the bar grows
  towards. In RTL it was placed past the bar's other end, landing on the axis over
  the category labels. `TooltipSelectionRenderer` reads the layout direction from
  the draw scope, so a custom `BarChartSelectionRenderer` can do the same without
  a signature change.

## [1.1.1] - 2026-08-10

### Fixed

- Dashed grid lines and dashed reference lines rendered solid on Android below API
  28. They were drawn with `drawLine`, and the hardware canvas takes a fast path
  for it that ignores the path effect; they now go through a `Path`, which honours
  it on every API. Dashed series strokes were never affected. The bar chart's grid
  has dashed by default since 1.0.0, so this is the first release where that is
  true on an older device.

### Changed

- `compileSdk` is 36 rather than 37, and `minSdk` 21 rather than 24. AGP writes
  `compileSdk` into the aar as `minCompileSdk`, so a library on 37 refuses to build
  for any consumer below it — a hard gate, not a warning. Nothing here needed
  either floor: the only platform call in the library reads
  `Settings.Global.ANIMATOR_DURATION_SCALE`, which is API 17, and there is no
  `@RequiresApi` or `SDK_INT` check anywhere in `library/src`.
  Reported by [Leo Colman](https://github.com/LeoColman) in #32 and #33.

  On API 21 the semantics, accessibility, interaction and layout tests all pass.
  The screenshot-based ones cannot run there — `captureToImage` goes through
  `PixelCopy`, which is API 26 and up — so CI runs the API-independent classes at
  the floor and the full suite on the newer emulator.

## [1.1.0] - 2026-08-08

### Added

- A `jvm` target, so the charts run on Compose for Desktop as well as Android and
  iOS, and a `desktopApp` running the sample in a window. Needs Java 11 or newer,
  matching Compose Multiplatform's own desktop artifacts. Reduce motion is the one
  thing desktop cannot read for itself: the JVM exposes no portable setting, so
  charts animate unless your host provides `LocalReduceMotion`.
  Thanks to [Lauren Darcey](https://github.com/ldarcey).
- `LineLegend`, a key mapping each series' colour to its label. The chart names
  its series only on selection, so a chart at rest — or a screenshot of one — gave
  a sighted reader no way to tell overlaid lines apart. Placed by the caller, so
  the plot keeps its full width. A series with `strokeGradientColors` gets a
  gradient swatch.
- `LineAxisConfig.referenceLines` draws a threshold across the plot at a fixed axis
  value — a target, a limit, or "now" on an axis of hours. Each `ReferenceLine`
  names its own axis, so it cannot be given both an x and a y. Drawn over the
  series, since a marker behind the data is not a marker. The axis widens to reach
  the line, because a target is normally above what has been achieved so far and an
  axis fitted to the data would leave it off the chart; `includeInRange = false`
  opts out, and a line still outside the range is skipped rather than clamped.
  `ReferenceLine.label` is drawn beside the line in its own colour and announced
  through `LineA11yConfig.referenceLineDescriptionBuilder`. `spokenLabel` resolves
  what a screen reader says — the `contentDescription` when set, otherwise the
  label. A drawn label claims its space before value labels do, so the two never
  overlap. See [docs/charts/line.md](docs/charts/line.md).
- `LineSeries.dashPattern` dashes a stroke, which is what tells a reader a series
  is derived rather than measured — a moving average against the readings it
  averages. `LineLegend` dashes that series' swatch to match. The area fill is
  never dashed. `DashPattern` lives in `io.grafima.charts`, since the bar chart's
  grid takes one too.
- `LineChartStyle.valueLabels` prints each point's value beside it, rather than
  only in the tooltip once something is selected, which suits a chart of few
  points and a screenshot of one. Labels take the side of their point the curve
  leaves open, and one that would overlap a label already drawn is dropped rather
  than stacked on it. `useSeriesColor` prints each in its own series' colour, which
  says which line a number belongs to on a crowded chart. See
  [docs/charts/line.md](docs/charts/line.md).
- `LineAxisConfig.xAxisTitle` and `yAxisTitle` name an axis, so the numbers on it
  carry their unit. The y title is drawn rotated beside its labels, on the right
  in RTL, and both are announced to screen readers. See
  [docs/charts/line.md](docs/charts/line.md).
- `LineAxisConfig.yMin`, `yMax`, `xMin` and `xMax` pin an axis to a fixed range,
  so several charts can share one scale instead of each fitting its own data.
  A pinned bound is used exactly, and a line that leaves the range is cut where
  it crosses the bound rather than flattened along it. A range that cannot work —
  inverted, empty, or not finite — falls back to fitting the data. See
  [docs/charts/line.md](docs/charts/line.md).

### Changed

- `LineSeries.strokeGradientColors` now spans the x axis rather than the series'
  own first and last points, so the same color sits at the same x on every series
  and on every chart sharing that axis. Identical for a single series whose points
  span the whole axis, which is the unpinned case.
- The line chart no longer selects a point that lies outside a pinned x range,
  by touch or through a screen reader's actions menu. Such points are not drawn,
  so selecting one moved the crosshair somewhere nothing was visible.
- `LineAxisConfig` gained eight constructor parameters, `LineA11yConfig` two, and
  `LineChartStyle`, `LineSeries` and `bar.AxisConfig` one each, which changes their
  generated constructor and `copy` signatures. Source-compatible, but an app built
  against 1.0.0 must be recompiled against this release rather than swapped in
  place.
- `LineAxisConfig.dashedGrid` is deprecated in favour of `gridDashPattern`, which
  says how long a dash is rather than only whether there is one, and says it in dp.
  A grid is solid by default as before, and `dashedGrid = true` still works — now
  as a dp pattern rather than the raw 8px/6px it used, so it is the same size on
  every screen: near enough unchanged at 3x, finer below it, desktop included.
- `bar.AxisConfig.dashEffect` is deprecated in favour of `gridDashPattern`, and now
  defaults to null. A `PathEffect` compares by identity, so every default
  `AxisConfig()` was unequal to every other and defeated the recomposition skipping
  the charts rely on — and it could not be constructed at all without the graphics
  runtime loaded, which put it out of reach of a plain unit test. An explicit
  `dashEffect` still wins over `gridDashPattern`.
  **If you passed `dashEffect = null` to get a solid grid, pass
  `gridDashPattern = null` instead.** Null now means "unset" and falls through to
  the default dash, and the compiler cannot warn you: a deprecated constructor
  property warns where it is read, not where it is set.
  The default dash is now measured in dp rather than pixels, so it is the same size
  on every screen — unchanged at 3x, shorter below it, desktop included.

### Fixed

- A line chart's crosshair tooltip kept the text colour it was first drawn with,
  so a theme change while the crosshair was up left the old colour on the new
  panel. It is also redrawn now when the display density changes.


- The bar, pie and radar charts kept the selection tooltip they first measured,
  so changing its text style or the display density left the old one on screen.
  The bar and radar tooltips also kept the old text colour; the pie tooltip kept
  the old size.

## [1.0.0] - 2026-07-31

First release.

### Added

- Five chart types for Compose Multiplatform on Android and iOS, drawn on
  `Canvas`: [bar](docs/charts/bar.md), [line](docs/charts/line.md),
  [pie](docs/charts/pie.md), [radar](docs/charts/radar.md) and
  [gauge](docs/charts/gauge.md).
- Accessibility on every chart: one labelled node, the selection published as
  `stateDescription`, and a named custom action per item so screen reader users
  never have to hit a target. See [docs/ACCESSIBILITY.md](docs/ACCESSIBILITY.md).
- Entry, morph and exit animations, with items animating out when they leave the
  dataset. Collapses to instant when the OS reports reduced motion.
- RTL support throughout, including which end of a bar is rounded.
- Hoisted selection — no chart owns its selection state.
- Per-chart `a11yConfig`, `style`, `axisConfig` and `animationConfig` for
  overriding text, geometry and timing.

[Unreleased]: https://github.com/Kyriakos-Georgiopoulos/Grafima/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/Kyriakos-Georgiopoulos/Grafima/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/Kyriakos-Georgiopoulos/Grafima/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Kyriakos-Georgiopoulos/Grafima/releases/tag/v1.0.0
