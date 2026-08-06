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
- `LineAxisConfig` gained six constructor parameters and `LineA11yConfig` one,
  which changes their generated constructor and `copy` signatures.
  Source-compatible, but an app built against 1.0.0 must be recompiled against
  this release rather than swapped in place.

### Fixed

- A line chart's crosshair tooltip kept the text colour it was first drawn with,
  so a theme change while the crosshair was up left the old colour on the new
  panel. It is also redrawn now when the display density changes.

### Fixed

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

[Unreleased]: https://github.com/Kyriakos-Georgiopoulos/Grafima/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Kyriakos-Georgiopoulos/Grafima/releases/tag/v1.0.0
