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

package io.grafima.charts.gauge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.grafima.charts.rememberEffectiveReduceMotion
import io.grafima.charts.toRadians
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

    val reduceMotion = rememberEffectiveReduceMotion()
    val effectiveAnimationConfig = remember(animationConfig, reduceMotion) {
        if (reduceMotion) {
            animationConfig.copy(
                needleSpec = snap(),
                initialDelayMs = 0L
            )
        } else {
            animationConfig
        }
    }

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
        needleAnimatable.animateTo(targetAngle, effectiveAnimationConfig.needleSpec)
    }

    // ── Pre-computed tick trig (zero trig in draw) ──
    val tickTrig = remember(
        tickConfig.majorTickCount, tickConfig.minorTicksPerMajor,
        style.startAngle, style.sweepAngle
    ) {
        val majors = tickConfig.majorTickCount
        val minorsPerMajor = tickConfig.minorTicksPerMajor
        val majorAnglesRad = DoubleArray(majors + 1) { i ->
            toRadians((style.startAngle + style.sweepAngle * (i.toFloat() / majors)).toDouble())
        }
        val minorAnglesList = mutableListOf<Double>()
        val majorStep = style.sweepAngle / majors
        val minorStep = majorStep / (minorsPerMajor + 1)
        for (m in 0 until majors) {
            for (n in 1..minorsPerMajor) {
                minorAnglesList.add(
                    toRadians((style.startAngle + m * majorStep + n * minorStep).toDouble())
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
                    role = Role.Image
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
            val needleAngleRad = toRadians(animatedAngle.toDouble())
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
