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

/**
 * Defines how a pie slice is painted. Each variant maps to a different [Brush] type
 * at draw time. When no [SliceBrush] is set on a [PieEntry], the [PieDataSet.defaultBrush]
 * is used as fallback.
 *
 * ```
 * // Solid fill
 * PieEntry("a", "Sales", 300f, brush = SliceBrush.Solid(Color.Red))
 *
 * // Diagonal linear gradient
 * PieEntry("b", "Costs", 200f, brush = SliceBrush.Linear(
 *     colors = listOf(Color.Cyan, Color.Blue),
 *     angleDegrees = 135f
 * ))
 *
 * // Or use the extension shorthand
 * PieEntry("c", "Tax", 100f, brush = Color.Green.toSliceBrush())
 * ```
 */
sealed interface SliceBrush {

    /** Flat single-color fill. */
    @Immutable
    data class Solid(val color: Color) : SliceBrush

    /**
     * Linear gradient across the slice.
     * [angleDegrees] controls the gradient axis: 0 = left-to-right,
     * 90 = top-to-bottom, 45 = diagonal. Defaults to 45.
     */
    @Immutable
    data class Linear(
        val colors: List<Color>,
        val angleDegrees: Float = 45f
    ) : SliceBrush

    /**
     * Radial gradient expanding outward from the pie center.
     * Works well for donut charts where the gradient follows the ring.
     */
    @Immutable
    data class Radial(val colors: List<Color>) : SliceBrush

    /**
     * Sweep (conic) gradient rotating around the pie center.
     * Creates a color wheel effect when applied to all slices.
     */
    @Immutable
    data class Sweep(val colors: List<Color>) : SliceBrush
}

/** Shorthand to wrap a [Color] into a [SliceBrush.Solid]. */
fun Color.toSliceBrush(): SliceBrush = SliceBrush.Solid(this)

/**
 * A single slice in the pie chart.
 *
 * @param id Stable identifier used to track this entry across data updates and drive
 *   animations. Must be unique within the dataset. Changing the id is treated as a
 *   removal + insertion, not a value morph.
 * @param label Human-readable name shown in tooltips and accessibility descriptions.
 * @param value The raw numeric value. Does not need to sum to 100, the chart normalizes
 *   internally. Must be > 0; zero or negative values are effectively invisible.
 * @param brush Fill style for this slice. When null, [PieDataSet.defaultBrush] is used.
 */
@Immutable
data class PieEntry(
    val id: String,
    val label: String,
    val value: Float,
    val brush: SliceBrush? = null
)

/**
 * Holds the full dataset and chart-level defaults.
 *
 * @param entries The slices to render. Order determines drawing order (first entry starts
 *   at [PieChartStyle.startAngle]).
 * @param defaultBrush Fallback brush for entries that don't specify their own.
 * @param contentDescription Root accessibility label for the chart.
 */
@Immutable
data class PieDataSet(
    val entries: List<PieEntry>,
    val defaultBrush: SliceBrush = SliceBrush.Linear(
        colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
    ),
    val contentDescription: String = "Pie Chart"
)

/**
 * Visual tuning for the chart geometry.
 *
 * The chart size is resolved in this order:
 * 1. If [outerRadius] is specified (not [Dp.Unspecified]), it is used as the exact outer radius.
 * 2. Otherwise, the chart auto-fits to the Canvas using [fillFraction] as a 0..1 ratio
 *    of the available space.
 *
 * @param startAngle Angle in degrees where the first slice begins. -90 = 12 o'clock.
 * @param donutRatio Inner hole size as a fraction of the outer radius. 0 = solid pie,
 *   0.5 = half-width ring. Clamped to 0..0.99 internally.
 * @param sliceSpacingAngle Gap between slices in degrees. Ignored for single-entry datasets.
 * @param unselectedAlpha Alpha applied to non-selected slices when a selection is active.
 * @param selectedScale Scale factor applied to the selected slice. 1.05 = 5% larger.
 * @param outerRadius Explicit outer radius in Dp. When [Dp.Unspecified], the chart
 *   auto-sizes using [fillFraction] instead.
 * @param fillFraction 0..1 ratio of the smaller Canvas dimension used as the diameter.
 *   Only applies when [outerRadius] is [Dp.Unspecified]. Default 0.60.
 * @param minSliceAngle Minimum sweep angle in degrees. Slices smaller than this are
 *   bumped up so they remain visible and tappable. 0 disables the floor.
 */
@Immutable
data class PieChartStyle(
    val startAngle: Float = -90f,
    val donutRatio: Float = 0.45f,
    val sliceSpacingAngle: Float = 2f,
    val unselectedAlpha: Float = 0.3f,
    val selectedScale: Float = 1.05f,
    val outerRadius: Dp = Dp.Unspecified,
    val fillFraction: Float = 0.60f,
    val minSliceAngle: Float = 0f
)

/**
 * Timing configuration for all chart animations.
 *
 * @param initialEntrySpec Drives the first appearance of each slice (value animates from 0).
 * @param morphSpec Drives value changes on existing slices (e.g. data update).
 * @param selectionSpec Drives scale and alpha changes on tap.
 * @param staggerDelayMs Delay between successive slice entry animations.
 * @param startDelayMs Initial delay before the first slice begins animating.
 */
@Immutable
data class PieAnimationConfig(
    val initialEntrySpec: AnimationSpec<Float> = tween(
        durationMillis = 1000,
        easing = FastOutSlowInEasing
    ),
    val morphSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    ),
    val selectionSpec: AnimationSpec<Float> = tween(durationMillis = 200, easing = LinearEasing),
    val staggerDelayMs: Long = 120L,
    val startDelayMs: Long = 100L
)

/**
 * Accessibility text builders. Override individual lambdas to customize screen reader output
 * without replacing the entire a11y strategy.
 */
@Stable
data class PieA11yConfig(
    val chartDescriptionBuilder: (PieDataSet) -> String = { ds ->
        "Pie Chart representing ${ds.contentDescription}"
    },
    val sliceDescriptionBuilder: (PieEntry, Float) -> String = { entry, percentage ->
        "${entry.label}, ${entry.value.toInt()}, ${percentage.toInt()} percent of total."
    },
    val sliceCountDescriptionBuilder: (Int) -> String = { count ->
        "$count slices. Use the actions menu to select one."
    },
    /**
     * Announced on its own when the selection changes, so it carries the value and
     * the share as well as the label — the share is passed in because a single
     * entry cannot know the total.
     *
     * The unselected state names the way in: custom actions are announced only as
     * "actions available", which does not tell a listener that they are how you
     * pick a slice.
     */
    val selectedStateDescription: (PieEntry?, Float) -> String = { entry, percentage ->
        entry?.let {
            "Currently selected: ${it.label}. Value ${it.value.toInt()}, " +
                "${percentage.toInt()} percent of total."
        } ?: "No slice selected. Use the actions menu to choose a slice."
    }
)
