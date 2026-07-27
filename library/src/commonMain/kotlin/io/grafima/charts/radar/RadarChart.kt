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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.grafima.charts.rememberEffectiveReduceMotion
import io.grafima.charts.toRadians
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * A composable radar (spider/web) chart with animated transitions, tap selection,
 * multiple series overlay, configurable grid, RTL mirroring, and a pluggable
 * selection indicator.
 *
 * Usage:
 * ```
 * val data = RadarDataSet(
 *     axes = listOf(
 *         RadarAxis("atk", "Attack"),
 *         RadarAxis("def", "Defense"),
 *         RadarAxis("spd", "Speed"),
 *         RadarAxis("mag", "Magic"),
 *         RadarAxis("sta", "Stamina")
 *     ),
 *     series = listOf(
 *         RadarSeries("warrior", "Warrior",
 *             values = mapOf("atk" to 90f, "def" to 80f, "spd" to 50f, "mag" to 20f, "sta" to 70f),
 *             color = Color.Red
 *         ),
 *         RadarSeries("mage", "Mage",
 *             values = mapOf("atk" to 30f, "def" to 40f, "spd" to 60f, "mag" to 95f, "sta" to 50f),
 *             color = Color.Blue
 *         )
 *     )
 * )
 *
 * var selected by remember { mutableStateOf<RadarSeries?>(null) }
 *
 * RadarChart(
 *     dataSet = data,
 *     modifier = Modifier.fillMaxWidth().height(400.dp),
 *     selectedSeries = selected,
 *     onSeriesSelected = { selected = it }
 * )
 * ```
 *
 * @param dataSet Axes and series to render. Requires at least 3 axes.
 * @param style Visual configuration for grid, axes, labels, and geometry.
 * @param animationConfig Timing for entry, morph, and selection animations.
 * @param a11yConfig Accessibility label builders.
 * @param selectionRenderer Strategy for drawing the selection indicator.
 * @param selectedSeries The currently selected series, or null for no selection.
 * @param selectionHaptic Haptic effect performed when a series becomes selected. Pass null to disable.
 * @param onSeriesSelected Called when the user taps near a vertex (with the series)
 *   or taps outside / re-taps the same series (with null).
 */
@Composable
fun RadarChart(
    dataSet: RadarDataSet,
    modifier: Modifier = Modifier,
    style: RadarChartStyle = RadarChartStyle(),
    animationConfig: RadarAnimationConfig = RadarAnimationConfig(),
    a11yConfig: RadarA11yConfig = RadarA11yConfig(),
    selectionRenderer: RadarChartSelectionRenderer = remember { TooltipRadarSelectionRenderer() },
    selectedSeries: RadarSeries? = null,
    selectionHaptic: HapticFeedbackType? = HapticFeedbackType.LongPress,
    onSeriesSelected: (RadarSeries?) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val axes = dataSet.axes
    val series = dataSet.series
    val animationEngine = remember { RadarChartAnimationEngine() }
    val density = LocalDensity.current

    val reduceMotion = rememberEffectiveReduceMotion()
    val effectiveAnimationConfig = remember(animationConfig, reduceMotion) {
        if (reduceMotion) {
            animationConfig.copy(
                initialEntrySpec = snap(),
                morphSpec = snap(),
                selectionSpec = snap(),
                startDelayMs = 0L,
                seriesStaggerMs = 0L,
                vertexStaggerMs = 0L
            )
        } else {
            animationConfig
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // ── Stable state refs for long-lived lambdas (pointerInput) ──
    val haptic = LocalHapticFeedback.current

    val currentSelectedSeries by rememberUpdatedState(selectedSeries)
    val currentOnSeriesSelected by rememberUpdatedState(onSeriesSelected)
    val currentSelectionHaptic by rememberUpdatedState(selectionHaptic)
    val currentStyle by rememberUpdatedState(style)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentDensity by rememberUpdatedState(density)
    val currentAxes by rememberUpdatedState(axes)
    val currentSeries by rememberUpdatedState(series)

    val selectionCache = remember { mutableMapOf<String, TextLayoutResult>() }

    // ── Pre-computed trig cache (allocated once, survives draw frames) ──
    // Only recomputes when axis count, start angle, or RTL changes.
    val trigCache = remember(axes.size, style.startAngle, isRtl) {
        val count = axes.size
        if (count < 3) AxisTrigCache(cosA = FloatArray(0), sinA = FloatArray(0))
        else {
            val step = 360f / count
            val dir = if (isRtl) -1f else 1f
            AxisTrigCache(
                cosA = FloatArray(count) {
                    cos(toRadians((style.startAngle + it * step * dir).toDouble())).toFloat()
                },
                sinA = FloatArray(count) {
                    sin(toRadians((style.startAngle + it * step * dir).toDouble())).toFloat()
                }
            )
        }
    }
    val currentTrigCache by rememberUpdatedState(trigCache)

    // ── Pre-computed animatable key matrix (avoids string concat in draw) ──
    // Flat array: keyMatrix[seriesIndex * axisCount + axisIndex] = "seriesId::axisId"
    val axisCount = axes.size
    val keyMatrix = remember(axes, series) {
        if (axisCount < 3 || series.isEmpty()) emptyArray()
        else Array(series.size * axisCount) { flat ->
            val si = flat / axisCount
            val ai = flat % axisCount
            "${series[si].id}::${axes[ai].id}"
        }
    }

    // ── Pre-measure axis labels (avoids per-frame text measurement) ──
    val labelTextStyle = remember(style.labelColor, style.labelFontSize) {
        TextStyle(
            color = style.labelColor,
            fontSize = style.labelFontSize,
            fontWeight = FontWeight.Medium
        )
    }
    val axisLabelLayouts = remember(axes, labelTextStyle, style.showLabels) {
        if (!style.showLabels) emptyMap()
        else axes.associate { axis ->
            axis.id to textMeasurer.measure(
                text = axis.label,
                style = labelTextStyle,
                maxLines = 1
            )
        }
    }
    val currentAxisLabelLayouts by rememberUpdatedState(axisLabelLayouts)

    // ── Cached max label dimension (used by both draw and pointer input) ──
    val maxLabelDim = remember(axisLabelLayouts) {
        if (axisLabelLayouts.isEmpty()) 0f
        else axisLabelLayouts.values.maxOf { max(it.size.width, it.size.height) }.toFloat()
    }
    val currentMaxLabelDim by rememberUpdatedState(maxLabelDim)

    val chartStateDescription = remember(selectedSeries, a11yConfig) {
        a11yConfig.selectedStateDescription(selectedSeries)
    }

    val chartDescription = remember(dataSet, a11yConfig) {
        buildString {
            append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
            series.forEach { s ->
                append(a11yConfig.seriesDescriptionBuilder(s, axes)).append(". ")
            }
        }
    }

    // ── Animatable map housekeeping (synchronous, before draw) ──
    SideEffect {
        animationEngine.syncAnimatables(axes, series)
    }

    // ── Entry value animations ──
    LaunchedEffect(axes, series) {
        selectionCache.clear()
        if (currentSelectedSeries != null && series.none { it.id == currentSelectedSeries?.id }) {
            currentOnSeriesSelected(null)
        }
        animationEngine.launchEntryAnimations(axes, series, effectiveAnimationConfig, this)
    }

    // ── Selection animations ──
    LaunchedEffect(axes, series, selectedSeries) {
        animationEngine.launchSelectionAnimations(
            series, selectedSeries, style, effectiveAnimationConfig, this
        )
    }

    // Reusable Path objects (allocated once, reset per frame)
    val gridPath = remember { Path() }
    val seriesPath = remember { Path() }

    Canvas(
        modifier = modifier
            .defaultMinSize(minWidth = style.minSize, minHeight = style.minSize)
            .semantics(mergeDescendants = true) {
                role = Role.Image
                contentDescription = chartDescription
                stateDescription = chartStateDescription
                liveRegion = LiveRegionMode.Polite
                customActions = buildList {
                    series.forEach { s ->
                        add(
                            CustomAccessibilityAction(label = "Select ${s.label}") {
                                onSeriesSelected(s)
                                true
                            }
                        )
                    }
                    if (selectedSeries != null) {
                        add(
                            CustomAccessibilityAction(label = "Clear selection") {
                                onSeriesSelected(null)
                                true
                            }
                        )
                    }
                }
            }
            // ── pointerInput(Unit): never restarts ──
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val touchPos = down.position

                    val activeStyle = currentStyle
                    val activeAxes = currentAxes
                    val activeSeries = currentSeries
                    val activeDensity = currentDensity
                    val activeTrig = currentTrigCache
                    val activeMaxLabelDim = currentMaxLabelDim

                    val count = activeAxes.size
                    if (count < 3 || activeTrig.cosA.size < count) return@awaitEachGesture

                    val cx = size.width.toFloat() / 2f
                    val cy = size.height.toFloat() / 2f

                    val labelSpace = if (activeStyle.showLabels) {
                        with(activeDensity) { activeStyle.labelPadding.toPx() } + activeMaxLabelDim / 2f
                    } else 0f

                    val chartRadius = resolveRadarRadius(
                        style = activeStyle,
                        canvasWidth = size.width.toFloat(),
                        canvasHeight = size.height.toFloat(),
                        labelSpace = labelSpace,
                        density = activeDensity
                    )

                    // Find closest vertex across all series
                    var closestSeries: RadarSeries? = null
                    var closestDist = Float.MAX_VALUE

                    activeSeries.forEach { s ->
                        for (i in 0 until count) {
                            val axis = activeAxes[i]
                            val key = "${s.id}::${axis.id}"
                            val animVal = animationEngine.valueAnimatables[key]?.value ?: 0f
                            val norm = (animVal / axis.maxValue).coerceIn(0f, 1f)
                            val vx = cx + chartRadius * norm * activeTrig.cosA[i]
                            val vy = cy + chartRadius * norm * activeTrig.sinA[i]
                            val dist = hypot(
                                (touchPos.x - vx).toDouble(),
                                (touchPos.y - vy).toDouble()
                            ).toFloat()
                            if (dist < closestDist) {
                                closestDist = dist
                                closestSeries = s
                            }
                        }
                    }

                    val touchThreshold = with(activeDensity) { 48.dp.toPx() }

                    if (closestSeries != null && closestDist < touchThreshold) {
                        if (currentSelectedSeries?.id == closestSeries.id) {
                            currentOnSeriesSelected(null)
                        } else {
                            currentSelectionHaptic?.let { haptic.performHapticFeedback(it) }
                            currentOnSeriesSelected(closestSeries)
                        }
                    } else {
                        if (currentSelectedSeries != null) currentOnSeriesSelected(null)
                    }
                }
            }
    ) {
        // ── Pure draw lambda: no state mutations ──
        if (axisCount < 3 || series.isEmpty()) return@Canvas

        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(x = cx, y = cy)

        val cosA = trigCache.cosA
        val sinA = trigCache.sinA
        if (cosA.size < axisCount) return@Canvas

        val labelSpace = if (style.showLabels) {
            style.labelPadding.toPx() + maxLabelDim / 2f
        } else 0f

        val chartRadius = resolveRadarRadius(
            style = style,
            canvasWidth = size.width,
            canvasHeight = size.height,
            labelSpace = labelSpace,
            density = density
        )

        // ── 1. Grid rings ──
        if (style.gridLevels > 0) {
            val gridStrokePx = style.gridStrokeWidth.toPx()

            for (level in 1..style.gridLevels) {
                val levelRadius = chartRadius * (level.toFloat() / style.gridLevels)

                when (style.gridStyle) {
                    RadarGridStyle.Polygon -> {
                        gridPath.reset()
                        for (i in 0 until axisCount) {
                            val x = cx + levelRadius * cosA[i]
                            val y = cy + levelRadius * sinA[i]
                            if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
                        }
                        gridPath.close()
                        drawPath(
                            path = gridPath,
                            color = style.gridColor,
                            style = Stroke(gridStrokePx)
                        )
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

        // ── 2. Axis spokes ──
        val axisStrokePx = style.axisStrokeWidth.toPx()
        for (i in 0 until axisCount) {
            drawLine(
                color = style.axisColor,
                start = center,
                end = Offset(x = cx + chartRadius * cosA[i], y = cy + chartRadius * sinA[i]),
                strokeWidth = axisStrokePx
            )
        }

        // ── 3. Series polygons (fill, stroke, dots) ──
        val dotRadiusPx = style.dotRadius.toPx()

        series.forEachIndexed { si, s ->
            val seriesAlpha = animationEngine.alphaAnimatables[s.id]?.value ?: 1f
            val strokePx = s.strokeWidth.toPx()

            // Build polygon path using pre-computed keys
            seriesPath.reset()
            for (i in 0 until axisCount) {
                val key = keyMatrix[si * axisCount + i]
                val animVal = animationEngine.valueAnimatables[key]?.value ?: 0f
                val norm = (animVal / axes[i].maxValue).coerceIn(0f, 1f)
                val x = cx + chartRadius * norm * cosA[i]
                val y = cy + chartRadius * norm * sinA[i]
                if (i == 0) seriesPath.moveTo(x, y) else seriesPath.lineTo(x, y)
            }
            seriesPath.close()

            // Fill
            drawPath(
                path = seriesPath,
                color = s.color.copy(alpha = s.fillAlpha * seriesAlpha)
            )

            // Stroke
            drawPath(
                path = seriesPath,
                color = s.color.copy(alpha = seriesAlpha),
                style = Stroke(width = strokePx)
            )

            // Dots (second pass over vertices, no allocation)
            if (style.showDots) {
                for (i in 0 until axisCount) {
                    val key = keyMatrix[si * axisCount + i]
                    val animVal = animationEngine.valueAnimatables[key]?.value ?: 0f
                    val norm = (animVal / axes[i].maxValue).coerceIn(0f, 1f)
                    drawCircle(
                        color = s.color.copy(alpha = seriesAlpha),
                        radius = dotRadiusPx,
                        center = Offset(
                            x = cx + chartRadius * norm * cosA[i],
                            y = cy + chartRadius * norm * sinA[i]
                        )
                    )
                }
            }
        }

        // ── 4. Axis labels ──
        if (style.showLabels) {
            val labelPaddingPx = style.labelPadding.toPx()

            for (i in 0 until axisCount) {
                val layout = axisLabelLayouts[axes[i].id] ?: continue
                val labelRadius = chartRadius + labelPaddingPx

                val lx = cx + labelRadius * cosA[i]
                val ly = cy + labelRadius * sinA[i]

                // Offset so the label sits flush away from the chart center.
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = lx - layout.size.width / 2f + cosA[i] * layout.size.width / 2f,
                        y = ly - layout.size.height / 2f + sinA[i] * layout.size.height / 2f
                    )
                )
            }
        }

        // ── 5. Selection indicator ──
        selectedSeries?.let { selected ->
            val selectedIdx = series.indexOfFirst { it.id == selected.id }
            if (selectedIdx < 0) return@let

            val vertices = List(axisCount) { i ->
                val key = keyMatrix[selectedIdx * axisCount + i]
                val animVal = animationEngine.valueAnimatables[key]?.value ?: 0f
                val norm = (animVal / axes[i].maxValue).coerceIn(0f, 1f)
                Offset(
                    x = cx + chartRadius * norm * cosA[i],
                    y = cy + chartRadius * norm * sinA[i]
                )
            }

            with(selectionRenderer) {
                drawSelection(
                    series = selected,
                    axes = axes,
                    vertices = vertices,
                    center = center,
                    chartRadius = chartRadius,
                    textMeasurer = textMeasurer,
                    tooltipCache = selectionCache,
                    layoutDirection = layoutDirection
                )
            }
        }
    }
}
