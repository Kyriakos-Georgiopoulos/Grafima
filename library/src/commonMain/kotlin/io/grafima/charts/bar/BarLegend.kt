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

package io.grafima.charts.bar

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.LegendOrientation

/** [BarLegend]'s default label style. Guarded by `ColorContrastTest`. */
internal val BarLegendLabelTextStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF6B7280))

/** One row of the key: a series, its name, and the colours its bars draw in. */
internal data class BarLegendEntry(
    val seriesId: String,
    val label: String,
    val colorStops: List<ColorStop>?,
    val gradientColors: List<Color>
)

/**
 * The series a key should list, in the order they first appear.
 *
 * A series' colours are taken from its first bar, since that is the one a reader
 * meets first; a series whose bars disagree on colour cannot be keyed by a single
 * swatch anyway. A dataset with no series at all yields nothing — its bars are
 * named by the axis, and a key repeating those names says nothing new.
 */
internal fun barLegendEntries(dataSet: BarDataSet): List<BarLegendEntry> {
    val seen = LinkedHashMap<String, BarLegendEntry>()
    dataSet.entries.forEach { entry ->
        val id = entry.seriesId?.takeIf { it.isNotBlank() } ?: return@forEach

        if (seen.containsKey(id)) return@forEach
        seen[id] = BarLegendEntry(
            seriesId = id,
            label = entry.spokenSeriesLabel ?: id,
            colorStops = entry.colorStops,
            gradientColors = entry.gradientColors ?: dataSet.defaultGradientColors
        )
    }
    return seen.values.toList()
}

/**
 * A key mapping each series' colours to its [BarEntry.seriesLabel].
 *
 * A grouped or stacked chart draws two or more bars per category and tells them
 * apart by colour alone. The chart names the series in its selection tooltip and to
 * a screen reader, but only once a bar is chosen — at rest, or in a screenshot, a
 * sighted reader has nothing to read the colours against. This fills that gap.
 *
 * Placed by the caller rather than drawn inside the chart, so it can sit above,
 * beside or below without taking room from the plot:
 *
 * ```
 * Column {
 *     BarLegend(dataSet = data)
 *     BarChart(dataSet = data, modifier = Modifier.fillMaxWidth().height(300.dp))
 * }
 * ```
 *
 * A dataset whose entries carry no [BarEntry.seriesId] draws nothing at all: those
 * bars are named by the axis already. Series that leave their colours unset all
 * take [BarDataSet.defaultGradientColors], so give them their own if the key is to
 * tell them apart.
 *
 * A screen reader reaches it as one item naming every series, rather than one stop
 * each. The colour mapping is visual only — the chart's own description is what
 * carries the series to a listener.
 *
 * @param dataSet The same dataset the chart is drawing.
 * @param orientation Row or column layout.
 * @param textStyle Label text. Defaults to the bar chart's own label tone.
 * @param swatchSize Side of the square colour sample beside each label.
 * @param spacing Gap between entries, and half that between wrapped lines.
 * @param entryAlignment Which edge the entries line up on when
 *   [Vertical][LegendOrientation.Vertical]. Ignored when horizontal.
 */
@Composable
fun BarLegend(
    dataSet: BarDataSet,
    modifier: Modifier = Modifier,
    orientation: LegendOrientation = LegendOrientation.Horizontal,
    textStyle: TextStyle = BarLegendLabelTextStyle,
    swatchSize: Dp = 12.dp,
    spacing: Dp = 12.dp,
    entryAlignment: Alignment.Horizontal = Alignment.Start
) {
    val series = remember(dataSet) { barLegendEntries(dataSet) }

    if (series.isEmpty()) return

    val grouped = modifier.semantics(mergeDescendants = true) { }

    val entries: @Composable () -> Unit = {
        series.forEach { legend ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(swatchSize)) {
                    // A miniature bar: the gradient runs top to bottom as a vertical
                    // chart's does. It names the series' colours, not their direction,
                    // so a horizontal chart keys off the same swatch.
                    val stops = legend.colorStops
                    val brush = if (stops != null) {
                        Brush.verticalGradient(
                            *stops.map { it.position to it.color }.toTypedArray(),
                            startY = 0f,
                            endY = size.height
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = legend.gradientColors,
                            startY = 0f,
                            endY = size.height
                        )
                    }
                    drawRoundRect(brush = brush, cornerRadius = CornerRadius(size.minDimension / 4f))
                }
                BasicText(
                    text = legend.label,
                    style = textStyle,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }

    when (orientation) {
        LegendOrientation.Horizontal -> FlowRow(
            modifier = grouped,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing / 2),
            itemVerticalAlignment = Alignment.CenterVertically,
            content = { entries() }
        )

        LegendOrientation.Vertical -> Column(
            modifier = grouped,
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = entryAlignment,
            content = { entries() }
        )
    }
}
