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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText

// DrawScope extensions, not composables or lambdas: static calls with no
// per-frame allocation. Called in paint order from RadarChart's Canvas.

internal fun DrawScope.drawRadarGrid(
    style: RadarChartStyle,
    gridPath: Path,
    cosA: FloatArray,
    sinA: FloatArray,
    axisCount: Int,
    center: Offset,
    chartRadius: Float
) {
    if (style.gridLevels <= 0) return
    val gridStrokePx = style.gridStrokeWidth.toPx()

    for (level in 1..style.gridLevels) {
        val levelRadius = chartRadius * (level.toFloat() / style.gridLevels)
        when (style.gridStyle) {
            RadarGridStyle.Polygon -> {
                gridPath.reset()
                for (i in 0 until axisCount) {
                    val x = center.x + levelRadius * cosA[i]
                    val y = center.y + levelRadius * sinA[i]
                    if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
                }
                gridPath.close()
                drawPath(path = gridPath, color = style.gridColor, style = Stroke(gridStrokePx))
            }

            RadarGridStyle.Circular -> {
                drawCircle(
                    color = style.gridColor,
                    radius = levelRadius,
                    center = center,
                    style = Stroke(gridStrokePx)
                )
            }
        }
    }
}

internal fun DrawScope.drawRadarAxes(
    style: RadarChartStyle,
    cosA: FloatArray,
    sinA: FloatArray,
    axisCount: Int,
    center: Offset,
    chartRadius: Float
) {
    val axisStrokePx = style.axisStrokeWidth.toPx()
    for (i in 0 until axisCount) {
        drawLine(
            color = style.axisColor,
            start = center,
            end = Offset(
                x = center.x + chartRadius * cosA[i],
                y = center.y + chartRadius * sinA[i]
            ),
            strokeWidth = axisStrokePx
        )
    }
}

/** Vertices are recomputed for the dots rather than stored, to avoid a list. */
internal fun DrawScope.drawRadarSeries(
    axes: List<RadarAxis>,
    series: List<RadarSeries>,
    style: RadarChartStyle,
    animationEngine: RadarChartAnimationEngine,
    keyMatrix: Array<String>,
    seriesPath: Path,
    cosA: FloatArray,
    sinA: FloatArray,
    axisCount: Int,
    center: Offset,
    chartRadius: Float
) {
    val dotRadiusPx = style.dotRadius.toPx()

    series.forEachIndexed { si, s ->
        val seriesAlpha = animationEngine.alphaAnimatables[s.id]?.value ?: 1f

        seriesPath.reset()
        for (i in 0 until axisCount) {
            val norm = animationEngine.normalizedVertex(keyMatrix, si, i, axisCount, axes)
            val x = center.x + chartRadius * norm * cosA[i]
            val y = center.y + chartRadius * norm * sinA[i]
            if (i == 0) seriesPath.moveTo(x, y) else seriesPath.lineTo(x, y)
        }
        seriesPath.close()

        drawPath(path = seriesPath, color = s.color.copy(alpha = s.fillAlpha * seriesAlpha))
        drawPath(
            path = seriesPath,
            color = s.color.copy(alpha = seriesAlpha),
            style = Stroke(width = s.strokeWidth.toPx())
        )

        if (style.showDots) {
            for (i in 0 until axisCount) {
                val norm = animationEngine.normalizedVertex(keyMatrix, si, i, axisCount, axes)
                drawCircle(
                    color = s.color.copy(alpha = seriesAlpha),
                    radius = dotRadiusPx,
                    center = Offset(
                        x = center.x + chartRadius * norm * cosA[i],
                        y = center.y + chartRadius * norm * sinA[i]
                    )
                )
            }
        }
    }
}

/** Labels are nudged outward along their own angle to clear the rim. */
internal fun DrawScope.drawRadarLabels(
    axes: List<RadarAxis>,
    style: RadarChartStyle,
    axisLabelLayouts: Map<String, TextLayoutResult>,
    cosA: FloatArray,
    sinA: FloatArray,
    axisCount: Int,
    center: Offset,
    chartRadius: Float
) {
    val labelRadius = chartRadius + style.labelPadding.toPx()

    for (i in 0 until axisCount) {
        val layout = axisLabelLayouts[axes[i].id] ?: continue
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                x = center.x + labelRadius * cosA[i] -
                    layout.size.width / 2f + cosA[i] * layout.size.width / 2f,
                y = center.y + labelRadius * sinA[i] -
                    layout.size.height / 2f + sinA[i] * layout.size.height / 2f
            )
        )
    }
}

/** One vertex's animated value, normalized against its axis maximum. */
internal fun RadarChartAnimationEngine.normalizedVertex(
    keyMatrix: Array<String>,
    seriesIndex: Int,
    axisIndex: Int,
    axisCount: Int,
    axes: List<RadarAxis>
): Float {
    val key = keyMatrix[seriesIndex * axisCount + axisIndex]
    val animatedValue = valueAnimatables[key]?.value ?: 0f
    return (animatedValue / axes[axisIndex].maxValue).coerceIn(0f, 1f)
}
