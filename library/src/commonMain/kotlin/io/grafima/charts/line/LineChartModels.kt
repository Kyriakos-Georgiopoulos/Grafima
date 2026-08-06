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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val label: String = "",
    /**
     * Spoken instead of [label] when set.
     *
     * Axis labels are abbreviated to fit — twelve of them have to share the width
     * — but "Apr" is not what a listener wants to hear. Set this to the full form
     * and the chart keeps drawing [label] while announcing this.
     */
    val contentDescription: String = ""
)

/**
 * Whether the stroke is drawn as a gradient rather than in [LineSeries.color].
 *
 * Read by the chart and by [LineLegend], which must agree or the key names a line
 * it does not match.
 */
internal val LineSeries.hasStrokeGradient: Boolean
    get() = strokeGradientColors.size >= 2

/** What a screen reader should say for this point. */
internal val LineDataPoint.spokenLabel: String
    get() = contentDescription
        .ifEmpty { label }
        .ifEmpty { x.toInt().toString() }

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
 * @param strokeGradientColors When set with 2+ colors, the line stroke renders as
 *   a horizontal gradient spanning the x axis, so the same color sits at the same
 *   x on every series and on every chart sharing that axis. Falls back to solid
 *   [color] when empty.
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
 * Set any of [yMin], [yMax], [xMin] or [xMax] to pin that edge instead, which is
 * what several charts on one shared scale need — otherwise each rescales to its
 * own data and they can no longer be compared by height. Pinning the y-axis turns
 * nice-number rounding off and divides the range into [yTickCount] equal steps,
 * because rounding would move the edge you just pinned.
 *
 * A line that leaves a pinned range is cut where it crosses the bound, leaving a
 * gap, and points outside the range get neither a dot nor a crosshair. A pin that
 * cannot produce a usable range — inverted, empty, or not finite — is ignored and
 * the axis falls back to fitting the data.
 *
 * @param showGrid Horizontal grid lines at each y-tick value.
 * @param showVerticalGrid Vertical grid lines at each x data point.
 * @param gridColor Grid line color. Use a very light tone to avoid overwhelming data.
 * @param gridStrokeWidth Grid line width. 1.dp is standard.
 * @param axisColor Color for the x-axis and y-axis baseline.
 * @param axisStrokeWidth Width of axis lines.
 * @param labelColor Text color for the x and y labels and the axis titles.
 * @param labelFontSize Font size for the axis labels and the axis titles.
 * @param yTickCount Desired number of y-axis intervals. The actual count may differ
 *   slightly due to nice-number rounding (e.g. 5 requested, 6 produced if the range
 *   rounds better that way).
 * @param xLabelFormatter Converts x-values to label strings. Called when
 *   [LineDataPoint.label] is empty. Default produces integers.
 * @param yLabelFormatter Converts y-tick values to label strings. Default produces
 *   integers. Override for currency, percentages, or decimal formatting.
 * @param includeZeroInYRange When true, the y-axis always starts at or below 0.
 *   Set to false for datasets where all values are large (e.g. 500..600) to zoom in.
 *   Ignored when [yMin] is set.
 * @param showXLabels Show labels below the x-axis.
 * @param showYLabels Show labels beside the y-axis.
 * @param maxXLabels Upper bound on visible x-labels. When more data points exist,
 *   labels are thinned by showing every Nth label. Prevents crowding.
 * @param dashedGrid Render grid lines as dashed instead of solid.
 * @param yMin Pins the bottom of the y-axis. Null auto-computes it from the data.
 * @param yMax Pins the top of the y-axis. Null auto-computes it from the data.
 * @param xMin Pins the left of the x-axis (the right in RTL). Null uses the
 *   smallest x in the data.
 * @param xMax Pins the right of the x-axis (the left in RTL). Null uses the
 *   largest x in the data.
 * @param xAxisTitle Names the x-axis below its labels, for the unit the numbers
 *   are in. Null or blank draws nothing. Screen readers announce it too.
 * @param yAxisTitle Names the y-axis, drawn rotated beside its labels — on the
 *   left, or the right in RTL. Null or blank draws nothing.
 */
@Immutable
data class LineAxisConfig(
    val showGrid: Boolean = true,
    val showVerticalGrid: Boolean = false,
    val gridColor: Color = Color(0xFFF1F5F9),
    val gridStrokeWidth: Dp = 1.dp,
    val axisColor: Color = Color(0xFFE2E8F0),
    val axisStrokeWidth: Dp = 1.dp,
    val labelColor: Color = Color(0xFF64748B),
    val labelFontSize: TextUnit = 10.sp,
    val yTickCount: Int = 5,
    val xLabelFormatter: (Float) -> String = { it.toInt().toString() },
    val yLabelFormatter: (Float) -> String = { it.toInt().toString() },
    val includeZeroInYRange: Boolean = true,
    val showXLabels: Boolean = true,
    val showYLabels: Boolean = true,
    val maxXLabels: Int = 12,
    val dashedGrid: Boolean = false,
    val yMin: Float? = null,
    val yMax: Float? = null,
    val xMin: Float? = null,
    val xMax: Float? = null,
    val xAxisTitle: String? = null,
    val yAxisTitle: String? = null
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
 * @param axisTitleDescriptionBuilder Announces [LineAxisConfig.xAxisTitle] and
 *   [LineAxisConfig.yAxisTitle], which carry the unit the numbers are in. Each is
 *   null when unset. Override to translate the wording; return an empty string to
 *   leave the titles unspoken.
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
                    append("${s.label} at ${p.spokenLabel}: ${p.y.toInt()}. ")
                }
            }
        }
    },
    val axisTitleDescriptionBuilder: (xAxisTitle: String?, yAxisTitle: String?) -> String =
        { xAxisTitle, yAxisTitle ->
            buildString {
                xAxisTitle?.let { append("X axis: $it.") }
                yAxisTitle?.let {
                    if (isNotEmpty()) append(' ')
                    append("Y axis: $it.")
                }
            }
        }
)
