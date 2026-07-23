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

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.math.min

/** Pre-computed trig values for all tick mark positions. */
@Stable
internal class TickTrigData(
    val majorCos: FloatArray,
    val majorSin: FloatArray,
    val minorCos: FloatArray,
    val minorSin: FloatArray
)

/** Resolves the gauge outer radius in pixels. */
internal fun resolveGaugeRadius(
    style: GaugeChartStyle,
    canvasWidth: Float,
    canvasHeight: Float,
    tickLabelSpace: Float,
    density: androidx.compose.ui.unit.Density
): Float {
    return if (style.outerRadius != Dp.Unspecified) {
        with(density) { style.outerRadius.toPx() }
    } else {
        val available = (min(canvasWidth, canvasHeight) / 2f) - tickLabelSpace
        available * style.fillFraction.coerceIn(0.1f, 1f)
    }
}

/** Maps a value to a needle angle in degrees, accounting for RTL. */
internal fun valueToAngle(
    value: Float,
    minValue: Float,
    maxValue: Float,
    startAngle: Float,
    sweepAngle: Float,
    isRtl: Boolean
): Float {
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val directed = if (isRtl) 1f - fraction else fraction
    return startAngle + sweepAngle * directed
}

/**
 * Computes [Brush.sweepGradient] color stops that map [colors] precisely to
 * the angular range [startAngle]..[startAngle + sweepAngle]. Handles wrap-around
 * past 360 correctly. Returns null if fewer than 2 colors.
 */
internal fun computeArcGradientStops(
    colors: List<Color>,
    startAngle: Float,
    sweepAngle: Float
): Array<Pair<Float, Color>>? {
    if (colors.size < 2) return null
    val startFrac = ((startAngle % 360f + 360f) % 360f) / 360f
    val sweepFrac = sweepAngle / 360f
    return colors.mapIndexed { i, color ->
        val t = i.toFloat() / (colors.size - 1)
        var pos = startFrac + t * sweepFrac
        if (pos > 1f) pos -= 1f
        pos to color
    }.sortedBy { it.first }.toTypedArray()
}
