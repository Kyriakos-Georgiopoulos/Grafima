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
import io.grafima.charts.drawDashableLine

/**
 * Filled in by the bar pass and read by the selection pass, so the tooltip cannot
 * disagree with where the bar was drawn. Mutated in place: the draw pass runs
 * every frame.
 */
internal class SelectedBarBounds {
    var found: Boolean = false
        private set
    var left: Float = 0f
        private set
    var top: Float = 0f
        private set
    var width: Float = 0f
        private set
    var height: Float = 0f
        private set

    fun clear() {
        found = false
    }

    fun set(left: Float, top: Float, width: Float, height: Float) {
        this.left = left
        this.top = top
        this.width = width
        this.height = height
        found = true
    }
}

/** Horizontal grid lines with y-axis labels, plus the baseline. */
internal fun DrawScope.drawVerticalGrid(
    axisConfig: AxisConfig,
    gridDash: DashStroke,
    gridPath: Path,
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
                drawDashableLine(
                    path = gridPath,
                    color = axisConfig.axisColor,
                    start = Offset(x = if (isRtl) 0f else yAxisWidthPx, y = yPos),
                    end = Offset(
                        x = if (isRtl) size.width - yAxisWidthPx else size.width,
                        y = yPos
                    ),
                    strokeWidth = 1.dp.toPx(),
                    dash = gridDash
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
    gridPath: Path,
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
                drawDashableLine(
                    path = gridPath,
                    color = axisConfig.axisColor,
                    start = Offset(x = gridX, y = topPadPx),
                    end = Offset(x = gridX, y = chartBottom),
                    strokeWidth = 1.dp.toPx(),
                    dash = gridDash
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
    slotWidth: Float,
    barSpacing: Float,
    yAxisWidthPx: Float,
    bottomSpacePx: Float,
    chartHeight: Float,
    cornerRadiusPx: Float,
    isRtl: Boolean,
    layout: BarGroupLayout,
    mode: BarGroupMode,
    selectionBounds: SelectedBarBounds
) {
    val stacked = mode == BarGroupMode.Stacked
    val baseline = size.height - bottomSpacePx
    selectionBounds.clear()

    // Running total of the slots to the left, so a collapsing slot slides the
    // bars after it across rather than teleporting them. One slot per category,
    // so a group moves as a unit.
    var position = 0f

    forEachBarCategory(renderEntries, layout, animationEngine) {
            first, end, members, categoryOccupancy, categoryAlpha ->

        val ltrSlotX = barSlotOffset(position, yAxisWidthPx, slotWidth, barSpacing)
        position += categoryOccupancy

        val sharesSlot = end - first > 1
        val barWidth =
            if (stacked) slotWidth
            else groupedBarThickness(slotWidth, members, style.groupSpacingFactor)
        val innerGap =
            if (stacked) 0f
            else groupedBarGap(slotWidth, members, style.groupSpacingFactor)

        var animatedStackBase = 0f
        // How much of the group precedes this bar, counting a departing one
        // by what it still holds, so the survivors slide as it shrinks.
        var memberPosition = 0f

        for (i in first until end) {
            val entry = renderEntries[i]
            val occupancy = animationEngine.slotOccupancy(entry.id)
            val animatedValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
            val selectionAlpha =
                (animationEngine.selectionAlphaAnimatables[entry.id]?.value ?: 1f) * occupancy
            val targetHeight = (animatedValue / maxBarValue) * chartHeight

            val ltrXOffset =
                if (stacked) ltrSlotX
                else ltrSlotX + groupedBarOffset(memberPosition, barWidth, innerGap)
            val xOffset = mirrorForRtl(ltrXOffset, size.width, barWidth, isRtl)

            val stackBase = if (stacked) animatedStackBase else 0f
            val yOffset = baseline - stackBase - targetHeight

            if (entry.id == selectedEntry?.id) {
                val fullHeight = (entry.y / maxBarValue) * chartHeight
                selectionBounds.set(
                    left = xOffset,
                    top = baseline - animatedStackBase - fullHeight,
                    width = barWidth,
                    height = fullHeight
                )
            }
            if (stacked) animatedStackBase += targetHeight
            memberPosition += occupancy

            if (targetHeight > 0f) {
                // Rounding every segment would leave gaps where they meet.
                val roundsTop = !stacked || i == end - 1
                val corner =
                    if (roundsTop) CornerRadius(x = cornerRadiusPx, y = cornerRadiusPx)
                    else CornerRadius.Zero
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

            if (style.showFloatingValues && animatedValue > 1f && selectedEntry == null) {
                val valueInt = animatedValue.toInt()
                val valueLayout = valueTextCache.getOrPut(valueInt) {
                    textMeasurer.measure(text = valueInt.toString(), style = centeredValueTextStyle)
                }
                // A bar with the slot to itself is as wide as it ever was, so it
                // keeps the label it drew before grouping existed however narrow
                // the chart gets. Only a bar sharing its slot has to fit.
                val fits = !sharesSlot ||
                    (
                        valueLayout.size.width <= barWidth &&
                            (!stacked || valueLayout.size.height <= targetHeight)
                        )
                if (fits) {
                    val valueY =
                        if (stacked) yOffset + (targetHeight - valueLayout.size.height) / 2
                        else yOffset - valueLayout.size.height - 6.dp.toPx()
                    drawText(
                        textLayoutResult = valueLayout,
                        topLeft = Offset(
                            x = xOffset + (barWidth - valueLayout.size.width) / 2,
                            y = valueY
                        ),
                        alpha = if (entry.y > 0f) (animatedValue / entry.y).coerceIn(0f, 1f) else 0f
                    )
                }
            }
        }

        val labelEntry = renderEntries[first]
        xLabelLayouts[labelEntry.id]?.let { labelLayout ->
            val ltrLabelX = ltrSlotX + (slotWidth - labelLayout.size.width) / 2
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    x = mirrorForRtl(ltrLabelX, size.width, labelLayout.size.width.toFloat(), isRtl),
                    y = baseline + 12.dp.toPx()
                ),
                // The label belongs to the category, so any selected bar keeps it lit.
                alpha = categoryAlpha * categoryOccupancy
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
    slotThickness: Float,
    barGap: Float,
    chartLeft: Float,
    chartRight: Float,
    chartWidth: Float,
    topPadPx: Float,
    cornerRadiusPx: Float,
    isRtl: Boolean,
    layout: BarGroupLayout,
    mode: BarGroupMode,
    selectionBounds: SelectedBarBounds
) {
    val stacked = mode == BarGroupMode.Stacked
    selectionBounds.clear()
    var position = 0f

    forEachBarCategory(renderEntries, layout, animationEngine) {
            first, end, members, categoryOccupancy, categoryAlpha ->

        val ltrSlotY = barSlotOffset(position, topPadPx, slotThickness, barGap)
        position += categoryOccupancy

        val sharesSlot = end - first > 1
        val barThickness =
            if (stacked) slotThickness
            else groupedBarThickness(slotThickness, members, style.groupSpacingFactor)
        val innerGap =
            if (stacked) 0f
            else groupedBarGap(slotThickness, members, style.groupSpacingFactor)

        var animatedStackBase = 0f
        // How much of the group precedes this bar, counting a departing one
        // by what it still holds, so the survivors slide as it shrinks.
        var memberPosition = 0f

        for (i in first until end) {
            val entry = renderEntries[i]
            val occupancy = animationEngine.slotOccupancy(entry.id)
            val animatedValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
            val selectionAlpha =
                (animationEngine.selectionAlphaAnimatables[entry.id]?.value ?: 1f) * occupancy
            val barLen = (animatedValue / maxBarValue) * chartWidth

            val yOff =
                if (stacked) ltrSlotY
                else ltrSlotY + groupedBarOffset(memberPosition, barThickness, innerGap)
            val base = if (stacked) animatedStackBase else 0f
            val xOff = if (isRtl) chartRight - base - barLen else chartLeft + base

            if (entry.id == selectedEntry?.id) {
                val fullLen = (entry.y / maxBarValue) * chartWidth
                selectionBounds.set(
                    left = if (isRtl) chartRight - animatedStackBase - fullLen
                    else chartLeft + animatedStackBase,
                    top = yOff,
                    width = fullLen,
                    height = barThickness
                )
            }
            if (stacked) animatedStackBase += barLen
            memberPosition += occupancy

            if (barLen > 0f) {
                // Only the growing end is rounded, and which side that is flips in RTL.
                val roundsEnd = !stacked || i == end - 1
                val corner =
                    if (roundsEnd) CornerRadius(x = cornerRadiusPx, y = cornerRadiusPx)
                    else CornerRadius.Zero
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

            if (style.showFloatingValues && animatedValue > 1f && selectedEntry == null) {
                val valueInt = animatedValue.toInt()
                val valueLayout = valueTextCache.getOrPut(valueInt) {
                    textMeasurer.measure(text = valueInt.toString(), style = centeredValueTextStyle)
                }
                val fits = !sharesSlot ||
                    (
                        valueLayout.size.height <= barThickness &&
                            (!stacked || valueLayout.size.width <= barLen)
                        )
                if (fits) {
                    val valueX = when {
                        stacked -> xOff + (barLen - valueLayout.size.width) / 2
                        isRtl -> xOff - valueLayout.size.width - 6.dp.toPx()
                        else -> xOff + barLen + 6.dp.toPx()
                    }
                    drawText(
                        textLayoutResult = valueLayout,
                        topLeft = Offset(
                            x = valueX,
                            y = yOff + (barThickness - valueLayout.size.height) / 2
                        ),
                        alpha = if (entry.y > 0f) {
                            (animatedValue / entry.y).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    )
                }
            }
        }

        val labelEntry = renderEntries[first]
        xLabelLayouts[labelEntry.id]?.let { labelLayout ->
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    x = if (isRtl) {
                        chartRight + 8.dp.toPx()
                    } else {
                        chartLeft - labelLayout.size.width - 8.dp.toPx()
                    },
                    y = ltrSlotY + (slotThickness - labelLayout.size.height) / 2
                ),
                // The label belongs to the category, so any selected bar keeps it lit.
                alpha = categoryAlpha * categoryOccupancy
            )
        }
    }
}

/** Held back until the bar is nearly grown, so the tooltip doesn't race ahead of it. */
internal fun DrawScope.drawBarSelection(
    entry: BarEntry,
    orientation: BarOrientation,
    selectionRenderer: BarChartSelectionRenderer,
    animationEngine: ChartAnimationEngine,
    textMeasurer: TextMeasurer,
    selectionCache: MutableMap<Int, TextLayoutResult>,
    bounds: SelectedBarBounds
) {
    if (!bounds.found) return
    val animatedValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
    if (animatedValue <= entry.y * 0.9f) return

    with(selectionRenderer) {
        drawSelection(
            entry = entry,
            barTopLeft = Offset(x = bounds.left, y = bounds.top),
            barSize = Size(width = bounds.width, height = bounds.height),
            orientation = orientation,
            textMeasurer = textMeasurer,
            tooltipCache = selectionCache
        )
    }
}
