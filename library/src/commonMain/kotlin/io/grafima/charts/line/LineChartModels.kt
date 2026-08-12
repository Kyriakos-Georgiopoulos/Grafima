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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.DashPattern

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

/** The tone axis labels and the legend share. Guarded by `ColorContrastTest`. */
internal val AxisLabelGrey = Color(0xFF64748B)

/** What [LineAxisConfig.dashedGrid] meant, in dp rather than the raw pixels it used. */
internal val LegacyDashedGrid = DashPattern(dash = 2.5.dp, gap = 2.dp)

/**
 * How value labels are printed by default. Guarded by `ColorContrastTest`.
 *
 * Darker than [AxisLabelGrey]: these sit inside the plot, over grid lines and area
 * fills rather than on the background.
 */
internal val ValueLabelTextStyle = TextStyle(
    color = Color(0xFF334155),
    fontSize = 10.sp,
    fontWeight = FontWeight.Medium
)

/**
 * Whether the stroke is drawn as a gradient rather than in [LineSeries.color].
 *
 * Read by the chart and by [LineLegend], which must agree or the key names a line
 * it does not match.
 */
internal val LineSeries.hasStrokeGradient: Boolean
    get() = strokeGradientColors.size >= 2

/**
 * What a screen reader should say for this point: its [LineDataPoint.contentDescription],
 * or its [LineDataPoint.label], or failing both its x.
 *
 * Public because [LineA11yConfig.selectedPointDescriptionBuilder] is the documented
 * way to reword announcements, and an override that re-derived this by hand would
 * drift from it.
 */
val LineDataPoint.spokenLabel: String
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
 * @param dashPattern Dashes the stroke. Dashing marks a series as derived rather
 *   than measured, which is how a moving average is told apart from the readings
 *   it averages. Null draws solid. The area fill is never dashed.
 * @param dotRadius Sizes this series' dots on their own, so a marker can outweigh
 *   the curve it marks. [Dp.Unspecified] takes [LineChartStyle.dotRadius]; `0.dp`
 *   leaves this series without dots while the rest of the chart keeps theirs. Read
 *   only when [LineChartStyle.showDots] is on.
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
    val strokeGradientColors: List<Color> = emptyList(),
    val dashPattern: DashPattern? = null,
    val dotRadius: Dp = Dp.Unspecified
)

/** Which axis a [ReferenceLine] is fixed to. */
enum class ReferenceLineAxis {
    /** A vertical line standing at an x value. */
    X,

    /** A horizontal line lying at a y value. */
    Y
}

/**
 * A line drawn across the plot at a fixed axis value.
 *
 * Marks a threshold the data is read against — a target, a limit, or the point
 * "now" has reached on an axis of hours:
 *
 * ```
 * axisConfig = LineAxisConfig(
 *     referenceLines = listOf(
 *         ReferenceLine(value = 14f, axis = ReferenceLineAxis.X, label = "Now"),
 *         ReferenceLine(value = 10f, axis = ReferenceLineAxis.Y, label = "Limit", color = Color.Red)
 *     )
 * )
 * ```
 *
 * Drawn over the series, because a marker hidden behind the data it qualifies is
 * not a marker. A value outside the axis range draws nothing rather than being
 * pulled to the nearest edge, where it would name a threshold that is not there.
 *
 * @param value Where on [axis] the line sits, in data units.
 * @param axis Whether [value] is an x or a y.
 * @param label Drawn beside the line, in the line's own colour and at
 *   [LineAxisConfig.labelFontSize], so a sighted reader knows what the threshold
 *   is. Null or blank draws nothing. It takes a box like a value label and claims
 *   it first, so the two never print over each other.
 * @param color Line color.
 * @param strokeWidth Line thickness.
 * @param dashPattern Dashes the line. Null draws solid.
 * @param contentDescription What a screen reader calls this line, when the drawn
 *   [label] is not what it should hear — a fuller form than the plot has room for.
 *   Left null, the label is spoken, so naming a line once names it for everyone. A
 *   line with neither is drawn but not announced.
 * @param includeInRange Widens the axis to reach this line when the data does not.
 *   A target is usually above what has been achieved so far, and an axis fitted to
 *   the data alone would leave the line off the chart entirely. Set false to leave
 *   the axis to the data, and accept that the line may not be drawn. A pinned
 *   [LineAxisConfig.yMin], `yMax`, `xMin` or `xMax` wins over this, so a line
 *   outside a pinned range is still not drawn.
 *
 *   Worth setting false for an x line well outside the data: an axis stretched to
 *   reach x = 20 for points spanning 0..2 squeezes them into a tenth of the plot.
 *   A y target above the data is the case this exists for.
 */
@Immutable
data class ReferenceLine(
    val value: Float,
    val axis: ReferenceLineAxis,
    val label: String? = null,
    val color: Color = AxisLabelGrey,
    val strokeWidth: Dp = 1.dp,
    val dashPattern: DashPattern? = null,
    val contentDescription: String? = null,
    val includeInRange: Boolean = true
) {

    /**
     * What a screen reader calls this line: its [contentDescription], or its
     * [label] when it has none. Null when it has neither.
     *
     * Resolved on read rather than defaulted in the constructor, because `copy()`
     * never re-runs a default — `copy(label = "Limit")` would keep announcing the
     * old name.
     */
    val spokenLabel: String?
        get() = contentDescription?.takeIf(String::isNotBlank)
            ?: label?.takeIf(String::isNotBlank)
}

/** The [ReferenceLine] values that [axis] must be wide enough to show. */
internal fun List<ReferenceLine>.boundsOn(axis: ReferenceLineAxis): List<Float> =
    mapNotNull { line ->
        line.value.takeIf { line.includeInRange && line.axis == axis && it.isFinite() }
    }

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
 * @param dashedGrid Superseded by [gridDashPattern], which says how long a dash is
 *   rather than only whether there is one, and says it in dp so it is the same size
 *   on every screen. Set, it still wins.
 * @param gridDashPattern Dashes the grid lines. Null draws them solid. A
 *   [dashedGrid] of true still wins over this until it is removed.
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
 * @param referenceLines Thresholds drawn across the plot at fixed axis values,
 *   over the series. Announced to screen readers through
 *   [LineA11yConfig.referenceLineDescriptionBuilder].
 */
@Immutable
data class LineAxisConfig(
    val showGrid: Boolean = true,
    val showVerticalGrid: Boolean = false,
    val gridColor: Color = Color(0xFFF1F5F9),
    val gridStrokeWidth: Dp = 1.dp,
    val axisColor: Color = Color(0xFFE2E8F0),
    val axisStrokeWidth: Dp = 1.dp,
    val labelColor: Color = AxisLabelGrey,
    val labelFontSize: TextUnit = 10.sp,
    val yTickCount: Int = 5,
    val xLabelFormatter: (Float) -> String = { it.toInt().toString() },
    val yLabelFormatter: (Float) -> String = { it.toInt().toString() },
    val includeZeroInYRange: Boolean = true,
    val showXLabels: Boolean = true,
    val showYLabels: Boolean = true,
    val maxXLabels: Int = 12,
    @Deprecated(
        "Superseded by gridDashPattern, which is measured in dp. Removed in 2.0."
    )
    val dashedGrid: Boolean = false,
    val gridDashPattern: DashPattern? = null,
    val yMin: Float? = null,
    val yMax: Float? = null,
    val xMin: Float? = null,
    val xMax: Float? = null,
    val xAxisTitle: String? = null,
    val yAxisTitle: String? = null,
    val referenceLines: List<ReferenceLine> = emptyList()
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
 * @param dotRadius Radius of the always-visible data point dots. A series that sets
 *   [LineSeries.dotRadius] uses that instead.
 * @param minSize Minimum intrinsic chart size. Applied via [Modifier.defaultMinSize].
 * @param labelGap Gap in dp between axis labels and the chart drawing area.
 * @param valueLabels Prints each point's value beside it. Off by default.
 */
@Immutable
data class LineChartStyle(
    val curveType: LineCurveType = LineCurveType.MonotoneCubic,
    val showDots: Boolean = false,
    val dotRadius: Dp = 3.dp,
    val minSize: Dp = 200.dp,
    val labelGap: Dp = 8.dp,
    val valueLabels: LineValueLabelConfig = LineValueLabelConfig()
)

/**
 * Values printed at the points themselves rather than only in the tooltip.
 *
 * A chart of a few points reads better with its numbers on it than with a tooltip
 * that has to be found by touch — and a screenshot of one carries the numbers with
 * it.
 *
 * ```
 * style = LineChartStyle(
 *     valueLabels = LineValueLabelConfig(
 *         enabled = true,
 *         formatter = { _, point -> "${point.y.toInt()}g" }
 *     )
 * )
 * ```
 *
 * Labels are placed above their point, or below it where there is no room above.
 * Where two would overlap the later one is dropped, so a crowded series shows what
 * fits rather than stacking text on text.
 *
 * Nothing is added to the screen reader description. A listener already reaches
 * any value by selecting its point, and reading all of them out up front would
 * bury the summary the description opens with.
 *
 * @param enabled Master toggle.
 * @param formatter Builds a point's printed text, as
 *   [LineCrosshairConfig.tooltipFormatter] does for the tooltip — so two series in
 *   different units can each carry their own. Given the point from the data, not
 *   the animated one, so the text never counts up during entry. Return an empty
 *   string to leave a point unlabelled, which is how you print only the last one.
 *   Hoist a formatter that captures anything in a `remember`: a fresh lambda makes
 *   this config unequal on every recomposition, and every point is measured again.
 * @param textStyle Label text. The default tone holds WCAG AA on a white surface
 *   and is checked by `ColorContrastTest`. A style that names no color of its own —
 *   which is every `MaterialTheme.typography` style — keeps that default tone and
 *   takes the rest.
 * @param useSeriesColor Prints each label in its own series' colour, which says
 *   which line a number belongs to on a crowded chart. The contrast of the result
 *   is then whatever the series colours are, so it is off by default.
 */
@Immutable
data class LineValueLabelConfig(
    val enabled: Boolean = false,
    val formatter: (LineSeries, LineDataPoint) -> String = { _, p -> p.y.toInt().toString() },
    val textStyle: TextStyle = ValueLabelTextStyle,
    val useSeriesColor: Boolean = false
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
 * @param referenceLineDescriptionBuilder Announces the thresholds drawn across the
 *   plot, which a sighted reader gets from the lines themselves. Given only the
 *   lines that fall inside the axis range, and none at all for a chart with no
 *   series, since those draw nothing. A line is named by its
 *   [ReferenceLine.spokenLabel] — its `contentDescription`, or its `label`. Return
 *   an empty string to leave them unspoken.
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
        },
    val referenceLineDescriptionBuilder: (List<ReferenceLine>) -> String = { lines ->
        val named = lines.mapNotNull { it.spokenLabel }
        when (named.size) {
            0 -> ""
            1 -> "Reference line: ${named.first()}."
            else -> "Reference lines: ${named.joinToString(", ")}."
        }
    },

    /** Names each point's action in the actions menu. */
    val selectActionLabel: (String) -> String = { spoken -> "Select $spoken" },

    /** Names the action that clears the selection. */
    val clearSelectionLabel: String = "Clear selection"
)
