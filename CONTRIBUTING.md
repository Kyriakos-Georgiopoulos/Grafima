# Contributing to Grafima

Thanks for considering it. This page covers how to file an issue, the branching
model, and what a PR needs to pass. For how the code is meant to be written (the
draw pass, animation state, selection hoisting, accessibility, RTL) read
[docs/AI_CONTRIBUTING.md](docs/AI_CONTRIBUTING.md). It applies whether or not an
assistant helped you.

## Filing an issue

Start here, before writing code. There are two forms, and picking the right one
saves a round trip:

- **Bug report** for something that renders, animates or announces wrongly.
- **Feature request** for a new chart type, or something a chart cannot do yet.

A bug report is only as good as its reproduction. What actually helps:

**The smallest chart that still shows it.** Trim your dataset to two or three
entries and strip the styling. If it only reproduces with your full data, say so,
that is useful information on its own.

**What you expected, separately from what happened.** These are different
sentences and both matter. "The bar is wrong" does not say which way.

**The platform.** Android and iOS share the drawing code but not the text
measurement or the accessibility bridge, so a bug on one is often absent on the
other. If you only tested one, say which.

**For an accessibility bug, quote the announcement.** What TalkBack or VoiceOver
actually read out tells me more than a screenshot does.

**For a visual bug, attach a screenshot or a screen recording.** Animation and
layout problems are much harder to describe than to show.

Please search the existing issues first. If you find yours, add your reproduction
to it rather than opening another, since two reproductions of the same bug are
more useful than two threads.

## Branching model

Grafima uses git-flow. Two branches live forever:

| Branch    | Holds                                        |
| --------- | -------------------------------------------- |
| `main`    | Released code only. Every commit is a release. |
| `develop` | Integration. Everything lands here first.    |

Everything else is short-lived and branches off `develop`:

| Prefix      | For                          | Branch from | Merge into          |
| ----------- | ---------------------------- | ----------- | ------------------- |
| `feature/`  | New behaviour                 | `develop`   | `develop`           |
| `fix/`      | Bug fixes                     | `develop`   | `develop`           |
| `chore/`    | Build, CI, tooling, docs      | `develop`   | `develop`           |
| `release/`  | Version bump and release prep | `develop`   | `main` and `develop` |
| `hotfix/`   | Urgent fix to a release       | `main`      | `main` and `develop` |

**Open your PR against `develop`.** A PR against `main` will be asked to
retarget, unless it is a release or a hotfix.

> **Before 1.0.0:** `develop` does not exist yet. It is created with the first
> release. Until then, branch from `main` and open your PR against `main`.

Nobody pushes to `main` or `develop` directly. Both are protected and only
change through a reviewed pull request.

## Opening a pull request

External contributors: fork the repo, push your branch to your fork, open the PR
from there.

```bash
git switch develop
git pull
git switch -c feature/candlestick-chart
# work
git push -u origin feature/candlestick-chart
```

Then fill in the PR template. The "how it was verified" section matters more than
the description. Say what you ran and what it said.

## What CI checks

Every PR runs:

- `./gradlew ktlintCheck` for formatting, plus `-p buildSrc ktlintCheck` for the
  build logic, which is a separate build and is not covered by the first
- `./gradlew build` for the Android build, Android Lint and JVM tests
- `./gradlew :library:checkKotlinAbi` to confirm the public API matches `library/api/`
- Android instrumented UI tests on an emulator
- The desktop JVM tests, and an assembly of the desktop sample
- iOS compile and simulator tests

Run the first three locally before pushing; they catch most of it.

If you changed the public API on purpose, run
`./gradlew :library:updateKotlinAbi` and commit the updated dump. CI fails
otherwise, which is the point. API changes should be visible in review.

## Commits

[Conventional Commits](https://www.conventionalcommits.org), written the way the
existing history is written:

```
type(scope): subject in lowercase, imperative, no full stop
```

```
fix(bar): render bars on first appearance on iOS
feat(charts): make charts operable and accessible beyond TalkBack
refactor(library): extract chart draw passes into named layer functions
chore(sample): drop unused ui.theme scaffolding
docs: document the radar chart's axis maximums
```

**Type**, one of:

| Type       | For                                                   |
| ---------- | ----------------------------------------------------- |
| `feat`     | New behaviour a user can see                           |
| `fix`      | A bug fix                                              |
| `perf`     | Same behaviour, less work                              |
| `refactor` | Same behaviour, different shape                        |
| `docs`     | Documentation and KDoc                                 |
| `test`     | Tests only                                             |
| `build`    | Gradle, dependencies, packaging                        |
| `ci`       | Workflows                                              |
| `style`    | Formatting with no code change                         |
| `chore`    | Anything else that does not fit above                  |

**Scope** is optional but usually worth adding. Use the chart (`bar`, `line`,
`pie`, `radar`, `gauge`), or the module (`library`, `sample`), or the area
(`charts`, `kmp`, `a11y`).

**Subject**: lowercase after the colon, imperative ("add", not "added" or
"adds"), no full stop at the end. Keep it scannable, roughly 72 characters.

**Body**, when the change is not self-evident: explain *why*, not *what*. The
diff already says what changed. Wrap at 72 columns, leave a blank line after the
subject.

Mark a breaking public API change with a `!` and explain it in the body:

```
feat(pie)!: pass the slice percentage to selectedStateDescription

The builder cannot compute a share from a single entry, so the chart now
passes it in. Any custom selectedStateDescription needs a second parameter.
```

One logical change per commit. PRs are squash-merged, which means **the PR title
becomes the commit message** on `develop`, so the same rules apply to it.

## Changelog

User-visible changes go in [CHANGELOG.md](CHANGELOG.md) under `[Unreleased]`, in
the same PR as the change. Build, CI and refactor-only work doesn't need an entry.

If you changed the public API, say so under **Changed** or **Removed**. That
entry and the `library/api/` diff should agree.

## Releasing

1. `release/x.y.z` off `develop`. Bump the version and move `[Unreleased]` to a
   new `[x.y.z]` section with the date.
2. PR into `main`. On merge, tag `vx.y.z`.
3. Merge `main` back into `develop` so the bump isn't lost.

The first release also creates `develop`, branched from `main` once `v1.0.0` is
tagged.
