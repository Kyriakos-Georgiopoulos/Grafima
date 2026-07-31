# Changelog

Notable changes to Grafima. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Grafima's public API is recorded in `library/api/library.klib.api`. Any entry
under **Changed** or **Removed** below has a matching diff in that file.

## [Unreleased]

Nothing yet.

## [1.0.0] — not yet released

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

[Unreleased]: https://github.com/Kyriakos-Georgiopoulos/Grafima/compare/main...HEAD
