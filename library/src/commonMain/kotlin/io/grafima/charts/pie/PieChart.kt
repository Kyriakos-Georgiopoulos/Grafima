/*
 * Copyright 2026 Kyriakos Georgiopoulos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grafima.charts.pie

import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import io.grafima.charts.rememberEffectiveReduceMotion
import io.grafima.charts.toDegrees
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A composable pie/donut chart with animated transitions, tap selection, gradient support,
 * RTL mirroring, and a pluggable selection indicator.
 *
 * Usage:
 * ```
 * val data = PieDataSet(
 *     entries = listOf(
 *         PieEntry("a", "Mobile", 620f, brush = SliceBrush.Linear(listOf(cyan, blue))),
 *         PieEntry("b", "Desktop", 380f, brush = Color.Red.toSliceBrush()),
 *     )
 * )
 *
 * var selected by remember { mutableStateOf<PieEntry?>(null) }
 *
 * PieChart(
 *     dataSet = data,
 *     modifier = Modifier.fillMaxWidth().height(300.dp),
 *     style = PieChartStyle(donutRatio = 0.5f),
 *     selectedEntry = selected,
 *     onSliceSelected = { selected = it },
 *     centerContent = {
 *         Text("Total\n${data.entries.sumOf { it.value.toInt() }}")
 *     }
 * )
 * ```
 *
 * @param dataSet The entries and default brush to render.
 * @param style Geometry and visual configuration.
 * @param animationConfig Timing for entry, morph, and selection animations.
 * @param a11yConfig Accessibility label builders.
 * @param selectionRenderer Strategy for drawing the selection indicator. Swap between
 *   [TooltipPieSelectionRenderer] and [ElbowCalloutPieSelectionRenderer], or provide
 *   your own [PieChartSelectionRenderer] implementation.
 * @param selectedEntry The currently selected slice, or null for no selection.
 *   Hoist this into the parent to control selection externally.
 * @param selectionHaptic Haptic effect performed when a slice becomes selected. Pass null to disable.
 * @param onSliceSelected Called when the user taps a slice (with the entry) or taps
 *   outside / re-taps the same slice (with null).
 * @param centerContent Optional composable rendered at the center of a donut chart.
 *   Only visible when [PieChartStyle.donutRatio] > 0. Receives no parameters;
 *   size yourself relative to the donut hole or use fixed dimensions.
 */
@Composable
fun PieChart(
    dataSet: PieDataSet,
    modifier: Modifier = Modifier,
    style: PieChartStyle = PieChartStyle(),
    animationConfig: PieAnimationConfig = PieAnimationConfig(),
    a11yConfig: PieA11yConfig = PieA11yConfig(),
    selectionRenderer: PieChartSelectionRenderer = remember { TooltipPieSelectionRenderer() },
    selectedEntry: PieEntry? = null,
    selectionHaptic: HapticFeedbackType? = HapticFeedbackType.LongPress,
    onSliceSelected: (PieEntry?) -> Unit = {},
    centerContent: @Composable (() -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val entries = dataSet.entries
    val animationEngine = remember { PieChartAnimationEngine() }
    val density = LocalDensity.current

    // Drawn, but not in the dataset: closing slices stay out of hit testing and a11y.
    val renderEntries = animationEngine.renderEntries(entries)

    val reduceMotion = rememberEffectiveReduceMotion()
    val effectiveAnimationConfig = remember(animationConfig, reduceMotion) {
        if (reduceMotion) {
            animationConfig.copy(
                initialEntrySpec = snap(),
                morphSpec = snap(),
                selectionSpec = snap(),
                staggerDelayMs = 0L,
                startDelayMs = 0L
            )
        } else {
            animationConfig
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // Let the long-lived pointerInput and derivedStateOf lambdas read current
    // values without restarting.
    val haptic = LocalHapticFeedback.current

    val currentSelectedEntry by rememberUpdatedState(selectedEntry)
    val currentOnSliceSelected by rememberUpdatedState(onSliceSelected)
    val currentSelectionHaptic by rememberUpdatedState(selectionHaptic)
    val currentStyle by rememberUpdatedState(style)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentDensity by rememberUpdatedState(density)
    val currentEntries by rememberUpdatedState(entries)

    val selectionCache = remember { mutableMapOf<String, TextLayoutResult>() }

    val targetTotalValue = remember(entries) {
        entries.sumOf { it.value.toDouble().coerceAtLeast(0.0) }.toFloat()
    }
    val currentTargetTotal by rememberUpdatedState(targetTotalValue)

    val chartStateDescription = remember(selectedEntry, targetTotalValue, a11yConfig) {
        val share = selectedEntry
            ?.takeIf { targetTotalValue > 0f }
            ?.let { (it.value / targetTotalValue) * 100f }
            ?: 0f
        a11yConfig.selectedStateDescription(selectedEntry, share)
    }

    // A summary, not a reading of the data: this node is a live region, so anything
    // here is repeated on every selection.
    val chartDescription = remember(dataSet, a11yConfig) {
        buildString {
            append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
            append(a11yConfig.sliceCountDescriptionBuilder(entries.size))
        }
    }

    // derivedStateOf so this is evaluated on tap rather than every animation
    // frame — idle and animating cost nothing.
    val sliceTouchBounds by remember {
        derivedStateOf {
            val ents = currentEntries
            val total = currentTargetTotal
            val startAngle = currentStyle.startAngle
            val minAngle = currentStyle.minSliceAngle

            if (ents.isEmpty() || total <= 0f) return@derivedStateOf emptyList()

            // First pass: floor-adjusted sum, accumulated rather than collected.
            val hasMinAngle = minAngle > 0f
            var rawSweepSum = 0f
            ents.forEach { entry ->
                val v = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                var s = (v / total) * 360f
                if (hasMinAngle && s > 0f) s = s.coerceAtLeast(minAngle)
                rawSweepSum += s
            }
            val normalizer = if (rawSweepSum > 0f) 360f / rawSweepSum else 1f

            val bounds = mutableListOf<Pair<PieEntry, ClosedFloatingPointRange<Float>>>()
            var logicalStart = startAngle

            ents.forEach { entry ->
                val v = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                val sweep = computeNormalizedSweep(
                    animatedValue = v,
                    totalValue = total,
                    minSliceAngle = minAngle,
                    normalizer = normalizer
                )

                val normStart = (logicalStart - startAngle) % 360f
                val normEnd = normStart + sweep

                if (normStart > normEnd) {
                    bounds.add(entry to (normStart..360f))
                    bounds.add(entry to (0f..normEnd))
                } else {
                    bounds.add(entry to (normStart..normEnd))
                }
                logicalStart += sweep
            }
            bounds
        }
    }

    // SideEffect runs before layout and draw, so the Canvas sees the animatables
    // on frame one. LaunchedEffect would leave a one-frame gap.
    SideEffect {
        animationEngine.syncAnimatables(entries)
    }

    // Scoped to entries so a data change cancels the old stagger coroutines.
    LaunchedEffect(entries) {
        selectionCache.clear()
        if (currentSelectedEntry != null && entries.none { it.id == currentSelectedEntry?.id }) {
            currentOnSliceSelected(null)
        }
        animationEngine.launchEntryAnimations(entries, effectiveAnimationConfig, this)
        animationEngine.launchExitAnimations(effectiveAnimationConfig, this)
    }

    // Keyed on entries and selection: a selection change restarts from the current
    // animated value rather than jumping, and new entries adopt the current state.
    LaunchedEffect(entries, selectedEntry) {
        animationEngine.launchSelectionAnimations(
            entries, selectedEntry, style, effectiveAnimationConfig, this
        )
    }

    Box(modifier = modifier) {
        Box(
            // On the Box, not the Canvas: `centerContent` is the Canvas's sibling, so
            // merging there would leave the centre text as a second focusable node
            // carrying none of the select actions.
            modifier = Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    role = Role.Image
                    contentDescription = chartDescription
                    stateDescription = chartStateDescription
                    customActions = buildList {
                        entries.forEach { entry ->
                            add(
                                CustomAccessibilityAction(label = "Select ${entry.label}") {
                                    onSliceSelected(entry)
                                    true
                                }
                            )
                        }
                        if (selectedEntry != null) {
                            add(
                                CustomAccessibilityAction(label = "Clear selection") {
                                    onSliceSelected(null)
                                    true
                                }
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // pointerInput(Unit) never restarts, so no touches are dropped
                    // during a restart window; current values come from the refs above.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val touchPos = down.position

                            val activeStyle = currentStyle
                            val activeIsRtl = currentIsRtl
                            val activeDensity = currentDensity

                            val cx = size.width.toFloat() / 2f
                            val cy = size.height.toFloat() / 2f

                            val effectiveX =
                                if (activeIsRtl) size.width.toFloat() - touchPos.x else touchPos.x
                            val dx = effectiveX - cx
                            val dy = touchPos.y - cy

                            val touchRadius = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                            val maxRadius = resolveOuterRadius(
                                style = activeStyle,
                                canvasWidth = size.width.toFloat(),
                                canvasHeight = size.height.toFloat(),
                                density = activeDensity
                            )
                            val minRadius = maxRadius * activeStyle.donutRatio.coerceIn(0f, 0.9f)

                            if (touchRadius !in minRadius..maxRadius) {
                                if (currentSelectedEntry != null) currentOnSliceSelected(null)
                                return@awaitEachGesture
                            }

                            var touchAngle = toDegrees(
                                atan2(dy.toDouble(), dx.toDouble())
                            ).toFloat()
                            touchAngle = (touchAngle - activeStyle.startAngle) % 360f
                            if (touchAngle < 0f) touchAngle += 360f

                            // derivedStateOf evaluates here, on-demand at touch time
                            val tappedSlice = sliceTouchBounds.find { (_, bounds) ->
                                touchAngle in bounds
                            }?.first

                            if (tappedSlice != null) {
                                if (currentSelectedEntry?.id == tappedSlice.id) {
                                    currentOnSliceSelected(null)
                                } else {
                                    currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                                    currentOnSliceSelected(tappedSlice)
                                }
                            } else {
                                currentOnSliceSelected(null)
                            }
                        }
                    }
            ) {
                // ── Pure draw lambda: no state mutations ──
                if (entries.isEmpty() || targetTotalValue <= 0f) return@Canvas

                // A closing slice keeps its share until shut, so the survivors expand
                // as it is released rather than the instant it left the dataset.
                val drawTotal = targetTotalValue + animationEngine.exitingValue(entries)

                val canvasRadius = resolveOuterRadius(
                    style = style,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    density = density
                )
                val cx = size.width / 2f
                val cy = size.height / 2f

                val safeDonutRatio = style.donutRatio.coerceIn(0f, 0.99f)
                val strokeWidth = canvasRadius * (1f - safeDonutRatio)
                val drawRadius = canvasRadius - (strokeWidth / 2f)

                val directionMultiplier = if (isRtl) -1f else 1f

                val normalizer = pieSweepNormalizer(
                    entries = renderEntries,
                    animationEngine = animationEngine,
                    totalValue = drawTotal,
                    minSliceAngle = style.minSliceAngle
                )
                drawPieSlices(
                    dataSet = dataSet,
                    style = style,
                    animationEngine = animationEngine,
                    totalValue = drawTotal,
                    normalizer = normalizer,
                    cx = cx,
                    cy = cy,
                    canvasRadius = canvasRadius,
                    drawRadius = drawRadius,
                    strokeWidth = strokeWidth,
                    directionMultiplier = directionMultiplier,
                    renderEntries = renderEntries
                )
                selectedEntry?.let { entry ->
                    drawPieSelection(
                        entry = entry,
                        entries = entries,
                        style = style,
                        animationEngine = animationEngine,
                        selectionRenderer = selectionRenderer,
                        textMeasurer = textMeasurer,
                        selectionCache = selectionCache,
                        layoutDirection = layoutDirection,
                        totalValue = drawTotal,
                        cx = cx,
                        cy = cy,
                        canvasRadius = canvasRadius,
                        strokeWidth = strokeWidth,
                        directionMultiplier = directionMultiplier
                    )
                }
            }

            if (centerContent != null && style.donutRatio > 0f) {
                centerContent()
            }
        }
    }
}
