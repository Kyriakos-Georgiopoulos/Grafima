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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.line.LineChart
import io.grafima.charts.line.LineChartStyle
import io.grafima.charts.line.LineCurveType
import io.grafima.charts.line.LineDataPoint
import io.grafima.charts.line.LineDataSet
import io.grafima.charts.line.LineSeries
import io.grafima.sample.theme.LocalDemoColors
import io.grafima.sample.theme.themedCrosshair
import io.grafima.sample.theme.themedLineAxis
import kotlin.random.Random

// ==========================================
// 5. DEMO
// ==========================================

private val Months = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

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
    val values = Months.map {
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
            LineDataPoint(x = i.toFloat(), y = v, label = Months[i])
        }
    )
}

private fun monthlyDataSet(): LineDataSet = LineDataSet(
    series = listOf(
        randomSeries(id = "rev", label = "Revenue", base = 80, variance = 40),
        randomSeries(id = "exp", label = "Expenses", base = 55, variance = 25)
    ),
    contentDescription = "Monthly Revenue vs Expenses"
)

private fun seededDataSet(): LineDataSet = LineDataSet(
    series = listOf(
        LineSeries(
            id = "rev",
            label = "Revenue",
            color = Color(0xFF6366F1),
            fillAlpha = 0.10f,
            strokeGradientColors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
            points = listOf(42f, 55f, 48f, 72f, 68f, 85f, 90f, 78f, 95f, 110f, 105f, 120f)
                .mapIndexed { i, v -> LineDataPoint(x = i.toFloat(), y = v, label = Months[i]) }
        ),
        LineSeries(
            id = "exp",
            label = "Expenses",
            color = Color(0xFFF59E0B),
            fillAlpha = 0.08f,
            strokeGradientColors = listOf(Color(0xFFFBBF24), Color(0xFFD97706)),
            points = listOf(38f, 42f, 50f, 45f, 55f, 52f, 60f, 58f, 62f, 65f, 70f, 68f)
                .mapIndexed { i, v -> LineDataPoint(x = i.toFloat(), y = v, label = Months[i]) }
        )
    ),
    contentDescription = "Monthly Revenue vs Expenses"
)

@Composable
fun LineChartDemoScreen() {
    val colors = LocalDemoColors.current

    var showFill by remember { mutableStateOf(true) }
    var curveType by remember { mutableStateOf(LineCurveType.MonotoneCubic) }
    var dataSet by remember { mutableStateOf(seededDataSet()) }
    var selectedIdx by rememberSaveable { mutableStateOf<Int?>(null) }

    // Stripping the area fill rewrites every series, so it is derived once per
    // change rather than on each recomposition — otherwise the chart is handed a
    // brand-new dataset each frame and can never skip.
    val visibleDataSet = remember(dataSet, showFill) {
        if (showFill) {
            dataSet
        } else {
            dataSet.copy(series = dataSet.series.map { it.copy(fillAlpha = 0f) })
        }
    }

    val chartStyle = remember(curveType) { LineChartStyle(curveType = curveType) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                            text = selectedIdx?.let { Months.getOrNull(it) }
                                ?: "Drag to explore",
                            fontSize = 13.sp,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        dataSet.series.forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    s.label,
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceMuted,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Canvas(Modifier.size(width = 18.dp, height = 4.dp)) {
                                    val cy = size.height / 2f
                                    val start = Offset(x = 0f, y = cy)
                                    val end = Offset(x = size.width, y = cy)
                                    if (s.strokeGradientColors.size >= 2) {
                                        drawLine(
                                            brush = Brush.horizontalGradient(s.strokeGradientColors),
                                            start = start,
                                            end = end,
                                            strokeWidth = size.height,
                                            cap = StrokeCap.Round
                                        )
                                    } else {
                                        drawLine(
                                            color = s.color,
                                            start = start,
                                            end = end,
                                            strokeWidth = size.height,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                            dashedGrid = true,
                            xLabels = Months
                        ),
                        crosshairConfig = themedCrosshair(),
                        selectedPointIndex = selectedIdx,
                        onPointSelected = { selectedIdx = it }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    curveType = if (curveType == LineCurveType.MonotoneCubic) {
                        LineCurveType.Linear
                    } else LineCurveType.MonotoneCubic
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
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
                onClick = { showFill = !showFill },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentWarm,
                    contentColor = colors.onAccentWarm
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
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

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { dataSet = monthlyDataSet() },
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
}

@Preview
@Composable
private fun LineChartDemoScreenPreview() {
    LineChartDemoScreen()
}
