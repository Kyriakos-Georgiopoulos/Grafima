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

package io.grafima.charts.line

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.grafima.charts.DashStroke
import io.grafima.charts.drawDashableLine
import io.grafima.charts.rememberEffectiveReduceMotion
import io.grafima.charts.toDashStroke
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A composable line chart with monotone cubic bezier curves, gradient strokes,
 * area fills, drag crosshair with tooltips, animated entry/morph, full RTL
 * mirroring, and accessibility support.
 *
 * Basic usage:
 * ```
 * val data = LineDataSet(
 *     series = listOf(
 *         LineSeries("sales", "Sales",
 *             points = values.mapIndexed { i, v -> LineDataPoint(i.toFloat(), v, months[i]) },
 *             color = Color(0xFF6366F1),
 *             fillAlpha = 0.12f
 *         )
 *     )
 * )
 *
 * var selected by remember { mutableStateOf<Int?>(null) }
 *
 * LineChart(
 *     dataSet = data,
 *     modifier = Modifier.fillMaxWidth().height(300.dp),
 *     selectedPointIndex = selected,
 *     onPointSelected = { selected = it }
 * )
 * ```
 *
 * Gradient stroke + area fill:
 * ```
 * LineSeries("rev", "Revenue",
 *     points = ...,
 *     strokeGradientColors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
 *     fillAlpha = 0.15f,
 *     fillGradientColors = listOf(Color(0xFF818CF8).copy(alpha = 0.3f), Color.Transparent)
 * )
 * ```
 *
 * @param dataSet Series and chart description to render.
 * @param style Visual configuration: curve type, dots, sizing.
 * @param axisConfig Axes, grid lines, labels, and value formatting.
 * @param crosshairConfig Crosshair interaction and tooltip appearance.
 * @param animationConfig Timing for entry wave, morph spring, and stagger delays.
 * @param a11yConfig Accessibility description builders for TalkBack.
 * @param selectedPointIndex Currently highlighted x-axis position, or null. Indexes
 *   every x any series reaches, ascending — the same as indexing the first series'
 *   points whenever the series share x positions and that series is sorted. Hoist
 *   this in the parent to control the crosshair externally.
 * @param selectionHaptic Haptic effect performed each time the crosshair snaps to a new
 *   data point during a drag. Pass null to disable.
 * @param xWindow The stretch of the x axis to show, in data units. Null draws the
 *   whole axis, which is what every chart did before this existed. A window narrower
 *   than the data turns the chart into a viewport onto it: a horizontal drag walks
 *   the window along, a flick carries on and settles, and both stop at the ends of
 *   the axis. The width is kept and the position clamped, so a window that runs off
 *   the end slides back on rather than shrinking, and one as wide as the data is the
 *   same as no window at all. Where a range is pinned with [LineAxisConfig.xMin] or
 *   `xMax`, that pinned range is what the window pans over.
 *
 *   Panning and the crosshair are both horizontal drags, so a chart with a window
 *   asks for a long press before it starts selecting. A plain drag pans.
 * @param onXWindowChange Called with the new window as it is dragged and as the
 *   flick settles, already clamped to the axis. Hold it and pass it back through
 *   [xWindow], the way [selectedPointIndex] and [onPointSelected] pair up: the
 *   chart owns no scroll position of its own, so the same window can be set from a
 *   period selector or a "jump to the start" button.
 * @param onPointSelected Called during touch/drag (with the nearest point index)
 *   and on finger release (with null). The crosshair renders at this index.
 */
@Composable
fun LineChart(
    dataSet: LineDataSet,
    modifier: Modifier = Modifier,
    style: LineChartStyle = LineChartStyle(),
    axisConfig: LineAxisConfig = LineAxisConfig(),
    crosshairConfig: LineCrosshairConfig = LineCrosshairConfig(),
    animationConfig: LineAnimationConfig = LineAnimationConfig(),
    a11yConfig: LineA11yConfig = LineA11yConfig(),
    selectedPointIndex: Int? = null,
    selectionHaptic: HapticFeedbackType? = HapticFeedbackType.SegmentTick,
    xWindow: ClosedFloatingPointRange<Float>? = null,
    onXWindowChange: (ClosedFloatingPointRange<Float>) -> Unit = {},
    onPointSelected: (Int?) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val series = dataSet.series
    val density = LocalDensity.current

    val reduceMotion = rememberEffectiveReduceMotion()
    val effectiveAnimationConfig = remember(animationConfig, reduceMotion) {
        if (reduceMotion) {
            animationConfig.copy(
                entrySpec = snap(),
                morphSpec = snap(),
                staggerMs = 0L,
                startDelayMs = 0L,
                seriesStaggerMs = 0L
            )
        } else {
            animationConfig
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val animationEngine = remember { LineChartAnimationEngine() }

    // Drawn, but not in the dataset: the crosshair and a11y stay on `series`.
    val renderSeries = animationEngine.renderSeries(series)

    // ── Stable state refs for pointerInput(Unit) ──
    val haptic = LocalHapticFeedback.current

    val currentSeries by rememberUpdatedState(series)
    val currentOnPointSelected by rememberUpdatedState(onPointSelected)
    val currentOnXWindowChange by rememberUpdatedState(onXWindowChange)
    val currentDensity by rememberUpdatedState(density)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentSelectionHaptic by rememberUpdatedState(selectionHaptic)
    val currentCrosshairEnabled by rememberUpdatedState(crosshairConfig.enabled)
    val currentLabelGap by rememberUpdatedState(style.labelGap)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)

    // ── Data ranges (recomputed only on data change) ──
    val allPoints = remember(renderSeries) { renderSeries.flatMap { it.points } }
    // A target is normally above what has been reached so far, so an axis fitted to
    // the data alone would leave the line it marks off the chart.
    val xReferences = remember(axisConfig.referenceLines) {
        axisConfig.referenceLines.boundsOn(ReferenceLineAxis.X)
    }
    val yReferences = remember(axisConfig.referenceLines) {
        axisConfig.referenceLines.boundsOn(ReferenceLineAxis.Y)
    }
    val xPointMin = remember(allPoints) { allPoints.minOfOrNull { it.x } ?: 0f }
    val xPointMax = remember(allPoints) { allPoints.maxOfOrNull { it.x } ?: 1f }
    val xDataMin = remember(xPointMin, xReferences) {
        minOf(xPointMin, xReferences.minOrNull() ?: Float.MAX_VALUE)
    }
    val xDataMax = remember(xPointMax, xReferences) {
        maxOf(xPointMax, xReferences.maxOrNull() ?: -Float.MAX_VALUE)
    }
    val xExtent = remember(xDataMin, xDataMax, axisConfig.xMin, axisConfig.xMax) {
        resolveAxisBounds(
            dataMin = xDataMin,
            dataMax = xDataMax,
            pinnedMin = axisConfig.xMin,
            pinnedMax = axisConfig.xMax
        )
    }
    val window = remember(xExtent, xWindow) { resolveXWindow(xExtent, xWindow) }
    val xMin = window?.start ?: xExtent.start
    val xMax = window?.endInclusive ?: xExtent.endInclusive
    val currentXMin by rememberUpdatedState(xMin)
    val currentXMax by rememberUpdatedState(xMax)
    val currentXExtent by rememberUpdatedState(xExtent)
    val currentWindow by rememberUpdatedState(window)
    // A window is a pin on both edges that moves, so everything a pinned x axis
    // suppresses is suppressed inside one too.
    val xIsPinned = axisConfig.xMin != null || axisConfig.xMax != null || window != null
    val currentXIsPinned by rememberUpdatedState(xIsPinned)

    val yPointMax = remember(allPoints) { allPoints.maxOfOrNull { it.y } ?: 1f }
    val yPointMin = remember(allPoints) { allPoints.minOfOrNull { it.y } ?: 0f }
    val yDataMax = remember(yPointMax, yReferences) {
        maxOf(yPointMax, yReferences.maxOrNull() ?: -Float.MAX_VALUE)
    }
    val yRawMin = remember(yPointMin, yReferences) {
        minOf(yPointMin, yReferences.minOrNull() ?: Float.MAX_VALUE)
    }
    val yDataMin = remember(yRawMin, axisConfig.includeZeroInYRange) {
        if (axisConfig.includeZeroInYRange) min(0f, yRawMin) else yRawMin
    }
    val yTickValues = remember(
        yDataMin,
        yDataMax,
        axisConfig.yTickCount,
        axisConfig.yMin,
        axisConfig.yMax
    ) {
        computeAxisTicks(
            dataMin = yDataMin,
            dataMax = yDataMax,
            tickCount = axisConfig.yTickCount,
            pinnedMin = axisConfig.yMin,
            pinnedMax = axisConfig.yMax
        )
    }
    val yMin = yTickValues.firstOrNull() ?: 0f
    val yMax = yTickValues.lastOrNull() ?: 1f

    // ── Pre-measured labels (zero text measurement in draw) ──
    val labelStyle = remember(axisConfig.labelColor, axisConfig.labelFontSize) {
        TextStyle(
            color = axisConfig.labelColor,
            fontSize = axisConfig.labelFontSize,
            fontWeight = FontWeight.Medium
        )
    }
    val yLabelLayouts = remember(yTickValues, labelStyle, textMeasurer, axisConfig.yLabelFormatter) {
        yTickValues.map { v ->
            textMeasurer.measure(
                text = axisConfig.yLabelFormatter(v),
                style = labelStyle,
                maxLines = 1
            )
        }
    }
    val maxYLabelWidth = remember(yLabelLayouts) {
        if (yLabelLayouts.isEmpty()) 0f else yLabelLayouts.maxOf { it.size.width }.toFloat()
    }
    val currentMaxYLabelWidth by rememberUpdatedState(maxYLabelWidth)
    val currentShowYLabels by rememberUpdatedState(axisConfig.showYLabels)

    val firstPoints = series.firstOrNull()?.points ?: emptyList()
    val xLabelStyle = remember(labelStyle) { labelStyle.copy(textAlign = TextAlign.Center) }
    val labelledPoints = remember(
        firstPoints,
        axisConfig.maxXLabels,
        axisConfig.showXLabels,
        xIsPinned,
        xMin,
        xMax
    ) {
        if (!axisConfig.showXLabels) {
            IntArray(0)
        } else {
            xLabelIndices(
                points = firstPoints,
                maxLabels = axisConfig.maxXLabels,
                xMin = xMin,
                xMax = xMax,
                restrictToAxis = xIsPinned
            )
        }
    }
    // Kept across windows, so panning measures only the labels it has just revealed.
    // Keyed by the text, as the value labels are: a window that crosses a stride
    // boundary picks a different set of points and finds them already measured.
    val xLabelCache = remember(xLabelStyle, textMeasurer) {
        mutableMapOf<String, TextLayoutResult>()
    }
    val xLabelLayouts = remember(labelledPoints, xLabelCache, axisConfig.xLabelFormatter) {
        labelledPoints.map { i ->
            val p = firstPoints[i]
            val text = p.label.ifEmpty { axisConfig.xLabelFormatter(p.x) }
            xLabelCache.getOrPut(text) {
                textMeasurer.measure(text = text, style = xLabelStyle, maxLines = 1)
            }
        }
    }
    val maxXLabelHeight = remember(xLabelLayouts) {
        if (xLabelLayouts.isEmpty()) 0f else xLabelLayouts.maxOf { it.size.height }.toFloat()
    }

    val xTitle = axisConfig.xAxisTitle?.takeIf { it.isNotBlank() }
    val yTitle = axisConfig.yAxisTitle?.takeIf { it.isNotBlank() }

    // Unconstrained, because the insets need the full line height. Length is
    // trimmed at draw time, which cannot change that height.
    val xTitleLayout = remember(xTitle, labelStyle, textMeasurer) {
        xTitle?.let { textMeasurer.measure(text = it, style = labelStyle, maxLines = 1) }
    }
    val yTitleLayout = remember(yTitle, labelStyle, textMeasurer) {
        yTitle?.let { textMeasurer.measure(text = it, style = labelStyle, maxLines = 1) }
    }
    val xFittedTitle = remember(xTitle, labelStyle, textMeasurer) { FittedTitle() }
    val yFittedTitle = remember(yTitle, labelStyle, textMeasurer) { FittedTitle() }
    val currentYTitleHeight by rememberUpdatedState(yTitleLayout?.size?.height?.toFloat() ?: 0f)

    val tooltipStyle = remember(crosshairConfig.tooltipTextColor, crosshairConfig.tooltipFontSize) {
        TextStyle(
            color = crosshairConfig.tooltipTextColor,
            fontSize = crosshairConfig.tooltipFontSize,
            fontWeight = FontWeight.Medium,
            lineHeight = crosshairConfig.tooltipFontSize * 1.4f
        )
    }

    // ── Pre-computed key matrix + draw buffers (zero allocation in draw) ──
    val seriesStructure = remember(renderSeries) { renderSeries.map { it.id to it.points.size } }
    val keyMatrix =
        remember(seriesStructure) {
            renderSeries.associate { s -> s.id to Array(s.points.size) { i -> "${s.id}::$i" } }
        }
    val xBuffers =
        remember(seriesStructure) { renderSeries.associate { s -> s.id to FloatArray(s.points.size) } }
    val yBuffers =
        remember(seriesStructure) { renderSeries.associate { s -> s.id to FloatArray(s.points.size) } }
    val tangentBuffers =
        remember(seriesStructure) { renderSeries.associate { s -> s.id to FloatArray(s.points.size) } }
    val deltasBuffers = remember(seriesStructure) {
        renderSeries.associate { s ->
            s.id to FloatArray(
                max(
                    0,
                    s.points.size - 1
                )
            )
        }
    }

    // Clipping a range with nothing outside it would only shave a cap on the bound.
    // Guards apply only to a pinned axis. An automatic one already contains its
    // data, so testing it can only misfire on a float rounding of its own ticks.
    val yIsPinned = axisConfig.yMin != null || axisConfig.yMax != null

    // Per edge, not per axis: an axis that cuts at one end must not clip the other,
    // where a mark sitting on the bound would lose its outer half.
    // Against the points alone. Reference lines are drawn unclipped and skipped when
    // off-axis, so one outside a pinned range must not shave the series at the edge.
    val lowXCuts = xIsPinned && xMin > xPointMin
    val highXCuts = xIsPinned && xMax < xPointMax
    val topCuts = yIsPinned && yMax < yPointMax
    val bottomCuts = yIsPinned && yMin > yPointMin

    // ── Resolved once per data change: building these per frame would allocate ──
    @Suppress("DEPRECATION")
    val gridDash = remember(axisConfig.dashedGrid, axisConfig.gridDashPattern, density) {
        // dashedGrid is deprecated but still honoured, and reading it is the only
        // way to keep a caller who set it from silently losing their dashes.
        val pattern =
            if (axisConfig.dashedGrid) LegacyDashedGrid else axisConfig.gridDashPattern
        pattern.toDashStroke(density)
    }
    // Keyed on what the draw pass walks: a departing series is still drawn.
    val seriesDashStrokes = remember(renderSeries, density) {
        renderSeries.associate { s -> s.id to s.dashPattern.toDashStroke(density) }
    }
    val referenceLineStrokes = remember(axisConfig.referenceLines, density) {
        axisConfig.referenceLines.map { it.dashPattern.toDashStroke(density) }
    }
    val referenceLabelLayouts = remember(axisConfig.referenceLines, labelStyle, textMeasurer) {
        axisConfig.referenceLines.map { line ->
            line.label?.takeIf { it.isNotBlank() }?.let {
                textMeasurer.measure(
                    text = it,
                    style = labelStyle.copy(color = line.color),
                    maxLines = 1
                )
            }
        }
    }

    // ── Pre-measured value labels (zero text measurement in draw) ──
    val valueLabels = style.valueLabels
    val valueLabelStyle = valueLabels.textStyle
    // Measured off the data rather than the animated value, so the entry wave does
    // not re-measure every point on every frame — and the printed number is the
    // one the point actually holds.
    val valueLabelLayouts = remember(renderSeries, textMeasurer, valueLabels) {
        if (!valueLabels.enabled) {
            emptyMap()
        } else {
            // Measured once per distinct string: real data repeats its values, and
            // a point that loses its place to a collision is measured all the same.
            val measured = mutableMapOf<String, TextLayoutResult>()
            renderSeries.associate { s ->
                s.id to s.points.map { p ->
                    val text = valueLabels.formatter(s, p)
                    // Null, not a zero-width layout, or an unlabelled point still
                    // claims room and turns the real labels away.
                    if (text.isBlank()) {
                        null
                    } else {
                        measured.getOrPut(text) {
                            textMeasurer.measure(
                                text = text,
                                style = valueLabelStyle,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Accessibility ──
    // Only the lines that are drawn: announcing one that falls off the axis tells a
    // listener about a threshold no one else can see.
    val announcedReferenceLines =
        remember(axisConfig.referenceLines, series, xMin, xMax, yMin, yMax) {
            // A chart with no series draws nothing at all, its reference lines
            // included, so it must not speak of a threshold either.
            if (series.isEmpty()) {
                emptyList()
            } else {
                axisConfig.referenceLines.filter { it.isOnAxis(xMin, xMax, yMin, yMax) }
            }
        }
    val baseDescription = remember(dataSet, a11yConfig, xTitle, yTitle, announcedReferenceLines) {
        buildString {
            append(a11yConfig.chartDescriptionBuilder(dataSet))
            appendSentence(a11yConfig.axisTitleDescriptionBuilder(xTitle, yTitle))
            appendSentence(a11yConfig.referenceLineDescriptionBuilder(announcedReferenceLines))
        }
    }
    val axis = remember(series) { buildAxisIndex(series) }
    val currentAxisX by rememberUpdatedState(axis.positions)

    val onAxis = remember(axis, xIsPinned, xMin, xMax) {
        if (xIsPinned) visiblePositions(axis.positions, xMin, xMax) else axis.positions.indices
    }

    val selectedDescription = remember(selectedPointIndex, series, a11yConfig, onAxis) {
        // Off the axis it has no crosshair, no dot and no tooltip. Announcing it
        // would tell a listener about a reading nobody else can see.
        selectedPointIndex?.takeIf { it in onAxis }?.let { idx ->
            // Resolved off the axis the chart already built, rather than rebuilt per
            // announcement: TalkBack asks for this on every snap of a drag.
            val selected = series.mapNotNull { s ->
                s.points.getOrNull(axis.pointIndex(s.id, idx))?.let { SelectedPoint(s, it) }
            }
            a11yConfig.selectedPointDescriptionBuilder(idx, selected)
        }
            ?: ""
    }

    // Keyed on the run of positions rather than on the window, so a drag that has not
    // yet brought a new point into view announces nothing.
    val windowDescription = remember(window != null, onAxis, series, axis, a11yConfig) {
        if (window == null) {
            ""
        } else if (onAxis.isEmpty()) {
            a11yConfig.visibleRangeDescriptionBuilder(null, null)
        } else {
            a11yConfig.visibleRangeDescriptionBuilder(
                pointAtPosition(series, axis, onAxis.first),
                if (onAxis.first == onAxis.last) null else pointAtPosition(series, axis, onAxis.last)
            )
        }
    }
    // Selection first: it is what a listener asked for, and the window behind it is
    // unchanged context they can stop listening to once they have the value.
    val spokenState = remember(windowDescription, selectedDescription) {
        if (windowDescription.isEmpty()) {
            selectedDescription
        } else {
            buildString {
                append(selectedDescription)
                appendSentence(windowDescription)
            }
        }
    }

    val tooltipCache = remember(textMeasurer, tooltipStyle) {
        mutableMapOf<String, TextLayoutResult>()
    }
    val labelBoxes = remember(seriesStructure, valueLabels.enabled, axisConfig.referenceLines) {
        val points = if (valueLabels.enabled) renderSeries.sumOf { it.points.size } else 0
        LabelBoxes(capacity = points + axisConfig.referenceLines.size)
    }
    val linePath = remember { Path() }
    val dashPath = remember { Path() }
    val areaPath = remember { Path() }
    val plotInsets = remember { PlotInsets() }
    val gestureInsets = remember { PlotInsets() }

    val widestDotPx = remember(renderSeries, style.showDots, style.dotRadius, density) {
        if (!style.showDots) {
            0f
        } else {
            // Sanitise each series before comparing: Dp compares as IEEE floats, so an
            // unspecified radius left in the max survives it and hides the real widest.
            with(density) {
                renderSeries.maxOfOrNull { s ->
                    val px = s.dotRadius.orElse(style.dotRadius).toPx()
                    if (px.isFinite()) px.coerceAtLeast(0f) else 0f
                } ?: 0f
            }
        }
    }
    val currentWidestDotPx by rememberUpdatedState(widestDotPx)

    // ── Animation lifecycle ──
    SideEffect { animationEngine.syncAnimatables(series) }
    LaunchedEffect(series) {
        tooltipCache.clear()
        animationEngine.launchEntryAnimations(series, effectiveAnimationConfig, yMin, this)
        animationEngine.launchExitAnimations(effectiveAnimationConfig, yMin, this)
    }

    Canvas(
        modifier = modifier
            .defaultMinSize(minWidth = style.minSize, minHeight = style.minSize)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                role = Role.Image
                contentDescription = baseDescription
                stateDescription = spokenState
                customActions = buildList {
                    // A drag is the sighted way through a window. Without these the
                    // rest of the data would be unreachable to a screen reader.
                    if (window != null) {
                        val page = window.endInclusive - window.start
                        val previous = shiftXWindow(window, xExtent, -page)
                        if (previous !== window) {
                            add(
                                CustomAccessibilityAction(label = a11yConfig.panPreviousLabel) {
                                    onXWindowChange(previous)
                                    true
                                }
                            )
                        }
                        val next = shiftXWindow(window, xExtent, page)
                        if (next !== window) {
                            add(
                                CustomAccessibilityAction(label = a11yConfig.panNextLabel) {
                                    onXWindowChange(next)
                                    true
                                }
                            )
                        }
                    }
                    if (axis.positions.isNotEmpty()) {
                        // Named, not next/previous: stepping leaves the listener
                        // counting along the axis to work out where they landed.
                        for (index in onAxis) {
                            val point = pointAtPosition(series, axis, index) ?: continue
                            add(
                                CustomAccessibilityAction(label = a11yConfig.selectActionLabel(point)) {
                                    onPointSelected(index)
                                    true
                                }
                            )
                        }
                        if (selectedPointIndex != null) {
                            add(
                                CustomAccessibilityAction(label = a11yConfig.clearSelectionLabel) {
                                    onPointSelected(null)
                                    true
                                }
                            )
                        }
                    }
                }
            }
            // pointerInput(Unit): never restarts, reads all values via rememberUpdatedState
            .pointerInput(Unit) {
                // Well short of the decay's own 0.1 px/s, which spends over a second
                // travelling distances no one can see, recomposing for each of them.
                val flingDecay = exponentialDecay<Float>(absVelocityThreshold = FlingStopVelocity)
                coroutineScope {
                    val gestures = this
                    var fling: Job? = null
                    awaitEachGesture {
                        // First, before any decision: leaving this block without having
                        // suspended spins awaitEachGesture on the ui thread. Waiting here
                        // also means everything below is read as the touch lands rather
                        // than as the previous gesture ended, which a settling fling or a
                        // window set from elsewhere would have left stale.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fling?.cancel()

                        val positions = currentAxisX
                        val panWindow = currentWindow
                        val canSelect = currentCrosshairEnabled && positions.isNotEmpty()
                        if (!canSelect && panWindow == null) return@awaitEachGesture

                        val den = currentDensity
                        val gap = with(den) { currentLabelGap.toPx() }
                        val rtl = currentIsRtl
                        // Must match the draw pass, or touch selects a point other than
                        // the one under the finger. Own scratch: not the draw coroutine.
                        val rect = computePlotInsets(
                            into = gestureInsets,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            gap = gap,
                            yLabelWidth = if (currentShowYLabels) currentMaxYLabelWidth else 0f,
                            xLabelHeight = 0f,
                            yTitleHeight = currentYTitleHeight,
                            xTitleHeight = 0f,
                            isRtl = rtl,
                            dotClearance = currentWidestDotPx
                        )
                        val cLeft = rect.left
                        val cRight = rect.right
                        val axMin = currentXMin
                        val axMax = currentXMax

                        val restrict = currentXIsPinned
                        fun nearest(touchX: Float): Int =
                            nearestAxisIndex(positions, touchX, axMin, axMax, cLeft, cRight, rtl, restrict)

                        var settleX = down.position.x
                        if (panWindow != null) {
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val decided = awaitPanOrSelect(
                                down = down,
                                slop = viewConfiguration.touchSlop,
                                longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                                canSelect = canSelect,
                                tracker = tracker
                            )
                            if (decided.touch == LineTouch.Cancelled) return@awaitEachGesture
                            settleX = down.position.x + decided.drift
                            if (decided.touch == LineTouch.Pan) {
                                var shown: ClosedFloatingPointRange<Float> = panWindow
                                var emitted: ClosedFloatingPointRange<Float> = panWindow
                                val width = panWindow.endInclusive - panWindow.start
                                val plotWidth = cRight - cLeft
                                fun panBy(dragPx: Float): Boolean {
                                    val moved = shiftXWindow(
                                        window = shown,
                                        extent = currentXExtent,
                                        delta = panDataDelta(dragPx, plotWidth, width, rtl),
                                        width = width
                                    )
                                    if (moved === shown) return false
                                    shown = moved
                                    emitted = moved
                                    currentOnXWindowChange(moved)
                                    return true
                                }

                                panBy(decided.overSlop)
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    // Only what the chart acted on: a window already at the
                                    // end of its data hands the drag back, or a pager
                                    // holding the chart could never turn its page.
                                    if (panBy(change.positionChange().x)) change.consume()
                                    if (!change.pressed) break
                                }

                                val ceiling = viewConfiguration.maximumFlingVelocity
                                val velocity = tracker
                                    .calculateVelocity(Velocity(ceiling, ceiling)).x
                                if (!currentReduceMotion && abs(velocity) > FlingStopVelocity) {
                                    fling = gestures.launch {
                                        var settled = 0f
                                        AnimationState(0f, velocity).animateDecay(flingDecay) {
                                            val step = value - settled
                                            settled = value
                                            // The first frame is still at rest, so it has
                                            // neither moved anything nor read a window the
                                            // composition has had a chance to catch up with.
                                            if (step == 0f) return@animateDecay
                                            val live = currentWindow
                                            val ours = live != null &&
                                                abs(live.start - emitted.start) <= width * 1e-4f
                                            // Either the end of the data, or a window the
                                            // caller has since taken somewhere of its own.
                                            if (!ours || !panBy(step)) cancelAnimation()
                                        }
                                    }
                                }
                                return@awaitEachGesture
                            }
                        }

                        var lastHapticIndex = nearest(settleX)
                        if (lastHapticIndex >= 0) {
                            currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                            currentOnPointSelected(lastHapticIndex)
                        }
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val index = nearest(change.position.x)
                            if (index < 0) {
                                change.consume()
                                continue
                            }
                            if (index != lastHapticIndex) {
                                lastHapticIndex = index
                                currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                            }
                            currentOnPointSelected(index)
                            change.consume()
                        }
                        currentOnPointSelected(null)
                    }
                }
            }
    ) {
        // ── Pure draw lambda: no state mutations ──
        if (series.isEmpty()) return@Canvas

        val labelGapPx = style.labelGap.toPx()

        val insets = computePlotInsets(
            into = plotInsets,
            width = size.width,
            height = size.height,
            gap = labelGapPx,
            yLabelWidth = if (axisConfig.showYLabels) maxYLabelWidth else 0f,
            xLabelHeight = if (axisConfig.showXLabels) maxXLabelHeight else 0f,
            yTitleHeight = yTitleLayout?.size?.height?.toFloat() ?: 0f,
            xTitleHeight = xTitleLayout?.size?.height?.toFloat() ?: 0f,
            isRtl = isRtl,
            dotClearance = widestDotPx
        )
        // Labels stand off by the clearance the plot was actually given, or they walk
        // inward past it once the clamp engages and leave the composable entirely.
        val yLabelGapPx = labelGapPx + insets.sideClearance
        val xLabelGapPx = labelGapPx + insets.stackClearance
        val chartLeft = insets.left
        val chartRight = insets.right
        val chartBottom = insets.bottom
        val chartTop = insets.top
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop
        val xRange = xMax - xMin
        val yRange = yMax - yMin

        fun mapX(v: Float) =
            mapDataXToCanvas(v, xMin, xMax, chartLeft, chartRight, isRtl)

        fun mapY(v: Float) =
            if (yRange > 0f) chartBottom - (v - yMin) / yRange * chartHeight else chartTop

        val yAxisX = if (isRtl) chartRight else chartLeft

        // ── 1. Grid ──
        if (axisConfig.showGrid) {
            val gridPx = axisConfig.gridStrokeWidth.toPx()
            yTickValues.forEach { v ->
                val y = mapY(v)
                drawDashableLine(
                    path = dashPath,
                    color = axisConfig.gridColor,
                    start = Offset(x = chartLeft, y = y),
                    end = Offset(x = chartRight, y = y),
                    strokeWidth = gridPx,
                    dash = gridDash
                )
            }
        }
        if (axisConfig.showVerticalGrid && firstPoints.size > 1) {
            val gridPx = axisConfig.gridStrokeWidth.toPx()
            firstPoints.forEach { p ->
                if (xIsPinned && !isWithinAxis(p.x, xMin, xMax)) return@forEach
                val x = mapX(p.x)
                drawDashableLine(
                    path = dashPath,
                    color = axisConfig.gridColor,
                    start = Offset(x = x, y = chartTop),
                    end = Offset(x = x, y = chartBottom),
                    strokeWidth = gridPx,
                    dash = gridDash
                )
            }
        }

        // ── 2. Axes ──
        val axisPx = axisConfig.axisStrokeWidth.toPx()
        drawLine(
            color = axisConfig.axisColor,
            start = Offset(x = yAxisX, y = chartTop),
            end = Offset(x = yAxisX, y = chartBottom),
            strokeWidth = axisPx
        )
        drawLine(
            color = axisConfig.axisColor,
            start = Offset(x = chartLeft, y = chartBottom),
            end = Offset(x = chartRight, y = chartBottom),
            strokeWidth = axisPx
        )

        // ── 3. Y labels (RTL: drawn on the right side) ──
        if (axisConfig.showYLabels) {
            yTickValues.forEachIndexed { i, v ->
                if (i < yLabelLayouts.size) {
                    val layout = yLabelLayouts[i]
                    val y = mapY(v)
                    val lx =
                        if (isRtl) {
                            chartRight + yLabelGapPx
                        } else {
                            chartLeft - yLabelGapPx - layout.size.width
                        }
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x = lx, y = y - layout.size.height / 2f)
                    )
                }
            }
        }

        // ── 4. Series: area fills, line strokes, then dots ──
        // xMin is the left edge in LTR and the right edge in RTL.
        val leftCuts = if (isRtl) highXCuts else lowXCuts
        val rightCuts = if (isRtl) lowXCuts else highXCuts
        val noClip = size.maxDimension
        val leftSlack = if (leftCuts) 0f else noClip
        val rightSlack = if (rightCuts) 0f else noClip
        val topSlack = if (topCuts) 0f else noClip
        val bottomSlack = if (bottomCuts) 0f else noClip

        // Only a drawn dot needs clearing; an unused radius must not push labels off.
        // A radius that is not a finite length is no radius at all: left as NaN it
        // makes every inset, every mapped coordinate and every label position NaN.
        fun markRadiusPx(s: LineSeries): Float {
            if (!style.showDots) return 0f
            val resolved = s.dotRadius.orElse(style.dotRadius).toPx()
            return if (resolved.isFinite()) resolved.coerceAtLeast(0f) else 0f
        }

        renderSeries.forEach { s ->
            val n = s.points.size
            if (n == 0) return@forEach
            val keys = keyMatrix[s.id] ?: return@forEach
            val xs = xBuffers[s.id] ?: return@forEach
            val ys = yBuffers[s.id] ?: return@forEach
            val tans = tangentBuffers[s.id] ?: return@forEach
            val delts = deltasBuffers[s.id] ?: return@forEach

            // Fill pre-allocated buffers with animated screen positions
            for (i in 0 until n) {
                val animY = animationEngine.yAnimatables[keys[i]]?.value ?: 0f
                xs[i] = mapX(s.points[i].x)
                ys[i] = mapY(animY)
            }
            if (style.curveType == LineCurveType.MonotoneCubic && n >= 2) {
                computeMonotoneTangents(xs = xs, ys = ys, tangents = tans, deltas = delts, n = n)
            }

            clipRect(
                left = chartLeft - leftSlack,
                top = chartTop - topSlack,
                right = chartRight + rightSlack,
                bottom = chartBottom + bottomSlack
            ) {
                // Area fill (one Brush creation per gradient series per frame, acceptable)
                if (s.fillAlpha > 0f || s.fillGradientColors.isNotEmpty()) {
                    areaPath.reset()
                    areaPath.buildArea(
                        xs = xs,
                        ys = ys,
                        tangents = tans,
                        n = n,
                        chartBottom = chartBottom,
                        curveType = style.curveType
                    )
                    val brush = if (s.fillGradientColors.size >= 2) {
                        Brush.verticalGradient(
                            colors = s.fillGradientColors,
                            startY = chartTop,
                            endY = chartBottom
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                s.color.copy(alpha = s.fillAlpha),
                                Color.Transparent
                            ), startY = chartTop, endY = chartBottom
                        )
                    }
                    drawPath(path = areaPath, brush = brush)
                }

                // Line stroke: gradient or solid
                linePath.reset()
                linePath.buildCurve(
                    xs = xs,
                    ys = ys,
                    tangents = tans,
                    n = n,
                    curveType = style.curveType
                )
                val dash = seriesDashStrokes[s.id] ?: DashStroke.Solid
                val strokeStyle = Stroke(
                    width = s.strokeWidth.toPx(),
                    cap = dash.cap,
                    pathEffect = dash.effect
                )
                if (s.hasStrokeGradient) {
                    drawPath(
                        path = linePath,
                        brush = Brush.horizontalGradient(
                            colors = s.strokeGradientColors,
                            startX = if (isRtl) chartRight else chartLeft,
                            endX = if (isRtl) chartLeft else chartRight
                        ),
                        style = strokeStyle
                    )
                } else {
                    drawPath(path = linePath, color = s.color, style = strokeStyle)
                }
            }
        }

        // Every dot after every fill, or a later series' wash sinks an earlier
        // series' marker. Outside the clip too, so one on a bound keeps all of itself.
        renderSeries.forEach { s ->
            val seriesDotRadiusPx = markRadiusPx(s)
            if (seriesDotRadiusPx <= 0f) return@forEach
            val xs = xBuffers[s.id] ?: return@forEach
            val ys = yBuffers[s.id] ?: return@forEach
            // Buffers are sized to the series they were filled for; two series sharing
            // an id leave only the last, so walk what the buffer actually holds.
            val n = minOf(s.points.size, xs.size, ys.size)
            for (i in 0 until n) {
                val p = s.points[i]
                if (yIsPinned && !isWithinAxis(p.y, yMin, yMax)) continue
                if (xIsPinned && !isWithinAxis(p.x, xMin, xMax)) continue
                drawCircle(
                    color = s.color,
                    radius = seriesDotRadiusPx,
                    center = Offset(x = xs[i], y = ys[i])
                )
            }
        }

        // ── 4b. Reference lines ──
        // Drawn over the series: a threshold hidden behind the data cannot be read
        // against it. A value off the axis is skipped rather than clamped, which
        // would put the line at an edge it does not mark.
        // Reset here rather than in the value-label pass: reference labels claim
        // their boxes first, and the values then go round them.
        labelBoxes.reset()
        val references = axisConfig.referenceLines
        for (i in 0 until references.size) {
            val line = references[i]
            if (!line.isOnAxis(xMin, xMax, yMin, yMax)) continue
            val dash = referenceLineStrokes.getOrNull(i) ?: DashStroke.Solid
            val lineWidth = line.strokeWidth.toPx()
            when (line.axis) {
                ReferenceLineAxis.X -> {
                    val x = mapX(line.value)
                    drawDashableLine(
                        path = dashPath,
                        color = line.color,
                        start = Offset(x = x, y = chartTop),
                        end = Offset(x = x, y = chartBottom),
                        strokeWidth = lineWidth,
                        dash = dash
                    )
                    referenceLabelLayouts.getOrNull(i)?.let { layout ->
                        val gap = labelGapPx / 2f
                        val lx = referenceLabelLeft(
                            lineX = x,
                            labelWidth = layout.size.width.toFloat(),
                            gap = gap,
                            chartLeft = chartLeft,
                            chartRight = chartRight,
                            isRtl = isRtl
                        )
                        drawReferenceLabel(
                            layout = layout,
                            left = lx,
                            top = chartTop + gap,
                            boxes = labelBoxes,
                            gap = gap,
                            plotWidth = chartRight - chartLeft
                        )
                    }
                }

                ReferenceLineAxis.Y -> {
                    val y = mapY(line.value)
                    drawDashableLine(
                        path = dashPath,
                        color = line.color,
                        start = Offset(x = chartLeft, y = y),
                        end = Offset(x = chartRight, y = y),
                        strokeWidth = lineWidth,
                        dash = dash
                    )
                    referenceLabelLayouts.getOrNull(i)?.let { layout ->
                        // At the trailing end, above the line, or below it at the top.
                        val gap = labelGapPx / 2f
                        val lx = referenceLabelEndLeft(
                            labelWidth = layout.size.width.toFloat(),
                            gap = gap,
                            chartLeft = chartLeft,
                            chartRight = chartRight,
                            isRtl = isRtl
                        )
                        val ly = valueLabelTop(
                            pointY = y,
                            labelHeight = layout.size.height.toFloat(),
                            offset = gap,
                            chartTop = chartTop,
                            chartBottom = chartBottom,
                            preferBelow = false
                        )
                        drawReferenceLabel(
                            layout = layout,
                            left = lx,
                            top = ly,
                            boxes = labelBoxes,
                            gap = gap,
                            plotWidth = chartRight - chartLeft
                        )
                    }
                }
            }
        }

        // ── 4c. Value labels ──
        if (valueLabelLayouts.isNotEmpty()) {
            val valueGap = labelGapPx / 2f
            for (seriesIndex in 0 until renderSeries.size) {
                val s = renderSeries[seriesIndex]
                val layouts = valueLabelLayouts[s.id] ?: continue
                val xs = xBuffers[s.id] ?: continue
                val ys = yBuffers[s.id] ?: continue
                val labelColor = when {
                    valueLabels.useSeriesColor -> s.color
                    valueLabelStyle.color.isSpecified -> valueLabelStyle.color
                    else -> ValueLabelTextStyle.color
                }
                val labelOffset = markRadiusPx(s) + valueGap + s.strokeWidth.toPx() / 2f
                val n = min(s.points.size, layouts.size)
                // Walked left to right on screen rather than through the data, so
                // the label kept out of a colliding pair is the leftmost one
                // whichever way the axis runs.
                for (k in 0 until n) {
                    val i = screenOrderIndex(step = k, count = n, isRtl = isRtl)
                    val p = s.points[i]
                    if (yIsPinned && !isWithinAxis(p.y, yMin, yMax)) continue
                    if (xIsPinned && !isWithinAxis(p.x, xMin, xMax)) continue
                    val layout = layouts[i] ?: continue
                    val labelWidth = layout.size.width
                    val lx = valueLabelLeft(
                        pointX = xs[i],
                        labelWidth = labelWidth.toFloat(),
                        chartLeft = chartLeft,
                        chartRight = chartRight
                    )
                    val ly = valueLabelTop(
                        pointY = ys[i],
                        labelHeight = layout.size.height.toFloat(),
                        offset = labelOffset,
                        chartTop = chartTop,
                        chartBottom = chartBottom,
                        // From the data: animated positions are all flat at rest, so
                        // the side would flip as the entry wave passed.
                        preferBelow = valueLabelPrefersBelow(
                            pointY = mapY(p.y),
                            previousY = mapY(s.points[if (i > 0) i - 1 else i].y),
                            nextY = mapY(s.points[if (i < n - 1) i + 1 else i].y)
                        )
                    )
                    val free = labelBoxes.takeIfFree(
                        left = lx - valueGap,
                        top = ly,
                        right = lx + labelWidth + valueGap,
                        bottom = ly + layout.size.height
                    )
                    if (!free) continue
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x = lx, y = ly),
                        color = labelColor
                    )
                }
            }
        }

        // ── 5. X labels ──
        for (slot in xLabelLayouts.indices) {
            val layout = xLabelLayouts[slot]
            val p = firstPoints[labelledPoints[slot]]
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = mapX(p.x) - layout.size.width / 2f,
                    y = chartBottom + xLabelGapPx
                )
            )
        }

        // ── 5b. Axis titles ──
        // Null when there is no room at all: drawing the full-length title would
        // put it across the plot. Re-measured per width rather than per frame.
        fun fitted(
            measured: TextLayoutResult,
            text: String,
            available: Float,
            slot: FittedTitle
        ): TextLayoutResult? {
            if (available <= 0f) return null
            if (measured.size.width <= available) return measured
            val target = available.toInt()
            if (slot.width != target) {
                slot.width = target
                slot.layout = textMeasurer.measure(
                    text = text,
                    style = labelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = target)
                )
            }
            return slot.layout
        }

        // Positioned by the unconstrained height the insets reserved, never by the
        // trimmed one, so a title cannot drift out of the band it was given.
        val xMeasured = xTitleLayout
        if (xTitle != null && xMeasured != null) {
            fitted(xMeasured, xTitle, chartRight - chartLeft, xFittedTitle)?.let { layout ->
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = chartLeft + (chartRight - chartLeft - layout.size.width) / 2f,
                        y = size.height - labelGapPx - xMeasured.size.height
                    )
                )
            }
        }
        val yMeasured = yTitleLayout
        if (yTitle != null && yMeasured != null) {
            // Rotated, so the space it has to fit is the plot's height.
            fitted(yMeasured, yTitle, chartBottom - chartTop, yFittedTitle)?.let { layout ->
                val half = yMeasured.size.height / 2f
                val cx = if (isRtl) size.width - labelGapPx - half else labelGapPx + half
                val cy = chartTop + (chartBottom - chartTop) / 2f
                rotate(degrees = -90f, pivot = Offset(cx, cy)) {
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(cx - layout.size.width / 2f, cy - half)
                    )
                }
            }
        }

        // ── 6. Crosshair + tooltip ──
        selectedPointIndex?.let { idx ->
            val anchorX = axis.positions.getOrNull(idx) ?: return@let
            if (xIsPinned && !isWithinAxis(anchorX, xMin, xMax)) return@let
            val crossX = mapX(anchorX)

            drawLine(
                color = crosshairConfig.lineColor,
                start = Offset(x = crossX, y = chartTop),
                end = Offset(x = crossX, y = chartBottom),
                strokeWidth = crosshairConfig.lineWidth.toPx()
            )

            val dotR = crosshairConfig.dotRadius.toPx()
            val borderW = crosshairConfig.dotBorderWidth.toPx()
            series.forEachIndexed { i, s ->
                val at = axis.pointIndex(s.id, idx)
                if (at >= 0) {
                    if (yIsPinned && !isWithinAxis(s.points[at].y, yMin, yMax)) return@forEachIndexed
                    val key = keyMatrix[s.id]?.getOrNull(at) ?: return@forEachIndexed
                    val animY = animationEngine.yAnimatables[key]?.value ?: s.points[at].y
                    val cy = mapY(animY)
                    drawCircle(
                        color = crosshairConfig.dotBorderColor,
                        radius = dotR + borderW,
                        center = Offset(x = crossX, y = cy)
                    )
                    drawCircle(color = s.color, radius = dotR, center = Offset(x = crossX, y = cy))
                }
            }

            if (crosshairConfig.showTooltip) {
                val tooltipText = buildString {
                    series.forEach { s ->
                        val at = axis.pointIndex(s.id, idx)
                        if (at >= 0) {
                            if (isNotEmpty()) append("\n")
                            append(crosshairConfig.tooltipFormatter(s, s.points[at]))
                        }
                    }
                }
                val cacheKey = "${idx}_${tooltipText.hashCode()}"
                val layout = tooltipCache.getOrPut(cacheKey) {
                    textMeasurer.measure(
                        text = tooltipText,
                        style = tooltipStyle
                    )
                }
                val padPx = crosshairConfig.tooltipPadding.toPx()
                val tw = layout.size.width + padPx * 2
                val th = layout.size.height + padPx * 2
                val margin = 8.dp.toPx()

                // RTL: tooltip prefers the left side of the crosshair
                val spaceRight = size.width - crossX - margin
                val spaceLeft = crossX - margin
                val tx = if (isRtl) {
                    if (spaceLeft >= tw) crossX - margin - tw else crossX + margin
                } else {
                    if (spaceRight >= tw) crossX + margin else crossX - margin - tw
                }
                val ty = chartTop + margin

                drawRoundRect(
                    color = crosshairConfig.tooltipBackground,
                    topLeft = Offset(x = tx, y = ty),
                    size = Size(width = tw, height = th),
                    cornerRadius = CornerRadius(crosshairConfig.tooltipCornerRadius.toPx())
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x = tx + padPx, y = ty + padPx)
                )
            }
        }
    }
}
