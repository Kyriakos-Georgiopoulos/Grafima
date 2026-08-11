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

    val barLayout = remember(renderEntries) { computeBarGroupLayout(renderEntries) }

    // Over what is drawn, so removing the tallest bar doesn't rescale the axis
    // out from under it mid-exit.
    val maxBarValue = remember(renderEntries, barLayout, dataSet.mode) {
        axisMaxForLayout(renderEntries, barLayout, dataSet.mode)
    }

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

    // The rest of a group shares the opener's xLabel and is never drawn.
    val xLabelLayouts = remember(renderEntries, style.labelTextStyle) {
        val layouts = mutableMapOf<String, TextLayoutResult>()
        var previous: BarEntry? = null
        renderEntries.forEach { entry ->
            if (!joinsCategory(previous, entry)) {
                layouts[entry.id] = textMeasurer.measure(
                    text = entry.xLabel,
                    style = style.labelTextStyle
                )
            }
            previous = entry
        }
        layouts
    }

    val valueTextCache = remember(style.valueTextStyle) { mutableMapOf<Int, TextLayoutResult>() }
    val selectionCache = remember(textMeasurer, selectionRenderer) {
        mutableMapOf<Int, TextLayoutResult>()
    }
    val barPath = remember { Path() }
    val gridPath = remember { Path() }

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

    val selectionBounds = remember { SelectedBarBounds() }

    // Hit testing walks what is drawn, exiting bars included, so a bar is grabbed
    // where it appears rather than where it is heading. Only bars the dataset still
    // holds can be selected, which is what keeps a departing one out of reach.
    val selectableIds = remember(entries) { entries.mapTo(HashSet()) { it.id } }
    val currentRenderEntries by rememberUpdatedState(renderEntries)
    val currentBarLayout by rememberUpdatedState(barLayout)
    val currentMaxBarValue by rememberUpdatedState(maxBarValue)
    val currentSelectableIds by rememberUpdatedState(selectableIds)
    val isStacked = dataSet.mode == BarGroupMode.Stacked

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
                        // A group's bars share an xLabel; the series is all that
                        // tells their actions apart.
                        val series = entry.spokenSeriesLabel
                        val actionLabel =
                            if (series == null) "Select ${entry.xLabel}"
                            else "Select ${entry.xLabel}, $series"
                        add(
                            CustomAccessibilityAction(label = actionLabel) {
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
                yAxisWidthPx,
                topSpacePx,
                bottomSpacePx,
                style.barSpacingFactor,
                style.groupSpacingFactor,
                isStacked,
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

                        val rendered = currentRenderEntries
                        val renderLayout = currentBarLayout
                        val axisMax = currentMaxBarValue
                        val selectable = currentSelectableIds
                        val (slotThickness, barGap) = barThicknessAndGap(
                            hChartHeight,
                            animationEngine.categorySlotCount(rendered, renderLayout),
                            style.barSpacingFactor
                        )

                        var slotPosition = 0f
                        forEachBarCategory(rendered, renderLayout, animationEngine) {
                                first, end, members, occupancy, _ ->
                            val barThickness =
                                if (isStacked) slotThickness
                                else groupedBarThickness(
                                    slotThickness, members, style.groupSpacingFactor
                                )
                            val innerGap =
                                if (isStacked) 0f
                                else groupedBarGap(slotThickness, members, style.groupSpacingFactor)
                            val slotY = barSlotOffset(
                                slotPosition, horizontalTopPadPx, slotThickness, barGap
                            )
                            slotPosition += occupancy

                            var memberPosition = 0f
                            var stackBase = 0f
                            for (i in first until end) {
                                val entry = rendered[i]
                                val yOff =
                                    if (isStacked) slotY
                                    else slotY + groupedBarOffset(
                                        memberPosition, barThickness, innerGap
                                    )
                                memberPosition += animationEngine.slotOccupancy(entry.id)

                                val animVal =
                                    animationEngine.heightAnimatables[entry.id]?.value ?: 0f
                                val barLen = (animVal / axisMax) * hChartWidth
                                val base = if (isStacked) stackBase else 0f
                                if (isStacked) stackBase += barLen

                                if (entry.id !in selectable) continue

                                val xOff =
                                    if (isRtl) hChartRight - base - barLen else hChartLeft + base

                                // No slop on the axis bars touch along, or the loop
                                // would claim the touch for whichever it reached first.
                                val xSlop = if (isStacked) 0f else hitSlopPx
                                val ySlop = if (isStacked || end - first == 1) hitSlopPx else 0f
                                if (touchPos.y in
                                    (yOff - ySlop)..(yOff + barThickness + ySlop) &&
                                    touchPos.x in (xOff - xSlop)..(xOff + barLen + xSlop)
                                ) {
                                    applySelection(entry, isInitialDown)
                                    return
                                }
                            }
                        }

                        applySelection(null, isInitialDown)
                        return
                    }

                    val chartWidth = canvasWidth - yAxisWidthPx
                    val chartHeight = canvasHeight - bottomSpacePx - topSpacePx

                    val rendered = currentRenderEntries
                    val renderLayout = currentBarLayout
                    val axisMax = currentMaxBarValue
                    val selectable = currentSelectableIds
                    val (slotWidth, barSpacing) = barThicknessAndGap(
                        chartWidth,
                        animationEngine.categorySlotCount(rendered, renderLayout),
                        style.barSpacingFactor
                    )

                    var slotPosition = 0f
                    forEachBarCategory(rendered, renderLayout, animationEngine) {
                            first, end, members, occupancy, _ ->
                        val barWidth =
                            if (isStacked) slotWidth
                            else groupedBarThickness(slotWidth, members, style.groupSpacingFactor)
                        val innerGap =
                            if (isStacked) 0f
                            else groupedBarGap(slotWidth, members, style.groupSpacingFactor)
                        val ltrSlotX =
                            barSlotOffset(slotPosition, yAxisWidthPx, slotWidth, barSpacing)
                        slotPosition += occupancy

                        var memberPosition = 0f
                        var stackBase = 0f
                        for (i in first until end) {
                            val entry = rendered[i]
                            val ltrStartX =
                                if (isStacked) ltrSlotX
                                else ltrSlotX + groupedBarOffset(
                                    memberPosition, barWidth, innerGap
                                )
                            memberPosition += animationEngine.slotOccupancy(entry.id)

                            val currentAnimatedValue =
                                animationEngine.heightAnimatables[entry.id]?.value ?: 0f
                            val targetHeight =
                                (currentAnimatedValue / axisMax) * chartHeight
                            val base = if (isStacked) stackBase else 0f
                            if (isStacked) stackBase += targetHeight

                            if (entry.id !in selectable) continue

                            val startX = mirrorForRtl(ltrStartX, canvasWidth, barWidth, isRtl)
                            val endX = startX + barWidth
                            val endY = canvasHeight - bottomSpacePx - base
                            val startY = endY - targetHeight

                            // No slop on the axis bars touch along, or the loop would
                            // claim the touch for whichever it reached first.
                            val xSlop = if (isStacked || end - first == 1) hitSlopPx else 0f
                            val ySlop = if (isStacked) 0f else hitSlopPx
                            val withinX = touchPos.x in (startX - xSlop)..(endX + xSlop)
                            val withinY = touchPos.y in (startY - ySlop)..(endY + ySlop)
                            if (withinX && withinY) {
                                applySelection(entry, isInitialDown)
                                return
                            }
                        }
                    }

                    applySelection(null, isInitialDown)
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
            val (slotThickness, barGap) = barThicknessAndGap(
                chartBottom - horizontalTopPadPx,
                animationEngine.categorySlotCount(renderEntries, barLayout),
                style.barSpacingFactor
            )

            drawHorizontalGrid(
                axisConfig = axisConfig,
                gridDash = gridDash,
                gridPath = gridPath,
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
                slotThickness = slotThickness,
                barGap = barGap,
                chartLeft = chartLeft,
                chartRight = chartRight,
                chartWidth = hChartWidth,
                topPadPx = horizontalTopPadPx,
                cornerRadiusPx = cornerRadiusPx,
                isRtl = isRtl,
                layout = barLayout,
                mode = dataSet.mode,
                selectionBounds = selectionBounds
            )
            selectedEntry?.let { entry ->
                drawBarSelection(
                    entry = entry,
                    orientation = orientation,
                    selectionRenderer = selectionRenderer,
                    animationEngine = animationEngine,
                    textMeasurer = textMeasurer,
                    selectionCache = selectionCache,
                    bounds = selectionBounds
                )
            }
            return@Canvas
        }

        val chartWidth = size.width - yAxisWidthPx
        val chartHeight = size.height - bottomSpacePx - topSpacePx
        val (slotWidth, barSpacing) = barThicknessAndGap(
            chartWidth,
            animationEngine.categorySlotCount(renderEntries, barLayout),
            style.barSpacingFactor
        )

        drawVerticalGrid(
            axisConfig = axisConfig,
            gridDash = gridDash,
            gridPath = gridPath,
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
            slotWidth = slotWidth,
            barSpacing = barSpacing,
            yAxisWidthPx = yAxisWidthPx,
            bottomSpacePx = bottomSpacePx,
            chartHeight = chartHeight,
            cornerRadiusPx = cornerRadiusPx,
            isRtl = isRtl,
            layout = barLayout,
            mode = dataSet.mode,
            selectionBounds = selectionBounds
        )
        selectedEntry?.let { entry ->
            drawBarSelection(
                entry = entry,
                orientation = orientation,
                selectionRenderer = selectionRenderer,
                animationEngine = animationEngine,
                textMeasurer = textMeasurer,
                selectionCache = selectionCache,
                bounds = selectionBounds
            )
        }
    }
}
