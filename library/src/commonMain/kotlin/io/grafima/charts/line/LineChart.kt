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

package io.grafima.charts.line

import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.grafima.charts.rememberEffectiveReduceMotion
import kotlin.math.max
import kotlin.math.min

/**
 * A composable line chart with monotone cubic bezier curves, gradient strokes,
 * area fills, drag crosshair with tooltips, animated entry/morph, full RTL
 * mirroring, and accessibility support.
 *
 * Basic usage:
 * ```
 * val data = LineDataSet(
 *     series = listOf(
 *         LineSeries("sales", "Sales",
 *             points = values.mapIndexed { i, v -> LineDataPoint(i.toFloat(), v, months[i]) },
 *             color = Color(0xFF6366F1),
 *             fillAlpha = 0.12f
 *         )
 *     )
 * )
 *
 * var selected by remember { mutableStateOf<Int?>(null) }
 *
 * LineChart(
 *     dataSet = data,
 *     modifier = Modifier.fillMaxWidth().height(300.dp),
 *     selectedPointIndex = selected,
 *     onPointSelected = { selected = it }
 * )
 * ```
 *
 * Gradient stroke + area fill:
 * ```
 * LineSeries("rev", "Revenue",
 *     points = ...,
 *     strokeGradientColors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
 *     fillAlpha = 0.15f,
 *     fillGradientColors = listOf(Color(0xFF818CF8).copy(alpha = 0.3f), Color.Transparent)
 * )
 * ```
 *
 * @param dataSet Series and chart description to render.
 * @param style Visual configuration: curve type, dots, sizing.
 * @param axisConfig Axes, grid lines, labels, and value formatting.
 * @param crosshairConfig Crosshair interaction and tooltip appearance.
 * @param animationConfig Timing for entry wave, morph spring, and stagger delays.
 * @param a11yConfig Accessibility description builders for TalkBack.
 * @param selectedPointIndex Currently highlighted x-axis data point index, or null.
 *   Hoist this in the parent to control crosshair externally.
 * @param selectionHaptic Haptic effect performed each time the crosshair snaps to a new
 *   data point during a drag. Pass null to disable.
 * @param onPointSelected Called during touch/drag (with the nearest point index)
 *   and on finger release (with null). The crosshair renders at this index.
 */
@Composable
fun LineChart(
    dataSet: LineDataSet,
    modifier: Modifier = Modifier,
    style: LineChartStyle = LineChartStyle(),
    axisConfig: LineAxisConfig = LineAxisConfig(),
    crosshairConfig: LineCrosshairConfig = LineCrosshairConfig(),
    animationConfig: LineAnimationConfig = LineAnimationConfig(),
    a11yConfig: LineA11yConfig = LineA11yConfig(),
    selectedPointIndex: Int? = null,
    selectionHaptic: HapticFeedbackType? = HapticFeedbackType.SegmentTick,
    onPointSelected: (Int?) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val series = dataSet.series
    val density = LocalDensity.current

    val reduceMotion = rememberEffectiveReduceMotion()
    val effectiveAnimationConfig = remember(animationConfig, reduceMotion) {
        if (reduceMotion) {
            animationConfig.copy(
                entrySpec = snap(),
                morphSpec = snap(),
                staggerMs = 0L,
                startDelayMs = 0L,
                seriesStaggerMs = 0L
            )
        } else {
            animationConfig
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val animationEngine = remember { LineChartAnimationEngine() }

    // Drawn, but not in the dataset: the crosshair and a11y stay on `series`.
    val renderSeries = animationEngine.renderSeries(series)

    // ── Stable state refs for pointerInput(Unit) ──
    val haptic = LocalHapticFeedback.current

    val currentSeries by rememberUpdatedState(series)
    val currentOnPointSelected by rememberUpdatedState(onPointSelected)
    val currentDensity by rememberUpdatedState(density)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentSelectionHaptic by rememberUpdatedState(selectionHaptic)

    // ── Data ranges (recomputed only on data change) ──
    val allPoints = remember(renderSeries) { renderSeries.flatMap { it.points } }
    val xDataMin = remember(allPoints) { allPoints.minOfOrNull { it.x } ?: 0f }
    val xDataMax = remember(allPoints) { allPoints.maxOfOrNull { it.x } ?: 1f }
    val xBounds = remember(xDataMin, xDataMax, axisConfig.xMin, axisConfig.xMax) {
        resolveAxisBounds(
            dataMin = xDataMin,
            dataMax = xDataMax,
            pinnedMin = axisConfig.xMin,
            pinnedMax = axisConfig.xMax
        )
    }
    val xMin = xBounds.start
    val xMax = xBounds.endInclusive
    val currentXMin by rememberUpdatedState(xMin)
    val currentXMax by rememberUpdatedState(xMax)

    val yDataMax = remember(allPoints) { allPoints.maxOfOrNull { it.y } ?: 1f }
    val yRawMin = remember(allPoints) { allPoints.minOfOrNull { it.y } ?: 0f }
    val yDataMin = remember(yRawMin, axisConfig.includeZeroInYRange) {
        if (axisConfig.includeZeroInYRange) min(0f, yRawMin) else yRawMin
    }
    val yTickValues = remember(
        yDataMin,
        yDataMax,
        axisConfig.yTickCount,
        axisConfig.yMin,
        axisConfig.yMax
    ) {
        computeAxisTicks(
            dataMin = yDataMin,
            dataMax = yDataMax,
            tickCount = axisConfig.yTickCount,
            pinnedMin = axisConfig.yMin,
            pinnedMax = axisConfig.yMax
        )
    }
    val yMin = yTickValues.firstOrNull() ?: 0f
    val yMax = yTickValues.lastOrNull() ?: 1f

    // ── Pre-measured labels (zero text measurement in draw) ──
    val labelStyle = remember(axisConfig.labelColor, axisConfig.labelFontSize) {
        TextStyle(
            color = axisConfig.labelColor,
            fontSize = axisConfig.labelFontSize,
            fontWeight = FontWeight.Medium
        )
    }
    val yLabelLayouts = remember(yTickValues, labelStyle, axisConfig.yLabelFormatter) {
        yTickValues.map { v ->
            textMeasurer.measure(
                text = axisConfig.yLabelFormatter(v),
                style = labelStyle,
                maxLines = 1
            )
        }
    }
    val maxYLabelWidth = remember(yLabelLayouts) {
        if (yLabelLayouts.isEmpty()) 0f else yLabelLayouts.maxOf { it.size.width }.toFloat()
    }
    val currentMaxYLabelWidth by rememberUpdatedState(maxYLabelWidth)

    val firstPoints = series.firstOrNull()?.points ?: emptyList()
    val xLabelInterval = remember(firstPoints, axisConfig.maxXLabels) {
        max(1, (firstPoints.size + axisConfig.maxXLabels - 1) / axisConfig.maxXLabels)
    }
    val xLabelLayouts = remember(
        firstPoints,
        labelStyle,
        xLabelInterval,
        axisConfig.xLabelFormatter,
        axisConfig.showXLabels
    ) {
        if (!axisConfig.showXLabels) emptyList()
        else firstPoints.filterIndexed { i, _ -> i % xLabelInterval == 0 }.map { p ->
            textMeasurer.measure(
                text = p.label.ifEmpty { axisConfig.xLabelFormatter(p.x) },
                style = labelStyle.copy(textAlign = TextAlign.Center),
                maxLines = 1
            )
        }
    }
    val maxXLabelHeight = remember(xLabelLayouts) {
        if (xLabelLayouts.isEmpty()) 0f else xLabelLayouts.maxOf { it.size.height }.toFloat()
    }

    // Blank counts as absent, decided once so the layout, the drawing and the
    // spoken description cannot disagree about whether a title exists.
    val xTitle = axisConfig.xAxisTitle?.takeIf { it.isNotBlank() }
    val yTitle = axisConfig.yAxisTitle?.takeIf { it.isNotBlank() }

    // Measured unconstrained: this gives the line height the plot insets need.
    // Length is trimmed to the space at draw time, which cannot change the height.
    val xTitleLayout = remember(xTitle, labelStyle) {
        xTitle?.let { textMeasurer.measure(text = it, style = labelStyle, maxLines = 1) }
    }
    val yTitleLayout = remember(yTitle, labelStyle) {
        yTitle?.let { textMeasurer.measure(text = it, style = labelStyle, maxLines = 1) }
    }
    val titleCache = remember(xTitle, yTitle, labelStyle) {
        mutableMapOf<String, TextLayoutResult>()
    }

    val tooltipStyle = remember(crosshairConfig.tooltipTextColor, crosshairConfig.tooltipFontSize) {
        TextStyle(
            color = crosshairConfig.tooltipTextColor,
            fontSize = crosshairConfig.tooltipFontSize,
            fontWeight = FontWeight.Medium,
            lineHeight = crosshairConfig.tooltipFontSize * 1.4f
        )
    }

    // ── Pre-computed key matrix + draw buffers (zero allocation in draw) ──
    val seriesStructure = remember(renderSeries) { renderSeries.map { it.id to it.points.size } }
    val keyMatrix =
        remember(seriesStructure) {
            renderSeries.associate { s -> s.id to Array(s.points.size) { i -> "${s.id}::$i" } }
        }
    val xBuffers =
        remember(seriesStructure) { renderSeries.associate { s -> s.id to FloatArray(s.points.size) } }
    val yBuffers =
        remember(seriesStructure) { renderSeries.associate { s -> s.id to FloatArray(s.points.size) } }
    val tangentBuffers =
        remember(seriesStructure) { renderSeries.associate { s -> s.id to FloatArray(s.points.size) } }
    val deltasBuffers = remember(seriesStructure) {
        renderSeries.associate { s ->
            s.id to FloatArray(
                max(
                    0,
                    s.points.size - 1
                )
            )
        }
    }

    // Clipping a range with nothing outside it would only shave a cap on the bound.
    // Guards apply only to a pinned axis. An automatic one already contains its
    // data, so testing it can only misfire on a float rounding of its own ticks.
    val xIsPinned = axisConfig.xMin != null || axisConfig.xMax != null
    val yIsPinned = axisConfig.yMin != null || axisConfig.yMax != null
    val currentXIsPinned by rememberUpdatedState(xIsPinned)

    // Per edge, not per axis: an axis that cuts at one end must not clip the other,
    // where a mark sitting on the bound would lose its outer half.
    val lowXCuts = xIsPinned && xMin > xDataMin
    val highXCuts = xIsPinned && xMax < xDataMax
    val topCuts = yIsPinned && yMax < yDataMax
    val bottomCuts = yIsPinned && yMin > yRawMin

    // ── Cached PathEffect for dashed grid ──
    val dashEffect = remember(axisConfig.dashedGrid) {
        if (axisConfig.dashedGrid) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
    }

    // ── Accessibility ──
    // Axis titles carry the unit the numbers are in, so they are spoken too. Only
    // when set: unset, the description is exactly what the builder produced.
    val baseDescription = remember(dataSet, a11yConfig, xTitle, yTitle) {
        buildString {
            append(a11yConfig.chartDescriptionBuilder(dataSet))
            xTitle?.let { append("X axis: $it. ") }
            yTitle?.let { append("Y axis: $it. ") }
        }
    }
    val selectedDescription = remember(selectedPointIndex, series, a11yConfig) {
        selectedPointIndex?.let { idx -> a11yConfig.selectedPointDescriptionBuilder(idx, series) }
            ?: ""
    }

    val tooltipCache = remember { mutableMapOf<String, TextLayoutResult>() }
    val linePath = remember { Path() }
    val areaPath = remember { Path() }
    val plotInsets = remember { PlotInsets() }

    // ── Animation lifecycle ──
    SideEffect { animationEngine.syncAnimatables(series) }
    LaunchedEffect(series) {
        tooltipCache.clear()
        animationEngine.launchEntryAnimations(series, effectiveAnimationConfig, yMin, this)
        animationEngine.launchExitAnimations(effectiveAnimationConfig, yMin, this)
    }

    Canvas(
        modifier = modifier
            .defaultMinSize(minWidth = style.minSize, minHeight = style.minSize)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                role = Role.Image
                contentDescription = baseDescription
                stateDescription = selectedDescription
                customActions = buildList {
                    val points = series.firstOrNull()?.points.orEmpty()
                    if (points.isNotEmpty()) {
                        // Named, not next/previous: stepping leaves the listener
                        // counting along the axis to work out where they landed.
                        points.forEachIndexed { index, point ->
                            if (xIsPinned && !isWithinAxis(point.x, xMin, xMax)) return@forEachIndexed
                            add(
                                CustomAccessibilityAction(label = "Select ${point.spokenLabel}") {
                                    onPointSelected(index)
                                    true
                                }
                            )
                        }
                        if (selectedPointIndex != null) {
                            add(
                                CustomAccessibilityAction(label = "Clear selection") {
                                    onPointSelected(null)
                                    true
                                }
                            )
                        }
                    }
                }
            }
            // pointerInput(Unit): never restarts, reads all values via rememberUpdatedState
            .pointerInput(Unit) {
                if (!crosshairConfig.enabled) return@pointerInput
                awaitEachGesture {
                    val activeSeries = currentSeries
                    val fp = activeSeries.firstOrNull()?.points ?: return@awaitEachGesture
                    if (fp.isEmpty()) return@awaitEachGesture

                    val den = currentDensity
                    val gap = with(den) { style.labelGap.toPx() }
                    val rtl = currentIsRtl
                    val yLabelW = currentMaxYLabelWidth
                    val cLeft = if (rtl) gap else yLabelW + gap
                    val cRight = if (rtl) size.width - yLabelW - gap else size.width - gap
                    val axMin = currentXMin
                    val axMax = currentXMax

                    val restrict = currentXIsPinned
                    fun nearest(touchX: Float): Int =
                        nearestPointIndex(fp, touchX, axMin, axMax, cLeft, cRight, rtl, restrict)

                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastHapticIndex = nearest(down.position.x)
                    if (lastHapticIndex >= 0) {
                        currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                        currentOnPointSelected(lastHapticIndex)
                    }
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val index = nearest(change.position.x)
                        if (index < 0) {
                            change.consume()
                            continue
                        }
                        if (index != lastHapticIndex) {
                            lastHapticIndex = index
                            currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                        }
                        currentOnPointSelected(index)
                        change.consume()
                    }
                    currentOnPointSelected(null)
                }
            }
    ) {
        // ── Pure draw lambda: no state mutations ──
        if (series.isEmpty()) return@Canvas

        val labelGapPx = style.labelGap.toPx()

        // RTL-aware chart area: Y labels and the Y title flip from left to right
        val insets = computePlotInsets(
            into = plotInsets,
            width = size.width,
            height = size.height,
            gap = labelGapPx,
            yLabelWidth = if (axisConfig.showYLabels) maxYLabelWidth else 0f,
            xLabelHeight = if (axisConfig.showXLabels) maxXLabelHeight else 0f,
            yTitleHeight = yTitleLayout?.size?.height?.toFloat() ?: 0f,
            xTitleHeight = xTitleLayout?.size?.height?.toFloat() ?: 0f,
            isRtl = isRtl
        )
        val chartLeft = insets.left
        val chartRight = insets.right
        val chartBottom = insets.bottom
        val chartTop = insets.top
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop
        val xRange = xMax - xMin
        val yRange = yMax - yMin

        fun mapX(v: Float): Float {
            val raw = if (xRange > 0f) chartLeft + (v - xMin) / xRange * chartWidth else chartLeft
            return if (isRtl) chartRight - (raw - chartLeft) else raw
        }

        fun mapY(v: Float) =
            if (yRange > 0f) chartBottom - (v - yMin) / yRange * chartHeight else chartTop

        val yAxisX = if (isRtl) chartRight else chartLeft

        // ── 1. Grid ──
        if (axisConfig.showGrid) {
            val gridPx = axisConfig.gridStrokeWidth.toPx()
            yTickValues.forEach { v ->
                val y = mapY(v)
                drawLine(
                    color = axisConfig.gridColor,
                    start = Offset(x = chartLeft, y = y),
                    end = Offset(x = chartRight, y = y),
                    strokeWidth = gridPx,
                    pathEffect = dashEffect
                )
            }
        }
        if (axisConfig.showVerticalGrid && firstPoints.size > 1) {
            val gridPx = axisConfig.gridStrokeWidth.toPx()
            firstPoints.forEach { p ->
                if (xIsPinned && !isWithinAxis(p.x, xMin, xMax)) return@forEach
                val x = mapX(p.x)
                drawLine(
                    color = axisConfig.gridColor,
                    start = Offset(x = x, y = chartTop),
                    end = Offset(x = x, y = chartBottom),
                    strokeWidth = gridPx,
                    pathEffect = dashEffect
                )
            }
        }

        // ── 2. Axes ──
        val axisPx = axisConfig.axisStrokeWidth.toPx()
        drawLine(
            color = axisConfig.axisColor,
            start = Offset(x = yAxisX, y = chartTop),
            end = Offset(x = yAxisX, y = chartBottom),
            strokeWidth = axisPx
        )
        drawLine(
            color = axisConfig.axisColor,
            start = Offset(x = chartLeft, y = chartBottom),
            end = Offset(x = chartRight, y = chartBottom),
            strokeWidth = axisPx
        )

        // ── 3. Y labels (RTL: drawn on the right side) ──
        if (axisConfig.showYLabels) {
            yTickValues.forEachIndexed { i, v ->
                if (i < yLabelLayouts.size) {
                    val layout = yLabelLayouts[i]
                    val y = mapY(v)
                    val lx =
                        if (isRtl) chartRight + labelGapPx else chartLeft - labelGapPx - layout.size.width
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x = lx, y = y - layout.size.height / 2f)
                    )
                }
            }
        }

        // ── 4. Series: area fills, line strokes, then dots ──
        // xMin is the left edge in LTR and the right edge in RTL.
        val leftCuts = if (isRtl) highXCuts else lowXCuts
        val rightCuts = if (isRtl) lowXCuts else highXCuts
        val noClip = size.maxDimension
        val leftSlack = if (leftCuts) 0f else noClip
        val rightSlack = if (rightCuts) 0f else noClip
        val topSlack = if (topCuts) 0f else noClip
        val bottomSlack = if (bottomCuts) 0f else noClip
        val dotRadiusPx = style.dotRadius.toPx()
        renderSeries.forEach { s ->
            val n = s.points.size
            if (n == 0) return@forEach
            val keys = keyMatrix[s.id] ?: return@forEach
            val xs = xBuffers[s.id] ?: return@forEach
            val ys = yBuffers[s.id] ?: return@forEach
            val tans = tangentBuffers[s.id] ?: return@forEach
            val delts = deltasBuffers[s.id] ?: return@forEach

            // Fill pre-allocated buffers with animated screen positions
            for (i in 0 until n) {
                val animY = animationEngine.yAnimatables[keys[i]]?.value ?: 0f
                xs[i] = mapX(s.points[i].x)
                ys[i] = mapY(animY)
            }
            if (style.curveType == LineCurveType.MonotoneCubic && n >= 2) {
                computeMonotoneTangents(xs = xs, ys = ys, tangents = tans, deltas = delts, n = n)
            }

            clipRect(
                left = chartLeft - leftSlack,
                top = chartTop - topSlack,
                right = chartRight + rightSlack,
                bottom = chartBottom + bottomSlack
            ) {
                // Area fill (one Brush creation per gradient series per frame, acceptable)
                if (s.fillAlpha > 0f || s.fillGradientColors.isNotEmpty()) {
                    areaPath.reset()
                    areaPath.buildArea(
                        xs = xs,
                        ys = ys,
                        tangents = tans,
                        n = n,
                        chartBottom = chartBottom,
                        curveType = style.curveType
                    )
                    val brush = if (s.fillGradientColors.size >= 2) {
                        Brush.verticalGradient(
                            colors = s.fillGradientColors,
                            startY = chartTop,
                            endY = chartBottom
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                s.color.copy(alpha = s.fillAlpha),
                                Color.Transparent
                            ), startY = chartTop, endY = chartBottom
                        )
                    }
                    drawPath(path = areaPath, brush = brush)
                }

                // Line stroke: gradient or solid
                linePath.reset()
                linePath.buildCurve(
                    xs = xs,
                    ys = ys,
                    tangents = tans,
                    n = n,
                    curveType = style.curveType
                )
                val strokeStyle = Stroke(width = s.strokeWidth.toPx(), cap = StrokeCap.Round)
                if (s.strokeGradientColors.size >= 2) {
                    drawPath(
                        path = linePath,
                        brush = Brush.horizontalGradient(
                            colors = s.strokeGradientColors,
                            startX = if (isRtl) chartRight else chartLeft,
                            endX = if (isRtl) chartLeft else chartRight
                        ),
                        style = strokeStyle
                    )
                } else {
                    drawPath(path = linePath, color = s.color, style = strokeStyle)
                }
            }

            // Dots sit outside the clip so one on a bound keeps all of itself.
            if (style.showDots) {
                for (i in 0 until n) {
                    val p = s.points[i]
                    if (yIsPinned && !isWithinAxis(p.y, yMin, yMax)) continue
                    if (xIsPinned && !isWithinAxis(p.x, xMin, xMax)) continue
                    drawCircle(
                        color = s.color,
                        radius = dotRadiusPx,
                        center = Offset(x = xs[i], y = ys[i])
                    )
                }
            }
        }

        // ── 5. X labels ──
        if (axisConfig.showXLabels && xLabelLayouts.isNotEmpty()) {
            var layoutIdx = 0
            firstPoints.forEachIndexed { i, p ->
                if (i % xLabelInterval == 0 && layoutIdx < xLabelLayouts.size) {
                    // After the increment: a skipped label still consumes its
                    // layout, or every later label lands on the wrong point.
                    val layout = xLabelLayouts[layoutIdx++]
                    if (xIsPinned && !isWithinAxis(p.x, xMin, xMax)) return@forEachIndexed
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = mapX(p.x) - layout.size.width / 2f,
                            y = chartBottom + labelGapPx
                        )
                    )
                }
            }
        }

        // ── 5b. Axis titles ──
        // A title longer than the side it names is ellipsised rather than drawn
        // over the plot. Re-measured once per width, not per frame, and never
        // taller than the unconstrained layout the insets were built from.
        fun fitted(layout: TextLayoutResult, text: String, available: Float): TextLayoutResult {
            if (available <= 0f || layout.size.width <= available) return layout
            return titleCache.getOrPut("$text|${available.toInt()}") {
                textMeasurer.measure(
                    text = text,
                    style = labelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = available.toInt())
                )
            }
        }

        xTitleLayout?.let { measured ->
            val layout = fitted(measured, xTitle!!, chartRight - chartLeft)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = chartLeft + (chartRight - chartLeft - layout.size.width) / 2f,
                    y = size.height - labelGapPx - layout.size.height
                )
            )
        }
        yTitleLayout?.let { measured ->
            // Rotated, so the space it has to fit is the plot's height.
            val layout = fitted(measured, yTitle!!, chartBottom - chartTop)
            val half = layout.size.height / 2f
            val cx = if (isRtl) size.width - labelGapPx - half else labelGapPx + half
            val cy = chartTop + (chartBottom - chartTop) / 2f
            rotate(degrees = -90f, pivot = Offset(cx, cy)) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(cx - layout.size.width / 2f, cy - half)
                )
            }
        }

        // ── 6. Crosshair + tooltip ──
        selectedPointIndex?.let { idx ->
            val fp = series.firstOrNull() ?: return@let
            if (idx !in fp.points.indices) return@let
            if (xIsPinned && !isWithinAxis(fp.points[idx].x, xMin, xMax)) return@let
            val crossX = mapX(fp.points[idx].x)

            drawLine(
                color = crosshairConfig.lineColor,
                start = Offset(x = crossX, y = chartTop),
                end = Offset(x = crossX, y = chartBottom),
                strokeWidth = crosshairConfig.lineWidth.toPx()
            )

            val dotR = crosshairConfig.dotRadius.toPx()
            val borderW = crosshairConfig.dotBorderWidth.toPx()
            series.forEach { s ->
                if (idx < s.points.size) {
                    if (yIsPinned && !isWithinAxis(s.points[idx].y, yMin, yMax)) return@forEach
                    val key = keyMatrix[s.id]?.getOrNull(idx) ?: return@forEach
                    val animY = animationEngine.yAnimatables[key]?.value ?: s.points[idx].y
                    val cy = mapY(animY)
                    drawCircle(
                        color = crosshairConfig.dotBorderColor,
                        radius = dotR + borderW,
                        center = Offset(x = crossX, y = cy)
                    )
                    drawCircle(color = s.color, radius = dotR, center = Offset(x = crossX, y = cy))
                }
            }

            if (crosshairConfig.showTooltip) {
                val tooltipText = buildString {
                    series.forEachIndexed { i, s ->
                        if (idx < s.points.size) {
                            if (i > 0) append("\n")
                            append(crosshairConfig.tooltipFormatter(s, s.points[idx]))
                        }
                    }
                }
                val cacheKey = "${idx}_${tooltipText.hashCode()}"
                val layout = tooltipCache.getOrPut(cacheKey) {
                    textMeasurer.measure(
                        text = tooltipText,
                        style = tooltipStyle
                    )
                }
                val padPx = crosshairConfig.tooltipPadding.toPx()
                val tw = layout.size.width + padPx * 2
                val th = layout.size.height + padPx * 2
                val margin = 8.dp.toPx()

                // RTL: tooltip prefers the left side of the crosshair
                val spaceRight = size.width - crossX - margin
                val spaceLeft = crossX - margin
                val tx = if (isRtl) {
                    if (spaceLeft >= tw) crossX - margin - tw else crossX + margin
                } else {
                    if (spaceRight >= tw) crossX + margin else crossX - margin - tw
                }
                val ty = chartTop + margin

                drawRoundRect(
                    color = crosshairConfig.tooltipBackground,
                    topLeft = Offset(x = tx, y = ty),
                    size = Size(width = tw, height = th),
                    cornerRadius = CornerRadius(crosshairConfig.tooltipCornerRadius.toPx())
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x = tx + padPx, y = ty + padPx)
                )
            }
        }
    }
}
