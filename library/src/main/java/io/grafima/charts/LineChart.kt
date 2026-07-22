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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ==========================================
// 1. DATA MODELS & CONFIG
// ==========================================

/**
 * A single data point on a line chart.
 *
 * Points within a [LineSeries] must be sorted by [x] in ascending order.
 * The chart does not sort internally.
 *
 * @param x Horizontal position. Can represent time, index, or any continuous scale.
 * @param y Vertical value. Negative values are supported when
 *   [LineAxisConfig.includeZeroInYRange] is false.
 * @param label Optional x-axis label at this point (e.g. "Jan", "Q1").
 *   When empty, [LineAxisConfig.xLabelFormatter] is called with [x] instead.
 */
@Immutable
data class LineDataPoint(
    val x: Float,
    val y: Float,
    val label: String = ""
)

/**
 * A data series rendered as a single line on the chart.
 *
 * Each series produces a stroke path and optionally an area fill beneath it.
 * Multiple series overlay in list order (first series draws behind).
 *
 * ```
 * LineSeries(
 *     id = "revenue",
 *     label = "Revenue",
 *     points = months.mapIndexed { i, name -> LineDataPoint(i.toFloat(), values[i], name) },
 *     color = Color(0xFF6366F1),
 *     fillAlpha = 0.12f,
 *     strokeGradientColors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
 * )
 * ```
 *
 * @param id Stable identifier for animation tracking. Changing the id is treated
 *   as a removal + insertion, not a morph. Must be unique within the dataset.
 * @param label Human-readable name shown in crosshair tooltips and accessibility.
 * @param points Data sorted by [LineDataPoint.x]. All series in a dataset should
 *   ideally share the same x positions for correct crosshair alignment.
 * @param color Primary color used for the line stroke, data dots, and the
 *   auto-generated area fill gradient. Ignored for stroke when
 *   [strokeGradientColors] has 2+ entries.
 * @param fillAlpha Opacity of the area fill under the curve. 0 = no fill (line only),
 *   0.1..0.2 = subtle wash, 1.0 = fully opaque. The fill is a vertical gradient
 *   from [color] at the curve to transparent at the x-axis.
 * @param strokeWidth Thickness of the line stroke.
 * @param fillGradientColors Overrides the auto-generated area fill gradient with
 *   explicit colors. Applied as a top-to-bottom vertical gradient.
 * @param strokeGradientColors When set with 2+ colors, the line stroke renders
 *   as a horizontal gradient mapped from the leftmost to the rightmost data point.
 *   Falls back to solid [color] when empty.
 */
@Immutable
data class LineSeries(
    val id: String,
    val label: String,
    val points: List<LineDataPoint>,
    val color: Color = Color(0xFF6366F1),
    val fillAlpha: Float = 0f,
    val strokeWidth: Dp = 2.5.dp,
    val fillGradientColors: List<Color> = emptyList(),
    val strokeGradientColors: List<Color> = emptyList()
)

/**
 * Groups one or more [LineSeries] into a renderable dataset.
 *
 * @param series Lines to render. Drawing order: index 0 draws behind index N.
 * @param contentDescription Root accessibility label for the chart container.
 */
@Immutable
data class LineDataSet(
    val series: List<LineSeries>,
    val contentDescription: String = "Line Chart"
)

/**
 * Curve interpolation between data points.
 *
 * [Linear] connects points with straight segments. Fast to compute, clear for
 * sparse or step-like data.
 *
 * [MonotoneCubic] uses the Fritsch-Carlson algorithm to produce smooth C1-continuous
 * cubic bezier curves that are mathematically guaranteed not to overshoot data points.
 * This is the standard for financial charts, analytics dashboards, and any dataset
 * where visual smoothness matters without introducing phantom peaks.
 */
enum class LineCurveType {
    Linear,
    MonotoneCubic
}

/**
 * Controls axes, grid, and labels.
 *
 * The y-axis range is auto-computed from the data with nice-number rounding
 * (1, 2, 5 multiples) so tick labels are always clean values like 0, 20, 40, 60
 * rather than 0, 17.4, 34.8.
 *
 * @param showGrid Horizontal grid lines at each y-tick value.
 * @param showVerticalGrid Vertical grid lines at each x data point.
 * @param gridColor Grid line color. Use a very light tone to avoid overwhelming data.
 * @param gridStrokeWidth Grid line width. 1.dp is standard.
 * @param axisColor Color for the x-axis and y-axis baseline.
 * @param axisStrokeWidth Width of axis lines.
 * @param labelColor Text color for both x and y labels.
 * @param labelFontSize Font size for axis labels.
 * @param yTickCount Desired number of y-axis intervals. The actual count may differ
 *   slightly due to nice-number rounding (e.g. 5 requested, 6 produced if the range
 *   rounds better that way).
 * @param xLabelFormatter Converts x-values to label strings. Called when
 *   [LineDataPoint.label] is empty. Default produces integers.
 * @param yLabelFormatter Converts y-tick values to label strings. Default produces
 *   integers. Override for currency, percentages, or decimal formatting.
 * @param includeZeroInYRange When true, the y-axis always starts at or below 0.
 *   Set to false for datasets where all values are large (e.g. 500..600) to zoom in.
 * @param showXLabels Show labels below the x-axis.
 * @param showYLabels Show labels beside the y-axis.
 * @param maxXLabels Upper bound on visible x-labels. When more data points exist,
 *   labels are thinned by showing every Nth label. Prevents crowding.
 * @param dashedGrid Render grid lines as dashed instead of solid.
 */
@Immutable
data class LineAxisConfig(
    val showGrid: Boolean = true,
    val showVerticalGrid: Boolean = false,
    val gridColor: Color = Color(0xFFF1F5F9),
    val gridStrokeWidth: Dp = 1.dp,
    val axisColor: Color = Color(0xFFE2E8F0),
    val axisStrokeWidth: Dp = 1.dp,
    val labelColor: Color = Color(0xFF94A3B8),
    val labelFontSize: TextUnit = 10.sp,
    val yTickCount: Int = 5,
    val xLabelFormatter: (Float) -> String = { it.toInt().toString() },
    val yLabelFormatter: (Float) -> String = { it.toInt().toString() },
    val includeZeroInYRange: Boolean = true,
    val showXLabels: Boolean = true,
    val showYLabels: Boolean = true,
    val maxXLabels: Int = 12,
    val dashedGrid: Boolean = false
)

/**
 * Controls the crosshair shown when the user touches or drags on the chart.
 *
 * The crosshair snaps to the nearest x data point and shows a vertical line,
 * highlighted dots on each series, and an optional tooltip. During a drag,
 * the crosshair follows the finger in real time.
 *
 * @param enabled Master toggle. When false, no pointer input is registered.
 * @param showTooltip Show a floating label with series values at the selected point.
 * @param lineColor Color of the vertical crosshair line.
 * @param lineWidth Width of the crosshair line.
 * @param dotRadius Size of the highlighted dot on each series at the selected x.
 * @param dotBorderWidth White border around each highlighted dot for contrast.
 * @param dotBorderColor Border color (typically white or the chart background).
 * @param tooltipBackground Background color of the tooltip panel.
 * @param tooltipTextColor Text color inside the tooltip.
 * @param tooltipFontSize Font size for tooltip text.
 * @param tooltipCornerRadius Corner rounding of the tooltip panel.
 * @param tooltipPadding Internal padding of the tooltip panel.
 * @param tooltipFormatter Builds the tooltip text for each series at the selected
 *   point. Called once per series. Lines are joined with newlines.
 */
@Immutable
data class LineCrosshairConfig(
    val enabled: Boolean = true,
    val showTooltip: Boolean = true,
    val lineColor: Color = Color(0xFFCBD5E1),
    val lineWidth: Dp = 1.dp,
    val dotRadius: Dp = 6.dp,
    val dotBorderWidth: Dp = 2.5.dp,
    val dotBorderColor: Color = Color.White,
    val tooltipBackground: Color = Color(0xFF111827),
    val tooltipTextColor: Color = Color.White,
    val tooltipFontSize: TextUnit = 11.sp,
    val tooltipCornerRadius: Dp = 8.dp,
    val tooltipPadding: Dp = 8.dp,
    val tooltipFormatter: (LineSeries, LineDataPoint) -> String = { s, p ->
        "${s.label}: ${p.y.toInt()}"
    }
)

/**
 * Visual configuration for the chart.
 *
 * @param curveType Interpolation strategy. [LineCurveType.MonotoneCubic] is recommended
 *   for smooth, accurate curves.
 * @param showDots Render small dots at every data point (independent of crosshair).
 * @param dotRadius Radius of the always-visible data point dots.
 * @param minSize Minimum intrinsic chart size. Applied via [Modifier.defaultMinSize].
 * @param labelGap Gap in dp between axis labels and the chart drawing area.
 */
@Immutable
data class LineChartStyle(
    val curveType: LineCurveType = LineCurveType.MonotoneCubic,
    val showDots: Boolean = false,
    val dotRadius: Dp = 3.dp,
    val minSize: Dp = 200.dp,
    val labelGap: Dp = 8.dp
)

/**
 * Animation timing for line entry, data morph, and series stagger.
 *
 * On first appearance, each point's y-value animates from the baseline (y-axis
 * minimum) to its target. Points animate left-to-right with a cascading delay,
 * creating a "wave" reveal effect. When data changes, existing points spring
 * from their current position to the new target simultaneously.
 *
 * @param entrySpec Drives the initial point reveal. A tween with ease-out works well.
 * @param morphSpec Drives value changes on existing points. A spring gives elastic feel.
 * @param staggerMs Delay between successive points within a single series.
 *   12ms for 50 points = 600ms spread. Increase for fewer points to make the
 *   wave visible.
 * @param startDelayMs Delay before the very first point begins animating.
 * @param seriesStaggerMs Delay between series. The second series starts
 *   [seriesStaggerMs] after the first, creating a layered reveal.
 */
@Immutable
data class LineAnimationConfig(
    val entrySpec: AnimationSpec<Float> = tween(900, easing = FastOutSlowInEasing),
    val morphSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessLow
    ),
    val staggerMs: Long = 12L,
    val startDelayMs: Long = 80L,
    val seriesStaggerMs: Long = 120L
)

/**
 * Accessibility description builders.
 *
 * @param chartDescriptionBuilder Builds the base screen reader description from the
 *   full dataset. Called once per data change. Default announces series names,
 *   value ranges, and point counts.
 * @param selectedPointDescriptionBuilder Appended to the base description when
 *   the crosshair is active. Announces the value at the selected point for each
 *   series. TalkBack reads this on each crosshair move.
 */
@Stable
data class LineA11yConfig(
    val chartDescriptionBuilder: (LineDataSet) -> String = { ds ->
        buildString {
            append("Line Chart: ${ds.contentDescription}. ")
            ds.series.forEach { s ->
                val mn = s.points.minOfOrNull { it.y }?.toInt() ?: 0
                val mx = s.points.maxOfOrNull { it.y }?.toInt() ?: 0
                append("${s.label}: range $mn to $mx, ${s.points.size} points. ")
            }
        }
    },
    val selectedPointDescriptionBuilder: (Int, List<LineSeries>) -> String = { idx, series ->
        buildString {
            series.forEach { s ->
                if (idx in s.points.indices) {
                    val p = s.points[idx]
                    val lbl = p.label.ifEmpty { p.x.toInt().toString() }
                    append("${s.label} at $lbl: ${p.y.toInt()}. ")
                }
            }
        }
    }
)

// ==========================================
// 2. INTERNAL HELPERS
// ==========================================

/**
 * Rounds axis step to a "nice" number (1, 2, 5 * 10^n) and generates evenly
 * spaced tick values that fully contain the data range.
 */
private fun computeNiceAxisTicks(dataMin: Float, dataMax: Float, tickCount: Int): List<Float> {
    if (tickCount <= 0 || dataMax <= dataMin) return listOf(dataMin, dataMax)
    val rawStep = (dataMax - dataMin) / tickCount
    if (rawStep <= 0f) return listOf(dataMin, dataMax)
    val magnitude = 10f.pow(floor(log10(rawStep)))
    val norm = rawStep / magnitude
    val niceStep = when {
        norm <= 1.0f -> magnitude
        norm <= 2.0f -> 2f * magnitude
        norm <= 5.0f -> 5f * magnitude
        else -> 10f * magnitude
    }
    val niceMin = floor(dataMin / niceStep) * niceStep
    val niceMax = ceil(dataMax / niceStep) * niceStep
    val count = ((niceMax - niceMin) / niceStep + 0.5f).toInt()
    return (0..count).map { niceMin + it * niceStep }
}

/**
 * Fritsch-Carlson monotone cubic tangent computation. Operates entirely on
 * pre-allocated [FloatArray] buffers with zero heap allocation.
 *
 * [deltas] is a scratch buffer of size >= n-1 that avoids a per-frame allocation.
 * The result is written into [tangents] in-place.
 */
private fun computeMonotoneTangents(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, deltas: FloatArray, n: Int
) {
    if (n < 2) return
    for (i in 0 until n - 1) {
        val dx = xs[i + 1] - xs[i]
        deltas[i] = if (dx == 0f) 0f else (ys[i + 1] - ys[i]) / dx
    }
    tangents[0] = deltas[0]
    tangents[n - 1] = deltas[n - 2]
    for (i in 1 until n - 1) {
        tangents[i] = if (deltas[i - 1] * deltas[i] <= 0f) 0f
        else (deltas[i - 1] + deltas[i]) / 2f
    }
    // Monotonicity enforcement: clamp tangent magnitudes so alpha^2 + beta^2 <= 9
    for (i in 0 until n - 1) {
        if (deltas[i] == 0f) {
            tangents[i] = 0f; tangents[i + 1] = 0f
        } else {
            val a = tangents[i] / deltas[i]
            val b = tangents[i + 1] / deltas[i]
            val s = a * a + b * b
            if (s > 9f) {
                val tau = 3f / sqrt(s)
                tangents[i] = tau * a * deltas[i]
                tangents[i + 1] = tau * b * deltas[i]
            }
        }
    }
}

/** Builds a line path (stroke only) from pre-computed screen-space buffers. */
private fun Path.buildCurve(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, n: Int,
    curveType: LineCurveType
) {
    if (n < 1) return
    moveTo(xs[0], ys[0])
    if (n == 1) return
    when (curveType) {
        LineCurveType.Linear -> for (i in 1 until n) lineTo(xs[i], ys[i])
        LineCurveType.MonotoneCubic -> for (i in 0 until n - 1) {
            val dx = (xs[i + 1] - xs[i]) / 3f
            cubicTo(
                xs[i] + dx, ys[i] + tangents[i] * dx,
                xs[i + 1] - dx, ys[i + 1] - tangents[i + 1] * dx,
                xs[i + 1], ys[i + 1]
            )
        }
    }
}

/** Builds a closed area path: curve on top, straight bottom edge at [chartBottom]. */
private fun Path.buildArea(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, n: Int,
    chartBottom: Float, curveType: LineCurveType
) {
    if (n < 1) return
    buildCurve(xs, ys, tangents, n, curveType)
    lineTo(xs[n - 1], chartBottom)
    lineTo(xs[0], chartBottom)
    close()
}

// ==========================================
// 3. ANIMATION ENGINE
// ==========================================

/**
 * Manages per-point Y-value [Animatable] instances keyed as `"seriesId::pointIndex"`.
 *
 * Lifecycle (same pattern as PieChart/RadarChart):
 * - [syncAnimatables]: synchronous map housekeeping via [SideEffect], ensures
 *   animatable instances exist for current data before the first draw.
 * - [launchEntryAnimations]: staggered async animations via [LaunchedEffect] scope.
 *   Old coroutines cancel automatically when data changes.
 */
@Stable
class LineChartAnimationEngine {
    internal val yAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedKeys = mutableSetOf<String>()

    /** Ensures animatables exist for all current points. Removes stale entries. */
    fun syncAnimatables(series: List<LineSeries>) {
        val activeKeys = mutableSetOf<String>()
        series.forEach { s ->
            s.points.forEachIndexed { i, _ ->
                val key = "${s.id}::$i"
                activeKeys.add(key)
                yAnimatables.getOrPut(key) { Animatable(0f) }
            }
        }
        yAnimatables.keys.removeAll { it !in activeKeys }
        initializedKeys.removeAll { it !in activeKeys }
    }

    /**
     * Launches staggered entry or morph animations. New points animate from
     * [yBaseline]; existing points spring to their updated target.
     */
    fun launchEntryAnimations(
        series: List<LineSeries>,
        config: LineAnimationConfig,
        yBaseline: Float,
        scope: CoroutineScope
    ) {
        series.forEachIndexed { si, s ->
            s.points.forEachIndexed { pi, point ->
                val key = "${s.id}::$pi"
                val anim = yAnimatables[key] ?: return@forEachIndexed
                val isInitial = initializedKeys.add(key)
                scope.launch {
                    if (isInitial) {
                        anim.snapTo(yBaseline)
                        delay(config.startDelayMs + si * config.seriesStaggerMs + pi * config.staggerMs)
                        anim.animateTo(point.y, config.entrySpec)
                    } else if (anim.targetValue != point.y) {
                        anim.animateTo(point.y, config.morphSpec)
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. THE CHART COMPOSABLE
// ==========================================

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
 * @param selectedPointIndex Currently highlighted x-axis data point index, or null.
 *   Hoist this in the parent to control crosshair externally.
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
    onPointSelected: (Int?) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val series = dataSet.series
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val animationEngine = remember { LineChartAnimationEngine() }

    // ── Stable state refs for pointerInput(Unit) ──
    val currentSeries by rememberUpdatedState(series)
    val currentOnPointSelected by rememberUpdatedState(onPointSelected)
    val currentDensity by rememberUpdatedState(density)
    val currentIsRtl by rememberUpdatedState(isRtl)

    // ── Data ranges (recomputed only on data change) ──
    val allPoints = remember(series) { series.flatMap { it.points } }
    val xMin = remember(allPoints) { allPoints.minOfOrNull { it.x } ?: 0f }
    val xMax = remember(allPoints) { allPoints.maxOfOrNull { it.x } ?: 1f }
    val currentXMin by rememberUpdatedState(xMin)
    val currentXMax by rememberUpdatedState(xMax)

    val yDataMax = remember(allPoints) { allPoints.maxOfOrNull { it.y } ?: 1f }
    val yDataMin = remember(allPoints, axisConfig.includeZeroInYRange) {
        val raw = allPoints.minOfOrNull { it.y } ?: 0f
        if (axisConfig.includeZeroInYRange) min(0f, raw) else raw
    }
    val yTickValues = remember(yDataMin, yDataMax, axisConfig.yTickCount) {
        computeNiceAxisTicks(yDataMin, yDataMax, axisConfig.yTickCount)
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
    val yLabelLayouts = remember(yTickValues, labelStyle, axisConfig.yLabelFormatter) {
        yTickValues.map { v ->
            textMeasurer.measure(axisConfig.yLabelFormatter(v), style = labelStyle, maxLines = 1)
        }
    }
    val maxYLabelWidth = remember(yLabelLayouts) {
        if (yLabelLayouts.isEmpty()) 0f else yLabelLayouts.maxOf { it.size.width }.toFloat()
    }
    val currentMaxYLabelWidth by rememberUpdatedState(maxYLabelWidth)

    val firstPoints = series.firstOrNull()?.points ?: emptyList()
    val xLabelInterval = remember(firstPoints, axisConfig.maxXLabels) {
        max(1, (firstPoints.size + axisConfig.maxXLabels - 1) / axisConfig.maxXLabels)
    }
    val xLabelLayouts = remember(
        firstPoints,
        labelStyle,
        xLabelInterval,
        axisConfig.xLabelFormatter,
        axisConfig.showXLabels
    ) {
        if (!axisConfig.showXLabels) emptyList()
        else firstPoints.filterIndexed { i, _ -> i % xLabelInterval == 0 }.map { p ->
            textMeasurer.measure(
                p.label.ifEmpty { axisConfig.xLabelFormatter(p.x) },
                style = labelStyle.copy(textAlign = TextAlign.Center),
                maxLines = 1
            )
        }
    }
    val maxXLabelHeight = remember(xLabelLayouts) {
        if (xLabelLayouts.isEmpty()) 0f else xLabelLayouts.maxOf { it.size.height }.toFloat()
    }

    val tooltipStyle = remember(crosshairConfig.tooltipTextColor, crosshairConfig.tooltipFontSize) {
        TextStyle(
            color = crosshairConfig.tooltipTextColor,
            fontSize = crosshairConfig.tooltipFontSize,
            fontWeight = FontWeight.Medium,
            lineHeight = crosshairConfig.tooltipFontSize * 1.4f
        )
    }

    // ── Pre-computed key matrix + draw buffers (zero allocation in draw) ──
    val seriesStructure = remember(series) { series.map { it.id to it.points.size } }
    val keyMatrix =
        remember(seriesStructure) { series.associate { s -> s.id to Array(s.points.size) { i -> "${s.id}::$i" } } }
    val xBuffers =
        remember(seriesStructure) { series.associate { s -> s.id to FloatArray(s.points.size) } }
    val yBuffers =
        remember(seriesStructure) { series.associate { s -> s.id to FloatArray(s.points.size) } }
    val tangentBuffers =
        remember(seriesStructure) { series.associate { s -> s.id to FloatArray(s.points.size) } }
    val deltasBuffers = remember(seriesStructure) {
        series.associate { s ->
            s.id to FloatArray(
                max(
                    0,
                    s.points.size - 1
                )
            )
        }
    }

    // ── Cached PathEffect for dashed grid ──
    val dashEffect = remember(axisConfig.dashedGrid) {
        if (axisConfig.dashedGrid) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
    }

    // ── Accessibility ──
    val baseDescription =
        remember(dataSet, a11yConfig) { a11yConfig.chartDescriptionBuilder(dataSet) }
    val selectedDescription = remember(selectedPointIndex, series, a11yConfig) {
        selectedPointIndex?.let { idx -> a11yConfig.selectedPointDescriptionBuilder(idx, series) }
            ?: ""
    }
    val chartDescription = "$baseDescription $selectedDescription".trim()

    val tooltipCache = remember { mutableMapOf<String, TextLayoutResult>() }
    val linePath = remember { Path() }
    val areaPath = remember { Path() }

    // ── Animation lifecycle ──
    SideEffect { animationEngine.syncAnimatables(series) }
    LaunchedEffect(series) {
        tooltipCache.clear()
        animationEngine.launchEntryAnimations(series, animationConfig, yMin, this)
    }

    Canvas(
        modifier = modifier
            .defaultMinSize(minWidth = style.minSize, minHeight = style.minSize)
            .semantics(mergeDescendants = true) { contentDescription = chartDescription }
            // pointerInput(Unit): never restarts, reads all values via rememberUpdatedState
            .pointerInput(Unit) {
                if (!crosshairConfig.enabled) return@pointerInput
                awaitEachGesture {
                    val activeSeries = currentSeries
                    val fp = activeSeries.firstOrNull()?.points ?: return@awaitEachGesture
                    if (fp.isEmpty()) return@awaitEachGesture

                    val den = currentDensity
                    val gap = with(den) { style.labelGap.toPx() }
                    val rtl = currentIsRtl
                    val yLabelW = currentMaxYLabelWidth
                    val cLeft = if (rtl) gap else yLabelW + gap
                    val cRight = if (rtl) size.width - yLabelW - gap else size.width - gap
                    val cWidth = cRight - cLeft
                    val axMin = currentXMin
                    val axMax = currentXMax
                    val xRange = axMax - axMin

                    fun mapTouchX(dataX: Float): Float {
                        val raw =
                            if (xRange > 0f) cLeft + (dataX - axMin) / xRange * cWidth else cLeft
                        return if (rtl) cRight - (raw - cLeft) else raw
                    }

                    fun nearest(touchX: Float): Int =
                        fp.indices.minByOrNull { abs(mapTouchX(fp[it].x) - touchX) } ?: 0

                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnPointSelected(nearest(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        currentOnPointSelected(nearest(change.position.x))
                        change.consume()
                    }
                    currentOnPointSelected(null)
                }
            }
    ) {
        // ── Pure draw lambda: no state mutations ──
        if (series.isEmpty()) return@Canvas

        val labelGapPx = style.labelGap.toPx()

        // RTL-aware chart area: Y labels flip from left to right
        val chartLeft =
            if (isRtl) labelGapPx else (if (axisConfig.showYLabels) maxYLabelWidth + labelGapPx else labelGapPx)
        val chartRight =
            if (isRtl) (if (axisConfig.showYLabels) size.width - maxYLabelWidth - labelGapPx else size.width - labelGapPx) else size.width - labelGapPx
        val chartBottom =
            size.height - (if (axisConfig.showXLabels) maxXLabelHeight + labelGapPx else labelGapPx)
        val chartTop = labelGapPx
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop
        val xRange = xMax - xMin
        val yRange = yMax - yMin

        fun mapX(v: Float): Float {
            val raw = if (xRange > 0f) chartLeft + (v - xMin) / xRange * chartWidth else chartLeft
            return if (isRtl) chartRight - (raw - chartLeft) else raw
        }

        fun mapY(v: Float) =
            if (yRange > 0f) chartBottom - (v - yMin) / yRange * chartHeight else chartTop

        val yAxisX = if (isRtl) chartRight else chartLeft

        // ── 1. Grid ──
        if (axisConfig.showGrid) {
            val gridPx = axisConfig.gridStrokeWidth.toPx()
            yTickValues.forEach { v ->
                val y = mapY(v)
                drawLine(
                    axisConfig.gridColor,
                    Offset(chartLeft, y),
                    Offset(chartRight, y),
                    strokeWidth = gridPx,
                    pathEffect = dashEffect
                )
            }
        }
        if (axisConfig.showVerticalGrid && firstPoints.size > 1) {
            val gridPx = axisConfig.gridStrokeWidth.toPx()
            firstPoints.forEach { p ->
                val x = mapX(p.x)
                drawLine(
                    axisConfig.gridColor,
                    Offset(x, chartTop),
                    Offset(x, chartBottom),
                    strokeWidth = gridPx,
                    pathEffect = dashEffect
                )
            }
        }

        // ── 2. Axes ──
        val axisPx = axisConfig.axisStrokeWidth.toPx()
        drawLine(
            axisConfig.axisColor,
            Offset(yAxisX, chartTop),
            Offset(yAxisX, chartBottom),
            axisPx
        )
        drawLine(
            axisConfig.axisColor,
            Offset(chartLeft, chartBottom),
            Offset(chartRight, chartBottom),
            axisPx
        )

        // ── 3. Y labels (RTL: drawn on the right side) ──
        if (axisConfig.showYLabels) {
            yTickValues.forEachIndexed { i, v ->
                if (i < yLabelLayouts.size) {
                    val layout = yLabelLayouts[i]
                    val y = mapY(v)
                    val lx =
                        if (isRtl) chartRight + labelGapPx else chartLeft - labelGapPx - layout.size.width
                    drawText(layout, topLeft = Offset(lx, y - layout.size.height / 2f))
                }
            }
        }

        // ── 4. Series: area fills, line strokes, dots ──
        series.forEachIndexed { si, s ->
            val n = s.points.size
            if (n == 0) return@forEachIndexed
            val keys = keyMatrix[s.id] ?: return@forEachIndexed
            val xs = xBuffers[s.id] ?: return@forEachIndexed
            val ys = yBuffers[s.id] ?: return@forEachIndexed
            val tans = tangentBuffers[s.id] ?: return@forEachIndexed
            val delts = deltasBuffers[s.id] ?: return@forEachIndexed

            // Fill pre-allocated buffers with animated screen positions
            for (i in 0 until n) {
                val animY = animationEngine.yAnimatables[keys[i]]?.value ?: 0f
                xs[i] = mapX(s.points[i].x)
                ys[i] = mapY(animY)
            }
            if (style.curveType == LineCurveType.MonotoneCubic && n >= 2) {
                computeMonotoneTangents(xs, ys, tans, delts, n)
            }

            // Area fill (one Brush creation per gradient series per frame, acceptable)
            if (s.fillAlpha > 0f || s.fillGradientColors.isNotEmpty()) {
                areaPath.reset()
                areaPath.buildArea(xs, ys, tans, n, chartBottom, style.curveType)
                val brush = if (s.fillGradientColors.size >= 2) {
                    Brush.verticalGradient(
                        s.fillGradientColors,
                        startY = chartTop,
                        endY = chartBottom
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            s.color.copy(alpha = s.fillAlpha),
                            Color.Transparent
                        ), startY = chartTop, endY = chartBottom
                    )
                }
                drawPath(areaPath, brush)
            }

            // Line stroke: gradient or solid
            linePath.reset()
            linePath.buildCurve(xs, ys, tans, n, style.curveType)
            val strokeStyle = Stroke(width = s.strokeWidth.toPx(), cap = StrokeCap.Round)
            if (s.strokeGradientColors.size >= 2) {
                drawPath(
                    linePath,
                    Brush.horizontalGradient(
                        s.strokeGradientColors,
                        startX = xs[0],
                        endX = xs[n - 1]
                    ),
                    style = strokeStyle
                )
            } else {
                drawPath(linePath, s.color, style = strokeStyle)
            }

            if (style.showDots) {
                val dotR = style.dotRadius.toPx()
                for (i in 0 until n) drawCircle(s.color, dotR, Offset(xs[i], ys[i]))
            }
        }

        // ── 5. X labels ──
        if (axisConfig.showXLabels && xLabelLayouts.isNotEmpty()) {
            var layoutIdx = 0
            firstPoints.forEachIndexed { i, p ->
                if (i % xLabelInterval == 0 && layoutIdx < xLabelLayouts.size) {
                    val layout = xLabelLayouts[layoutIdx++]
                    drawText(
                        layout,
                        topLeft = Offset(
                            mapX(p.x) - layout.size.width / 2f,
                            chartBottom + labelGapPx
                        )
                    )
                }
            }
        }

        // ── 6. Crosshair + tooltip ──
        selectedPointIndex?.let { idx ->
            val fp = series.firstOrNull() ?: return@let
            if (idx !in fp.points.indices) return@let
            val crossX = mapX(fp.points[idx].x)

            drawLine(
                crosshairConfig.lineColor,
                Offset(crossX, chartTop),
                Offset(crossX, chartBottom),
                crosshairConfig.lineWidth.toPx()
            )

            val dotR = crosshairConfig.dotRadius.toPx()
            val borderW = crosshairConfig.dotBorderWidth.toPx()
            series.forEach { s ->
                if (idx < s.points.size) {
                    val key = keyMatrix[s.id]?.getOrNull(idx) ?: return@forEach
                    val animY = animationEngine.yAnimatables[key]?.value ?: s.points[idx].y
                    val cy = mapY(animY)
                    drawCircle(crosshairConfig.dotBorderColor, dotR + borderW, Offset(crossX, cy))
                    drawCircle(s.color, dotR, Offset(crossX, cy))
                }
            }

            if (crosshairConfig.showTooltip) {
                val tooltipText = buildString {
                    series.forEachIndexed { i, s ->
                        if (idx < s.points.size) {
                            if (i > 0) append("\n")
                            append(crosshairConfig.tooltipFormatter(s, s.points[idx]))
                        }
                    }
                }
                val cacheKey = "${idx}_${tooltipText.hashCode()}"
                val layout = tooltipCache.getOrPut(cacheKey) {
                    textMeasurer.measure(
                        tooltipText,
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
                    crosshairConfig.tooltipBackground,
                    Offset(tx, ty),
                    Size(tw, th),
                    CornerRadius(crosshairConfig.tooltipCornerRadius.toPx())
                )
                drawText(layout, topLeft = Offset(tx + padPx, ty + padPx))
            }
        }
    }
}

