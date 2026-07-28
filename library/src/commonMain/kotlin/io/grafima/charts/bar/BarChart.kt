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

import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.grafima.charts.rememberEffectiveReduceMotion

/**
 * An animated bar chart with touch selection, RTL support, and accessibility.
 *
 * Bars animate in with a stagger on first appearance and morph smoothly when values change.
 * Tap a bar to select it; tap again or tap empty space to deselect.
 *
 * Selection state is hoisted: the caller owns [selectedEntry] and receives updates via [onBarSelected].
 *
 * @param dataSet The data to display.
 * @param style Visual styling (bar shape, spacing, text styles).
 * @param axisConfig Y-axis and grid line configuration.
 * @param animationConfig Timing and easing for all animations.
 * @param a11yConfig Accessibility label builders for TalkBack.
 * @param selectionRenderer Draws the selection indicator. Defaults to [TooltipSelectionRenderer].
 * @param selectedEntry The currently selected bar, or null for no selection.
 * @param selectionHaptic Haptic effect performed when a bar becomes selected. Pass null to disable.
 * @param onBarSelected Called when the user taps a bar (entry) or deselects (null).
 */
@Composable
fun BarChart(
    dataSet: BarDataSet,
    modifier: Modifier = Modifier,
    orientation: BarOrientation = BarOrientation.Vertical,
    style: ChartStyle = ChartStyle(),
    axisConfig: AxisConfig = AxisConfig(),
    animationConfig: AnimationConfig = AnimationConfig(),
    a11yConfig: A11yConfig = A11yConfig(),
    selectionRenderer: BarChartSelectionRenderer = remember { TooltipSelectionRenderer() },
    selectedEntry: BarEntry? = null,
    selectionHaptic: HapticFeedbackType? = HapticFeedbackType.LongPress,
    onBarSelected: (BarEntry?) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val entries = dataSet.entries
    val animationEngine = remember { ChartAnimationEngine() }
    val density = LocalDensity.current

    // Create the animatables after composition but before the first draw so the Canvas
    // subscribes to them on frame one. Populating them only inside LaunchedEffect (below)
    // leaves the first draw with no subscription, so the entry animation never repaints
    // the bars — the slices/lines charts already avoid this via the same SideEffect.
    SideEffect { animationEngine.syncAnimatables(entries) }

    val reduceMotion = rememberEffectiveReduceMotion()
    val effectiveAnimationConfig = remember(animationConfig, reduceMotion) {
        if (reduceMotion) {
            animationConfig.copy(
                initialEntrySpec = snap(),
                morphSpec = snap(),
                selectionSpec = snap(),
                staggerDelayMs = 0L,
                startDelayMs = 0L
            )
        } else {
            animationConfig
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    val haptic = LocalHapticFeedback.current

    val currentSelectedEntry by rememberUpdatedState(selectedEntry)
    val currentOnBarSelected by rememberUpdatedState(onBarSelected)
    val currentSelectionHaptic by rememberUpdatedState(selectionHaptic)

    val maxBarValue = remember(entries) { computeBarAxisMax(entries) }

    val maxLabelResult = remember(maxBarValue, axisConfig.axisLabelTextStyle) {
        textMeasurer.measure(
            text = maxBarValue.toInt().toString(),
            style = axisConfig.axisLabelTextStyle
        )
    }

    val yAxisWidthPx =
        remember(axisConfig.showYAxis, maxLabelResult, axisConfig.yAxisLabelPadding, density) {
            if (axisConfig.showYAxis) maxLabelResult.size.width + with(density) { axisConfig.yAxisLabelPadding.toPx() } else 0f
        }

    val yAxisTextLayouts =
        remember(maxBarValue, axisConfig.yAxisSteps, axisConfig.axisLabelTextStyle) {
            (0..axisConfig.yAxisSteps).associateWith { i ->
                val stepValue = (maxBarValue / axisConfig.yAxisSteps) * i
                textMeasurer.measure(
                    text = stepValue.toInt().toString(),
                    style = axisConfig.axisLabelTextStyle
                )
            }
        }

    val xLabelLayouts = remember(entries, style.labelTextStyle) {
        entries.associate {
            it.id to textMeasurer.measure(
                text = it.xLabel,
                style = style.labelTextStyle
            )
        }
    }

    val valueTextCache = remember(style.valueTextStyle) { mutableMapOf<Int, TextLayoutResult>() }
    val selectionCache = remember { mutableMapOf<Int, TextLayoutResult>() }
    val barPath = remember { Path() }

    val bottomSpacePx = remember(style.bottomLabelSpace, density) {
        with(density) { style.bottomLabelSpace.toPx() }
    }
    val topSpacePx = remember(style.topValueSpace, density) {
        with(density) { style.topValueSpace.toPx() }
    }
    val cornerRadiusPx = remember(style.barCornerRadius, density) {
        with(density) { style.barCornerRadius.toPx() }
    }
    val hitSlopPx = remember(density) { with(density) { 12.dp.toPx() } }

    val centeredValueTextStyle = remember(style.valueTextStyle) {
        style.valueTextStyle.copy(textAlign = TextAlign.Center)
    }

    val colorStopArrays = remember(entries) {
        entries.associate { entry ->
            entry.id to entry.colorStops?.map { stop -> stop.position to stop.color }?.toTypedArray()
        }
    }

    val entryIndexMap = remember(entries) {
        entries.withIndex().associate { (index, entry) -> entry.id to index }
    }

    val isHorizontal = orientation == BarOrientation.Horizontal

    val horizontalCatLabelSpacePx = remember(xLabelLayouts, isHorizontal, density) {
        if (!isHorizontal) 0f
        else (xLabelLayouts.values.maxOfOrNull { it.size.width } ?: 0) +
                with(density) { 16.dp.toPx() }
    }

    val horizontalTopPadPx = remember(isHorizontal, density) {
        if (!isHorizontal) 0f else with(density) { 8.dp.toPx() }
    }

    val chartDescription = remember(dataSet, a11yConfig) {
        buildBarChartDescription(dataSet, a11yConfig)
    }
    val chartStateDescription = remember(selectedEntry, a11yConfig) {
        a11yConfig.selectedStateDescription(selectedEntry)
    }

    LaunchedEffect(entries) {
        if (currentSelectedEntry != null && entries.none { it.id == currentSelectedEntry?.id }) {
            currentOnBarSelected(null)
        }
        valueTextCache.clear()
        animationEngine.launchEntryAnimations(entries, effectiveAnimationConfig, this)
    }

    LaunchedEffect(entries, selectedEntry) {
        animationEngine.updateSelectionState(
            entries,
            selectedEntry,
            style,
            effectiveAnimationConfig,
            this
        )
    }

    Canvas(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Image
                contentDescription = chartDescription
                stateDescription = chartStateDescription
                liveRegion = LiveRegionMode.Polite
                customActions = buildList {
                    entries.forEach { entry ->
                        add(
                            CustomAccessibilityAction(label = "Select ${entry.xLabel}") {
                                onBarSelected(entry)
                                true
                            }
                        )
                    }
                    if (selectedEntry != null) {
                        add(
                            CustomAccessibilityAction(label = "Clear selection") {
                                onBarSelected(null)
                                true
                            }
                        )
                    }
                }
            }
            .pointerInput(
                entries,
                yAxisWidthPx,
                style.barSpacingFactor,
                isRtl,
                orientation,
                horizontalCatLabelSpacePx,
                hitSlopPx
            ) {
                fun processTouch(touchPos: Offset, isInitialDown: Boolean) {
                    val canvasWidth = size.width.toFloat()
                    val canvasHeight = size.height.toFloat()

                    if (isHorizontal) {
                        val hChartLeft = if (isRtl) topSpacePx else horizontalCatLabelSpacePx
                        val hChartRight =
                            if (isRtl) canvasWidth - horizontalCatLabelSpacePx else canvasWidth - topSpacePx
                        val hChartBottom = canvasHeight - bottomSpacePx
                        val hChartWidth = hChartRight - hChartLeft
                        val hChartHeight = hChartBottom - horizontalTopPadPx

                        val (barThickness, barGap) = barThicknessAndGap(
                            hChartHeight, entries.size, style.barSpacingFactor
                        )

                        var foundEntry: BarEntry? = null
                        for (i in entries.indices) {
                            val yOff = barSlotOffset(i, horizontalTopPadPx, barThickness, barGap)
                            val animVal =
                                animationEngine.heightAnimatables[entries[i].id]?.value ?: 0f
                            val barLen = (animVal / maxBarValue) * hChartWidth
                            val xOff = if (isRtl) hChartRight - barLen else hChartLeft

                            if (touchPos.y in (yOff - hitSlopPx)..(yOff + barThickness + hitSlopPx) &&
                                touchPos.x in (xOff - hitSlopPx)..(xOff + barLen + hitSlopPx)
                            ) {
                                foundEntry = entries[i]
                                break
                            }
                        }

                        if (foundEntry == null) {
                            if (currentSelectedEntry != null) currentOnBarSelected(null)
                        } else {
                            if (isInitialDown && currentSelectedEntry?.id == foundEntry.id) {
                                currentOnBarSelected(null)
                            } else if (currentSelectedEntry?.id != foundEntry.id) {
                                currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                                currentOnBarSelected(foundEntry)
                            }
                        }
                        return
                    }

                    val chartWidth = canvasWidth - yAxisWidthPx
                    val chartHeight = canvasHeight - bottomSpacePx - topSpacePx

                    val (barWidth, barSpacing) = barThicknessAndGap(
                        chartWidth, entries.size, style.barSpacingFactor
                    )

                    var foundEntry: BarEntry? = null

                    for (i in entries.indices) {
                        val ltrStartX = barSlotOffset(i, yAxisWidthPx, barWidth, barSpacing)
                        val startX = mirrorForRtl(ltrStartX, canvasWidth, barWidth, isRtl)
                        val endX = startX + barWidth

                        val currentAnimatedValue =
                            animationEngine.heightAnimatables[entries[i].id]?.value ?: 0f
                        val targetHeight = (currentAnimatedValue / maxBarValue) * chartHeight
                        val startY = canvasHeight - bottomSpacePx - targetHeight
                        val endY = canvasHeight - bottomSpacePx

                        if (touchPos.x in (startX - hitSlopPx)..(endX + hitSlopPx) && touchPos.y in (startY - hitSlopPx)..(endY + hitSlopPx)) {
                            foundEntry = entries[i]
                            break
                        }
                    }

                    if (foundEntry == null) {
                        if (currentSelectedEntry != null) currentOnBarSelected(null)
                    } else {
                        if (isInitialDown && currentSelectedEntry?.id == foundEntry.id) {
                            currentOnBarSelected(null)
                        } else if (currentSelectedEntry?.id != foundEntry.id) {
                            currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                            currentOnBarSelected(foundEntry)
                        }
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    processTouch(down.position, isInitialDown = true)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            processTouch(event.changes.first().position, isInitialDown = false)
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        if (entries.isEmpty()) return@Canvas

        if (isHorizontal) {
            val hChartLeft = if (isRtl) topSpacePx else horizontalCatLabelSpacePx
            val hChartRight = if (isRtl) size.width - horizontalCatLabelSpacePx else size.width - topSpacePx
            val hChartBottom = size.height - bottomSpacePx
            val hChartWidth = hChartRight - hChartLeft
            val hChartHeight = hChartBottom - horizontalTopPadPx

            if (axisConfig.showYAxis || axisConfig.showGridLines) {
                for (i in 0..axisConfig.yAxisSteps) {
                    val ratio = i.toFloat() / axisConfig.yAxisSteps
                    val gridX = if (isRtl) hChartRight - hChartWidth * ratio
                    else hChartLeft + hChartWidth * ratio

                    if (axisConfig.showGridLines) {
                        drawLine(
                            color = axisConfig.axisColor,
                            start = Offset(x = gridX, y = horizontalTopPadPx),
                            end = Offset(x = gridX, y = hChartBottom),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = axisConfig.dashEffect
                        )
                    }

                    if (axisConfig.showYAxis) {
                        yAxisTextLayouts[i]?.let { layout ->
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(
                                    x = gridX - layout.size.width / 2,
                                    y = hChartBottom + 8.dp.toPx()
                                )
                            )
                        }
                    }
                }
            }

            val zeroX = if (isRtl) hChartRight else hChartLeft
            drawLine(
                color = axisConfig.axisColor,
                start = Offset(x = zeroX, y = horizontalTopPadPx),
                end = Offset(x = zeroX, y = hChartBottom),
                strokeWidth = 2.dp.toPx()
            )

            val barCount = entries.size
            val (barThickness, barGap) = barThicknessAndGap(
                hChartHeight, barCount, style.barSpacingFactor
            )

            entries.forEachIndexed { index, entry ->
                val animVal = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
                val selAlpha = animationEngine.selectionAlphaAnimatables[entry.id]?.value ?: 1f
                val barLen = (animVal / maxBarValue) * hChartWidth

                val yOff = barSlotOffset(index, horizontalTopPadPx, barThickness, barGap)
                val xOff = if (isRtl) hChartRight - barLen else hChartLeft

                if (barLen > 0f) {
                    barPath.rewind()
                    val cr = CornerRadius(x = cornerRadiusPx, y = cornerRadiusPx)
                    barPath.addRoundRect(
                        RoundRect(
                            left = xOff,
                            top = yOff,
                            right = xOff + barLen,
                            bottom = yOff + barThickness,
                            topLeftCornerRadius = if (isRtl) cr else CornerRadius.Zero,
                            topRightCornerRadius = if (isRtl) CornerRadius.Zero else cr,
                            bottomLeftCornerRadius = if (isRtl) cr else CornerRadius.Zero,
                            bottomRightCornerRadius = if (isRtl) CornerRadius.Zero else cr
                        )
                    )

                    val stops = colorStopArrays[entry.id]
                    val colors = entry.gradientColors ?: dataSet.defaultGradientColors
                    val brushStart = if (isRtl) xOff + barLen else xOff
                    val brushEnd = if (isRtl) xOff else xOff + barLen
                    val brush = if (stops != null) {
                        Brush.horizontalGradient(*stops, startX = brushStart, endX = brushEnd)
                    } else {
                        Brush.horizontalGradient(
                            colors = colors,
                            startX = brushStart,
                            endX = brushEnd
                        )
                    }

                    drawPath(path = barPath, brush = brush, alpha = selAlpha)
                }

                xLabelLayouts[entry.id]?.let { layout ->
                    val lx = if (isRtl) hChartRight + 8.dp.toPx()
                    else hChartLeft - layout.size.width - 8.dp.toPx()
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = lx,
                            y = yOff + (barThickness - layout.size.height) / 2
                        ),
                        alpha = selAlpha
                    )
                }

                if (style.showFloatingValues && animVal > 1f && selectedEntry == null) {
                    val vi = animVal.toInt()
                    val vl = valueTextCache.getOrPut(vi) {
                        textMeasurer.measure(text = vi.toString(), style = centeredValueTextStyle)
                    }
                    val prog = if (entry.y > 0f) (animVal / entry.y).coerceIn(0f, 1f) else 0f
                    val vx = if (isRtl) xOff - vl.size.width - 6.dp.toPx()
                    else xOff + barLen + 6.dp.toPx()
                    drawText(
                        textLayoutResult = vl,
                        topLeft = Offset(x = vx, y = yOff + (barThickness - vl.size.height) / 2),
                        alpha = prog
                    )
                }
            }

            selectedEntry?.let { entry ->
                val animVal = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
                if (animVal > entry.y * 0.9f) {
                    val targetLen = (entry.y / maxBarValue) * hChartWidth
                    val idx = entryIndexMap[entry.id] ?: return@let
                    val yOff = barSlotOffset(idx, horizontalTopPadPx, barThickness, barGap)
                    val xOff = if (isRtl) hChartRight - targetLen else hChartLeft

                    with(selectionRenderer) {
                        drawSelection(
                            entry = entry,
                            barTopLeft = Offset(x = xOff, y = yOff),
                            barSize = Size(width = targetLen, height = barThickness),
                            orientation = orientation,
                            textMeasurer = textMeasurer,
                            tooltipCache = selectionCache
                        )
                    }
                }
            }

            return@Canvas
        }

        val chartWidth = size.width - yAxisWidthPx
        val chartHeight = size.height - bottomSpacePx - topSpacePx

        if (axisConfig.showYAxis || axisConfig.showGridLines) {
            for (i in 0..axisConfig.yAxisSteps) {
                val yRatio = 1f - (i.toFloat() / axisConfig.yAxisSteps.toFloat())
                val yPos = topSpacePx + (chartHeight * yRatio)

                if (axisConfig.showGridLines) {
                    val startX = if (isRtl) 0f else yAxisWidthPx
                    val endX = if (isRtl) size.width - yAxisWidthPx else size.width
                    drawLine(
                        color = axisConfig.axisColor,
                        start = Offset(x = startX, y = yPos),
                        end = Offset(x = endX, y = yPos),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = axisConfig.dashEffect
                    )
                }

                if (axisConfig.showYAxis) {
                    yAxisTextLayouts[i]?.let { layout ->
                        val ltrTextX =
                            yAxisWidthPx - layout.size.width - axisConfig.yAxisLabelPadding.toPx()
                        val rtlTextX =
                            size.width - yAxisWidthPx + axisConfig.yAxisLabelPadding.toPx()
                        val textX = if (isRtl) rtlTextX else ltrTextX
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(x = textX, y = yPos - (layout.size.height / 2))
                        )
                    }
                }
            }
        }

        val baseLineStart = if (isRtl) 0f else yAxisWidthPx
        val baseLineEnd = if (isRtl) size.width - yAxisWidthPx else size.width
        drawLine(
            color = axisConfig.axisColor,
            start = Offset(x = baseLineStart, y = size.height - bottomSpacePx),
            end = Offset(x = baseLineEnd, y = size.height - bottomSpacePx),
            strokeWidth = 2.dp.toPx()
        )

        val barCount = entries.size
        val (barWidth, barSpacing) = barThicknessAndGap(
            chartWidth, barCount, style.barSpacingFactor
        )

        entries.forEachIndexed { index, entry ->
            val currentAnimatedValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
            val currentSelectionAlpha =
                animationEngine.selectionAlphaAnimatables[entry.id]?.value ?: 1f
            val targetHeight = (currentAnimatedValue / maxBarValue) * chartHeight

            val ltrXOffset = barSlotOffset(index, yAxisWidthPx, barWidth, barSpacing)
            val xOffset = mirrorForRtl(ltrXOffset, size.width, barWidth, isRtl)
            val yOffset = size.height - bottomSpacePx - targetHeight

            if (targetHeight > 0f) {
                barPath.rewind()
                barPath.addRoundRect(
                    RoundRect(
                        left = xOffset,
                        top = yOffset,
                        right = xOffset + barWidth,
                        bottom = yOffset + targetHeight,
                        topLeftCornerRadius = CornerRadius(x = cornerRadiusPx, y = cornerRadiusPx),
                        topRightCornerRadius = CornerRadius(x = cornerRadiusPx, y = cornerRadiusPx),
                        bottomLeftCornerRadius = CornerRadius.Zero,
                        bottomRightCornerRadius = CornerRadius.Zero
                    )
                )

                val cachedStops = colorStopArrays[entry.id]
                val activeColors = entry.gradientColors ?: dataSet.defaultGradientColors
                val barBrush = if (cachedStops != null) Brush.verticalGradient(
                    *cachedStops,
                    startY = yOffset,
                    endY = yOffset + targetHeight
                ) else Brush.verticalGradient(
                    colors = activeColors,
                    startY = yOffset,
                    endY = yOffset + targetHeight
                )

                drawPath(path = barPath, brush = barBrush, alpha = currentSelectionAlpha)
            }

            if (style.showFloatingValues && currentAnimatedValue > 1f && selectedEntry == null) {
                val valueInt = currentAnimatedValue.toInt()
                val valueLayout = valueTextCache.getOrPut(valueInt) {
                    textMeasurer.measure(text = valueInt.toString(), style = centeredValueTextStyle)
                }
                val animationProgress =
                    if (entry.y > 0f) (currentAnimatedValue / entry.y).coerceIn(0f, 1f) else 0f
                drawText(
                    textLayoutResult = valueLayout,
                    topLeft = Offset(
                        x = xOffset + (barWidth - valueLayout.size.width) / 2,
                        y = yOffset - valueLayout.size.height - 6.dp.toPx()
                    ),
                    alpha = animationProgress
                )
            }

            xLabelLayouts[entry.id]?.let { cachedLayout ->
                drawText(
                    textLayoutResult = cachedLayout,
                    topLeft = Offset(
                        x = xOffset + (barWidth - cachedLayout.size.width) / 2,
                        y = size.height - bottomSpacePx + 12.dp.toPx()
                    ),
                    alpha = currentSelectionAlpha
                )
            }
        }

        selectedEntry?.let { entry ->
            val currentHeightValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
            if (currentHeightValue > entry.y * 0.9f) {
                val targetHeight = (entry.y / maxBarValue) * chartHeight
                val targetIndex = entryIndexMap[entry.id] ?: return@let
                val ltrXOffset = barSlotOffset(targetIndex, yAxisWidthPx, barWidth, barSpacing)
                val xOffset = mirrorForRtl(ltrXOffset, size.width, barWidth, isRtl)
                val yOffset = size.height - bottomSpacePx - targetHeight

                with(selectionRenderer) {
                    drawSelection(
                        entry = entry,
                        barTopLeft = Offset(x = xOffset, y = yOffset),
                        barSize = Size(width = barWidth, height = targetHeight),
                        orientation = orientation,
                        textMeasurer = textMeasurer,
                        tooltipCache = selectionCache
                    )
                }
            }
        }
    }
}

