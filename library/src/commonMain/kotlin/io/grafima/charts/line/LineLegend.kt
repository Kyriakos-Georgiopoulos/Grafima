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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.dashIntervalsOf
import kotlin.math.min

/** [LineLegend]'s default label style. Guarded by `ColorContrastTest`. */
internal val LegendLabelTextStyle = TextStyle(fontSize = 12.sp, color = AxisLabelGrey)

/** Whether [LineLegend] lays its entries out in a row or a column. */
enum class LineLegendOrientation {
    Horizontal,
    Vertical
}

/**
 * A key mapping each series' colour to its [LineSeries.label].
 *
 * The chart names its series in the crosshair tooltip and to a screen reader, but
 * only once something is selected — a chart at rest, or in a screenshot, gives a
 * sighted reader no way to tell four overlaid lines apart. This fills that gap.
 *
 * Placed by the caller rather than drawn inside the chart, so it can sit above,
 * beside or below without taking room from the plot:
 *
 * ```
 * Column {
 *     LineLegend(dataSet = data)
 *     LineChart(dataSet = data, modifier = Modifier.fillMaxWidth().height(300.dp))
 * }
 * ```
 *
 * A series drawn with [LineSeries.strokeGradientColors] gets a gradient swatch, and
 * one with a [LineSeries.dashPattern] a dashed swatch, so the key matches the line
 * it names.
 *
 * A screen reader reaches it as one item naming every series, rather than one
 * stop each. The colour mapping itself is visual only — the chart's own
 * description is what carries the series to a listener.
 *
 * [Horizontal][LineLegendOrientation.Horizontal] wraps onto further lines when the
 * entries do not fit.
 *
 * @param dataSet The same dataset the chart is drawing.
 * @param orientation Row or column layout.
 * @param textStyle Label text. Defaults to the axis label tone.
 * @param swatchWidth Length of the colour sample beside each label.
 * @param spacing Gap between entries, and half that between wrapped lines.
 * @param entryAlignment Which edge the entries line up on when
 *   [Vertical][LineLegendOrientation.Vertical]. Ignored when horizontal.
 */
@Composable
fun LineLegend(
    dataSet: LineDataSet,
    modifier: Modifier = Modifier,
    orientation: LineLegendOrientation = LineLegendOrientation.Horizontal,
    textStyle: TextStyle = LegendLabelTextStyle,
    swatchWidth: Dp = 18.dp,
    spacing: Dp = 12.dp,
    entryAlignment: Alignment.Horizontal = Alignment.Start
) {
    val grouped = modifier.semantics(mergeDescendants = true) { }
    val density = LocalDensity.current
    // The swatch is a symbol, not a scale model. A 10dp dash laid on an 18dp key
    // draws one dash and runs its gap off the end, which reads as a short solid
    // bar — so a dashed series gets a dash sized to the swatch instead.
    val dashEffects = remember(dataSet.series, density, swatchWidth) {
        val step = with(density) { swatchWidth.toPx() } / 5f
        val symbol = PathEffect.dashPathEffect(floatArrayOf(step, step))
        dataSet.series.map { series ->
            if (dashIntervalsOf(series.dashPattern, density) != null) symbol else null
        }
    }

    val entries: @Composable () -> Unit = {
        dataSet.series.forEachIndexed { index, series ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(width = swatchWidth, height = 4.dp)) {
                    val dash = dashEffects[index]
                    val y = size.height / 2f
                    // Inset by the cap radius, or the round ends paint outside
                    // the width the caller asked for. A dashed swatch takes butt
                    // ends instead: round ones bleed across a gap this short and
                    // close it up again.
                    val cap = if (dash == null) min(size.height, size.width) / 2f else 0f
                    val strokeCap = if (dash == null) StrokeCap.Round else StrokeCap.Butt
                    val start = Offset(x = cap, y = y)
                    val end = Offset(x = size.width - cap, y = y)
                    if (series.hasStrokeGradient) {
                        // The chart mirrors its own gradient, so the swatch has to
                        // as well or the key runs opposite to the line in RTL.
                        val rtl = layoutDirection == LayoutDirection.Rtl
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = series.strokeGradientColors,
                                startX = if (rtl) size.width else 0f,
                                endX = if (rtl) 0f else size.width
                            ),
                            start = start,
                            end = end,
                            strokeWidth = size.height,
                            cap = strokeCap,
                            pathEffect = dash
                        )
                    } else {
                        drawLine(
                            color = series.color,
                            start = start,
                            end = end,
                            strokeWidth = size.height,
                            cap = strokeCap,
                            pathEffect = dash
                        )
                    }
                }
                BasicText(
                    text = series.label,
                    style = textStyle,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }

    when (orientation) {
        LineLegendOrientation.Horizontal -> FlowRow(
            modifier = grouped,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing / 2),
            itemVerticalAlignment = Alignment.CenterVertically,
            content = { entries() }
        )

        LineLegendOrientation.Vertical -> Column(
            modifier = grouped,
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = entryAlignment,
            content = { entries() }
        )
    }
}
