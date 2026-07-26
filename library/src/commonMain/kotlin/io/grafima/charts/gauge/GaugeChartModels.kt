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

package io.grafima.charts.gauge

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A colored segment on the gauge arc representing a value range.
 *
 * @param id Stable identifier.
 * @param label Human-readable zone name (e.g. "Safe", "Warning", "Critical").
 *   Used by the default accessibility description builder.
 * @param range The value range this zone covers. Must be within min..max.
 * @param color Solid fill color for the arc segment. Ignored when [gradientColors]
 *   has 2+ entries.
 * @param gradientColors When set with 2+ colors, renders this zone as a sweep gradient
 *   along the arc instead of a solid color. The gradient interpolates across the
 *   zone's angular range. When empty, falls back to [color].
 */
@Immutable
data class GaugeZone(
    val id: String,
    val label: String = "",
    val range: ClosedFloatingPointRange<Float>,
    val color: Color,
    val gradientColors: List<Color> = emptyList()
)

/**
 * Configuration for tick marks and their labels.
 *
 * @param majorTickCount Number of major tick intervals (not marks). 10 produces
 *   11 marks at 0%, 10%, 20%, ... 100%.
 * @param minorTicksPerMajor Number of minor ticks between each pair of major ticks.
 * @param majorTickLength Length of major tick lines, extending inward from the arc.
 * @param minorTickLength Length of minor tick lines.
 * @param majorTickWidth Stroke width for major ticks.
 * @param minorTickWidth Stroke width for minor ticks.
 * @param majorTickColor Color for major ticks.
 * @param minorTickColor Color for minor ticks.
 * @param showLabels Whether to draw value labels at each major tick.
 * @param labelColor Color for tick labels.
 * @param labelFontSize Font size for tick labels.
 * @param labelPadding Distance between the arc outer edge and label anchor.
 * @param labelFormatter Transforms a Float value into the label string.
 */
@Immutable
data class GaugeTickConfig(
    val majorTickCount: Int = 10,
    val minorTicksPerMajor: Int = 4,
    val majorTickLength: Dp = 10.dp,
    val minorTickLength: Dp = 5.dp,
    val majorTickWidth: Dp = 2.dp,
    val minorTickWidth: Dp = 1.dp,
    val majorTickColor: Color = Color(0xFF374151),
    val minorTickColor: Color = Color(0xFF9CA3AF),
    val showLabels: Boolean = true,
    val labelColor: Color = Color(0xFF6B7280),
    val labelFontSize: TextUnit = 11.sp,
    val labelPadding: Dp = 8.dp,
    val labelFormatter: (Float) -> String = { it.toInt().toString() }
)

/** Visual style for the gauge needle. */
enum class GaugeNeedleStyle {
    /** Tapered triangle: wide at the center hub, sharp at the tip. */
    Tapered,

    /** Thin rounded line from center hub to tip. */
    Line
}

/**
 * Configuration for the gauge needle.
 *
 * @param style Visual shape of the needle.
 * @param color Fill (or stroke) color for the needle body.
 * @param lengthFraction Needle length as a fraction of the gauge radius. 0.85 = 85%.
 * @param tailFraction Small counter-weight tail behind center, as a fraction of radius.
 * @param baseRadius Radius of the center hub circle that covers the needle base.
 * @param baseColor Color of the center hub circle.
 * @param width Needle width at the base (for [GaugeNeedleStyle.Tapered]) or
 *   stroke width (for [GaugeNeedleStyle.Line]).
 */
@Immutable
data class GaugeNeedleConfig(
    val style: GaugeNeedleStyle = GaugeNeedleStyle.Tapered,
    val color: Color = Color(0xFFDC2626),
    val lengthFraction: Float = 0.80f,
    val tailFraction: Float = 0.12f,
    val baseRadius: Dp = 10.dp,
    val baseColor: Color = Color(0xFF374151),
    val width: Dp = 4.dp
)

/**
 * Visual tuning for the gauge chart.
 *
 * Common arc presets:
 * - **3/4 gauge** (default): startAngle=135, sweepAngle=270 (opening at bottom)
 * - **Semi-circle**: startAngle=180, sweepAngle=180
 * - **Full circle**: startAngle=-90, sweepAngle=360
 *
 * @param startAngle Angle in degrees where the arc begins. 135 = 7:30 position.
 * @param sweepAngle Total angular sweep of the arc. 270 = three-quarter circle.
 * @param arcWidth Thickness of the gauge arc band.
 * @param trackColor Background track color (visible behind zones and value arc).
 * @param arcGradientColors When set with 2+ colors, draws a single sweep gradient
 *   across the entire arc instead of individual zones. The gradient interpolates
 *   from the first color at the arc start to the last color at the arc end.
 *   Takes priority over zones when non-empty.
 * @param showValueArc Draw a colored arc from min to the current animated value.
 * @param valueArcColor Color for the value arc.
 * @param valueArcWidth Width of the value arc. Defaults to [arcWidth].
 * @param fillFraction 0..1 ratio of available space used as the gauge radius.
 * @param outerRadius Explicit outer radius override.
 * @param minSize Minimum intrinsic chart size.
 * @param centerContentOffset Vertical offset for the center content slot.
 *   Positive values push the content downward, away from the needle hub.
 *   For a 270 sweep, values around 40-56 dp work well.
 */
@Immutable
data class GaugeChartStyle(
    val startAngle: Float = 135f,
    val sweepAngle: Float = 270f,
    val arcWidth: Dp = 20.dp,
    val trackColor: Color = Color(0xFFE5E7EB),
    val arcGradientColors: List<Color> = emptyList(),
    val showValueArc: Boolean = false,
    val valueArcColor: Color = Color(0xFF4F46E5),
    val valueArcWidth: Dp = Dp.Unspecified,
    val fillFraction: Float = 0.85f,
    val outerRadius: Dp = Dp.Unspecified,
    val minSize: Dp = 200.dp,
    val centerContentOffset: Dp = 48.dp
)

/**
 * Animation configuration for the gauge needle.
 *
 * The default uses an underdamped spring (dampingRatio < 1.0) that causes the
 * needle to overshoot its target and oscillate before settling.
 *
 * @param needleSpec Spring or tween spec driving the needle.
 * @param initialDelayMs Delay before the needle begins its first animation.
 */
@Immutable
data class GaugeAnimationConfig(
    val needleSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.45f,
        stiffness = Spring.StiffnessLow
    ),
    val initialDelayMs: Long = 200L
)

/**
 * Accessibility text builders for the gauge.
 */
@Stable
data class GaugeA11yConfig(
    val descriptionBuilder: (
        value: Float, minValue: Float, maxValue: Float, zones: List<GaugeZone>
    ) -> String = { value, minValue, maxValue, zones ->
        val pct = (((value - minValue) / (maxValue - minValue)) * 100).toInt()
        val activeZone = zones.find { value in it.range }
        buildString {
            append("Gauge at $pct percent. Value: ${value.toInt()} of ${maxValue.toInt()}.")
            activeZone?.let { append(" Zone: ${it.label}.") }
        }
    }
)
