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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import io.grafima.charts.toRadians
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawGaugeTrack(
    style: GaugeChartStyle,
    arcTopLeft: Offset,
    arcRect: Size,
    arcWidthPx: Float
) {
    drawArc(
        color = style.trackColor,
        startAngle = style.startAngle,
        sweepAngle = style.sweepAngle,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcRect,
        style = Stroke(width = arcWidthPx, cap = StrokeCap.Round)
    )
}

/** A global sweep gradient if configured, otherwise one arc per zone. */
internal fun DrawScope.drawGaugeArcFill(
    style: GaugeChartStyle,
    zones: List<GaugeZone>,
    globalGradientStops: Array<Pair<Float, Color>>?,
    zoneGradientStops: Map<String, Array<Pair<Float, Color>>>,
    minValue: Float,
    range: Float,
    isRtl: Boolean,
    center: Offset,
    arcTopLeft: Offset,
    arcRect: Size,
    arcWidthPx: Float
) {
    if (globalGradientStops != null) {
        drawArc(
            brush = Brush.sweepGradient(*globalGradientStops, center = center),
            startAngle = style.startAngle,
            sweepAngle = style.sweepAngle,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRect,
            style = Stroke(width = arcWidthPx, cap = StrokeCap.Round)
        )
        return
    }
    if (range <= 0f) return

    // Resolve which bands actually paint before drawing any of them. A zone
    // collapses to nothing when its range falls outside [minValue, maxValue],
    // and the rounded ends belong to the first and last *visible* band — not to
    // whichever zones happen to sit at either end of the list.
    val bands = zones.mapNotNull { zone ->
        val zoneStartFrac = ((zone.range.start - minValue) / range).coerceIn(0f, 1f)
        val zoneEndFrac = ((zone.range.endInclusive - minValue) / range).coerceIn(0f, 1f)
        val (sf, ef) = if (isRtl) {
            (1f - zoneEndFrac) to (1f - zoneStartFrac)
        } else {
            zoneStartFrac to zoneEndFrac
        }
        val zoneSweep = style.sweepAngle * (ef - sf)
        if (zoneSweep <= 0f) {
            null
        } else {
            GaugeBand(
                zone = zone,
                startAngle = style.startAngle + style.sweepAngle * sf,
                sweepAngle = zoneSweep
            )
        }
    }
    if (bands.isEmpty()) return

    // A round cap overhangs its end by half the stroke width, so rounding a
    // shared join spills one zone's colour over its neighbour. Paint the two
    // outer ends first, then lay every band except the first back down with
    // butt caps: the outermost curves survive — they have to, to cover the
    // rounded track beneath — and both overhangs end up buried under the very
    // bands they were spilling into.
    if (bands.size > 1) {
        drawGaugeBand(bands.last(), StrokeCap.Round, zoneGradientStops, center, arcTopLeft, arcRect, arcWidthPx)
    }
    drawGaugeBand(bands.first(), StrokeCap.Round, zoneGradientStops, center, arcTopLeft, arcRect, arcWidthPx)
    for (index in 1..bands.lastIndex) {
        drawGaugeBand(bands[index], StrokeCap.Butt, zoneGradientStops, center, arcTopLeft, arcRect, arcWidthPx)
    }
}

/** One zone's slice of the arc, already resolved to angles. */
private class GaugeBand(
    val zone: GaugeZone,
    val startAngle: Float,
    val sweepAngle: Float
)

private fun DrawScope.drawGaugeBand(
    band: GaugeBand,
    cap: StrokeCap,
    zoneGradientStops: Map<String, Array<Pair<Float, Color>>>,
    center: Offset,
    arcTopLeft: Offset,
    arcRect: Size,
    arcWidthPx: Float
) {
    val gradStops = zoneGradientStops[band.zone.id]
    if (gradStops != null) {
        drawArc(
            brush = Brush.sweepGradient(*gradStops, center = center),
            startAngle = band.startAngle,
            sweepAngle = band.sweepAngle,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRect,
            style = Stroke(width = arcWidthPx, cap = cap)
        )
    } else {
        drawArc(
            color = band.zone.color,
            startAngle = band.startAngle,
            sweepAngle = band.sweepAngle,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRect,
            style = Stroke(width = arcWidthPx, cap = cap)
        )
    }
}

internal fun DrawScope.drawGaugeValueArc(
    style: GaugeChartStyle,
    animatedAngle: Float,
    cx: Float,
    cy: Float,
    gaugeRadius: Float,
    arcWidthPx: Float
) {
    val valueSweep = (animatedAngle - style.startAngle).coerceIn(0f, style.sweepAngle)
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

/** Minor ticks first so major ticks draw over them. */
internal fun DrawScope.drawGaugeTicks(
    tickConfig: GaugeTickConfig,
    tickTrig: TickTrigData,
    cx: Float,
    cy: Float,
    gaugeRadius: Float
) {
    val minorInnerR = gaugeRadius - tickConfig.minorTickLength.toPx()
    val minorTickWidthPx = tickConfig.minorTickWidth.toPx()
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

    val majorInnerR = gaugeRadius - tickConfig.majorTickLength.toPx()
    val majorTickWidthPx = tickConfig.majorTickWidth.toPx()
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
}

/**
 * Labels are nudged outward along their own angle to clear their tick. In RTL
 * the label order reverses while the tick positions stay put.
 */
internal fun DrawScope.drawGaugeTickLabels(
    tickConfig: GaugeTickConfig,
    tickTrig: TickTrigData,
    tickLabelLayouts: List<TextLayoutResult>,
    isRtl: Boolean,
    cx: Float,
    cy: Float,
    gaugeRadius: Float
) {
    val labelR = gaugeRadius + tickConfig.labelPadding.toPx()
    val count = min(tickTrig.majorCos.size, tickLabelLayouts.size)

    for (i in 0 until count) {
        val layout = tickLabelLayouts[if (isRtl) count - 1 - i else i]
        val cosV = tickTrig.majorCos[i]
        val sinV = tickTrig.majorSin[i]
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                x = cx + labelR * cosV - layout.size.width / 2f + cosV * layout.size.width / 2f,
                y = cy + labelR * sinV - layout.size.height / 2f + sinV * layout.size.height / 2f
            )
        )
    }
}

internal fun DrawScope.drawGaugeNeedle(
    needleConfig: GaugeNeedleConfig,
    needlePath: Path,
    animatedAngle: Float,
    cx: Float,
    cy: Float,
    gaugeRadius: Float
) {
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
            needlePath.apply {
                rewind()
                moveTo(x = tipX, y = tipY)
                lineTo(x = cx + perpX, y = cy + perpY)
                lineTo(x = cx - tailLength * cosN, y = cy - tailLength * sinN)
                lineTo(x = cx - perpX, y = cy - perpY)
                close()
            }
            // Drawn twice: a faint pass widens the silhouette against the arc.
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
}

internal fun DrawScope.drawGaugeHub(needleConfig: GaugeNeedleConfig, center: Offset) {
    val baseRadiusPx = needleConfig.baseRadius.toPx()
    drawCircle(color = needleConfig.baseColor, radius = baseRadiusPx, center = center)
    drawCircle(color = needleConfig.color, radius = baseRadiusPx * 0.4f, center = center)
}
