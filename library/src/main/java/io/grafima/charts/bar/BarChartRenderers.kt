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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Draws the selection indicator when a bar is tapped.
 *
 * Implement this to create custom selection visuals (tooltips, highlights, overlays).
 * See [TooltipSelectionRenderer] for the default tooltip implementation.
 */
@Stable
fun interface BarChartSelectionRenderer {
    fun DrawScope.drawSelection(
        entry: BarEntry,
        barTopLeft: Offset,
        barSize: Size,
        orientation: BarOrientation,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<Int, TextLayoutResult>
    )
}

/**
 * Default selection renderer — a rounded tooltip showing the bar's value above it.
 */
@Stable
class TooltipSelectionRenderer(
    val backgroundColor: Color = Color(0xFF111827),
    val textStyle: TextStyle = TextStyle(
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    ),
    val cornerRadius: Dp = 8.dp,
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 6.dp,
    val bottomMargin: Dp = 8.dp
) : BarChartSelectionRenderer {
    override fun DrawScope.drawSelection(
        entry: BarEntry,
        barTopLeft: Offset,
        barSize: Size,
        orientation: BarOrientation,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<Int, TextLayoutResult>
    ) {
        val tooltipValueInt = entry.y.toInt()
        val tooltipLayout = tooltipCache.getOrPut(tooltipValueInt) {
            textMeasurer.measure(
                text = tooltipValueInt.toString(),
                style = textStyle
            )
        }
        val hPaddingPx = horizontalPadding.toPx()
        val vPaddingPx = verticalPadding.toPx()
        val tooltipWidth = tooltipLayout.size.width + hPaddingPx * 2
        val tooltipHeight = tooltipLayout.size.height + vPaddingPx * 2

        val tooltipLeft: Float
        val tooltipTop: Float

        when (orientation) {
            BarOrientation.Vertical -> {
                tooltipLeft = (barTopLeft.x + (barSize.width / 2) - (tooltipWidth / 2))
                    .coerceIn(0f, size.width - tooltipWidth)
                tooltipTop = barTopLeft.y - tooltipHeight - bottomMargin.toPx()
            }

            BarOrientation.Horizontal -> {
                tooltipLeft = (barTopLeft.x + barSize.width + bottomMargin.toPx())
                    .coerceAtMost(size.width - tooltipWidth)
                tooltipTop = (barTopLeft.y + (barSize.height - tooltipHeight) / 2)
                    .coerceIn(0f, size.height - tooltipHeight)
            }
        }

        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(x = tooltipLeft, y = tooltipTop),
            size = Size(width = tooltipWidth, height = tooltipHeight),
            cornerRadius = CornerRadius(x = cornerRadius.toPx(), y = cornerRadius.toPx())
        )
        drawText(
            textLayoutResult = tooltipLayout,
            topLeft = Offset(
                x = tooltipLeft + (tooltipWidth - tooltipLayout.size.width) / 2,
                y = tooltipTop + (tooltipHeight - tooltipLayout.size.height) / 2
            )
        )
    }
}
