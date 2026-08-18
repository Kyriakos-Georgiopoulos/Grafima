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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.grafima.charts.LegendOrientation
import kotlinx.coroutines.launch

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
 * A series is whatever the chart groups by — any non-null [BarEntry.seriesId], blank
 * included — so the key can never omit a colour the chart is drawing. One with no
 * name to show is skipped instead, since a blank row keys nothing.
 *
 * Colours come from the same resolver the bars use, so a series that sets none is
 * keyed with the palette gradient it is actually painted with rather than a guess.
 */
internal fun barLegendEntries(dataSet: BarDataSet): List<BarLegendEntry> {
    val order = seriesOrder(dataSet.entries)
    return dataSet.entries
        .filter { it.seriesId != null }
        .distinctBy { it.seriesId }
        .mapNotNull { entry ->
            val label = entry.spokenSeriesLabel ?: return@mapNotNull null
            BarLegendEntry(
                seriesId = entry.seriesId!!,
                label = label,
                colorStops = entry.colorStops?.takeIf { it.size >= 2 },
                gradientColors = barGradientColors(entry, dataSet, order)
            )
        }
}

/**
 * The rows to draw, each with the opacity it should carry.
 *
 * A series removed from the dataset is kept until its bars have finished shrinking,
 * so the key and the chart agree on what is on screen for the whole animation.
 */
@Composable
private fun rememberLegendWithDepartures(
    current: List<BarLegendEntry>,
    animationConfig: AnimationConfig
): List<Pair<BarLegendEntry, Animatable<Float, AnimationVector1D>>> {
    val shown = remember { mutableStateMapOf<String, BarLegendEntry>() }
    val alphas = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(current) {
        current.forEach { entry ->
            shown[entry.seriesId] = entry
            val alpha = alphas.getOrPut(entry.seriesId) { Animatable(0f) }
            if (alpha.targetValue != 1f) scope.launch { alpha.animateTo(1f, animationConfig.morphSpec) }
        }
        val present = current.map { it.seriesId }.toSet()
        shown.keys.filterNot { it in present }.forEach { gone ->
            val alpha = alphas[gone] ?: return@forEach
            if (alpha.targetValue == 0f) return@forEach
            scope.launch {
                alpha.animateTo(0f, animationConfig.initialEntrySpec)
                shown.remove(gone)
                alphas.remove(gone)
            }
        }
    }

    // Dataset order first so a departing row keeps its place rather than jumping to
    // the end while it fades.
    val order = current.map { it.seriesId }
    return (order + shown.keys.filterNot { it in order }).mapNotNull { id ->
        val entry = shown[id] ?: return@mapNotNull null
        val alpha = alphas[id] ?: return@mapNotNull null
        entry to alpha
    }
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
 * each. Unlike the line chart, the bar chart's own description carries counts rather
 * than series names, so this key is the only place a listener meets them before
 * selecting a bar — [describe] names the item so it arrives as something placeable
 * rather than two loose nouns.
 *
 * @param dataSet The same dataset the chart is drawing.
 * @param orientation Row or column layout.
 * @param textStyle Label text. Defaults to the bar chart's own label tone.
 * @param swatchSize Side of the square colour sample beside each label.
 *   [Dp.Unspecified], the default, scales it with [textStyle]'s font size so the one
 *   thing carrying the mapping grows with the reader's font setting.
 * @param animationConfig Timing a departing series' row fades on. The chart holds a
 *   removed series' bars for [AnimationConfig.initialEntrySpec] while they shrink, so
 *   a key that dropped the row at once would name fewer colours than are on screen.
 * @param describe Names the key for a screen reader, given the series in order.
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
    swatchSize: Dp = Dp.Unspecified,
    spacing: Dp = 12.dp,
    entryAlignment: Alignment.Horizontal = Alignment.Start,
    animationConfig: AnimationConfig = AnimationConfig(),
    describe: (List<String>) -> String = { names -> "Key: ${names.joinToString(", ")}" }
) {
    val current = remember(dataSet) { barLegendEntries(dataSet) }
    val series = rememberLegendWithDepartures(current, animationConfig)

    if (series.isEmpty()) return

    // A style with no colour of its own resolves to black, which is invisible on the
    // dark surfaces these charts are usually put on.
    val labelStyle = if (textStyle.color.isSpecified) {
        textStyle
    } else {
        textStyle.copy(color = BarLegendLabelTextStyle.color)
    }
    val swatch = if (swatchSize.isSpecified) {
        swatchSize
    } else {
        with(LocalDensity.current) { labelStyle.fontSize.toDp() }
    }
    // Named from what the dataset holds, not from what is still fading: a listener
    // should not be told about a series that has just been taken away.
    val spoken = remember(current, describe) { describe(current.map { it.label }) }
    val grouped = modifier.semantics(mergeDescendants = true) {
        contentDescription = spoken
    }

    val entries: @Composable () -> Unit = {
        series.forEach { (legend, alpha) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
            ) {
                Canvas(modifier = Modifier.size(swatch)) {
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
                    style = labelStyle,
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
