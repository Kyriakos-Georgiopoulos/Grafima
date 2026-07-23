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

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Rendering strategy for the visual indicator shown when a slice is selected.
 * Implement this to provide a custom selection treatment (e.g. a leader line,
 * a floating card, a glow effect).
 *
 * The chart calls [drawSelection] inside its [DrawScope] after all slices are drawn,
 * only when a selection is active.
 *
 * @see TooltipPieSelectionRenderer for a centered tooltip implementation.
 * @see ElbowCalloutPieSelectionRenderer for a leader-line + pill implementation.
 */
@Stable
fun interface PieChartSelectionRenderer {
    /**
     * @param entry The currently selected slice data.
     * @param pieCenter Center point of the pie in Canvas coordinates.
     * @param pieRadius Outer radius of the pie in pixels.
     * @param sliceCentroid A point on the slice's mid-angle at the ring's center radius.
     *   Useful as an anchor for lines or indicators.
     * @param midAngleDegrees The visual angle bisecting the selected slice, in degrees.
     *   Already accounts for RTL mirroring.
     * @param textMeasurer Shared [TextMeasurer] for laying out label text.
     * @param tooltipCache Reusable [TextLayoutResult] cache keyed by entry id + value.
     *   Avoids re-measuring identical text on every frame during animations.
     * @param layoutDirection Current layout direction. Use this when your renderer
     *   needs to flip text alignment or anchor logic for RTL.
     */
    fun DrawScope.drawSelection(
        entry: PieEntry,
        pieCenter: Offset,
        pieRadius: Float,
        sliceCentroid: Offset,
        midAngleDegrees: Float,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<String, TextLayoutResult>,
        layoutDirection: LayoutDirection
    )
}

/**
 * Renders a floating rounded-rect tooltip positioned radially outside the pie.
 * Pushes outward from the slice's mid-angle so it never overlaps the chart center,
 * and clamps to Canvas bounds to stay fully visible.
 */
@Stable
class TooltipPieSelectionRenderer(
    val backgroundColor: Color = Color(0xFF111827),
    val textStyle: TextStyle = TextStyle(
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    ),
    val cornerRadius: Dp = 8.dp,
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 8.dp,
    val radialOffset: Dp = 16.dp
) : PieChartSelectionRenderer {
    override fun DrawScope.drawSelection(
        entry: PieEntry,
        pieCenter: Offset,
        pieRadius: Float,
        sliceCentroid: Offset,
        midAngleDegrees: Float,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<String, TextLayoutResult>,
        layoutDirection: LayoutDirection
    ) {
        val cacheKey = "${entry.id}_${entry.value.toInt()}"
        val tooltipLayout = tooltipCache.getOrPut(cacheKey) {
            textMeasurer.measure(text = "${entry.label}: ${entry.value.toInt()}", style = textStyle)
        }
        val tooltipWidth = tooltipLayout.size.width + (horizontalPadding.toPx() * 2)
        val tooltipHeight = tooltipLayout.size.height + (verticalPadding.toPx() * 2)

        val angleRad = Math.toRadians(midAngleDegrees.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()

        val targetRadius = pieRadius + radialOffset.toPx()
        val anchorX = pieCenter.x + targetRadius * cosA
        val anchorY = pieCenter.y + targetRadius * sinA

        val rawLeft = anchorX - (tooltipWidth / 2f) + (cosA * tooltipWidth / 2f)
        val rawTop = anchorY - (tooltipHeight / 2f) + (sinA * tooltipHeight / 2f)

        val safeLeft = rawLeft.coerceIn(0f, size.width - tooltipWidth)
        val safeTop = rawTop.coerceIn(0f, size.height - tooltipHeight)

        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(x = safeLeft, y = safeTop),
            size = Size(width = tooltipWidth, height = tooltipHeight),
            cornerRadius = CornerRadius(x = cornerRadius.toPx(), y = cornerRadius.toPx())
        )
        drawText(
            textLayoutResult = tooltipLayout,
            color = textStyle.color,
            topLeft = Offset(
                x = safeLeft + (tooltipWidth - tooltipLayout.size.width) / 2,
                y = safeTop + (tooltipHeight - tooltipLayout.size.height) / 2
            )
        )
    }
}

/**
 * Renders a two-segment leader line (radial + horizontal stub) ending in a bordered
 * pill-shaped label. The line originates at the slice centroid, breaks at a "knee"
 * point outside the pie, then runs horizontally to the text pill.
 *
 * Automatically flips the horizontal direction based on which side of the pie the
 * slice sits on, and clamps all geometry to Canvas bounds.
 */
@Stable
class ElbowCalloutPieSelectionRenderer(
    val lineColor: Color = Color(0xFF374151),
    val lineWidth: Dp = 2.dp,
    val radialExtension: Dp = 24.dp,
    val stubLength: Dp = 16.dp,
    val textStyle: TextStyle = TextStyle(
        color = Color(0xFF111827),
        fontSize = 13.sp,
        fontWeight = FontWeight.Black
    ),
    val pillRadius: Dp = 6.dp,
    val pillPaddingX: Dp = 10.dp,
    val pillPaddingY: Dp = 6.dp,
    val pillBackgroundColor: Color = Color.White
) : PieChartSelectionRenderer {

    private val reusablePath = Path()

    override fun DrawScope.drawSelection(
        entry: PieEntry,
        pieCenter: Offset,
        pieRadius: Float,
        sliceCentroid: Offset,
        midAngleDegrees: Float,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<String, TextLayoutResult>,
        layoutDirection: LayoutDirection
    ) {
        val cacheKey = "${entry.id}_${entry.value.toInt()}"
        val layout = tooltipCache.getOrPut(cacheKey) {
            textMeasurer.measure(text = "${entry.label}: ${entry.value.toInt()}", style = textStyle)
        }

        val angleRad = Math.toRadians(midAngleDegrees.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val isRightSide = cosA >= 0

        val dotAnchorPoint = sliceCentroid
        val dotRadius = 4.dp.toPx()

        val bgWidth = layout.size.width + (pillPaddingX.toPx() * 2)
        val bgHeight = layout.size.height + (pillPaddingY.toPx() * 2)
        val margin = 8.dp.toPx()

        val targetRadius = pieRadius + radialExtension.toPx()
        var kneeX = pieCenter.x + targetRadius * cosA
        var kneeY = pieCenter.y + targetRadius * sinA

        val minKneeY = margin + bgHeight / 2f
        val maxKneeY = size.height - margin - bgHeight / 2f
        kneeY = kneeY.coerceIn(minKneeY, maxKneeY)

        val stubPx = stubLength.toPx()
        var endX = kneeX + if (isRightSide) stubPx else -stubPx

        if (isRightSide) {
            val maxEndX = size.width - margin - bgWidth
            endX = min(endX, maxEndX)
            kneeX = min(kneeX, endX - stubPx)
            if (kneeX < dotAnchorPoint.x) {
                kneeX = dotAnchorPoint.x
                endX = kneeX + stubPx
            }
        } else {
            val minEndX = margin + bgWidth
            endX = max(endX, minEndX)
            kneeX = max(kneeX, endX + stubPx)
            if (kneeX > dotAnchorPoint.x) {
                kneeX = dotAnchorPoint.x
                endX = kneeX - stubPx
            }
        }

        val safeBoxLeft = if (isRightSide) endX else endX - bgWidth
        val safeBoxTop = kneeY - bgHeight / 2f

        val sliceColor = resolveEntryColor(entry) ?: lineColor

        reusablePath.apply {
            reset()
            moveTo(x = dotAnchorPoint.x, y = dotAnchorPoint.y)
            lineTo(x = kneeX, y = kneeY)
            lineTo(x = endX, y = kneeY)
        }

        drawPath(path = reusablePath, color = sliceColor, style = Stroke(width = lineWidth.toPx()))
        drawCircle(color = sliceColor, radius = dotRadius, center = dotAnchorPoint)

        drawRoundRect(
            color = pillBackgroundColor,
            topLeft = Offset(x = safeBoxLeft, y = safeBoxTop),
            size = Size(width = bgWidth, height = bgHeight),
            cornerRadius = CornerRadius(x = pillRadius.toPx(), y = pillRadius.toPx())
        )
        drawRoundRect(
            color = sliceColor,
            topLeft = Offset(x = safeBoxLeft, y = safeBoxTop),
            size = Size(width = bgWidth, height = bgHeight),
            cornerRadius = CornerRadius(x = pillRadius.toPx(), y = pillRadius.toPx()),
            style = Stroke(width = lineWidth.toPx())
        )
        drawText(
            textLayoutResult = layout,
            color = textStyle.color,
            topLeft = Offset(
                x = safeBoxLeft + pillPaddingX.toPx(),
                y = safeBoxTop + pillPaddingY.toPx()
            )
        )
    }
}

/**
 * Extracts the first meaningful color from a [PieEntry]'s brush.
 * Used by renderers (e.g. callout line color) to match the slice visually.
 * Returns null if the entry has no brush set.
 */
private fun resolveEntryColor(entry: PieEntry): Color? = when (val b = entry.brush) {
    is SliceBrush.Solid -> b.color
    is SliceBrush.Linear -> b.colors.firstOrNull()
    is SliceBrush.Radial -> b.colors.firstOrNull()
    is SliceBrush.Sweep -> b.colors.firstOrNull()
    null -> null
}
