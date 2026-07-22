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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ==========================================
// 1. DATA MODELS & CONFIG
// ==========================================

/**
 * Defines a single axis (spoke) on the radar chart.
 *
 * @param id Stable identifier used internally for mapping series values and
 *   driving animations. Must be unique within the dataset.
 * @param label Human-readable name rendered at the axis tip.
 * @param maxValue Upper bound for this axis. Series values are normalized
 *   against this (value / maxValue). Defaults to 100.
 */
@Immutable
data class RadarAxis(
    val id: String,
    val label: String,
    val maxValue: Float = 100f
)

/**
 * A single data series rendered as a filled polygon on the radar chart.
 *
 * @param id Stable identifier for animation tracking. Must be unique.
 * @param label Human-readable name shown in tooltips and accessibility.
 * @param values Map of axisId to raw value. Missing axes default to 0.
 * @param color Primary color for fill, stroke, and data point dots.
 * @param fillAlpha Opacity applied to the polygon fill. 0 = no fill, 1 = opaque.
 * @param strokeWidth Width of the polygon outline.
 */
@Immutable
data class RadarSeries(
    val id: String,
    val label: String,
    val values: Map<String, Float>,
    val color: Color = Color(0xFF6366F1),
    val fillAlpha: Float = 0.2f,
    val strokeWidth: Dp = 2.dp
)

/**
 * Holds the full dataset: axes and one or more series.
 *
 * @param axes The spokes of the radar. Order determines drawing order clockwise
 *   from [RadarChartStyle.startAngle]. Minimum 3.
 * @param series The data polygons. Later entries draw on top of earlier ones.
 * @param contentDescription Root accessibility label for the chart.
 */
@Immutable
data class RadarDataSet(
    val axes: List<RadarAxis>,
    val series: List<RadarSeries>,
    val contentDescription: String = "Radar Chart"
)

/** Grid ring shape. */
enum class RadarGridStyle {
    /** Concentric polygons matching the number of axes. */
    Polygon,

    /** Concentric circles. */
    Circular
}

/**
 * Visual tuning for the radar chart.
 *
 * The chart size is resolved in this order:
 * 1. If [outerRadius] is specified (not [Dp.Unspecified]), it is used as the exact outer radius.
 * 2. Otherwise, the chart auto-fits to the Canvas using [fillFraction] as a 0..1 ratio
 *    of available space minus label protrusion.
 *
 * @param startAngle Angle in degrees where the first axis points. -90 = 12 o'clock.
 * @param gridLevels Number of concentric grid rings. 0 disables the grid.
 * @param gridStyle Shape of the grid rings.
 * @param gridColor Color for grid rings.
 * @param gridStrokeWidth Width of grid ring lines.
 * @param axisColor Color for axis spoke lines.
 * @param axisStrokeWidth Width of axis spoke lines.
 * @param labelColor Color for axis label text.
 * @param labelFontSize Font size for axis labels.
 * @param labelPadding Distance from axis tip to label anchor.
 * @param fillFraction 0..1 ratio of available space used as the chart radius.
 *   Only applies when [outerRadius] is [Dp.Unspecified].
 * @param outerRadius Explicit outer radius. When [Dp.Unspecified], the chart
 *   auto-sizes using [fillFraction].
 * @param minSize Minimum intrinsic size for the chart. Applied via
 *   [Modifier.defaultMinSize] so the chart renders even if the caller provides
 *   no explicit sizing.
 * @param dotRadius Size of data point dots on each vertex.
 * @param showDots Whether to render data point dots at each vertex.
 * @param showLabels Whether to render axis labels at the spoke tips.
 * @param unselectedAlpha Alpha applied to non-selected series when a selection is active.
 */
@Immutable
data class RadarChartStyle(
    val startAngle: Float = -90f,
    val gridLevels: Int = 5,
    val gridStyle: RadarGridStyle = RadarGridStyle.Polygon,
    val gridColor: Color = Color(0xFFE5E7EB),
    val gridStrokeWidth: Dp = 1.dp,
    val axisColor: Color = Color(0xFFD1D5DB),
    val axisStrokeWidth: Dp = 1.dp,
    val labelColor: Color = Color(0xFF6B7280),
    val labelFontSize: TextUnit = 12.sp,
    val labelPadding: Dp = 16.dp,
    val fillFraction: Float = 0.75f,
    val outerRadius: Dp = Dp.Unspecified,
    val minSize: Dp = 200.dp,
    val dotRadius: Dp = 4.dp,
    val showDots: Boolean = true,
    val showLabels: Boolean = true,
    val unselectedAlpha: Float = 0.3f
)

/**
 * Timing configuration for all radar chart animations.
 *
 * @param initialEntrySpec Drives the first appearance of each vertex (value animates from 0).
 * @param morphSpec Drives value changes on existing vertices.
 * @param selectionSpec Drives alpha changes on series selection/deselection.
 * @param startDelayMs Initial delay before the first vertex begins animating.
 * @param seriesStaggerMs Delay between successive series entry animations.
 * @param vertexStaggerMs Delay between successive vertex animations within a series.
 */
@Immutable
data class RadarAnimationConfig(
    val initialEntrySpec: AnimationSpec<Float> = tween(
        durationMillis = 800,
        easing = FastOutSlowInEasing
    ),
    val morphSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessLow
    ),
    val selectionSpec: AnimationSpec<Float> = tween(durationMillis = 200, easing = LinearEasing),
    val startDelayMs: Long = 80L,
    val seriesStaggerMs: Long = 150L,
    val vertexStaggerMs: Long = 40L
)

/**
 * Accessibility text builders for the radar chart.
 */
@Stable
data class RadarA11yConfig(
    val chartDescriptionBuilder: (RadarDataSet) -> String = { ds ->
        "Radar Chart representing ${ds.contentDescription}"
    },
    val seriesDescriptionBuilder: (RadarSeries, List<RadarAxis>) -> String = { series, axes ->
        val valueText = axes.joinToString(", ") { axis ->
            val v = series.values[axis.id] ?: 0f
            val pct = ((v / axis.maxValue) * 100).toInt()
            "${axis.label}: $pct%"
        }
        "${series.label} ($valueText)"
    },
    val selectedStateDescription: (RadarSeries?) -> String = { series ->
        series?.let { "Currently selected: ${it.label}." } ?: "No series selected."
    }
)

// ==========================================
// 2. SELECTION INDICATOR API
// ==========================================

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
            topLeft = Offset(tooltipX, tooltipY),
            size = Size(tooltipW, tooltipH),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(tooltipX + padPx, tooltipY + padPx)
        )
    }
}

// ==========================================
// 3. INTERNAL HELPERS
// ==========================================

/**
 * Pre-computed cosine/sine values for each axis angle.
 * Allocated once via [remember] and reused across every draw frame.
 */
@Stable
private class AxisTrigCache(val cosA: FloatArray, val sinA: FloatArray)

/**
 * Resolves the outer radius in pixels for the radar chart.
 */
private fun resolveRadarRadius(
    style: RadarChartStyle,
    canvasWidth: Float,
    canvasHeight: Float,
    labelSpace: Float,
    density: androidx.compose.ui.unit.Density
): Float {
    return if (style.outerRadius != Dp.Unspecified) {
        with(density) { style.outerRadius.toPx() }
    } else {
        val available = (min(canvasWidth, canvasHeight) / 2f) - labelSpace
        available * style.fillFraction.coerceIn(0.1f, 1f)
    }
}

// ==========================================
// 4. ANIMATION ENGINE
// ==========================================

/**
 * Manages per-vertex [Animatable] instances for value and per-series alpha.
 *
 * Vertex values are keyed as `"seriesId::axisId"`. Series alpha is keyed by `seriesId`.
 *
 * Lifecycle split:
 * - [syncAnimatables]: synchronous map housekeeping, called via [SideEffect].
 * - [launchEntryAnimations]: async vertex animations, called in [LaunchedEffect] scope.
 * - [launchSelectionAnimations]: async alpha animations, called in [LaunchedEffect] scope.
 */
@Stable
class RadarChartAnimationEngine {
    internal val valueAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    internal val alphaAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedKeys = mutableSetOf<String>()

    /**
     * Ensures Animatable instances exist for all current (series, axis) pairs
     * and removes stale ones. Synchronous and idempotent.
     */
    fun syncAnimatables(axes: List<RadarAxis>, series: List<RadarSeries>) {
        val activeValueKeys = mutableSetOf<String>()
        val activeSeriesIds = series.mapTo(mutableSetOf()) { it.id }

        series.forEach { s ->
            axes.forEach { a ->
                val key = "${s.id}::${a.id}"
                activeValueKeys.add(key)
                valueAnimatables.getOrPut(key) { Animatable(0f) }
            }
            alphaAnimatables.getOrPut(s.id) { Animatable(1f) }
        }

        valueAnimatables.keys.removeAll { it !in activeValueKeys }
        alphaAnimatables.keys.removeAll { it !in activeSeriesIds }
        initializedKeys.removeAll { it !in activeValueKeys }
    }

    /**
     * Launches staggered vertex animations. New vertices animate from 0 with
     * a cascading delay across series and axes. Existing vertices morph to
     * their new target value.
     */
    fun launchEntryAnimations(
        axes: List<RadarAxis>,
        series: List<RadarSeries>,
        config: RadarAnimationConfig,
        scope: CoroutineScope
    ) {
        series.forEachIndexed { seriesIndex, s ->
            axes.forEachIndexed { axisIndex, a ->
                val key = "${s.id}::${a.id}"
                val anim = valueAnimatables[key] ?: return@forEachIndexed
                val target = s.values[a.id] ?: 0f
                val isInitial = initializedKeys.add(key)

                scope.launch {
                    if (isInitial) {
                        val totalDelay = config.startDelayMs +
                                (seriesIndex * config.seriesStaggerMs) +
                                (axisIndex * config.vertexStaggerMs)
                        delay(totalDelay)
                        anim.animateTo(target, config.initialEntrySpec)
                    } else if (anim.targetValue != target) {
                        anim.animateTo(target, config.morphSpec)
                    }
                }
            }
        }
    }

    /**
     * Launches alpha animations to reflect the current selection state.
     * The selected series stays at full alpha; all others dim.
     */
    fun launchSelectionAnimations(
        series: List<RadarSeries>,
        selectedSeries: RadarSeries?,
        style: RadarChartStyle,
        config: RadarAnimationConfig,
        scope: CoroutineScope
    ) {
        series.forEach { s ->
            val alphaAnim = alphaAnimatables[s.id] ?: return@forEach
            val targetAlpha = if (selectedSeries != null && selectedSeries.id != s.id) {
                style.unselectedAlpha
            } else 1f

            if (alphaAnim.targetValue != targetAlpha) {
                scope.launch { alphaAnim.animateTo(targetAlpha, config.selectionSpec) }
            }
        }
    }
}

// ==========================================
// 5. THE CHART COMPONENT
// ==========================================

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
    onSeriesSelected: (RadarSeries?) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val axes = dataSet.axes
    val series = dataSet.series
    val animationEngine = remember { RadarChartAnimationEngine() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // ── Stable state refs for long-lived lambdas (pointerInput) ──
    val currentSelectedSeries by rememberUpdatedState(selectedSeries)
    val currentOnSeriesSelected by rememberUpdatedState(onSeriesSelected)
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
        if (count < 3) AxisTrigCache(FloatArray(0), FloatArray(0))
        else {
            val step = 360f / count
            val dir = if (isRtl) -1f else 1f
            AxisTrigCache(
                cosA = FloatArray(count) {
                    cos(Math.toRadians((style.startAngle + it * step * dir).toDouble())).toFloat()
                },
                sinA = FloatArray(count) {
                    sin(Math.toRadians((style.startAngle + it * step * dir).toDouble())).toFloat()
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

    val chartDescription = remember(dataSet, selectedSeries, a11yConfig) {
        buildString {
            append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
            series.forEach { s ->
                append(a11yConfig.seriesDescriptionBuilder(s, axes)).append(". ")
            }
            append(a11yConfig.selectedStateDescription(selectedSeries))
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
        animationEngine.launchEntryAnimations(axes, series, animationConfig, this)
    }

    // ── Selection animations ──
    LaunchedEffect(axes, series, selectedSeries) {
        animationEngine.launchSelectionAnimations(
            series, selectedSeries, style, animationConfig, this
        )
    }

    // Reusable Path objects (allocated once, reset per frame)
    val gridPath = remember { Path() }
    val seriesPath = remember { Path() }

    Canvas(
        modifier = modifier
            .defaultMinSize(minWidth = style.minSize, minHeight = style.minSize)
            .semantics(mergeDescendants = true) {
                contentDescription = chartDescription
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
                        activeStyle, size.width.toFloat(), size.height.toFloat(),
                        labelSpace, activeDensity
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
                        if (currentSelectedSeries?.id == closestSeries?.id) {
                            currentOnSeriesSelected(null)
                        } else {
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
        val center = Offset(cx, cy)

        val cosA = trigCache.cosA
        val sinA = trigCache.sinA
        if (cosA.size < axisCount) return@Canvas

        val labelSpace = if (style.showLabels) {
            style.labelPadding.toPx() + maxLabelDim / 2f
        } else 0f

        val chartRadius = resolveRadarRadius(
            style, size.width, size.height, labelSpace, density
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
                        drawPath(gridPath, style.gridColor, style = Stroke(gridStrokePx))
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
                end = Offset(cx + chartRadius * cosA[i], cy + chartRadius * sinA[i]),
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
                            cx + chartRadius * norm * cosA[i],
                            cy + chartRadius * norm * sinA[i]
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
                    cx + chartRadius * norm * cosA[i],
                    cy + chartRadius * norm * sinA[i]
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

// ==========================================
// 6. DEMO IMPLEMENTATION
// ==========================================

@Composable
fun RadarChartDemoScreen() {
    val defaultAxes = listOf(
        RadarAxis("atk", "Attack"),
        RadarAxis("def", "Defense"),
        RadarAxis("spd", "Speed"),
        RadarAxis("mag", "Magic"),
        RadarAxis("sta", "Stamina"),
        RadarAxis("lck", "Luck")
    )

    var dataSet by remember {
        mutableStateOf(
            RadarDataSet(
                axes = defaultAxes,
                series = listOf(
                    RadarSeries(
                        id = "warrior",
                        label = "Warrior",
                        values = mapOf(
                            "atk" to 88f, "def" to 82f, "spd" to 55f,
                            "mag" to 20f, "sta" to 75f, "lck" to 45f
                        ),
                        color = Color(0xFFEF4444),
                        fillAlpha = 0.15f
                    ),
                    RadarSeries(
                        id = "mage",
                        label = "Mage",
                        values = mapOf(
                            "atk" to 30f, "def" to 35f, "spd" to 60f,
                            "mag" to 95f, "sta" to 45f, "lck" to 70f
                        ),
                        color = Color(0xFF6366F1),
                        fillAlpha = 0.15f
                    ),
                    RadarSeries(
                        id = "rogue",
                        label = "Rogue",
                        values = mapOf(
                            "atk" to 65f, "def" to 30f, "spd" to 92f,
                            "mag" to 40f, "sta" to 50f, "lck" to 85f
                        ),
                        color = Color(0xFF10B981),
                        fillAlpha = 0.15f
                    )
                ),
                contentDescription = "Character Class Comparison"
            )
        )
    }

    var selectedSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSeriesData by remember {
        derivedStateOf {
            dataSet.series.find { it.id == selectedSeriesId }
        }
    }

    var isPolygonGrid by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .background(Color.White, shape = RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        "Character Stats",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = if (selectedSeriesData != null) {
                            "Viewing ${selectedSeriesData?.label} build"
                        } else "Tap a vertex to inspect a class.",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Legend
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    dataSet.series.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(10.dp)) {
                                drawCircle(color = s.color, radius = size.minDimension / 2f)
                            }
                            Text(
                                text = s.label,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 6.dp),
                                fontWeight = if (selectedSeriesData?.id == s.id) {
                                    FontWeight.Bold
                                } else FontWeight.Normal,
                                color = if (selectedSeriesData == null || selectedSeriesData?.id == s.id) {
                                    Color(0xFF374151)
                                } else Color(0xFFD1D5DB)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    RadarChart(
                        dataSet = dataSet,
                        modifier = Modifier.fillMaxSize(),
                        style = RadarChartStyle(
                            gridStyle = if (isPolygonGrid) {
                                RadarGridStyle.Polygon
                            } else RadarGridStyle.Circular,
                            fillFraction = 0.78f,
                            gridLevels = 5,
                            dotRadius = 5.dp
                        ),
                        selectedSeries = selectedSeriesData,
                        onSeriesSelected = { s -> selectedSeriesId = s?.id }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { isPolygonGrid = !isPolygonGrid },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (isPolygonGrid) "Circle Grid" else "Polygon Grid",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = {
                    val newSeries = dataSet.series.map { s ->
                        s.copy(
                            values = dataSet.axes.associate { a ->
                                a.id to Random.nextInt(15, 100).toFloat()
                            }
                        )
                    }
                    dataSet = dataSet.copy(series = newSeries)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = "Randomize",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = {
                    val currentSeries = dataSet.series
                    if (currentSeries.size >= 5) {
                        dataSet = dataSet.copy(series = currentSeries.dropLast(1))
                    } else {
                        val colors = listOf(
                            Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899)
                        )
                        val names = listOf("Paladin", "Ranger", "Bard")
                        val index = currentSeries.size - 3
                        val newId = "class_${currentSeries.size}"
                        val newSeries = RadarSeries(
                            id = newId,
                            label = names.getOrElse(index) { "Class ${currentSeries.size + 1}" },
                            values = dataSet.axes.associate { a ->
                                a.id to Random.nextInt(20, 95).toFloat()
                            },
                            color = colors.getOrElse(index) { Color(0xFF64748B) },
                            fillAlpha = 0.15f
                        )
                        dataSet = dataSet.copy(series = currentSeries + newSeries)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (dataSet.series.size >= 5) "Remove" else "Add Class",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}