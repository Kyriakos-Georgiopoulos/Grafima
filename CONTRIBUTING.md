# Contributing to Grafima

Thanks for considering it. This page covers the branching model and what a PR
needs to pass. For how the code is meant to be written — the draw pass, animation
state, selection hoisting, accessibility, RTL — read
[docs/AI_CONTRIBUTING.md](docs/AI_CONTRIBUTING.md). It applies whether or not an
assistant helped you.

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

Nobody pushes to `main` or `develop` directly — both are protected and only
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
the description — say what you ran and what it said.

## What CI checks

Every PR runs:

- `./gradlew ktlintCheck` — formatting
- `./gradlew build` — Android build, Android Lint, JVM tests
- `./gradlew :library:checkKotlinAbi` — the public API matches `library/api/`
- Android instrumented UI tests on an emulator
- iOS compile and simulator tests

Run the first three locally before pushing; they catch most of it.

If you changed the public API on purpose, run
`./gradlew :library:updateKotlinAbi` and commit the updated dump. CI fails
otherwise, which is the point — API changes should be visible in review.

## Commits

Conventional-commit prefixes, matching the existing history:

```
feat: Add a candlestick chart
fix: Keep the gauge's selected value across rotation
perf: Skip the exit tracker's diff when the dataset is unchanged
chore: Drop unused imports
docs: Document the radar chart's axis maximums
```

One logical change per commit. PRs are squash-merged, so the PR title becomes the
commit on `develop` — write it accordingly.

## Releasing

1. `release/x.y.z` off `develop`, bump the version, update the changelog.
2. PR into `main`. On merge, tag `vx.y.z`.
3. Merge `main` back into `develop` so the bump isn't lost.
