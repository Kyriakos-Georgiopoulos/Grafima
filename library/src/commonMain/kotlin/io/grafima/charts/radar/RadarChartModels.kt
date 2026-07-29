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

package io.grafima.charts.radar

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
 * Defines a single axis (spoke) on the radar chart.
 *
 * @param id Stable identifier used internally for mapping series values and
 *   driving animations. Must be unique within the dataset.
 * @param label Human-readable name rendered at the axis tip.
 * @param maxValue Upper bound for this axis. Series values are normalized
 *   against this (value / maxValue). Defaults to 100.
 */
@Immutable
data class RadarAxis(
    val id: String,
    val label: String,
    val maxValue: Float = 100f
)

/**
 * A single data series rendered as a filled polygon on the radar chart.
 *
 * @param id Stable identifier for animation tracking. Must be unique.
 * @param label Human-readable name shown in tooltips and accessibility.
 * @param values Map of axisId to raw value. Missing axes default to 0.
 * @param color Primary color for fill, stroke, and data point dots.
 * @param fillAlpha Opacity applied to the polygon fill. 0 = no fill, 1 = opaque.
 * @param strokeWidth Width of the polygon outline.
 */
@Immutable
data class RadarSeries(
    val id: String,
    val label: String,
    val values: Map<String, Float>,
    val color: Color = Color(0xFF6366F1),
    val fillAlpha: Float = 0.2f,
    val strokeWidth: Dp = 2.dp
)

/**
 * Holds the full dataset: axes and one or more series.
 *
 * @param axes The spokes of the radar. Order determines drawing order clockwise
 *   from [RadarChartStyle.startAngle]. Minimum 3.
 * @param series The data polygons. Later entries draw on top of earlier ones.
 * @param contentDescription Root accessibility label for the chart.
 */
@Immutable
data class RadarDataSet(
    val axes: List<RadarAxis>,
    val series: List<RadarSeries>,
    val contentDescription: String = "Radar Chart"
)

/** Grid ring shape. */
enum class RadarGridStyle {
    /** Concentric polygons matching the number of axes. */
    Polygon,

    /** Concentric circles. */
    Circular
}

/**
 * Visual tuning for the radar chart.
 *
 * The chart size is resolved in this order:
 * 1. If [outerRadius] is specified (not [Dp.Unspecified]), it is used as the exact outer radius.
 * 2. Otherwise, the chart auto-fits to the Canvas using [fillFraction] as a 0..1 ratio
 *    of available space minus label protrusion.
 *
 * @param startAngle Angle in degrees where the first axis points. -90 = 12 o'clock.
 * @param gridLevels Number of concentric grid rings. 0 disables the grid.
 * @param gridStyle Shape of the grid rings.
 * @param gridColor Color for grid rings.
 * @param gridStrokeWidth Width of grid ring lines.
 * @param axisColor Color for axis spoke lines.
 * @param axisStrokeWidth Width of axis spoke lines.
 * @param labelColor Color for axis label text.
 * @param labelFontSize Font size for axis labels.
 * @param labelPadding Distance from axis tip to label anchor.
 * @param fillFraction 0..1 ratio of available space used as the chart radius.
 *   Only applies when [outerRadius] is [Dp.Unspecified].
 * @param outerRadius Explicit outer radius. When [Dp.Unspecified], the chart
 *   auto-sizes using [fillFraction].
 * @param minSize Minimum intrinsic size for the chart. Applied via
 *   [Modifier.defaultMinSize] so the chart renders even if the caller provides
 *   no explicit sizing.
 * @param dotRadius Size of data point dots on each vertex.
 * @param showDots Whether to render data point dots at each vertex.
 * @param showLabels Whether to render axis labels at the spoke tips.
 * @param unselectedAlpha Alpha applied to non-selected series when a selection is active.
 */
@Immutable
data class RadarChartStyle(
    val startAngle: Float = -90f,
    val gridLevels: Int = 5,
    val gridStyle: RadarGridStyle = RadarGridStyle.Polygon,
    val gridColor: Color = Color(0xFFE5E7EB),
    val gridStrokeWidth: Dp = 1.dp,
    val axisColor: Color = Color(0xFFD1D5DB),
    val axisStrokeWidth: Dp = 1.dp,
    val labelColor: Color = Color(0xFF6B7280),
    val labelFontSize: TextUnit = 12.sp,
    val labelPadding: Dp = 16.dp,
    val fillFraction: Float = 0.75f,
    val outerRadius: Dp = Dp.Unspecified,
    val minSize: Dp = 200.dp,
    val dotRadius: Dp = 4.dp,
    val showDots: Boolean = true,
    val showLabels: Boolean = true,
    val unselectedAlpha: Float = 0.3f
)

/**
 * Timing configuration for all radar chart animations.
 *
 * @param initialEntrySpec Drives the first appearance of each vertex (value animates from 0).
 * @param morphSpec Drives value changes on existing vertices.
 * @param selectionSpec Drives alpha changes on series selection/deselection.
 * @param startDelayMs Initial delay before the first vertex begins animating.
 * @param seriesStaggerMs Delay between successive series entry animations.
 * @param vertexStaggerMs Delay between successive vertex animations within a series.
 */
@Immutable
data class RadarAnimationConfig(
    val initialEntrySpec: AnimationSpec<Float> = tween(
        durationMillis = 800,
        easing = FastOutSlowInEasing
    ),
    val morphSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessLow
    ),
    val selectionSpec: AnimationSpec<Float> = tween(durationMillis = 200, easing = LinearEasing),
    val startDelayMs: Long = 80L,
    val seriesStaggerMs: Long = 150L,
    val vertexStaggerMs: Long = 40L
)

/**
 * Accessibility text builders for the radar chart.
 */
@Stable
data class RadarA11yConfig(
    val chartDescriptionBuilder: (RadarDataSet) -> String = { ds ->
        "Radar Chart representing ${ds.contentDescription}"
    },
    val seriesDescriptionBuilder: (RadarSeries, List<RadarAxis>) -> String = { series, axes ->
        val valueText = axes.joinToString(", ") { axis ->
            val v = series.values[axis.id] ?: 0f
            val pct = ((v / axis.maxValue) * 100).toInt()
            "${axis.label}: $pct%"
        }
        "${series.label} ($valueText)"
    },
    /**
     * Announced on its own when the selection changes, so it has to carry the
     * whole story — the axes are passed in because a series' values are keyed by
     * axis id and mean nothing without the labels.
     */
    val selectedStateDescription: (RadarSeries?, List<RadarAxis>) -> String = { series, axes ->
        series?.let { s ->
            val valueText = axes.joinToString(", ") { axis ->
                "${axis.label} ${(s.values[axis.id] ?: 0f).toInt()}"
            }
            "Currently selected: ${s.label}. $valueText."
        } ?: "No series selected. Use the actions menu to choose a series."
    }
)
