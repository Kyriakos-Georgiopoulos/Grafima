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

package io.grafima.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.grafima.charts.DashPattern
import io.grafima.charts.line.LineChart
import io.grafima.charts.line.LineChartStyle
import io.grafima.charts.line.LineCurveType
import io.grafima.charts.line.LineDataPoint
import io.grafima.charts.line.LineDataSet
import io.grafima.charts.line.LineLegend
import io.grafima.charts.line.LineLegendOrientation
import io.grafima.charts.line.LineSeries
import io.grafima.charts.line.LineValueLabelConfig
import io.grafima.charts.line.ReferenceLine
import io.grafima.charts.line.ReferenceLineAxis
import io.grafima.sample.theme.LocalDemoColors
import io.grafima.sample.theme.ProvideDemoLayout
import io.grafima.sample.theme.themedCrosshair
import io.grafima.sample.theme.themedLineAxis
import kotlin.random.Random

private val SeriesPalette = listOf(
    Color(0xFF6366F1), Color(0xFFF59E0B), Color(0xFF10B981),
    Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFF06B6D4), Color(0xFF84CC16)
)

/**
 * Each point drifts from the previous one instead of being drawn independently
 * around a base — independent noise looks like static, not like something you'd
 * actually plot.
 */
private fun randomSeries(
    id: String,
    label: String,
    base: Int,
    variance: Int
): LineSeries {
    val c1 = SeriesPalette.random()
    val c2 = SeriesPalette.filter { it != c1 }.random()
    val trend = (-variance / 8f)..(variance / 5f)
    var value = base.toFloat() + (-variance / 3..variance / 3).random()
    val values = MonthLabels.map {
        value = (value + trend.start + Random.nextFloat() * (trend.endInclusive - trend.start))
            .coerceIn(base * 0.35f, base * 1.75f)
        value
    }
    return LineSeries(
        id = id,
        label = label,
        color = c1,
        fillAlpha = 0.10f,
        strokeGradientColors = listOf(c1, c2),
        points = values.mapIndexed { i, v ->
            LineDataPoint(
                x = i.toFloat(),
                y = v,
                label = MonthLabels[i],
                contentDescription = SpokenMonths[i]
            )
        }
    )
}

/**
 * The mean of every series at each x, dashed because it is derived rather than
 * measured — the dash is what stops it being read as another set of readings.
 *
 * Recomputed from whatever is on the chart, so adding or randomizing a series
 * moves it.
 */
private fun LineDataSet.averageSeries(): LineSeries {
    val reference = series.first().points
    return LineSeries(
        id = "average",
        label = "Average",
        color = Color(0xFF10B981),
        strokeWidth = 2.dp,
        dashPattern = DashPattern(dash = 10.dp, gap = 6.dp),
        // Derived, so it has no readings to mark even when the rest show theirs.
        dotRadius = 0.dp,
        points = reference.indices.map { i ->
            LineDataPoint(
                x = reference[i].x,
                y = series.mapNotNull { it.points.getOrNull(i)?.y }.average().toFloat(),
                label = reference[i].label,
                contentDescription = reference[i].contentDescription
            )
        }
    )
}

private const val MinSeries = 1
private const val MaxSeries = 4

private fun LineDataSet.plusSeries(): LineDataSet {
    val index = series.size
    return copy(
        series = series + randomSeries(
            id = "series$index",
            label = "Series ${index + 1}",
            base = 70,
            variance = 35
        )
    )
}

private fun LineDataSet.minusSeries(): LineDataSet = copy(series = series.dropLast(1))

/** Re-rolls every series that is currently on the chart, added ones included. */
private fun LineDataSet.randomized(): LineDataSet = copy(
    series = series.map { s ->
        randomSeries(id = s.id, label = s.label, base = 75, variance = 35)
    }
)

private fun seededDataSet(): LineDataSet = LineDataSet(
    series = listOf(
        LineSeries(
            id = "rev",
            label = "Revenue",
            color = Color(0xFF6366F1),
            fillAlpha = 0.10f,
            strokeGradientColors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
            dotRadius = 6.dp,
            points = listOf(42f, 55f, 48f, 72f, 68f, 85f, 90f, 78f, 95f, 110f, 105f, 120f)
                .mapIndexed { i, v -> LineDataPoint(
                    x = i.toFloat(),
                    y = v,
                    label = MonthLabels[i],
                    contentDescription = SpokenMonths[i]
                ) }
        ),
        LineSeries(
            id = "exp",
            label = "Expenses",
            color = Color(0xFFF59E0B),
            fillAlpha = 0.08f,
            strokeGradientColors = listOf(Color(0xFFFBBF24), Color(0xFFD97706)),
            points = listOf(38f, 42f, 50f, 45f, 55f, 52f, 60f, 58f, 62f, 65f, 70f, 68f)
                .mapIndexed { i, v -> LineDataPoint(
                    x = i.toFloat(),
                    y = v,
                    label = MonthLabels[i],
                    contentDescription = SpokenMonths[i]
                ) }
        )
    ),
    contentDescription = "Monthly Revenue vs Expenses"
)

internal class LineChartViewModel : ViewModel() {
    var showFill by mutableStateOf(true)
    var curveType by mutableStateOf(LineCurveType.MonotoneCubic)
    var showValues by mutableStateOf(false)
    var showTrend by mutableStateOf(false)
    var showDots by mutableStateOf(false)
    var dataSet by mutableStateOf(seededDataSet())
    var selectedIdx by mutableStateOf<Int?>(null)
}

@Composable
internal fun LineChartDemoScreen(
    viewModel: LineChartViewModel = viewModel { LineChartViewModel() }
) {
    val colors = LocalDemoColors.current

    val dataSet = viewModel.dataSet
    val showFill = viewModel.showFill
    val curveType = viewModel.curveType
    val selectedIdx = viewModel.selectedIdx
    val showValues = viewModel.showValues
    val showTrend = viewModel.showTrend
    val showDots = viewModel.showDots

    // Stripping the fill rewrites every series, so derive it once per change:
    // rebuilding inline hands the chart a new dataset on every recomposition and
    // it can never skip.
    // Derived here rather than held in the dataset: it is not data of its own, and
    // it has to follow every series being added, removed or re-rolled.
    val visibleDataSet = remember(dataSet, showFill, showTrend) {
        val shown = if (showFill) {
            dataSet
        } else {
            dataSet.copy(series = dataSet.series.map { it.copy(fillAlpha = 0f) })
        }
        if (!showTrend || shown.series.size < 2) {
            shown
        } else {
            shown.copy(series = shown.series + shown.averageSeries())
        }
    }

    val chartStyle = remember(curveType, showValues, showDots, colors) {
        LineChartStyle(
            curveType = curveType,
            showDots = showDots,
            valueLabels = LineValueLabelConfig(
                enabled = showValues,
                // Copied from the default rather than rebuilt, which keeps its weight.
                textStyle = LineValueLabelConfig().textStyle.copy(color = colors.onSurface)
            )
        )
    }
    val targetLine = remember(colors) {
        listOf(
            ReferenceLine(
                value = 100f,
                axis = ReferenceLineAxis.Y,
                label = "Target",
                color = colors.accentWarm,
                dashPattern = DashPattern(dash = 6.dp, gap = 6.dp),
                contentDescription = "Target of 100 thousand euros"
            )
        )
    }

    DemoScreenScaffold(
        controls = {
            DemoControls { buttonModifier ->
                DemoAddButton(
                    text = "Add Series",
                    enabled = dataSet.series.size < MaxSeries,
                    onClick = { viewModel.dataSet = dataSet.plusSeries() },
                    modifier = buttonModifier
                )
                DemoRemoveButton(
                    text = "Remove Series",
                    enabled = dataSet.series.size > MinSeries,
                    onClick = { viewModel.dataSet = dataSet.minusSeries() },
                    modifier = buttonModifier
                )
            }

            DemoControls { buttonModifier ->
                Button(
                    onClick = {
                        viewModel.curveType = if (curveType == LineCurveType.MonotoneCubic) {
                            LineCurveType.Linear
                        } else LineCurveType.MonotoneCubic
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        if (curveType == LineCurveType.MonotoneCubic) "Linear" else "Smooth",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { viewModel.showFill = !showFill },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm,
                        contentColor = colors.onAccentWarm
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        if (showFill) "No Fill" else "Area Fill",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DemoControls { buttonModifier ->
                Button(
                    onClick = { viewModel.showValues = !showValues },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        if (showValues) "Hide Values" else "Show Values",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { viewModel.showTrend = !showTrend },
                    enabled = dataSet.series.size > 1,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm,
                        contentColor = colors.onAccentWarm
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        if (showTrend) "Hide Average" else "Show Average",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DemoControls { buttonModifier ->
                Button(
                    onClick = { viewModel.showDots = !showDots },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        if (showDots) "Hide Dots" else "Show Dots",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = { viewModel.dataSet = dataSet.randomized() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.onSurface,
                    contentColor = colors.background
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) { Text("Randomize", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Revenue & Expenses",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.onSurface
                        )
                        Text(
                            text = selectedIdx?.let { MonthLabels.getOrNull(it) }
                                ?: "Drag to explore",
                            fontSize = 13.sp,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    LineLegend(
                        dataSet = visibleDataSet,
                        orientation = LineLegendOrientation.Vertical,
                        textStyle = TextStyle(fontSize = 11.sp, color = colors.onSurfaceMuted),
                        spacing = 4.dp,
                        entryAlignment = Alignment.End
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    LineChart(
                        dataSet = visibleDataSet,
                        modifier = Modifier.fillMaxSize(),
                        style = chartStyle,
                        axisConfig = themedLineAxis(
                            yTickCount = 5,
                            gridDashPattern = DashPattern(dash = 2.dp, gap = 3.dp),
                            xLabels = MonthLabels,
                            xAxisTitle = "Month",
                            yAxisTitle = "Thousands of euros",
                            referenceLines = targetLine
                        ),
                        crosshairConfig = themedCrosshair(),
                        selectedPointIndex = selectedIdx,
                        onPointSelected = { viewModel.selectedIdx = it }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LineChartDemoScreenPreview() {
    LineChartDemoScreen()
}

@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
private fun LineChartDemoScreenLandscapePreview() {
    ProvideDemoLayout { LineChartDemoScreen() }
}
