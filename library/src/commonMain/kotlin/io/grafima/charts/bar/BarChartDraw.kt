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

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import io.grafima.charts.DashStroke

/** Horizontal grid lines with y-axis labels, plus the baseline. */
internal fun DrawScope.drawVerticalGrid(
    axisConfig: AxisConfig,
    gridDash: DashStroke,
    yAxisTextLayouts: Map<Int, TextLayoutResult>,
    yAxisWidthPx: Float,
    topSpacePx: Float,
    bottomSpacePx: Float,
    chartHeight: Float,
    isRtl: Boolean
) {
    if (axisConfig.showYAxis || axisConfig.showGridLines) {
        for (i in 0..axisConfig.yAxisSteps) {
            val yRatio = 1f - (i.toFloat() / axisConfig.yAxisSteps.toFloat())
            val yPos = topSpacePx + (chartHeight * yRatio)

            if (axisConfig.showGridLines) {
                drawLine(
                    color = axisConfig.axisColor,
                    start = Offset(x = if (isRtl) 0f else yAxisWidthPx, y = yPos),
                    end = Offset(
                        x = if (isRtl) size.width - yAxisWidthPx else size.width,
                        y = yPos
                    ),
                    strokeWidth = 1.dp.toPx(),
                    cap = gridDash.gridCap,
                    pathEffect = gridDash.effect
                )
            }

            if (axisConfig.showYAxis) {
                yAxisTextLayouts[i]?.let { layout ->
                    val textX = if (isRtl) {
                        size.width - yAxisWidthPx + axisConfig.yAxisLabelPadding.toPx()
                    } else {
                        yAxisWidthPx - layout.size.width - axisConfig.yAxisLabelPadding.toPx()
                    }
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x = textX, y = yPos - (layout.size.height / 2))
                    )
                }
            }
        }
    }

    drawLine(
        color = axisConfig.axisColor,
        start = Offset(
            x = if (isRtl) 0f else yAxisWidthPx,
            y = size.height - bottomSpacePx
        ),
        end = Offset(
            x = if (isRtl) size.width - yAxisWidthPx else size.width,
            y = size.height - bottomSpacePx
        ),
        strokeWidth = 2.dp.toPx()
    )
}

/** Vertical grid lines with value labels below, plus the zero line. */
internal fun DrawScope.drawHorizontalGrid(
    axisConfig: AxisConfig,
    gridDash: DashStroke,
    yAxisTextLayouts: Map<Int, TextLayoutResult>,
    chartLeft: Float,
    chartRight: Float,
    chartBottom: Float,
    chartWidth: Float,
    topPadPx: Float,
    isRtl: Boolean
) {
    if (axisConfig.showYAxis || axisConfig.showGridLines) {
        for (i in 0..axisConfig.yAxisSteps) {
            val ratio = i.toFloat() / axisConfig.yAxisSteps
            val gridX = if (isRtl) {
                chartRight - chartWidth * ratio
            } else {
                chartLeft + chartWidth * ratio
            }

            if (axisConfig.showGridLines) {
                drawLine(
                    color = axisConfig.axisColor,
                    start = Offset(x = gridX, y = topPadPx),
                    end = Offset(x = gridX, y = chartBottom),
                    strokeWidth = 1.dp.toPx(),
                    cap = gridDash.gridCap,
                    pathEffect = gridDash.effect
                )
            }

            if (axisConfig.showYAxis) {
                yAxisTextLayouts[i]?.let { layout ->
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = gridX - layout.size.width / 2,
                            y = chartBottom + 8.dp.toPx()
                        )
                    )
                }
            }
        }
    }

    val zeroX = if (isRtl) chartRight else chartLeft
    drawLine(
        color = axisConfig.axisColor,
        start = Offset(x = zeroX, y = topPadPx),
        end = Offset(x = zeroX, y = chartBottom),
        strokeWidth = 2.dp.toPx()
    )
}

internal fun DrawScope.drawVerticalBars(
    dataSet: BarDataSet,
    renderEntries: List<BarEntry>,
    style: ChartStyle,
    animationEngine: ChartAnimationEngine,
    colorStopArrays: Map<String, Array<Pair<Float, Color>>?>,
    xLabelLayouts: Map<String, TextLayoutResult>,
    valueTextCache: MutableMap<Int, TextLayoutResult>,
    textMeasurer: TextMeasurer,
    centeredValueTextStyle: TextStyle,
    barPath: Path,
    selectedEntry: BarEntry?,
    maxBarValue: Float,
    barWidth: Float,
    barSpacing: Float,
    yAxisWidthPx: Float,
    bottomSpacePx: Float,
    chartHeight: Float,
    cornerRadiusPx: Float,
    isRtl: Boolean
) {
    // Running total of the slots to the left, so a collapsing slot slides the
    // bars after it across rather than teleporting them.
    var position = 0f
    renderEntries.forEach { entry ->
        val occupancy = animationEngine.slotOccupancy(entry.id)
        val animatedValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
        val selectionAlpha =
            (animationEngine.selectionAlphaAnimatables[entry.id]?.value ?: 1f) * occupancy
        val targetHeight = (animatedValue / maxBarValue) * chartHeight

        val ltrXOffset = barSlotOffset(position, yAxisWidthPx, barWidth, barSpacing)
        position += occupancy
        val xOffset = mirrorForRtl(ltrXOffset, size.width, barWidth, isRtl)
        val yOffset = size.height - bottomSpacePx - targetHeight

        if (targetHeight > 0f) {
            val corner = CornerRadius(x = cornerRadiusPx, y = cornerRadiusPx)
            barPath.rewind()
            barPath.addRoundRect(
                RoundRect(
                    left = xOffset,
                    top = yOffset,
                    right = xOffset + barWidth,
                    bottom = yOffset + targetHeight,
                    topLeftCornerRadius = corner,
                    topRightCornerRadius = corner,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                    bottomRightCornerRadius = CornerRadius.Zero
                )
            )
            val stops = colorStopArrays[entry.id]
            val brush = if (stops != null) {
                Brush.verticalGradient(*stops, startY = yOffset, endY = yOffset + targetHeight)
            } else {
                Brush.verticalGradient(
                    colors = entry.gradientColors ?: dataSet.defaultGradientColors,
                    startY = yOffset,
                    endY = yOffset + targetHeight
                )
            }
            drawPath(path = barPath, brush = brush, alpha = selectionAlpha)
        }

        // Floating values fade in with the bar and yield to the selection tooltip.
        if (style.showFloatingValues && animatedValue > 1f && selectedEntry == null) {
            val valueInt = animatedValue.toInt()
            val valueLayout = valueTextCache.getOrPut(valueInt) {
                textMeasurer.measure(text = valueInt.toString(), style = centeredValueTextStyle)
            }
            drawText(
                textLayoutResult = valueLayout,
                topLeft = Offset(
                    x = xOffset + (barWidth - valueLayout.size.width) / 2,
                    y = yOffset - valueLayout.size.height - 6.dp.toPx()
                ),
                alpha = if (entry.y > 0f) (animatedValue / entry.y).coerceIn(0f, 1f) else 0f
            )
        }

        xLabelLayouts[entry.id]?.let { layout ->
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = xOffset + (barWidth - layout.size.width) / 2,
                    y = size.height - bottomSpacePx + 12.dp.toPx()
                ),
                alpha = selectionAlpha
            )
        }
    }
}

internal fun DrawScope.drawHorizontalBars(
    dataSet: BarDataSet,
    renderEntries: List<BarEntry>,
    style: ChartStyle,
    animationEngine: ChartAnimationEngine,
    colorStopArrays: Map<String, Array<Pair<Float, Color>>?>,
    xLabelLayouts: Map<String, TextLayoutResult>,
    valueTextCache: MutableMap<Int, TextLayoutResult>,
    textMeasurer: TextMeasurer,
    centeredValueTextStyle: TextStyle,
    barPath: Path,
    selectedEntry: BarEntry?,
    maxBarValue: Float,
    barThickness: Float,
    barGap: Float,
    chartLeft: Float,
    chartRight: Float,
    chartWidth: Float,
    topPadPx: Float,
    cornerRadiusPx: Float,
    isRtl: Boolean
) {
    var position = 0f
    renderEntries.forEach { entry ->
        val occupancy = animationEngine.slotOccupancy(entry.id)
        val animatedValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
        val selectionAlpha =
            (animationEngine.selectionAlphaAnimatables[entry.id]?.value ?: 1f) * occupancy
        val barLen = (animatedValue / maxBarValue) * chartWidth

        val yOff = barSlotOffset(position, topPadPx, barThickness, barGap)
        position += occupancy
        val xOff = if (isRtl) chartRight - barLen else chartLeft

        if (barLen > 0f) {
            // Only the growing end of the bar is rounded, so it stays flush
            // with the zero line — which side that is flips in RTL.
            val corner = CornerRadius(x = cornerRadiusPx, y = cornerRadiusPx)
            barPath.rewind()
            barPath.addRoundRect(
                RoundRect(
                    left = xOff,
                    top = yOff,
                    right = xOff + barLen,
                    bottom = yOff + barThickness,
                    topLeftCornerRadius = if (isRtl) corner else CornerRadius.Zero,
                    topRightCornerRadius = if (isRtl) CornerRadius.Zero else corner,
                    bottomLeftCornerRadius = if (isRtl) corner else CornerRadius.Zero,
                    bottomRightCornerRadius = if (isRtl) CornerRadius.Zero else corner
                )
            )
            val stops = colorStopArrays[entry.id]
            val brushStart = if (isRtl) xOff + barLen else xOff
            val brushEnd = if (isRtl) xOff else xOff + barLen
            val brush = if (stops != null) {
                Brush.horizontalGradient(*stops, startX = brushStart, endX = brushEnd)
            } else {
                Brush.horizontalGradient(
                    colors = entry.gradientColors ?: dataSet.defaultGradientColors,
                    startX = brushStart,
                    endX = brushEnd
                )
            }
            drawPath(path = barPath, brush = brush, alpha = selectionAlpha)
        }

        xLabelLayouts[entry.id]?.let { layout ->
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = if (isRtl) {
                        chartRight + 8.dp.toPx()
                    } else {
                        chartLeft - layout.size.width - 8.dp.toPx()
                    },
                    y = yOff + (barThickness - layout.size.height) / 2
                ),
                alpha = selectionAlpha
            )
        }

        if (style.showFloatingValues && animatedValue > 1f && selectedEntry == null) {
            val valueInt = animatedValue.toInt()
            val valueLayout = valueTextCache.getOrPut(valueInt) {
                textMeasurer.measure(text = valueInt.toString(), style = centeredValueTextStyle)
            }
            drawText(
                textLayoutResult = valueLayout,
                topLeft = Offset(
                    x = if (isRtl) {
                        xOff - valueLayout.size.width - 6.dp.toPx()
                    } else {
                        xOff + barLen + 6.dp.toPx()
                    },
                    y = yOff + (barThickness - valueLayout.size.height) / 2
                ),
                alpha = if (entry.y > 0f) (animatedValue / entry.y).coerceIn(0f, 1f) else 0f
            )
        }
    }
}

/**
 * Anchored to the bar's target geometry rather than its animated one, and held
 * back until the bar is nearly grown so the tooltip doesn't race ahead of it.
 */
internal fun DrawScope.drawBarSelection(
    entry: BarEntry,
    orientation: BarOrientation,
    selectionRenderer: BarChartSelectionRenderer,
    animationEngine: ChartAnimationEngine,
    entryIndexMap: Map<String, Int>,
    textMeasurer: TextMeasurer,
    selectionCache: MutableMap<Int, TextLayoutResult>,
    maxBarValue: Float,
    barExtent: Float,
    barGap: Float,
    chartExtent: Float,
    leadingInset: Float,
    crossAxisOffset: Float,
    chartRight: Float,
    isRtl: Boolean
) {
    val animatedValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
    if (animatedValue <= entry.y * 0.9f) return
    val index = entryIndexMap[entry.id] ?: return

    val targetLength = (entry.y / maxBarValue) * chartExtent
    val slot = barSlotOffset(index, leadingInset, barExtent, barGap)

    val topLeft: Offset
    val barSize: Size
    if (orientation == BarOrientation.Horizontal) {
        topLeft = Offset(
            x = if (isRtl) chartRight - targetLength else crossAxisOffset,
            y = slot
        )
        barSize = Size(width = targetLength, height = barExtent)
    } else {
        topLeft = Offset(
            x = mirrorForRtl(slot, size.width, barExtent, isRtl),
            y = crossAxisOffset - targetLength
        )
        barSize = Size(width = barExtent, height = targetLength)
    }

    with(selectionRenderer) {
        drawSelection(
            entry = entry,
            barTopLeft = topLeft,
            barSize = barSize,
            orientation = orientation,
            textMeasurer = textMeasurer,
            tooltipCache = selectionCache
        )
    }
}
