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

package io.grafima.charts

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // ── Stable state refs for long-lived lambdas (pointerInput, derivedStateOf) ──
    // These allow the pointer input coroutine and derived state lambda to always
    // read the latest values without restarting or re-creating.
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

    val chartDescription = remember(dataSet, selectedEntry, targetTotalValue, a11yConfig) {
        buildString {
            append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
            if (targetTotalValue > 0f) {
                entries.forEach { entry ->
                    val percentage = (entry.value / targetTotalValue) * 100
                    append(a11yConfig.sliceDescriptionBuilder(entry, percentage)).append(". ")
                }
            }
            append(a11yConfig.selectedStateDescription(selectedEntry))
        }
    }

    // ── Touch bounds computed reactively via derivedStateOf ──
    // Reads animatable snapshot state + rememberUpdatedState refs.
    // Evaluated lazily only when the pointer input handler accesses it on tap,
    // not on every animation frame. Zero cost during idle and animation.
    val sliceTouchBounds by remember {
        derivedStateOf {
            val ents = currentEntries
            val total = currentTargetTotal
            val startAngle = currentStyle.startAngle
            val minAngle = currentStyle.minSliceAngle

            if (ents.isEmpty() || total <= 0f) return@derivedStateOf emptyList()

            // First pass: compute floor-adjusted sum for normalization (no allocation)
            val hasMinAngle = minAngle > 0f
            var rawSweepSum = 0f
            ents.forEach { entry ->
                val v = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                var s = (v / total) * 360f
                if (hasMinAngle && s > 0f) s = s.coerceAtLeast(minAngle)
                rawSweepSum += s
            }
            val normalizer = if (rawSweepSum > 0f) 360f / rawSweepSum else 1f

            // Second pass: build bounds list
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

    // ── Animatable map housekeeping (synchronous, runs before draw) ──
    // SideEffect runs after every committed composition, before layout and draw.
    // This ensures animatable maps are in sync with the latest entries before the
    // Canvas reads them, eliminating the one-frame gap that LaunchedEffect would have.
    SideEffect {
        animationEngine.syncAnimatables(entries)
    }

    // ── Entry value animations ──
    // Scoped to entries: when data changes, old stagger coroutines are cancelled
    // and new ones start. No leaked animation coroutines across data updates.
    LaunchedEffect(entries) {
        selectionCache.clear()
        if (currentSelectedEntry != null && entries.none { it.id == currentSelectedEntry?.id }) {
            currentOnSliceSelected(null)
        }
        animationEngine.launchEntryAnimations(entries, animationConfig, this)
    }

    // ── Selection animations ──
    // Keyed on both entries AND selectedEntry. When selection changes, old scale/alpha
    // animations cancel and new ones start from the current animated value (no jump).
    // When entries change, new entries pick up the current selection state.
    LaunchedEffect(entries, selectedEntry) {
        animationEngine.launchSelectionAnimations(
            entries, selectedEntry, style, animationConfig, this
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    contentDescription = chartDescription
                }
                // ── pointerInput(Unit): never restarts ──
                // All external values are read through rememberUpdatedState refs,
                // so the gesture coroutine survives data/style/selection changes
                // without dropping touches during a restart window.
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

                        var touchAngle = Math.toDegrees(
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

            // Two-pass min-angle normalization without list allocation.
            // First pass: accumulate the floor-adjusted sweep sum.
            val minAngle = style.minSliceAngle
            val hasMinAngle = minAngle > 0f
            var rawSweepSum = 0f
            if (hasMinAngle) {
                entries.forEach { entry ->
                    val v = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                    var s = (v / targetTotalValue) * 360f
                    if (s > 0f) s = s.coerceAtLeast(minAngle)
                    rawSweepSum += s
                }
            }
            val normalizer = if (hasMinAngle && rawSweepSum > 0f) 360f / rawSweepSum else 1f

            // Second pass: draw arcs.
            var drawnStartAngle = style.startAngle

            entries.forEach { entry ->
                val animatedValue = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                val sweepAngle = computeNormalizedSweep(
                    animatedValue = animatedValue,
                    totalValue = targetTotalValue,
                    minSliceAngle = minAngle,
                    normalizer = normalizer
                )

                val currentScale = animationEngine.scaleAnimatables[entry.id]?.value ?: 1f
                val currentAlpha = animationEngine.alphaAnimatables[entry.id]?.value ?: 1f
                val scaledDrawRadius = drawRadius * currentScale
                val scaledStrokeWidth = strokeWidth * currentScale

                if (sweepAngle > 0f) {
                    val spacing = if (entries.size > 1 && sweepAngle > style.sliceSpacingAngle) {
                        style.sliceSpacingAngle
                    } else 0f
                    val finalSweepAngle = (sweepAngle - spacing).coerceAtLeast(0f)

                    val brush = resolveBrush(
                        sliceBrush = entry.brush ?: dataSet.defaultBrush,
                        cx = cx,
                        cy = cy,
                        radius = canvasRadius
                    )

                    drawArc(
                        brush = brush,
                        startAngle = drawnStartAngle,
                        sweepAngle = finalSweepAngle * directionMultiplier,
                        useCenter = false,
                        topLeft = Offset(x = cx - scaledDrawRadius, y = cy - scaledDrawRadius),
                        size = Size(width = scaledDrawRadius * 2, height = scaledDrawRadius * 2),
                        style = Stroke(width = scaledStrokeWidth),
                        alpha = currentAlpha
                    )
                }

                drawnStartAngle += (sweepAngle * directionMultiplier)
            }

            // Draw selection indicator
            selectedEntry?.let { entry ->
                val animatedValue = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                if (animatedValue > 0f) {
                    var targetStartAngle = style.startAngle
                    for (e in entries) {
                        if (e.id == entry.id) break
                        val valAnim = animationEngine.valueAnimatables[e.id]?.value ?: 0f
                        targetStartAngle += (valAnim / targetTotalValue) * 360f * directionMultiplier
                    }

                    val sweepAngle = (animatedValue / targetTotalValue) * 360f * directionMultiplier
                    val midAngle = targetStartAngle + (sweepAngle / 2f)

                    val midAngleRad = Math.toRadians(midAngle.toDouble())
                    val centroidRadius = canvasRadius - (strokeWidth / 2f)
                    val centroidX = cx + (centroidRadius * cos(midAngleRad)).toFloat()
                    val centroidY = cy + (centroidRadius * sin(midAngleRad)).toFloat()

                    with(selectionRenderer) {
                        drawSelection(
                            entry = entry,
                            pieCenter = Offset(x = cx, y = cy),
                            pieRadius = canvasRadius,
                            sliceCentroid = Offset(x = centroidX, y = centroidY),
                            midAngleDegrees = midAngle,
                            textMeasurer = textMeasurer,
                            tooltipCache = selectionCache,
                            layoutDirection = layoutDirection
                        )
                    }
                }
            }
        }

        // Donut center content slot
        if (centerContent != null && style.donutRatio > 0f) {
            centerContent()
        }
    }
}
