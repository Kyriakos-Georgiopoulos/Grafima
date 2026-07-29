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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import io.grafima.charts.toRadians
import kotlin.math.cos
import kotlin.math.sin

// DrawScope extensions, not composables or lambdas: static calls with no
// per-frame allocation.

/**
 * Keeps total sweep at 360° once [PieChartStyle.minSliceAngle] has inflated the
 * smallest slices. Separate from drawing because the sum must be complete
 * before any arc is drawn; accumulates rather than building a list.
 */
internal fun pieSweepNormalizer(
    entries: List<PieEntry>,
    animationEngine: PieChartAnimationEngine,
    totalValue: Float,
    minSliceAngle: Float
): Float {
    if (minSliceAngle <= 0f) return 1f
    var rawSweepSum = 0f
    entries.forEach { entry ->
        val animatedValue = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
        var sweep = (animatedValue / totalValue) * 360f
        if (sweep > 0f) sweep = sweep.coerceAtLeast(minSliceAngle)
        rawSweepSum += sweep
    }
    return if (rawSweepSum > 0f) 360f / rawSweepSum else 1f
}

/** Each slice scales and fades independently, so one can pop without the rest. */
internal fun DrawScope.drawPieSlices(
    dataSet: PieDataSet,
    style: PieChartStyle,
    animationEngine: PieChartAnimationEngine,
    totalValue: Float,
    normalizer: Float,
    cx: Float,
    cy: Float,
    canvasRadius: Float,
    drawRadius: Float,
    strokeWidth: Float,
    directionMultiplier: Float,
    renderEntries: List<PieEntry>
) {
    val entries = renderEntries
    var drawnStartAngle = style.startAngle

    entries.forEach { entry ->
        val animatedValue = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
        val sweepAngle = computeNormalizedSweep(
            animatedValue = animatedValue,
            totalValue = totalValue,
            minSliceAngle = style.minSliceAngle,
            normalizer = normalizer
        )

        if (sweepAngle > 0f) {
            val scale = animationEngine.scaleAnimatables[entry.id]?.value ?: 1f
            val alpha = animationEngine.alphaAnimatables[entry.id]?.value ?: 1f
            val scaledDrawRadius = drawRadius * scale

            // Spacing is skipped when a slice is too thin to survive it.
            val spacing = if (entries.size > 1 && sweepAngle > style.sliceSpacingAngle) {
                style.sliceSpacingAngle
            } else 0f

            drawArc(
                brush = resolveBrush(
                    sliceBrush = entry.brush ?: dataSet.defaultBrush,
                    cx = cx,
                    cy = cy,
                    radius = canvasRadius
                ),
                startAngle = drawnStartAngle,
                sweepAngle = (sweepAngle - spacing).coerceAtLeast(0f) * directionMultiplier,
                useCenter = false,
                topLeft = Offset(x = cx - scaledDrawRadius, y = cy - scaledDrawRadius),
                size = Size(width = scaledDrawRadius * 2, height = scaledDrawRadius * 2),
                style = Stroke(width = strokeWidth * scale),
                alpha = alpha
            )
        }

        drawnStartAngle += sweepAngle * directionMultiplier
    }
}

/**
 * Anchored to the slice centroid. The angle is recomputed from raw values
 * rather than the normalized sweeps used for drawing, so the tooltip tracks
 * the slice's true position.
 */
internal fun DrawScope.drawPieSelection(
    entry: PieEntry,
    entries: List<PieEntry>,
    style: PieChartStyle,
    animationEngine: PieChartAnimationEngine,
    selectionRenderer: PieChartSelectionRenderer,
    textMeasurer: TextMeasurer,
    selectionCache: MutableMap<String, TextLayoutResult>,
    layoutDirection: LayoutDirection,
    totalValue: Float,
    cx: Float,
    cy: Float,
    canvasRadius: Float,
    strokeWidth: Float,
    directionMultiplier: Float
) {
    val animatedValue = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
    if (animatedValue <= 0f) return

    var targetStartAngle = style.startAngle
    for (e in entries) {
        if (e.id == entry.id) break
        val value = animationEngine.valueAnimatables[e.id]?.value ?: 0f
        targetStartAngle += (value / totalValue) * 360f * directionMultiplier
    }

    val sweepAngle = (animatedValue / totalValue) * 360f * directionMultiplier
    val midAngle = targetStartAngle + (sweepAngle / 2f)
    val midAngleRad = toRadians(midAngle.toDouble())
    val centroidRadius = canvasRadius - (strokeWidth / 2f)

    with(selectionRenderer) {
        drawSelection(
            entry = entry,
            pieCenter = Offset(x = cx, y = cy),
            pieRadius = canvasRadius,
            sliceCentroid = Offset(
                x = cx + (centroidRadius * cos(midAngleRad)).toFloat(),
                y = cy + (centroidRadius * sin(midAngleRad)).toFloat()
            ),
            midAngleDegrees = midAngle,
            textMeasurer = textMeasurer,
            tooltipCache = selectionCache,
            layoutDirection = layoutDirection
        )
    }
}
