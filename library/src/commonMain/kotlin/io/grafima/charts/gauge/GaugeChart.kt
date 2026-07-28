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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import io.grafima.charts.rememberEffectiveReduceMotion
import io.grafima.charts.toRadians
import kotlin.math.cos
import kotlin.math.max
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

    // Pre-computed so the draw pass does no trigonometry.
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

    // Pre-computed so the draw pass allocates no stop arrays.
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

    val chartDescription = remember(value, minValue, maxValue, zones, a11yConfig) {
        a11yConfig.descriptionBuilder(value, minValue, maxValue, zones)
    }
    val clampedValue = value.coerceIn(minValue, maxValue)

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

            drawGaugeTrack(style, arcTopLeft, arcRect, arcWidthPx)

            val range = maxValue - minValue
            drawGaugeArcFill(
                style = style,
                zones = zones,
                globalGradientStops = globalGradientStops,
                zoneGradientStops = zoneGradientStops,
                minValue = minValue,
                range = range,
                isRtl = isRtl,
                center = center,
                arcTopLeft = arcTopLeft,
                arcRect = arcRect,
                arcWidthPx = arcWidthPx
            )

            if (style.showValueArc && range > 0f) {
                drawGaugeValueArc(
                    style = style,
                    animatedAngle = needleAnimatable.value,
                    cx = cx,
                    cy = cy,
                    gaugeRadius = gaugeRadius,
                    arcWidthPx = arcWidthPx
                )
            }

            drawGaugeTicks(tickConfig, tickTrig, cx, cy, gaugeRadius)

            if (tickConfig.showLabels && tickLabelLayouts.isNotEmpty()) {
                drawGaugeTickLabels(
                    tickConfig = tickConfig,
                    tickTrig = tickTrig,
                    tickLabelLayouts = tickLabelLayouts,
                    isRtl = isRtl,
                    cx = cx,
                    cy = cy,
                    gaugeRadius = gaugeRadius
                )
            }

            drawGaugeNeedle(
                needleConfig = needleConfig,
                needlePath = needlePath,
                animatedAngle = needleAnimatable.value,
                cx = cx,
                cy = cy,
                gaugeRadius = gaugeRadius
            )

            drawGaugeHub(needleConfig, center)
        }

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
