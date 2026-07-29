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
 * @property id Unique identifier — drives animation continuity across data updates.
 * @property xLabel Text shown on the X axis below the bar.
 * @property y The bar's numeric value. Must be positive.
 * @property gradientColors Vertical gradient colors. Falls back to [BarDataSet.defaultGradientColors] when null.
 * @property colorStops Explicit gradient [ColorStop]s. Takes priority over [gradientColors].
 */
@Immutable
data class BarEntry(
    val id: String,
    val xLabel: String,
    val y: Float,
    val gradientColors: List<Color>? = null,
    val colorStops: List<ColorStop>? = null
)

enum class BarOrientation { Vertical, Horizontal }

/**
 * Groups bar entries with shared defaults.
 *
 * @property entries The bars to display, in order.
 * @property defaultGradientColors Gradient applied to bars that don't specify their own.
 * @property contentDescription Accessibility label describing the chart's purpose.
 */
@Immutable
data class BarDataSet(
    val entries: List<BarEntry>,
    val defaultGradientColors: List<Color> = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
    val contentDescription: String = "Bar Chart"
)

/**
 * Visual styling for bars and labels.
 *
 * @property barCornerRadius Rounding applied to the top corners of each bar.
 * @property barSpacingFactor Fraction of chart width used for inter-bar spacing (0f..0.9f).
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
    val showFloatingValues: Boolean = true
)

/**
 * Configuration for the Y axis and grid lines.
 *
 * @property showYAxis Whether to draw Y-axis value labels.
 * @property showGridLines Whether to draw horizontal grid lines.
 * @property yAxisSteps Number of evenly-spaced grid lines and Y-axis labels.
 * @property dashEffect [PathEffect] applied to grid lines. Pass null for solid lines.
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
    val dashEffect: PathEffect? = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
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
 * Accessibility configuration with builders for TalkBack descriptions.
 */
@Stable
data class A11yConfig(
    val chartDescriptionBuilder: (BarDataSet) -> String = { "Bar Chart representing ${it.contentDescription}" },
    val barDescriptionBuilder: (BarEntry) -> String = { "${it.xLabel} value is ${it.y.toInt()}" },
    val selectedStateDescription: (BarEntry?) -> String = { entry ->
        entry?.let { "Currently selected: ${it.xLabel}, ${it.y.toInt()}." } ?: "No bar selected."
    },
    val barCountDescriptionBuilder: (Int) -> String = { count ->
        "$count bars. Use the actions menu to select one."
    }
)
