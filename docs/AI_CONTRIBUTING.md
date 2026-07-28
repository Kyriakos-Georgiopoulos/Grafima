# Working on Grafima with an AI assistant

Plenty of contributions here will be written with Claude, Copilot, Cursor or
similar. That's fine. This page is the context an assistant needs to produce
something we can merge, and the checks that catch it when it doesn't.

Point your assistant at this file before it writes anything.

## What this project is

A Kotlin Multiplatform charting library for Compose. Android and iOS, from one
`commonMain` source set. Charts draw to a `Canvas`; there are no Android or iOS
views involved.

```
library/src/commonMain     all chart code lives here
library/src/androidMain    one file: the reduce-motion actual
library/src/iosMain        one file: the reduce-motion actual
library/src/commonTest     runs on JVM and iOS
library/src/uiTest         Compose UI tests, run on iOS simulator and Android device
```

If you find yourself adding a second file to `androidMain` or `iosMain`, stop
and reconsider. Almost nothing here is platform-specific.

## The rules that actually matter

### 1. The draw pass runs every frame

Anything inside a `Canvas` block or a `DrawScope` function runs 60–120 times a
second during animation. That means:

- **No allocation.** No `listOf`, no `map`, no string building, no object
  creation. Pre-compute it in the composable with `remember` and pass it in.
- **No text measurement.** Measure once with `rememberTextMeasurer`, reuse the
  `TextLayoutResult`.
- **No trigonometry** if it can be cached. See `AxisTrigCache` for the pattern.

Draw code lives in `*ChartDraw.kt` as `DrawScope` extensions — those compile to
static calls with no allocation. Don't convert them into composables or pass
lambdas into them.

### 2. Animation state has a specific shape

Each chart has an animation engine with two phases:

```kotlin
SideEffect { engine.syncAnimatables(entries) }        // create/remove, before draw
LaunchedEffect(entries) { engine.launchEntryAnimations(entries, config, this) }
```

`syncAnimatables` must run in `SideEffect`, not `LaunchedEffect`. `LaunchedEffect`
dispatches after the first draw, so the Canvas reads an empty map, subscribes to
nothing, and the chart never repaints. This was a real bug — bars rendered blank
on iOS while Android hid it.

Pass `this` (the `LaunchedEffect` scope) to the launch functions, never
`rememberCoroutineScope()`. The composition scope isn't cancelled when data
changes, so stale staggered animations land later and revert the chart. Also a
real bug, also fixed.

### 3. Selection is hoisted, always

Charts never own selection state. They take `selectedX` and call back with the
new value. Don't add internal `remember { mutableStateOf(...) }` for selection.

### 4. Accessibility is not optional

A new chart needs: a merged semantics node, `Role.Image`, a content description
built from the data, a state description for selection, `liveRegion = Polite`,
and one custom action per selectable entry. Read
[ACCESSIBILITY.md](ACCESSIBILITY.md) and copy an existing chart's semantics block.

### 5. RTL is not optional either

Every chart mirrors. Read `LocalLayoutDirection`, and remember it affects more
than x-coordinates — which end of a bar is rounded, which direction slices sweep,
which way the gauge needle travels.

## What will fail your PR

These run in CI, so find out early:

```bash
./gradlew build                        # compile, lint, JVM tests
./gradlew :library:iosSimulatorArm64Test
./gradlew :library:connectedAndroidDeviceTest   # needs a device or emulator
./gradlew :library:checkKotlinAbi      # public API changed?
```

**`checkKotlinAbi` is the one that surprises people.** Grafima records its
public API in `library/api/`. Adding a public function fails the build until you
run `./gradlew :library:updateKotlinAbi` and commit the diff. That's intended —
it makes API changes a deliberate, reviewable act.

Keep new helpers `internal` unless you mean to support them forever.

## Tests

Two source sets, both required for a new chart:

- `commonTest` — maths, animation engines, accessibility strings. Runs on JVM
  and iOS. Fast; write most of your tests here.
- `uiTest` — Compose UI tests. Runs on the iOS simulator and a real Android
  device or emulator.

Two conventions that trip up generated code:

**Test names are sentences.** In `commonTest` use backticks:

```kotlin
@Test fun `a cancelled scope never applies stale values after a dataset swap`()
```

In `uiTest` use underscores instead — those tests are dexed for Android, and D8
rejects spaces in class names below DEX 040:

```kotlin
@Test fun the_chart_description_contains_the_dataset_and_every_bar()
```

**The name must be a claim the test proves.** A test called "animates from the
baseline" that never asserts the baseline value is worse than no test.

**No wall-clock time.** Animation tests use virtual time via `runEngineTest` and
a hand-pumped frame clock. Never `Thread.sleep` or `delay` against real time.

See [TESTING.md](TESTING.md) for how to run everything.

## Comments

Comments explain *why*. The code already says what.

Delete anything that restates its own line:

```kotlin
// Draw the needle          ← delete this
drawGaugeNeedle(...)
```

Keep anything a reader would otherwise get wrong:

```kotlin
// Only the growing end of the bar is rounded, so it stays flush
// with the zero line — which side that is flips in RTL.
```

Assistants tend to over-comment. Do a pass and cut.

## A prompt that works

> Read `docs/AI_CONTRIBUTING.md` first. I want to add [X] to Grafima.
> Chart code is in `library/src/commonMain`. Nothing in the draw pass may
> allocate. Keep new declarations `internal`. Add tests to `commonTest`, and
> `uiTest` if it's user-visible behaviour. Run `./gradlew build` and
> `:library:checkKotlinAbi` and show me the output before you say it works.

That last sentence matters most. Ask for the command output, not the claim.

## Before you open the PR

- [ ] `./gradlew build` passes with no new warnings
- [ ] Tests pass on both platforms
- [ ] `checkKotlinAbi` passes, or the dump is updated and committed
- [ ] New public API is documented, or it isn't public
- [ ] Comments that restate the code are gone
- [ ] You ran it and looked at it — a screenshot beats a claim
