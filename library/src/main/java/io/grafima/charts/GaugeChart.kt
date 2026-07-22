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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ==========================================
// 1. DATA MODELS & CONFIG
// ==========================================

/**
 * A colored segment on the gauge arc representing a value range.
 *
 * @param id Stable identifier.
 * @param label Human-readable zone name (e.g. "Safe", "Warning", "Critical").
 *   Used by the default accessibility description builder.
 * @param range The value range this zone covers. Must be within min..max.
 * @param color Solid fill color for the arc segment. Ignored when [gradientColors]
 *   has 2+ entries.
 * @param gradientColors When set with 2+ colors, renders this zone as a sweep gradient
 *   along the arc instead of a solid color. The gradient interpolates across the
 *   zone's angular range. When empty, falls back to [color].
 */
@Immutable
data class GaugeZone(
    val id: String,
    val label: String = "",
    val range: ClosedFloatingPointRange<Float>,
    val color: Color,
    val gradientColors: List<Color> = emptyList()
)

/**
 * Configuration for tick marks and their labels.
 *
 * @param majorTickCount Number of major tick intervals (not marks). 10 produces
 *   11 marks at 0%, 10%, 20%, ... 100%.
 * @param minorTicksPerMajor Number of minor ticks between each pair of major ticks.
 * @param majorTickLength Length of major tick lines, extending inward from the arc.
 * @param minorTickLength Length of minor tick lines.
 * @param majorTickWidth Stroke width for major ticks.
 * @param minorTickWidth Stroke width for minor ticks.
 * @param majorTickColor Color for major ticks.
 * @param minorTickColor Color for minor ticks.
 * @param showLabels Whether to draw value labels at each major tick.
 * @param labelColor Color for tick labels.
 * @param labelFontSize Font size for tick labels.
 * @param labelPadding Distance between the arc outer edge and label anchor.
 * @param labelFormatter Transforms a Float value into the label string.
 */
@Immutable
data class GaugeTickConfig(
    val majorTickCount: Int = 10,
    val minorTicksPerMajor: Int = 4,
    val majorTickLength: Dp = 10.dp,
    val minorTickLength: Dp = 5.dp,
    val majorTickWidth: Dp = 2.dp,
    val minorTickWidth: Dp = 1.dp,
    val majorTickColor: Color = Color(0xFF374151),
    val minorTickColor: Color = Color(0xFF9CA3AF),
    val showLabels: Boolean = true,
    val labelColor: Color = Color(0xFF6B7280),
    val labelFontSize: TextUnit = 11.sp,
    val labelPadding: Dp = 8.dp,
    val labelFormatter: (Float) -> String = { it.toInt().toString() }
)

/** Visual style for the gauge needle. */
enum class GaugeNeedleStyle {
    /** Tapered triangle: wide at the center hub, sharp at the tip. */
    Tapered,

    /** Thin rounded line from center hub to tip. */
    Line
}

/**
 * Configuration for the gauge needle.
 *
 * @param style Visual shape of the needle.
 * @param color Fill (or stroke) color for the needle body.
 * @param lengthFraction Needle length as a fraction of the gauge radius. 0.85 = 85%.
 * @param tailFraction Small counter-weight tail behind center, as a fraction of radius.
 * @param baseRadius Radius of the center hub circle that covers the needle base.
 * @param baseColor Color of the center hub circle.
 * @param width Needle width at the base (for [GaugeNeedleStyle.Tapered]) or
 *   stroke width (for [GaugeNeedleStyle.Line]).
 */
@Immutable
data class GaugeNeedleConfig(
    val style: GaugeNeedleStyle = GaugeNeedleStyle.Tapered,
    val color: Color = Color(0xFFDC2626),
    val lengthFraction: Float = 0.80f,
    val tailFraction: Float = 0.12f,
    val baseRadius: Dp = 10.dp,
    val baseColor: Color = Color(0xFF374151),
    val width: Dp = 4.dp
)

/**
 * Visual tuning for the gauge chart.
 *
 * Common arc presets:
 * - **3/4 gauge** (default): startAngle=135, sweepAngle=270 (opening at bottom)
 * - **Semi-circle**: startAngle=180, sweepAngle=180
 * - **Full circle**: startAngle=-90, sweepAngle=360
 *
 * @param startAngle Angle in degrees where the arc begins. 135 = 7:30 position.
 * @param sweepAngle Total angular sweep of the arc. 270 = three-quarter circle.
 * @param arcWidth Thickness of the gauge arc band.
 * @param trackColor Background track color (visible behind zones and value arc).
 * @param arcGradientColors When set with 2+ colors, draws a single sweep gradient
 *   across the entire arc instead of individual zones. The gradient interpolates
 *   from the first color at the arc start to the last color at the arc end.
 *   Takes priority over zones when non-empty.
 * @param showValueArc Draw a colored arc from min to the current animated value.
 * @param valueArcColor Color for the value arc.
 * @param valueArcWidth Width of the value arc. Defaults to [arcWidth].
 * @param fillFraction 0..1 ratio of available space used as the gauge radius.
 * @param outerRadius Explicit outer radius override.
 * @param minSize Minimum intrinsic chart size.
 * @param centerContentOffset Vertical offset for the center content slot.
 *   Positive values push the content downward, away from the needle hub.
 *   For a 270 sweep, values around 40-56 dp work well.
 */
@Immutable
data class GaugeChartStyle(
    val startAngle: Float = 135f,
    val sweepAngle: Float = 270f,
    val arcWidth: Dp = 20.dp,
    val trackColor: Color = Color(0xFFE5E7EB),
    val arcGradientColors: List<Color> = emptyList(),
    val showValueArc: Boolean = false,
    val valueArcColor: Color = Color(0xFF4F46E5),
    val valueArcWidth: Dp = Dp.Unspecified,
    val fillFraction: Float = 0.85f,
    val outerRadius: Dp = Dp.Unspecified,
    val minSize: Dp = 200.dp,
    val centerContentOffset: Dp = 48.dp
)

/**
 * Animation configuration for the gauge needle.
 *
 * The default uses an underdamped spring (dampingRatio < 1.0) that causes the
 * needle to overshoot its target and oscillate before settling.
 *
 * @param needleSpec Spring or tween spec driving the needle.
 * @param initialDelayMs Delay before the needle begins its first animation.
 */
@Immutable
data class GaugeAnimationConfig(
    val needleSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.45f,
        stiffness = Spring.StiffnessLow
    ),
    val initialDelayMs: Long = 200L
)

/**
 * Accessibility text builders for the gauge.
 */
@Stable
data class GaugeA11yConfig(
    val descriptionBuilder: (
        value: Float, minValue: Float, maxValue: Float, zones: List<GaugeZone>
    ) -> String = { value, minValue, maxValue, zones ->
        val pct = (((value - minValue) / (maxValue - minValue)) * 100).toInt()
        val activeZone = zones.find { value in it.range }
        buildString {
            append("Gauge at $pct percent. Value: ${value.toInt()} of ${maxValue.toInt()}.")
            activeZone?.let { append(" Zone: ${it.label}.") }
        }
    }
)

// ==========================================
// 2. INTERNAL HELPERS
// ==========================================

/** Pre-computed trig values for all tick mark positions. */
@Stable
private class TickTrigData(
    val majorCos: FloatArray,
    val majorSin: FloatArray,
    val minorCos: FloatArray,
    val minorSin: FloatArray
)

/** Resolves the gauge outer radius in pixels. */
private fun resolveGaugeRadius(
    style: GaugeChartStyle,
    canvasWidth: Float,
    canvasHeight: Float,
    tickLabelSpace: Float,
    density: androidx.compose.ui.unit.Density
): Float {
    return if (style.outerRadius != Dp.Unspecified) {
        with(density) { style.outerRadius.toPx() }
    } else {
        val available = (min(canvasWidth, canvasHeight) / 2f) - tickLabelSpace
        available * style.fillFraction.coerceIn(0.1f, 1f)
    }
}

/** Maps a value to a needle angle in degrees, accounting for RTL. */
private fun valueToAngle(
    value: Float,
    minValue: Float,
    maxValue: Float,
    startAngle: Float,
    sweepAngle: Float,
    isRtl: Boolean
): Float {
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val directed = if (isRtl) 1f - fraction else fraction
    return startAngle + sweepAngle * directed
}

/**
 * Computes [Brush.sweepGradient] color stops that map [colors] precisely to
 * the angular range [startAngle]..[startAngle + sweepAngle]. Handles wrap-around
 * past 360 correctly. Returns null if fewer than 2 colors.
 */
private fun computeArcGradientStops(
    colors: List<Color>,
    startAngle: Float,
    sweepAngle: Float
): Array<Pair<Float, Color>>? {
    if (colors.size < 2) return null
    val startFrac = ((startAngle % 360f + 360f) % 360f) / 360f
    val sweepFrac = sweepAngle / 360f
    return colors.mapIndexed { i, color ->
        val t = i.toFloat() / (colors.size - 1)
        var pos = startFrac + t * sweepFrac
        if (pos > 1f) pos -= 1f
        pos to color
    }.sortedBy { it.first }.toTypedArray()
}

// ==========================================
// 3. THE CHART COMPONENT
// ==========================================

/**
 * A composable gauge/speedometer chart with spring-physics needle animation,
 * configurable color zones with gradient support, tick marks, RTL mirroring,
 * accessibility, and a center content slot.
 *
 * Usage:
 * ```
 * var speed by remember { mutableFloatStateOf(42f) }
 *
 * GaugeChart(
 *     value = speed,
 *     minValue = 0f,
 *     maxValue = 100f,
 *     modifier = Modifier.size(300.dp),
 *     zones = listOf(
 *         GaugeZone("safe", "Safe", 0f..40f, Color.Green),
 *         GaugeZone("warn", "Warning", 40f..70f, Color.Yellow),
 *         GaugeZone("danger", "Danger", 70f..100f, Color.Red)
 *     ),
 *     centerContent = {
 *         Text("${speed.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
 *     }
 * )
 * ```
 *
 * Gradient arc (instead of zones):
 * ```
 * GaugeChart(
 *     value = 65f,
 *     style = GaugeChartStyle(
 *         arcGradientColors = listOf(Color.Green, Color.Yellow, Color.Red)
 *     )
 * )
 * ```
 *
 * @param value The current gauge reading. Animated with spring physics.
 * @param minValue Lower bound of the gauge scale.
 * @param maxValue Upper bound of the gauge scale.
 * @param zones Optional colored arc segments. Overridden by
 *   [GaugeChartStyle.arcGradientColors] when set.
 * @param style Visual configuration for the arc geometry, colors, and gradient.
 * @param tickConfig Tick mark and label configuration.
 * @param needleConfig Needle shape, color, and sizing.
 * @param animationConfig Spring spec for the needle animation.
 * @param a11yConfig Accessibility label builders.
 * @param centerContent Optional composable rendered below the gauge hub.
 *   Offset controlled by [GaugeChartStyle.centerContentOffset].
 */
@Composable
fun GaugeChart(
    value: Float,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 100f,
    zones: List<GaugeZone> = emptyList(),
    style: GaugeChartStyle = GaugeChartStyle(),
    tickConfig: GaugeTickConfig = GaugeTickConfig(),
    needleConfig: GaugeNeedleConfig = GaugeNeedleConfig(),
    animationConfig: GaugeAnimationConfig = GaugeAnimationConfig(),
    a11yConfig: GaugeA11yConfig = GaugeA11yConfig(),
    centerContent: @Composable (() -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // ── Needle animation ──
    val targetAngle =
        remember(value, minValue, maxValue, style.startAngle, style.sweepAngle, isRtl) {
            valueToAngle(
                value = value,
                minValue = minValue,
                maxValue = maxValue,
                startAngle = style.startAngle,
                sweepAngle = style.sweepAngle,
                isRtl = isRtl
            )
        }
    val needleAnimatable = remember { Animatable(style.startAngle) }
    LaunchedEffect(targetAngle) {
        needleAnimatable.animateTo(targetAngle, animationConfig.needleSpec)
    }

    // ── Pre-computed tick trig (zero trig in draw) ──
    val tickTrig = remember(
        tickConfig.majorTickCount, tickConfig.minorTicksPerMajor,
        style.startAngle, style.sweepAngle
    ) {
        val majors = tickConfig.majorTickCount
        val minorsPerMajor = tickConfig.minorTicksPerMajor
        val majorAnglesRad = DoubleArray(majors + 1) { i ->
            Math.toRadians((style.startAngle + style.sweepAngle * (i.toFloat() / majors)).toDouble())
        }
        val minorAnglesList = mutableListOf<Double>()
        val majorStep = style.sweepAngle / majors
        val minorStep = majorStep / (minorsPerMajor + 1)
        for (m in 0 until majors) {
            for (n in 1..minorsPerMajor) {
                minorAnglesList.add(
                    Math.toRadians((style.startAngle + m * majorStep + n * minorStep).toDouble())
                )
            }
        }
        TickTrigData(
            majorCos = FloatArray(majors + 1) { cos(majorAnglesRad[it]).toFloat() },
            majorSin = FloatArray(majors + 1) { sin(majorAnglesRad[it]).toFloat() },
            minorCos = FloatArray(minorAnglesList.size) { cos(minorAnglesList[it]).toFloat() },
            minorSin = FloatArray(minorAnglesList.size) { sin(minorAnglesList[it]).toFloat() }
        )
    }

    // ── Pre-measured tick labels ──
    val tickLabelStyle = remember(tickConfig.labelColor, tickConfig.labelFontSize) {
        TextStyle(
            color = tickConfig.labelColor,
            fontSize = tickConfig.labelFontSize,
            fontWeight = FontWeight.Medium
        )
    }
    val tickLabelLayouts: List<TextLayoutResult> = remember(
        minValue, maxValue, tickConfig.majorTickCount,
        tickConfig.showLabels, tickConfig.labelFormatter, tickLabelStyle
    ) {
        if (!tickConfig.showLabels) emptyList()
        else {
            val step = (maxValue - minValue) / tickConfig.majorTickCount
            (0..tickConfig.majorTickCount).map { i ->
                textMeasurer.measure(
                    text = tickConfig.labelFormatter(minValue + i * step),
                    style = tickLabelStyle,
                    maxLines = 1
                )
            }
        }
    }
    val maxTickLabelDim = remember(tickLabelLayouts) {
        if (tickLabelLayouts.isEmpty()) 0f
        else tickLabelLayouts.maxOf { max(it.size.width, it.size.height) }.toFloat()
    }

    // ── Pre-computed gradient stops (zero allocation in draw for the stops) ──
    val globalGradientStops =
        remember(style.arcGradientColors, style.startAngle, style.sweepAngle) {
            computeArcGradientStops(
                colors = style.arcGradientColors,
                startAngle = style.startAngle,
                sweepAngle = style.sweepAngle
            )
        }

    val zoneGradientStops: Map<String, Array<Pair<Float, Color>>> = remember(
        zones, style.startAngle, style.sweepAngle, minValue, maxValue, isRtl
    ) {
        val range = maxValue - minValue
        if (range <= 0f) return@remember emptyMap()
        zones.filter { it.gradientColors.size >= 2 }.associate { zone ->
            val sf = ((zone.range.start - minValue) / range).coerceIn(0f, 1f)
            val ef = ((zone.range.endInclusive - minValue) / range).coerceIn(0f, 1f)
            val (startF, endF) = if (isRtl) (1f - ef) to (1f - sf) else sf to ef
            val zoneStartAngle = style.startAngle + style.sweepAngle * startF
            val zoneSweep = style.sweepAngle * (endF - startF)
            zone.id to (computeArcGradientStops(
                colors = zone.gradientColors,
                startAngle = zoneStartAngle,
                sweepAngle = zoneSweep
            ) ?: emptyArray())
        }.filterValues { it.isNotEmpty() }
    }

    // ── Accessibility ──
    val chartDescription = remember(value, minValue, maxValue, zones, a11yConfig) {
        a11yConfig.descriptionBuilder(value, minValue, maxValue, zones)
    }
    val clampedValue = value.coerceIn(minValue, maxValue)

    // ── Reusable Path ──
    val needlePath = remember { Path() }

    Box(
        modifier = modifier.defaultMinSize(minWidth = style.minSize, minHeight = style.minSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    contentDescription = chartDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = clampedValue,
                        range = minValue..maxValue
                    )
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(x = cx, y = cy)

            val tickLabelSpace = if (tickConfig.showLabels) {
                tickConfig.labelPadding.toPx() + maxTickLabelDim / 2f
            } else 0f

            val gaugeRadius =
                resolveGaugeRadius(
                    style = style,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    tickLabelSpace = tickLabelSpace,
                    density = density
                )
            val arcWidthPx = style.arcWidth.toPx()
            val arcCenterRadius = gaugeRadius - arcWidthPx / 2f
            val arcRect = Size(width = arcCenterRadius * 2, height = arcCenterRadius * 2)
            val arcTopLeft = Offset(x = cx - arcCenterRadius, y = cy - arcCenterRadius)

            // ── 1. Background track ──
            drawArc(
                color = style.trackColor,
                startAngle = style.startAngle,
                sweepAngle = style.sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcRect,
                style = Stroke(width = arcWidthPx, cap = StrokeCap.Round)
            )

            // ── 2. Arc fill: global gradient > zones > nothing ──
            val range = maxValue - minValue

            if (globalGradientStops != null) {
                // Global sweep gradient across the full arc
                val brush = Brush.sweepGradient(
                    *globalGradientStops,
                    center = center
                )
                drawArc(
                    brush = brush,
                    startAngle = style.startAngle,
                    sweepAngle = style.sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcRect,
                    style = Stroke(width = arcWidthPx, cap = StrokeCap.Round)
                )
            } else if (range > 0f) {
                // Per-zone arcs
                zones.forEach { zone ->
                    val zoneStartFrac = ((zone.range.start - minValue) / range).coerceIn(0f, 1f)
                    val zoneEndFrac =
                        ((zone.range.endInclusive - minValue) / range).coerceIn(0f, 1f)
                    val (sf, ef) = if (isRtl) (1f - zoneEndFrac) to (1f - zoneStartFrac) else zoneStartFrac to zoneEndFrac
                    val zoneStart = style.startAngle + style.sweepAngle * sf
                    val zoneSweep = style.sweepAngle * (ef - sf)

                    if (zoneSweep > 0f) {
                        val gradStops = zoneGradientStops[zone.id]
                        if (gradStops != null) {
                            drawArc(
                                brush = Brush.sweepGradient(*gradStops, center = center),
                                startAngle = zoneStart,
                                sweepAngle = zoneSweep,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcRect,
                                style = Stroke(width = arcWidthPx, cap = StrokeCap.Butt)
                            )
                        } else {
                            drawArc(
                                color = zone.color,
                                startAngle = zoneStart,
                                sweepAngle = zoneSweep,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcRect,
                                style = Stroke(width = arcWidthPx, cap = StrokeCap.Butt)
                            )
                        }
                    }
                }
            }

            // ── 3. Value arc (optional) ──
            if (style.showValueArc && range > 0f) {
                val animAngle = needleAnimatable.value
                val valueSweep = (animAngle - style.startAngle).coerceIn(0f, style.sweepAngle)
                val valueArcW =
                    if (style.valueArcWidth != Dp.Unspecified) style.valueArcWidth.toPx() else arcWidthPx
                val valueArcCR = gaugeRadius - valueArcW / 2f
                drawArc(
                    color = style.valueArcColor,
                    startAngle = style.startAngle,
                    sweepAngle = valueSweep,
                    useCenter = false,
                    topLeft = Offset(x = cx - valueArcCR, y = cy - valueArcCR),
                    size = Size(width = valueArcCR * 2, height = valueArcCR * 2),
                    style = Stroke(width = valueArcW, cap = StrokeCap.Round)
                )
            }

            // ── 4. Tick marks ──
            val majorTickLenPx = tickConfig.majorTickLength.toPx()
            val minorTickLenPx = tickConfig.minorTickLength.toPx()
            val majorTickWidthPx = tickConfig.majorTickWidth.toPx()
            val minorTickWidthPx = tickConfig.minorTickWidth.toPx()
            val minorInnerR = gaugeRadius - minorTickLenPx
            val majorInnerR = gaugeRadius - majorTickLenPx

            for (i in tickTrig.minorCos.indices) {
                drawLine(
                    color = tickConfig.minorTickColor,
                    start = Offset(
                        x = cx + gaugeRadius * tickTrig.minorCos[i],
                        y = cy + gaugeRadius * tickTrig.minorSin[i]
                    ),
                    end = Offset(
                        x = cx + minorInnerR * tickTrig.minorCos[i],
                        y = cy + minorInnerR * tickTrig.minorSin[i]
                    ),
                    strokeWidth = minorTickWidthPx
                )
            }
            for (i in tickTrig.majorCos.indices) {
                drawLine(
                    color = tickConfig.majorTickColor,
                    start = Offset(
                        x = cx + gaugeRadius * tickTrig.majorCos[i],
                        y = cy + gaugeRadius * tickTrig.majorSin[i]
                    ),
                    end = Offset(
                        x = cx + majorInnerR * tickTrig.majorCos[i],
                        y = cy + majorInnerR * tickTrig.majorSin[i]
                    ),
                    strokeWidth = majorTickWidthPx
                )
            }

            // ── 5. Tick labels ──
            if (tickConfig.showLabels && tickLabelLayouts.isNotEmpty()) {
                val labelPadPx = tickConfig.labelPadding.toPx()
                val labelR = gaugeRadius + labelPadPx
                val count = min(tickTrig.majorCos.size, tickLabelLayouts.size)

                for (i in 0 until count) {
                    val idx = if (isRtl) (count - 1 - i) else i
                    val layout = tickLabelLayouts[idx]
                    val cosV = tickTrig.majorCos[i]
                    val sinV = tickTrig.majorSin[i]
                    val lx = cx + labelR * cosV
                    val ly = cy + labelR * sinV
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = lx - layout.size.width / 2f + cosV * layout.size.width / 2f,
                            y = ly - layout.size.height / 2f + sinV * layout.size.height / 2f
                        )
                    )
                }
            }

            // ── 6. Needle ──
            val animatedAngle = needleAnimatable.value
            val needleAngleRad = Math.toRadians(animatedAngle.toDouble())
            val cosN = cos(needleAngleRad).toFloat()
            val sinN = sin(needleAngleRad).toFloat()
            val needleLength = gaugeRadius * needleConfig.lengthFraction
            val tailLength = gaugeRadius * needleConfig.tailFraction
            val needleWidthPx = needleConfig.width.toPx()
            val tipX = cx + needleLength * cosN
            val tipY = cy + needleLength * sinN

            when (needleConfig.style) {
                GaugeNeedleStyle.Tapered -> {
                    val perpX = -sinN * needleWidthPx / 2f
                    val perpY = cosN * needleWidthPx / 2f
                    val tailX = cx - tailLength * cosN
                    val tailY = cy - tailLength * sinN
                    needlePath.apply {
                        rewind()
                        moveTo(x = tipX, y = tipY)
                        lineTo(x = cx + perpX, y = cy + perpY)
                        lineTo(x = tailX, y = tailY)
                        lineTo(x = cx - perpX, y = cy - perpY)
                        close()
                    }
                    drawPath(path = needlePath, color = Color.Black.copy(alpha = 0.08f))
                    drawPath(path = needlePath, color = needleConfig.color)
                }

                GaugeNeedleStyle.Line -> {
                    drawLine(
                        color = needleConfig.color,
                        start = Offset(x = cx - tailLength * cosN, y = cy - tailLength * sinN),
                        end = Offset(x = tipX, y = tipY),
                        strokeWidth = needleWidthPx,
                        cap = StrokeCap.Round
                    )
                }
            }

            // ── 7. Center hub ──
            val baseRadiusPx = needleConfig.baseRadius.toPx()
            drawCircle(color = needleConfig.baseColor, radius = baseRadiusPx, center = center)
            drawCircle(color = needleConfig.color, radius = baseRadiusPx * 0.4f, center = center)
        }

        // ── Center content, offset below the hub ──
        if (centerContent != null) {
            Box(
                modifier = Modifier.padding(top = style.centerContentOffset),
                contentAlignment = Alignment.Center
            ) {
                centerContent()
            }
        }
    }
}

