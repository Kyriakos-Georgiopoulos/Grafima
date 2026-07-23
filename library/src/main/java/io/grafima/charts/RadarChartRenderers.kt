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

package io.grafima.charts

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rendering strategy for the visual indicator shown when a series is selected.
 * The chart calls [drawSelection] after all polygons are drawn.
 *
 * @see TooltipRadarSelectionRenderer for the default tooltip implementation.
 */
@Stable
fun interface RadarChartSelectionRenderer {
    /**
     * @param series The currently selected series.
     * @param axes All chart axes in order.
     * @param vertices Animated vertex positions for the selected series, in axis order.
     * @param center Center point of the chart.
     * @param chartRadius Outer radius of the chart in pixels.
     * @param textMeasurer Shared [TextMeasurer] for label layout.
     * @param tooltipCache Reusable [TextLayoutResult] cache keyed by series state.
     * @param layoutDirection Current layout direction for RTL support.
     */
    fun DrawScope.drawSelection(
        series: RadarSeries,
        axes: List<RadarAxis>,
        vertices: List<Offset>,
        center: Offset,
        chartRadius: Float,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<String, TextLayoutResult>,
        layoutDirection: LayoutDirection
    )
}

/**
 * Default selection renderer: highlights the selected series' vertices with
 * bordered dots and shows a tooltip panel listing all values.
 */
@Stable
class TooltipRadarSelectionRenderer(
    val backgroundColor: Color = Color(0xFF111827),
    val textColor: Color = Color.White,
    val titleFontSize: TextUnit = 13.sp,
    val bodyFontSize: TextUnit = 11.sp,
    val cornerRadius: Dp = 10.dp,
    val tooltipPadding: Dp = 12.dp,
    val highlightBorderWidth: Dp = 2.5.dp,
    val highlightExtraRadius: Dp = 3.dp
) : RadarChartSelectionRenderer {

    override fun DrawScope.drawSelection(
        series: RadarSeries,
        axes: List<RadarAxis>,
        vertices: List<Offset>,
        center: Offset,
        chartRadius: Float,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<String, TextLayoutResult>,
        layoutDirection: LayoutDirection
    ) {
        val dotR = 4.dp.toPx()
        val highlightR = dotR + highlightExtraRadius.toPx()
        val borderW = highlightBorderWidth.toPx()

        // Highlighted dots with white border
        vertices.forEach { pos ->
            drawCircle(color = Color.White, radius = highlightR + borderW, center = pos)
            drawCircle(color = series.color, radius = highlightR, center = pos)
        }

        // Tooltip text
        val cacheKey = "${series.id}_${series.values.hashCode()}"
        val layout = tooltipCache.getOrPut(cacheKey) {
            val text = buildString {
                append(series.label)
                axes.forEach { axis ->
                    val v = series.values[axis.id] ?: 0f
                    append("\n${axis.label}: ${v.toInt()} / ${axis.maxValue.toInt()}")
                }
            }
            textMeasurer.measure(
                text = text,
                style = TextStyle(
                    color = textColor,
                    fontSize = bodyFontSize,
                    fontWeight = FontWeight.Medium,
                    lineHeight = bodyFontSize * 1.5f
                )
            )
        }

        val padPx = tooltipPadding.toPx()
        val tooltipW = layout.size.width + padPx * 2
        val tooltipH = layout.size.height + padPx * 2
        val margin = 8.dp.toPx()

        val isLtr = layoutDirection == LayoutDirection.Ltr
        val tooltipX = if (isLtr) size.width - tooltipW - margin else margin
        val tooltipY = margin

        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(x = tooltipX, y = tooltipY),
            size = Size(width = tooltipW, height = tooltipH),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(x = tooltipX + padPx, y = tooltipY + padPx)
        )
    }
}
