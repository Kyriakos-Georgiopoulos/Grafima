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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.random.Random

// ==========================================
// 1. DATA MODELS & CONFIG
// ==========================================

@Stable
open class ChartEntry(open val id: String, open val xLabel: String, open val y: Float)

/**
 * A single bar in the chart.
 *
 * @property id Unique identifier — drives animation continuity across data updates.
 * @property xLabel Text shown on the X axis below the bar.
 * @property y The bar's numeric value. Must be positive.
 * @property gradientColors Vertical gradient colors. Falls back to [BarDataSet.defaultGradientColors] when null.
 * @property colorStops Explicit gradient stops (position 0f..1f to [Color]). Takes priority over [gradientColors].
 */
@Immutable
data class BarEntry(
    override val id: String,
    override val xLabel: String,
    override val y: Float,
    val gradientColors: List<Color>? = null,
    val colorStops: List<Pair<Float, Color>>? = null
) : ChartEntry(id, xLabel, y)

enum class BarOrientation { Vertical, Horizontal }

/**
 * Groups bar entries with shared defaults.
 *
 * @property entries The bars to display, in order.
 * @property defaultGradientColors Gradient applied to bars that don't specify their own.
 * @property contentDescription Accessibility label describing the chart's purpose.
 */
@Immutable
data class BarDataSet(
    val entries: List<BarEntry>,
    val defaultGradientColors: List<Color> = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
    val contentDescription: String = "Bar Chart"
)

/**
 * Visual styling for bars and labels.
 *
 * @property barCornerRadius Rounding applied to the top corners of each bar.
 * @property barSpacingFactor Fraction of chart width used for inter-bar spacing (0f..0.9f).
 * @property bottomLabelSpace Vertical space reserved below bars for X-axis labels.
 * @property topValueSpace Vertical space reserved above bars for floating value labels.
 * @property unselectedAlpha Opacity applied to non-selected bars when one is selected.
 * @property showFloatingValues Whether to show animated value labels above bars during entry animation.
 */
@Immutable
data class ChartStyle(
    val barCornerRadius: Dp = 6.dp,
    val barSpacingFactor: Float = 0.35f,
    val bottomLabelSpace: Dp = 32.dp,
    val topValueSpace: Dp = 28.dp,
    val unselectedAlpha: Float = 0.25f,
    val labelTextStyle: TextStyle = TextStyle(
        color = Color(0xFF6B7280),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    ),
    val valueTextStyle: TextStyle = TextStyle(
        color = Color(0xFF111827),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    ),
    val showFloatingValues: Boolean = true
)

/**
 * Configuration for the Y axis and grid lines.
 *
 * @property showYAxis Whether to draw Y-axis value labels.
 * @property showGridLines Whether to draw horizontal grid lines.
 * @property yAxisSteps Number of evenly-spaced grid lines and Y-axis labels.
 * @property dashEffect [PathEffect] applied to grid lines. Pass null for solid lines.
 */
@Immutable
data class AxisConfig(
    val showYAxis: Boolean = true,
    val showGridLines: Boolean = true,
    val yAxisSteps: Int = 4,
    val axisColor: Color = Color(0xFFE5E7EB),
    val axisLabelTextStyle: TextStyle = TextStyle(
        color = Color(0xFF9CA3AF),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    ),
    val yAxisLabelPadding: Dp = 12.dp,
    val dashEffect: PathEffect? = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
)

/**
 * Controls the chart's animation timing and easing.
 *
 * @property initialEntrySpec Animation for bars appearing for the first time.
 * @property morphSpec Animation for bars changing value.
 * @property selectionSpec Animation for the selection alpha transition.
 * @property staggerDelayMs Delay between each bar's initial entry animation.
 * @property startDelayMs Delay before the first bar begins animating.
 */
@Immutable
data class AnimationConfig(
    val initialEntrySpec: AnimationSpec<Float> = tween(
        durationMillis = 1200,
        easing = FastOutSlowInEasing
    ),
    val morphSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    ),
    val selectionSpec: AnimationSpec<Float> = tween(durationMillis = 250, easing = LinearEasing),
    val staggerDelayMs: Long = 120L,
    val startDelayMs: Long = 200L
)

/**
 * Accessibility configuration with builders for TalkBack descriptions.
 */
@Stable
data class A11yConfig(
    val chartDescriptionBuilder: (BarDataSet) -> String = { "Bar Chart representing ${it.contentDescription}" },
    val barDescriptionBuilder: (BarEntry) -> String = { "${it.xLabel} value is ${it.y.toInt()}" },
    val selectedStateDescription: (BarEntry?) -> String = { entry ->
        entry?.let { "Currently selected: ${it.xLabel}, ${it.y.toInt()}." } ?: "No bar selected."
    }
)

// ==========================================
// 2. SELECTION INDICATOR API
// ==========================================

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
            topLeft = Offset(tooltipLeft, tooltipTop),
            size = Size(tooltipWidth, tooltipHeight),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
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

// ==========================================
// 3. STABLE ANIMATION ENGINE
// ==========================================

@Stable
class ChartAnimationEngine {
    val heightAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    val selectionAlphaAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedIds = mutableSetOf<String>()

    fun updateEntryData(entries: List<BarEntry>, config: AnimationConfig, scope: CoroutineScope) {
        val currentIds = entries.mapTo(mutableSetOf()) { it.id }
        heightAnimatables.keys.removeAll { it !in currentIds }
        selectionAlphaAnimatables.keys.removeAll { it !in currentIds }
        initializedIds.removeAll { it !in currentIds }

        entries.forEachIndexed { index, entry ->
            heightAnimatables.getOrPut(entry.id) { Animatable(0f) }
            selectionAlphaAnimatables.getOrPut(entry.id) { Animatable(1f) }
            val isInitialLoad = initializedIds.add(entry.id)

            scope.launch {
                if (isInitialLoad) {
                    delay(config.startDelayMs + (index * config.staggerDelayMs))
                    heightAnimatables[entry.id]?.animateTo(entry.y, config.initialEntrySpec)
                } else if (heightAnimatables[entry.id]?.targetValue != entry.y) {
                    heightAnimatables[entry.id]?.animateTo(entry.y, config.morphSpec)
                }
            }
        }
    }

    fun updateSelectionState(
        entries: List<BarEntry>,
        selectedEntry: BarEntry?,
        style: ChartStyle,
        config: AnimationConfig,
        scope: CoroutineScope
    ) {
        entries.forEach { entry ->
            val animatable = selectionAlphaAnimatables[entry.id] ?: return@forEach
            val isSelected = (selectedEntry?.id == entry.id)
            val targetAlpha =
                if (selectedEntry != null && !isSelected) style.unselectedAlpha else 1f

            if (animatable.targetValue != targetAlpha) {
                scope.launch { animatable.animateTo(targetAlpha, config.selectionSpec) }
            }
        }
    }
}

// ==========================================
// 4. THE CHART COMPONENT
// ==========================================

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
    onBarSelected: (BarEntry?) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val entries = dataSet.entries
    val coroutineScope = rememberCoroutineScope()
    val animationEngine = remember { ChartAnimationEngine() }
    val density = LocalDensity.current

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    val currentSelectedEntry by rememberUpdatedState(selectedEntry)
    val currentOnBarSelected by rememberUpdatedState(onBarSelected)

    val maxBarValue = remember(entries) {
        val rawMax = entries.maxOfOrNull { it.y }?.takeIf { it > 0f } ?: 1f
        val maxWithHeadroom = rawMax * 1.2f
        val step = if (maxWithHeadroom > 100) 50f else if (maxWithHeadroom > 10) 10f else 5f
        ceil(maxWithHeadroom / step) * step
    }

    val maxLabelResult = remember(maxBarValue, axisConfig.axisLabelTextStyle) {
        textMeasurer.measure(maxBarValue.toInt().toString(), axisConfig.axisLabelTextStyle)
    }

    val yAxisWidthPx =
        remember(axisConfig.showYAxis, maxLabelResult, axisConfig.yAxisLabelPadding, density) {
            if (axisConfig.showYAxis) maxLabelResult.size.width + with(density) { axisConfig.yAxisLabelPadding.toPx() } else 0f
        }

    val yAxisTextLayouts =
        remember(maxBarValue, axisConfig.yAxisSteps, axisConfig.axisLabelTextStyle) {
            (0..axisConfig.yAxisSteps).associateWith { i ->
                val stepValue = (maxBarValue / axisConfig.yAxisSteps) * i
                textMeasurer.measure(stepValue.toInt().toString(), axisConfig.axisLabelTextStyle)
            }
        }

    val xLabelLayouts = remember(entries, style.labelTextStyle) {
        entries.associate { it.id to textMeasurer.measure(it.xLabel, style.labelTextStyle) }
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

    val centeredValueTextStyle = remember(style.valueTextStyle) {
        style.valueTextStyle.copy(textAlign = TextAlign.Center)
    }

    val colorStopArrays = remember(entries) {
        entries.associate { it.id to it.colorStops?.toTypedArray() }
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

    val chartDescription = remember(dataSet, selectedEntry, a11yConfig) {
        buildString {
            append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
            entries.forEach { append(a11yConfig.barDescriptionBuilder(it)).append(". ") }
            append(a11yConfig.selectedStateDescription(selectedEntry))
        }
    }

    LaunchedEffect(entries) {
        if (currentSelectedEntry != null && entries.none { it.id == currentSelectedEntry?.id }) {
            currentOnBarSelected(null)
        }
        valueTextCache.clear()
        animationEngine.updateEntryData(entries, animationConfig, coroutineScope)
    }

    LaunchedEffect(entries, selectedEntry) {
        animationEngine.updateSelectionState(
            entries,
            selectedEntry,
            style,
            animationConfig,
            coroutineScope
        )
    }

    Canvas(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = chartDescription
            }
            .pointerInput(entries, yAxisWidthPx, style.barSpacingFactor, isRtl, orientation, horizontalCatLabelSpacePx) {
                fun processTouch(touchPos: Offset, isInitialDown: Boolean) {
                    val canvasWidth = size.width.toFloat()
                    val canvasHeight = size.height.toFloat()

                    if (isHorizontal) {
                        val hChartLeft = if (isRtl) topSpacePx else horizontalCatLabelSpacePx
                        val hChartRight = if (isRtl) canvasWidth - horizontalCatLabelSpacePx else canvasWidth - topSpacePx
                        val hChartTop = horizontalTopPadPx
                        val hChartBottom = canvasHeight - bottomSpacePx
                        val hChartWidth = hChartRight - hChartLeft
                        val hChartHeight = hChartBottom - hChartTop

                        val safeSpacing = style.barSpacingFactor.coerceIn(0f, 0.9f)
                        val totalSpacing = hChartHeight * safeSpacing
                        val barThickness = (hChartHeight - totalSpacing) / entries.size
                        val barGap = totalSpacing / (entries.size + 1)

                        var foundEntry: BarEntry? = null
                        for (i in entries.indices) {
                            val yOff = hChartTop + barGap + i * (barThickness + barGap)
                            val animVal = animationEngine.heightAnimatables[entries[i].id]?.value ?: 0f
                            val barLen = (animVal / maxBarValue) * hChartWidth
                            val xOff = if (isRtl) hChartRight - barLen else hChartLeft

                            if (touchPos.y in (yOff - 15f)..(yOff + barThickness + 15f) &&
                                touchPos.x in (xOff - 15f)..(xOff + barLen + 15f)
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
                                currentOnBarSelected(foundEntry)
                            }
                        }
                        return
                    }

                    val chartWidth = canvasWidth - yAxisWidthPx
                    val chartHeight = canvasHeight - bottomSpacePx - topSpacePx

                    val safeSpacingFactor = style.barSpacingFactor.coerceIn(0f, 0.9f)
                    val totalSpacing = chartWidth * safeSpacingFactor
                    val barWidth = (chartWidth - totalSpacing) / entries.size
                    val barSpacing = totalSpacing / (entries.size + 1)

                    var foundEntry: BarEntry? = null

                    for (i in entries.indices) {
                        val ltrStartX = yAxisWidthPx + barSpacing + i * (barWidth + barSpacing)
                        val startX = if (isRtl) canvasWidth - ltrStartX - barWidth else ltrStartX
                        val endX = startX + barWidth

                        val currentAnimatedValue =
                            animationEngine.heightAnimatables[entries[i].id]?.value ?: 0f
                        val targetHeight = (currentAnimatedValue / maxBarValue) * chartHeight
                        val startY = canvasHeight - bottomSpacePx - targetHeight
                        val endY = canvasHeight - bottomSpacePx

                        if (touchPos.x in (startX - 15f)..(endX + 15f) && touchPos.y in (startY - 15f)..(endY + 15f)) {
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
            val hCatSpace = horizontalCatLabelSpacePx
            val hValEndSpace = topSpacePx
            val hChartLeft = if (isRtl) hValEndSpace else hCatSpace
            val hChartRight = if (isRtl) size.width - hCatSpace else size.width - hValEndSpace
            val hChartTop = horizontalTopPadPx
            val hChartBottom = size.height - bottomSpacePx
            val hChartWidth = hChartRight - hChartLeft
            val hChartHeight = hChartBottom - hChartTop

            if (axisConfig.showYAxis || axisConfig.showGridLines) {
                for (i in 0..axisConfig.yAxisSteps) {
                    val ratio = i.toFloat() / axisConfig.yAxisSteps
                    val gridX = if (isRtl) hChartRight - hChartWidth * ratio
                    else hChartLeft + hChartWidth * ratio

                    if (axisConfig.showGridLines) {
                        drawLine(
                            color = axisConfig.axisColor,
                            start = Offset(gridX, hChartTop),
                            end = Offset(gridX, hChartBottom),
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
                start = Offset(zeroX, hChartTop),
                end = Offset(zeroX, hChartBottom),
                strokeWidth = 2.dp.toPx()
            )

            val barCount = entries.size
            val hTotalSpacing = hChartHeight * style.barSpacingFactor.coerceIn(0f, 0.9f)
            val barThickness = (hChartHeight - hTotalSpacing) / barCount
            val barGap = hTotalSpacing / (barCount + 1)

            entries.forEachIndexed { index, entry ->
                val animVal = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
                val selAlpha = animationEngine.selectionAlphaAnimatables[entry.id]?.value ?: 1f
                val barLen = (animVal / maxBarValue) * hChartWidth

                val yOff = hChartTop + barGap + index * (barThickness + barGap)
                val xOff = if (isRtl) hChartRight - barLen else hChartLeft

                if (barLen > 0f) {
                    barPath.rewind()
                    val cr = CornerRadius(cornerRadiusPx, cornerRadiusPx)
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
                        Brush.horizontalGradient(colors, startX = brushStart, endX = brushEnd)
                    }

                    drawPath(path = barPath, brush = brush, alpha = selAlpha)
                }

                xLabelLayouts[entry.id]?.let { layout ->
                    val lx = if (isRtl) hChartRight + 8.dp.toPx()
                    else hChartLeft - layout.size.width - 8.dp.toPx()
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(lx, yOff + (barThickness - layout.size.height) / 2),
                        alpha = selAlpha
                    )
                }

                if (style.showFloatingValues && animVal > 1f && selectedEntry == null) {
                    val vi = animVal.toInt()
                    val vl = valueTextCache.getOrPut(vi) {
                        textMeasurer.measure(vi.toString(), centeredValueTextStyle)
                    }
                    val prog = if (entry.y > 0f) (animVal / entry.y).coerceIn(0f, 1f) else 0f
                    val vx = if (isRtl) xOff - vl.size.width - 6.dp.toPx()
                    else xOff + barLen + 6.dp.toPx()
                    drawText(
                        textLayoutResult = vl,
                        topLeft = Offset(vx, yOff + (barThickness - vl.size.height) / 2),
                        alpha = prog
                    )
                }
            }

            selectedEntry?.let { entry ->
                val animVal = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
                if (animVal > entry.y * 0.9f) {
                    val targetLen = (entry.y / maxBarValue) * hChartWidth
                    val idx = entryIndexMap[entry.id] ?: return@let
                    val yOff = hChartTop + barGap + idx * (barThickness + barGap)
                    val xOff = if (isRtl) hChartRight - targetLen else hChartLeft

                    with(selectionRenderer) {
                        drawSelection(
                            entry = entry,
                            barTopLeft = Offset(xOff, yOff),
                            barSize = Size(targetLen, barThickness),
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
        val totalSpacing = chartWidth * style.barSpacingFactor.coerceIn(0f, 0.9f)
        val barWidth = (chartWidth - totalSpacing) / barCount
        val barSpacing = totalSpacing / (barCount + 1)

        entries.forEachIndexed { index, entry ->
            val currentAnimatedValue = animationEngine.heightAnimatables[entry.id]?.value ?: 0f
            val currentSelectionAlpha =
                animationEngine.selectionAlphaAnimatables[entry.id]?.value ?: 1f
            val targetHeight = (currentAnimatedValue / maxBarValue) * chartHeight

            val ltrXOffset = yAxisWidthPx + barSpacing + index * (barWidth + barSpacing)
            val xOffset = if (isRtl) size.width - ltrXOffset - barWidth else ltrXOffset
            val yOffset = size.height - bottomSpacePx - targetHeight

            if (targetHeight > 0f) {
                barPath.rewind()
                barPath.addRoundRect(
                    RoundRect(
                        left = xOffset,
                        top = yOffset,
                        right = xOffset + barWidth,
                        bottom = yOffset + targetHeight,
                        topLeftCornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        topRightCornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
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
                    textMeasurer.measure(valueInt.toString(), centeredValueTextStyle)
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
                val ltrXOffset = yAxisWidthPx + barSpacing + targetIndex * (barWidth + barSpacing)
                val xOffset = if (isRtl) size.width - ltrXOffset - barWidth else ltrXOffset
                val yOffset = size.height - bottomSpacePx - targetHeight

                with(selectionRenderer) {
                    drawSelection(
                        entry = entry,
                        barTopLeft = Offset(xOffset, yOffset),
                        barSize = Size(barWidth, targetHeight),
                        orientation = orientation,
                        textMeasurer = textMeasurer,
                        tooltipCache = selectionCache
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. DEMO IMPLEMENTATION
// ==========================================

@Composable
fun BarChartDemoScreen() {
    val oceanGradient = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D))
    val emeraldGradient = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    val sunsetGradient = listOf(Color(0xFFFF512F), Color(0xFFF09819), Color(0xFFFFB75E))
    val amethystGradient = listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))

    var orientation by remember { mutableStateOf(BarOrientation.Horizontal) }

    var dataSet by remember {
        mutableStateOf(
            BarDataSet(
                entries = listOf(
                    BarEntry("JAN", "Jan", 45f, gradientColors = oceanGradient),
                    BarEntry("FEB", "Feb", 80f, gradientColors = sunsetGradient),
                    BarEntry("MAR", "Mar", 55f, gradientColors = amethystGradient),
                    BarEntry("APR", "Apr", 95f, gradientColors = sunsetGradient),
                    BarEntry("MAY", "May", 65f, gradientColors = emeraldGradient)
                ),
                contentDescription = "Q1 and Q2 Revenue Trends"
            )
        )
    }

    var selectedBarId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedBarData by remember {
        derivedStateOf { dataSet.entries.find { it.id == selectedBarId } }
    }

    val customPillRenderer = remember {
        TooltipSelectionRenderer(
            backgroundColor = Color(0xFF4F46E5),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            ),
            cornerRadius = 16.dp,
            horizontalPadding = 16.dp,
            verticalPadding = 8.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf(
                BarOrientation.Vertical to "Vertical",
                BarOrientation.Horizontal to "Horizontal"
            ).forEach { (orient, label) ->
                val selected = orientation == orient
                Button(
                    onClick = {
                        selectedBarId = null
                        orientation = orient
                        dataSet = dataSet.copy(
                            entries = dataSet.entries.map { entry ->
                                entry.copy(
                                    y = Random.nextInt(20, 110).toFloat(),
                                    gradientColors = listOf(
                                        sunsetGradient,
                                        oceanGradient,
                                        amethystGradient,
                                        emeraldGradient
                                    ).random()
                                )
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(0xFF111827) else Color.Transparent,
                        contentColor = if (selected) Color.White else Color(0xFF6B7280)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(Color.White, shape = RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Revenue Overview",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF111827)
                        )
                        Text(
                            text = if (selectedBarData != null) "Viewing metrics for ${selectedBarData?.xLabel}" else "Tap a bar to inspect metrics.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (selectedBarData != null) {
                        Text(
                            text = "$${selectedBarData?.y?.toInt()}k",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF111827)
                        )
                    }
                }

                BarChart(
                    dataSet = dataSet,
                    modifier = Modifier.fillMaxSize(),
                    orientation = orientation,
                    selectionRenderer = customPillRenderer,
                    selectedEntry = selectedBarData,
                    onBarSelected = { entry -> selectedBarId = entry?.id },
                    a11yConfig = A11yConfig(
                        chartDescriptionBuilder = { "Financial Revenue Chart for ${it.contentDescription}." },
                        barDescriptionBuilder = { "In ${it.xLabel}, revenue was $${it.y.toInt()} thousand dollars." },
                        selectedStateDescription = { entry ->
                            entry?.let { "You are inspecting ${it.xLabel}." }
                                ?: "Double tap and drag to explore metrics."
                        }
                    )
                )
            }
        }

        // The HorizontalDivider and one Spacer were removed here.
        // A single Spacer is kept to maintain a clean gap between the chart and button.
        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                val newEntries = dataSet.entries.map { entry ->
                    entry.copy(
                        y = Random.nextInt(20, 110).toFloat(),
                        gradientColors = listOf(
                            sunsetGradient,
                            oceanGradient,
                            amethystGradient,
                            emeraldGradient
                        ).random()
                    )
                }
                dataSet = dataSet.copy(entries = newEntries)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text("Update Data", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}