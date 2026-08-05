# Manual testing

The automated suite (see [TESTING.md](TESTING.md)) covers chart maths, animation
lifecycles, and the accessibility **semantics** both platforms expose. It cannot
cover what a screen reader actually says, how animations feel, or whether a
chart is readable on a real display.

This checklist covers the rest. Work through it before a release, and after any
change to rendering, animation, or accessibility.

Run the sample app:

```bash
# Android
./gradlew :androidApp:installDebug

# Desktop
./gradlew :desktopApp:run

# iOS — open iosApp/iosApp.xcodeproj in Xcode and run, or see TESTING.md
```

---

## 1. Screen readers

The highest-value section, and the one automation cannot replace. Announcements
can be technically correct and still useless — only listening reveals that.

### Android — TalkBack

Enable: **Settings → Accessibility → TalkBack**, or hold both volume keys.

For each chart tab:

- [ ] Swipe right onto the chart. It is announced **once**, as a single element —
      not as a series of fragments, and not silently skipped.
- [ ] The announcement names the chart, then the data. It should be
      comprehensible read aloud, not a wall of numbers.
- [ ] Selecting a bar announces **only the selection change**, not the whole
      chart again. (Selection is exposed as `stateDescription`; this is the
      main thing to listen for.)
- [ ] Open the actions menu (swipe up-then-right). Every entry has a distinct,
      meaningful "Select …" action.
- [ ] Activate a "Select …" action. The new selection is announced.
- [ ] "Clear selection" appears only while something is selected, and works.
- [ ] Press **Update Data** in the sample. The change is announced without
      having to re-focus the chart.
- [ ] Gauge: the value is announced as a percentage/progress, not as raw text.

Judgement calls worth recording:

- [ ] Is the full announcement too long to be useful? A 12-bar chart reads every
      bar on **every** selection change — note how that feels in practice.
- [ ] Does anything sound robotic or ambiguous when spoken (units, decimals,
      abbreviations)?

### iOS — VoiceOver

Enable: **Settings → Accessibility → VoiceOver**, or triple-click the side button.

- [ ] Swipe to the chart. Announced once, as a single element.
- [ ] The rotor exposes the chart's custom actions; each one works.
- [ ] Selection changes are announced.
- [ ] Compare the wording against TalkBack — they share source strings and
      should read equivalently.

### Desktop — VoiceOver, Narrator or Orca

Compose exposes the same semantics through the platform's accessibility bridge,
so this is a spot check rather than a third full pass.

- [ ] The chart is reachable and announced as one element.
- [ ] The gauge is exposed as a progress indicator, not as plain text.
- [ ] Selection changes are announced.

---

## 2. Display and input

- [ ] **Large fonts.** Set system font size to maximum. Labels stay legible and
      don't overlap the plot area or each other.
- [ ] **Display size / zoom.** Set display size to its largest. Charts still fit.
- [ ] **RTL.** Switch the device to a right-to-left language (Arabic, Hebrew).
      Bars, axes, labels, and tooltips all mirror; touch selection still lands
      on the bar you actually pressed.
- [ ] **Dark mode**, if the host app themes the charts. Contrast is still
      comfortable.
- [ ] **Touch targets.** With many entries in a narrow chart, can you reliably
      hit the bar/slice you intend? Note the point at which it becomes fiddly.
- [ ] **Rotation.** Rotate mid-animation. No crash, no stuck animation.

---

## 3. Motion

- [ ] **Reduce motion.** Android: *Settings → Accessibility → Remove animations*.
      iOS: *Settings → Accessibility → Motion → Reduce Motion*. Charts should
      appear instantly with no entry animation. Desktop reads no OS setting at
      all, so test it there by providing `LocalReduceMotion`.
      **Known limitation:** the OS setting is read once when the chart enters
      composition, so toggling it takes effect on the next launch rather than
      immediately. A host that needs live control can provide
      `LocalReduceMotion` instead, which applies on the next recomposition.
- [ ] Entry animations feel smooth, not janky, on the oldest device you support.
- [ ] Rapidly pressing **Update Data** doesn't leave bars stranded at stale
      values or mid-animation.

---

## 4. Real-data sanity

The sample uses tidy numbers. Try awkward ones:

- [ ] A single entry; two entries; ~50 entries.
- [ ] All values equal; one value dwarfing the rest.
- [ ] Zero and very small values — do they render, and are they still selectable?
- [ ] Very long labels — truncated gracefully rather than overlapping.
- [ ] An empty dataset — renders empty, no crash.

---

## 5. Desktop

A window resizes continuously and is driven by a pointer, neither of which a
phone does.

- [ ] **Resize.** Drag the window from large to small and back. Charts reflow;
      labels don't overlap or clip at either end.
- [ ] **Pointer.** Drag across the line chart — the crosshair follows and the
      tooltip tracks it. Click a bar, slice or vertex to select it.
- [ ] **Resize mid-animation.** Press Update Data, then resize while it runs.
      No crash, no stuck animation.

---

## Recording results

Note the device, OS version, and anything that felt wrong even if it wasn't
strictly broken — awkward screen-reader phrasing and fiddly touch targets are
real defects, and they are exactly what the automated suite cannot see.
