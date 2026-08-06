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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * A series drawn with [LineSeries.strokeGradientColors] gets a gradient swatch, so
 * the key matches the line it names.
 *
 * [Horizontal][LineLegendOrientation.Horizontal] does not wrap. Use
 * [Vertical][LineLegendOrientation.Vertical] when the labels are long or numerous.
 *
 * @param dataSet The same dataset the chart is drawing.
 * @param orientation Row or column layout.
 * @param textStyle Label text. Defaults to the axis label tone.
 * @param swatchWidth Length of the colour sample beside each label.
 * @param spacing Gap between entries.
 */
@Composable
fun LineLegend(
    dataSet: LineDataSet,
    modifier: Modifier = Modifier,
    orientation: LineLegendOrientation = LineLegendOrientation.Horizontal,
    textStyle: TextStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF64748B)),
    swatchWidth: Dp = 18.dp,
    spacing: Dp = 12.dp
) {
    val entries: @Composable () -> Unit = {
        dataSet.series.forEach { series ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(width = swatchWidth, height = 4.dp)) {
                    val y = size.height / 2f
                    val start = Offset(x = 0f, y = y)
                    val end = Offset(x = size.width, y = y)
                    if (series.strokeGradientColors.size >= 2) {
                        drawLine(
                            brush = Brush.horizontalGradient(series.strokeGradientColors),
                            start = start,
                            end = end,
                            strokeWidth = size.height,
                            cap = StrokeCap.Round
                        )
                    } else {
                        drawLine(
                            color = series.color,
                            start = start,
                            end = end,
                            strokeWidth = size.height,
                            cap = StrokeCap.Round
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
        LineLegendOrientation.Horizontal -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
            content = { entries() }
        )

        LineLegendOrientation.Vertical -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = { entries() }
        )
    }
}
