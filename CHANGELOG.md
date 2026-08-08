# Changelog

Notable changes to Grafima. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Grafima's public API is recorded in `library/api/library.klib.api` and
`library/api/jvm/library.api`. Any entry under **Changed** or **Removed** that
alters a signature has a matching diff in those files; entries that only change
behaviour do not.

## [Unreleased]

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
  opts out, and a line still outside the range is skipped rather than clamped. `contentDescription` announces it to a
  screen reader through `LineA11yConfig.referenceLineDescriptionBuilder`. See
  [docs/charts/line.md](docs/charts/line.md).
- `LineSeries.dashPattern` dashes a stroke, which is what tells a reader a series
  is derived rather than measured — a moving average against the readings it
  averages. `LineLegend` dashes that series' swatch to match. The area fill is
  never dashed. `DashPattern` lives in `io.grafima.charts`, since the bar chart's
  grid takes one too.
- `LineChartStyle.valueLabels` prints each point's value beside it, rather than
  only in the tooltip once something is selected, which suits a chart of few
  points and a screenshot of one. Labels are placed above their point, or below
  where there is no room above, and one that would overlap a label already drawn
  is dropped rather than stacked on it. See
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
  generated constructor and `copy` signatures. Source-compatible, but an app built against
  1.0.0 must be recompiled against this release rather than swapped in place.
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

[Unreleased]: https://github.com/Kyriakos-Georgiopoulos/Grafima/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/Kyriakos-Georgiopoulos/Grafima/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Kyriakos-Georgiopoulos/Grafima/releases/tag/v1.0.0
