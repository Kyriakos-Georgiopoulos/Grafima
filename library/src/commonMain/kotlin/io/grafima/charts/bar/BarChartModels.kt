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

package io.grafima.charts.bar

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.DashPattern

/**
 * A single gradient color stop: a [color] anchored at a fractional [position]
 * along the gradient axis (0f = gradient start, 1f = gradient end).
 *
 * @property position Position along the gradient, from 0f to 1f.
 * @property color The color at this position.
 */
@Immutable
data class ColorStop(val position: Float, val color: Color)

/**
 * A single bar in the chart.
 *
 * Give [seriesId] a value to compare several measures over the same categories.
 * Consecutive entries that carry a series and share an [xLabel] form one category,
 * drawn side by side or stacked according to [BarDataSet.mode]:
 *
 * ```
 * BarEntry("q1-rev",  "Q1", 45f, seriesId = "rev",  seriesLabel = "Revenue")
 * BarEntry("q1-cost", "Q1", 30f, seriesId = "cost", seriesLabel = "Cost")
 * BarEntry("q2-rev",  "Q2", 80f, seriesId = "rev",  seriesLabel = "Revenue")
 * BarEntry("q2-cost", "Q2", 52f, seriesId = "cost", seriesLabel = "Cost")
 * ```
 *
 * An entry with no [seriesId] is always a category of its own, so a dataset that
 * sets none behaves exactly as it did before series existed.
 *
 * @property id Unique identifier — drives animation continuity across data updates.
 * @property xLabel Text shown on the X axis below the bar. Doubles as the category
 *   key once [seriesId] is set: neighbours sharing it are grouped.
 * @property y The bar's numeric value. Must be positive.
 * @property gradientColors Vertical gradient colors. Falls back to [BarDataSet.defaultGradientColors] when null.
 * @property colorStops Explicit gradient [ColorStop]s. Takes priority over [gradientColors].
 * @property seriesId Which measure this bar belongs to. Null leaves the bar standalone.
 * @property seriesLabel Human-readable name of the series, spoken by screen readers
 *   and available for a legend you draw yourself. Falls back to [seriesId] when null.
 */
@Immutable
data class BarEntry(
    val id: String,
    val xLabel: String,
    val y: Float,
    val gradientColors: List<Color>? = null,
    val colorStops: List<ColorStop>? = null,
    val seriesId: String? = null,
    val seriesLabel: String? = null
)

/** The name a screen reader uses for this bar's series. */
val BarEntry.spokenSeriesLabel: String?
    get() = seriesLabel?.takeIf { it.isNotBlank() } ?: seriesId?.takeIf { it.isNotBlank() }

enum class BarOrientation { Vertical, Horizontal }

/**
 * How the bars of one category are arranged.
 *
 * [Grouped] sets them side by side, which compares series against each other.
 * [Stacked] piles them into one bar, which compares each series against the
 * category total. Stacking only reads correctly when the parts genuinely sum to
 * something meaningful.
 */
enum class BarGroupMode { Grouped, Stacked }

/**
 * Groups bar entries with shared defaults.
 *
 * @property entries The bars to display, in order.
 * @property defaultGradientColors Gradient applied to bars that don't specify their own.
 * @property contentDescription Accessibility label describing the chart's purpose.
 * @property mode Arrangement of bars within a category. Has no effect on a dataset
 *   whose entries carry no [BarEntry.seriesId], since every bar is then its own category.
 */
@Immutable
data class BarDataSet(
    val entries: List<BarEntry>,
    val defaultGradientColors: List<Color> = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
    val contentDescription: String = "Bar Chart",
    val mode: BarGroupMode = BarGroupMode.Grouped
)

/**
 * Visual styling for bars and labels.
 *
 * @property barCornerRadius Rounding applied to the top corners of each bar.
 * @property barSpacingFactor Fraction of chart width used for inter-bar spacing (0f..0.9f).
 * @property groupSpacingFactor Fraction of a category's slot given to the gaps between
 *   its side-by-side bars (0f..0.9f). Only applies to [BarGroupMode.Grouped] with more
 *   than one series; 0f makes the bars of a group touch.
 * @property bottomLabelSpace Vertical space reserved below bars for X-axis labels.
 * @property topValueSpace Vertical space reserved above bars for floating value labels.
 * @property unselectedAlpha Opacity applied to non-selected bars when one is selected.
 * @property showFloatingValues Whether to show animated value labels above bars during entry animation.
 */
@Immutable
data class ChartStyle(
    val barCornerRadius: Dp = 6.dp,
    val barSpacingFactor: Float = 0.35f,
    val bottomLabelSpace: Dp = 32.dp,
    val topValueSpace: Dp = 28.dp,
    val unselectedAlpha: Float = 0.25f,
    val labelTextStyle: TextStyle = TextStyle(
        color = Color(0xFF6B7280),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    ),
    val valueTextStyle: TextStyle = TextStyle(
        color = Color(0xFF111827),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    ),
    val showFloatingValues: Boolean = true,
    val groupSpacingFactor: Float = 0.08f
)

/**
 * Configuration for the Y axis and grid lines.
 *
 * @property showYAxis Whether to draw Y-axis value labels.
 * @property showGridLines Whether to draw horizontal grid lines.
 * @property yAxisSteps Number of evenly-spaced grid lines and Y-axis labels.
 * @property gridDashPattern Dashes the grid lines. Null draws them solid. An
 *   explicit [dashEffect] still wins over this until it is removed.
 * @property dashEffect Superseded by [gridDashPattern]. A [PathEffect] compares by
 *   identity, so a config holding one is never equal to another and defeats the
 *   recomposition skipping every chart relies on — and it cannot be constructed at
 *   all without the graphics runtime loaded, which puts it out of reach of a plain
 *   unit test. Set, it still wins, so existing code keeps working.
 */
@Immutable
data class AxisConfig(
    val showYAxis: Boolean = true,
    val showGridLines: Boolean = true,
    val yAxisSteps: Int = 4,
    val axisColor: Color = Color(0xFFE5E7EB),
    val axisLabelTextStyle: TextStyle = TextStyle(
        color = Color(0xFF6B7280),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    ),
    val yAxisLabelPadding: Dp = 12.dp,
    @Deprecated(
        "Superseded by gridDashPattern, which compares by value and needs no renderer. " +
            "Removed in 2.0."
    )
    val dashEffect: PathEffect? = null,
    val gridDashPattern: DashPattern? = DashPattern(dash = 5.dp, gap = 5.dp)
)

/**
 * Controls the chart's animation timing and easing.
 *
 * @property initialEntrySpec Animation for bars appearing for the first time.
 * @property morphSpec Animation for bars changing value.
 * @property selectionSpec Animation for the selection alpha transition.
 * @property staggerDelayMs Delay between each bar's initial entry animation.
 * @property startDelayMs Delay before the first bar begins animating.
 */
@Immutable
data class AnimationConfig(
    val initialEntrySpec: AnimationSpec<Float> = tween(
        durationMillis = 1200,
        easing = FastOutSlowInEasing
    ),
    val morphSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    ),
    val selectionSpec: AnimationSpec<Float> = tween(durationMillis = 250, easing = LinearEasing),
    val staggerDelayMs: Long = 120L,
    val startDelayMs: Long = 200L
)

/**
 * What a chart amounts to, for the opening line of its description.
 *
 * Only the library constructs it, so it can report more in a later release without
 * the `copy` and `componentN` of a data class freezing its shape.
 *
 * @property bars Total bars, series included.
 * @property categories Positions along the axis.
 * @property series Distinct measures. Zero when no entry carries one.
 * @property uniformGroupSize Bars per category when every category holds the same
 *   number, null when they differ. Ragged data has no single group size to report.
 */
@Immutable
class BarChartSummary internal constructor(
    val bars: Int,
    val categories: Int,
    val series: Int,
    val uniformGroupSize: Int?
)

/**
 * Accessibility configuration with builders for TalkBack descriptions.
 *
 * @property countDescriptionBuilder Opens the chart's description with what it holds.
 *   Receives a [BarChartSummary] rather than loose numbers, so one override covers a
 *   dataset whether or not it carries series.
 * @property selectActionLabel Names the per-bar action in the actions menu. Grouped
 *   bars share an [BarEntry.xLabel], so the default adds the series to keep the
 *   labels distinct.
 */
@Stable
data class A11yConfig(
    val chartDescriptionBuilder: (BarDataSet) -> String = { "Bar Chart representing ${it.contentDescription}" },
    val selectedStateDescription: (BarEntry?) -> String = { entry ->
        entry?.let {
            val series = it.spokenSeriesLabel
            if (series == null) {
                "Currently selected: ${it.xLabel}, ${it.y.toInt()}."
            } else {
                "Currently selected: ${it.xLabel}, $series, ${it.y.toInt()}."
            }
        } ?: "No bar selected."
    },
    val countDescriptionBuilder: (BarChartSummary) -> String = { summary ->
        val held = when {
            summary.series == 0 -> "${summary.bars} bars"
            summary.uniformGroupSize != null ->
                "${summary.bars} bars in ${summary.categories} groups of " +
                    "${summary.uniformGroupSize}"
            else -> "${summary.bars} bars in ${summary.categories} groups"
        }
        "$held. Use the actions menu to select one."
    },
    val selectActionLabel: (BarEntry) -> String = { entry ->
        val series = entry.spokenSeriesLabel
        if (series == null) "Select ${entry.xLabel}" else "Select ${entry.xLabel}, $series"
    }
)
