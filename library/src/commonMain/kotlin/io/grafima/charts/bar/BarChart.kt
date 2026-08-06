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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.grafima.charts.DashStroke
import io.grafima.charts.rememberEffectiveReduceMotion
import io.grafima.charts.toDashStroke

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

    // dashEffect is deprecated but still honoured, and reading it is the only way to
    // keep a caller who set one from silently losing their dashes. The suppression
    // is that read, and comes out when the property does.
    @Suppress("DEPRECATION")
    val gridDash = remember(axisConfig.dashEffect, axisConfig.gridDashPattern, density) {
        val explicit = axisConfig.dashEffect
        // An explicit effect keeps the butt ends the grid has always drawn with.
        if (explicit != null) {
            DashStroke(effect = explicit, cap = StrokeCap.Butt)
        } else {
            axisConfig.gridDashPattern.toDashStroke(density)
        }
    }

    SideEffect { animationEngine.syncAnimatables(entries) }
    val renderEntries = animationEngine.renderEntries(entries)

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

    // Over what is drawn, so removing the tallest bar doesn't rescale the axis
    // out from under it mid-exit.
    val maxBarValue = remember(renderEntries) { computeBarAxisMax(renderEntries) }

    val maxLabelResult = remember(maxBarValue, axisConfig.axisLabelTextStyle) {
        textMeasurer.measure(
            text = maxBarValue.toInt().toString(),
            style = axisConfig.axisLabelTextStyle
        )
    }

    val yAxisWidthPx =
        remember(axisConfig.showYAxis, maxLabelResult, axisConfig.yAxisLabelPadding, density) {
            if (axisConfig.showYAxis) {
                maxLabelResult.size.width + with(density) { axisConfig.yAxisLabelPadding.toPx() }
            } else {
                0f
            }
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

    val xLabelLayouts = remember(renderEntries, style.labelTextStyle) {
        renderEntries.associate {
            it.id to textMeasurer.measure(
                text = it.xLabel,
                style = style.labelTextStyle
            )
        }
    }

    val valueTextCache = remember(style.valueTextStyle) { mutableMapOf<Int, TextLayoutResult>() }
    val selectionCache = remember(textMeasurer, selectionRenderer) {
        mutableMapOf<Int, TextLayoutResult>()
    }
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

    val colorStopArrays = remember(renderEntries) {
        renderEntries.associate { entry ->
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
        animationEngine.launchExitAnimations(effectiveAnimationConfig, this)
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
                liveRegion = LiveRegionMode.Polite
                role = Role.Image
                contentDescription = chartDescription
                stateDescription = chartStateDescription
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
                var clearedId: String? = null

                fun applySelection(foundEntry: BarEntry?, isInitialDown: Boolean) {
                    if (foundEntry == null) {
                        if (currentSelectedEntry != null) currentOnBarSelected(null)
                        return
                    }
                    if (foundEntry.id == clearedId) return
                    clearedId = null

                    if (isInitialDown && currentSelectedEntry?.id == foundEntry.id) {
                        clearedId = foundEntry.id
                        currentOnBarSelected(null)
                    } else if (currentSelectedEntry?.id != foundEntry.id) {
                        currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                        currentOnBarSelected(foundEntry)
                    }
                }

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

                        applySelection(foundEntry, isInitialDown)
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

                        val withinX = touchPos.x in (startX - hitSlopPx)..(endX + hitSlopPx)
                        val withinY = touchPos.y in (startY - hitSlopPx)..(endY + hitSlopPx)
                        if (withinX && withinY) {
                            foundEntry = entries[i]
                            break
                        }
                    }

                    applySelection(foundEntry, isInitialDown)
                }

                awaitEachGesture {
                    clearedId = null
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
            val chartLeft = if (isRtl) topSpacePx else horizontalCatLabelSpacePx
            val chartRight =
                if (isRtl) size.width - horizontalCatLabelSpacePx else size.width - topSpacePx
            val chartBottom = size.height - bottomSpacePx
            val hChartWidth = chartRight - chartLeft
            val (barThickness, barGap) = barThicknessAndGap(
                chartBottom - horizontalTopPadPx, animationEngine.slotCount(renderEntries), style.barSpacingFactor
            )

            drawHorizontalGrid(
                axisConfig = axisConfig,
                gridDash = gridDash,
                yAxisTextLayouts = yAxisTextLayouts,
                chartLeft = chartLeft,
                chartRight = chartRight,
                chartBottom = chartBottom,
                chartWidth = hChartWidth,
                topPadPx = horizontalTopPadPx,
                isRtl = isRtl
            )
            drawHorizontalBars(
                dataSet = dataSet,
                renderEntries = renderEntries,
                style = style,
                animationEngine = animationEngine,
                colorStopArrays = colorStopArrays,
                xLabelLayouts = xLabelLayouts,
                valueTextCache = valueTextCache,
                textMeasurer = textMeasurer,
                centeredValueTextStyle = centeredValueTextStyle,
                barPath = barPath,
                selectedEntry = selectedEntry,
                maxBarValue = maxBarValue,
                barThickness = barThickness,
                barGap = barGap,
                chartLeft = chartLeft,
                chartRight = chartRight,
                chartWidth = hChartWidth,
                topPadPx = horizontalTopPadPx,
                cornerRadiusPx = cornerRadiusPx,
                isRtl = isRtl
            )
            selectedEntry?.let { entry ->
                drawBarSelection(
                    entry = entry,
                    orientation = orientation,
                    selectionRenderer = selectionRenderer,
                    animationEngine = animationEngine,
                    entryIndexMap = entryIndexMap,
                    textMeasurer = textMeasurer,
                    selectionCache = selectionCache,
                    maxBarValue = maxBarValue,
                    barExtent = barThickness,
                    barGap = barGap,
                    chartExtent = hChartWidth,
                    leadingInset = horizontalTopPadPx,
                    crossAxisOffset = chartLeft,
                    chartRight = chartRight,
                    isRtl = isRtl
                )
            }
            return@Canvas
        }

        val chartWidth = size.width - yAxisWidthPx
        val chartHeight = size.height - bottomSpacePx - topSpacePx
        val (barWidth, barSpacing) = barThicknessAndGap(
            chartWidth, animationEngine.slotCount(renderEntries), style.barSpacingFactor
        )

        drawVerticalGrid(
            axisConfig = axisConfig,
            gridDash = gridDash,
            yAxisTextLayouts = yAxisTextLayouts,
            yAxisWidthPx = yAxisWidthPx,
            topSpacePx = topSpacePx,
            bottomSpacePx = bottomSpacePx,
            chartHeight = chartHeight,
            isRtl = isRtl
        )
        drawVerticalBars(
            dataSet = dataSet,
            renderEntries = renderEntries,
            style = style,
            animationEngine = animationEngine,
            colorStopArrays = colorStopArrays,
            xLabelLayouts = xLabelLayouts,
            valueTextCache = valueTextCache,
            textMeasurer = textMeasurer,
            centeredValueTextStyle = centeredValueTextStyle,
            barPath = barPath,
            selectedEntry = selectedEntry,
            maxBarValue = maxBarValue,
            barWidth = barWidth,
            barSpacing = barSpacing,
            yAxisWidthPx = yAxisWidthPx,
            bottomSpacePx = bottomSpacePx,
            chartHeight = chartHeight,
            cornerRadiusPx = cornerRadiusPx,
            isRtl = isRtl
        )
        selectedEntry?.let { entry ->
            drawBarSelection(
                entry = entry,
                orientation = orientation,
                selectionRenderer = selectionRenderer,
                animationEngine = animationEngine,
                entryIndexMap = entryIndexMap,
                textMeasurer = textMeasurer,
                selectionCache = selectionCache,
                maxBarValue = maxBarValue,
                barExtent = barWidth,
                barGap = barSpacing,
                chartExtent = chartHeight,
                leadingInset = yAxisWidthPx,
                crossAxisOffset = size.height - bottomSpacePx,
                chartRight = 0f,
                isRtl = isRtl
            )
        }
    }
}
