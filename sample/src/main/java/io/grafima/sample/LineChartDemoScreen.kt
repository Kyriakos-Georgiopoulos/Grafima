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
import io.grafima.charts.LineAxisConfig
import io.grafima.charts.LineChart
import io.grafima.charts.LineChartStyle
import io.grafima.charts.LineCurveType
import io.grafima.charts.LineDataPoint
import io.grafima.charts.LineDataSet
import io.grafima.charts.LineSeries

// ==========================================
// 5. DEMO
// ==========================================

@Composable
fun LineChartDemoScreen() {
    val months =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFFF59E0B), Color(0xFF10B981),
        Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899),
        Color(0xFF06B6D4), Color(0xFF84CC16)
    )

    fun randomSeries(
        id: String,
        label: String,
        base: Int,
        variance: Int,
        showFill: Boolean
    ): LineSeries {
        val c1 = palette.random()
        val c2 = palette.filter { it != c1 }.random()
        return LineSeries(
            id = id, label = label, color = c1, fillAlpha = if (showFill) 0.10f else 0f,
            strokeGradientColors = listOf(c1, c2),
            points = months.mapIndexed { i, m ->
                LineDataPoint(
                    x = i.toFloat(),
                    y = (base + (-variance..variance).random()).toFloat(),
                    label = m
                )
            }
        )
    }

    var showFill by remember { mutableStateOf(true) }
    var curveType by remember { mutableStateOf(LineCurveType.MonotoneCubic) }

    var dataSet by remember {
        mutableStateOf(
            LineDataSet(
                series = listOf(
                    LineSeries(
                        id = "rev", label = "Revenue", color = Color(0xFF6366F1), fillAlpha = 0.10f,
                        strokeGradientColors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
                        points = listOf(
                            42f,
                            55f,
                            48f,
                            72f,
                            68f,
                            85f,
                            90f,
                            78f,
                            95f,
                            110f,
                            105f,
                            120f
                        )
                            .mapIndexed { i, v ->
                                LineDataPoint(
                                    x = i.toFloat(),
                                    y = v,
                                    label = months[i]
                                )
                            }),
                    LineSeries(
                        id = "exp",
                        label = "Expenses",
                        color = Color(0xFFF59E0B),
                        fillAlpha = 0.08f,
                        strokeGradientColors = listOf(Color(0xFFFBBF24), Color(0xFFD97706)),
                        points = listOf(38f, 42f, 50f, 45f, 55f, 52f, 60f, 58f, 62f, 65f, 70f, 68f)
                            .mapIndexed { i, v ->
                                LineDataPoint(
                                    x = i.toFloat(),
                                    y = v,
                                    label = months[i]
                                )
                            })
                ),
                contentDescription = "Monthly Revenue vs Expenses"
            )
        )
    }

    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(Color.White, RoundedCornerShape(24.dp))
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
                            color = Color(0xFF111827)
                        )
                        Text(
                            if (selectedIdx != null && selectedIdx!! < months.size) months[selectedIdx!!] else "Drag to explore",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
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
                                    color = Color(0xFF6B7280),
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Canvas(Modifier.size(width = 18.dp, height = 4.dp)) {
                                    val cy = size.height / 2f
                                    if (s.strokeGradientColors.size >= 2) {
                                        drawLine(
                                            brush = Brush.horizontalGradient(s.strokeGradientColors),
                                            start = Offset(x = 0f, y = cy),
                                            end = Offset(x = size.width, y = cy),
                                            strokeWidth = size.height,
                                            cap = StrokeCap.Round
                                        )
                                    } else {
                                        drawLine(
                                            color = s.color,
                                            start = Offset(x = 0f, y = cy),
                                            end = Offset(x = size.width, y = cy),
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
                        dataSet = if (showFill) dataSet else dataSet.copy(series = dataSet.series.map {
                            it.copy(
                                fillAlpha = 0f
                            )
                        }),
                        modifier = Modifier.fillMaxSize(),
                        style = LineChartStyle(curveType = curveType),
                        axisConfig = LineAxisConfig(
                            yTickCount = 5,
                            dashedGrid = true,
                            xLabelFormatter = { months.getOrElse(it.toInt()) { "" } }),
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
                    curveType =
                        if (curveType == LineCurveType.MonotoneCubic) LineCurveType.Linear else LineCurveType.MonotoneCubic
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    if (showFill) "No Fill" else "Area Fill",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF78350F),
                    maxLines = 1
                )
            }
            Button(
                onClick = {
                    dataSet = LineDataSet(
                        series = listOf(
                            randomSeries(
                                id = "rev",
                                label = "Revenue",
                                base = 80,
                                variance = 40,
                                showFill = showFill
                            ),
                            randomSeries(
                                id = "exp",
                                label = "Expenses",
                                base = 55,
                                variance = 25,
                                showFill = showFill
                            )
                        ),
                        contentDescription = "Monthly Revenue vs Expenses"
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) { Text("Randomize", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LineChartDemoScreenPreview() {
    LineChartDemoScreen()
}
