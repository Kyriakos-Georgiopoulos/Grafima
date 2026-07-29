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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.radar.RadarAxis
import io.grafima.charts.radar.RadarChart
import io.grafima.charts.radar.RadarDataSet
import io.grafima.charts.radar.RadarGridStyle
import io.grafima.charts.radar.RadarSeries
import io.grafima.sample.theme.DemoColors
import io.grafima.sample.theme.LocalDemoColors
import io.grafima.sample.theme.LocalIsWideLayout
import io.grafima.sample.theme.themedRadarStyle
import kotlin.random.Random

private val DefaultAxes = listOf(
    RadarAxis(id = "atk", label = "Attack"),
    RadarAxis(id = "def", label = "Defense"),
    RadarAxis(id = "spd", label = "Speed"),
    RadarAxis(id = "mag", label = "Magic"),
    RadarAxis(id = "sta", label = "Stamina"),
    RadarAxis(id = "lck", label = "Luck")
)

private val ExtraClassNames = listOf("Paladin", "Ranger", "Bard")
private val ExtraClassColors = listOf(
    Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899)
)

@Composable
fun RadarChartDemoScreen() {
    val colors = LocalDemoColors.current

    var dataSet by remember {
        mutableStateOf(
            RadarDataSet(
                axes = DefaultAxes,
                series = listOf(
                    RadarSeries(
                        id = "warrior",
                        label = "Warrior",
                        values = mapOf(
                            "atk" to 88f, "def" to 82f, "spd" to 55f,
                            "mag" to 20f, "sta" to 75f, "lck" to 45f
                        ),
                        color = Color(0xFFEF4444),
                        fillAlpha = 0.15f
                    ),
                    RadarSeries(
                        id = "mage",
                        label = "Mage",
                        values = mapOf(
                            "atk" to 30f, "def" to 35f, "spd" to 60f,
                            "mag" to 95f, "sta" to 45f, "lck" to 70f
                        ),
                        color = Color(0xFF6366F1),
                        fillAlpha = 0.15f
                    ),
                    RadarSeries(
                        id = "rogue",
                        label = "Rogue",
                        values = mapOf(
                            "atk" to 65f, "def" to 30f, "spd" to 92f,
                            "mag" to 40f, "sta" to 50f, "lck" to 85f
                        ),
                        color = Color(0xFF10B981),
                        fillAlpha = 0.15f
                    )
                ),
                contentDescription = "Character Class Comparison"
            )
        )
    }

    var selectedSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSeriesData by remember {
        derivedStateOf { dataSet.series.find { it.id == selectedSeriesId } }
    }

    var isPolygonGrid by remember { mutableStateOf(true) }

    DemoScreenScaffold(
        controls = {
            DemoControls { buttonModifier ->
                Button(
                    onClick = { isPolygonGrid = !isPolygonGrid },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        text = if (isPolygonGrid) "Circle Grid" else "Polygon Grid",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        dataSet = dataSet.copy(
                            series = dataSet.series.map { s ->
                                s.copy(
                                    values = dataSet.axes.associate { a ->
                                        a.id to Random.nextInt(15, 100).toFloat()
                                    }
                                )
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.onSurface,
                        contentColor = colors.background
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        text = "Randomize",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        val currentSeries = dataSet.series
                        dataSet = if (currentSeries.size >= 5) {
                            dataSet.copy(series = currentSeries.dropLast(1))
                        } else {
                            val index = currentSeries.size - 3
                            val newSeries = RadarSeries(
                                id = "class_${currentSeries.size}",
                                label = ExtraClassNames.getOrElse(index) {
                                    "Class ${currentSeries.size + 1}"
                                },
                                values = dataSet.axes.associate { a ->
                                    a.id to Random.nextInt(20, 95).toFloat()
                                },
                                color = ExtraClassColors.getOrElse(index) { Color(0xFF64748B) },
                                fillAlpha = 0.15f
                            )
                            dataSet.copy(series = currentSeries + newSeries)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm,
                        contentColor = colors.onAccentWarm
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        text = if (dataSet.series.size >= 5) "Remove" else "Add Class",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface, shape = RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            // The radar is sized off the card's shorter side — its height in
            // landscape — so anything stacked above it costs diameter.
            if (LocalIsWideLayout.current) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.width(148.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        RadarCaption(selectedSeriesData, colors)
                        Spacer(Modifier.height(16.dp))
                        RadarLegend(
                            series = dataSet.series,
                            selected = selectedSeriesData,
                            stacked = true,
                            colors = colors
                        )
                    }

                    RadarPlot(
                        dataSet = dataSet,
                        isPolygonGrid = isPolygonGrid,
                        selected = selectedSeriesData,
                        onSelect = { s -> selectedSeriesId = s?.id },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    RadarCaption(selectedSeriesData, colors)

                    Spacer(Modifier.height(16.dp))

                    RadarLegend(
                        series = dataSet.series,
                        selected = selectedSeriesData,
                        stacked = false,
                        colors = colors
                    )

                    Spacer(Modifier.height(12.dp))

                    RadarPlot(
                        dataSet = dataSet,
                        isPolygonGrid = isPolygonGrid,
                        selected = selectedSeriesData,
                        onSelect = { s -> selectedSeriesId = s?.id },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarCaption(selected: RadarSeries?, colors: DemoColors) {
    Column {
        Text(
            "Character Stats",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onSurface
        )
        Text(
            text = selected?.let { "Viewing ${it.label} build" }
                ?: "Tap a vertex to inspect a class.",
            fontSize = 13.sp,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RadarLegend(
    series: List<RadarSeries>,
    selected: RadarSeries?,
    stacked: Boolean,
    colors: DemoColors
) {
    val entries: @Composable () -> Unit = {
        series.forEach { s ->
            // Emphasis rides on weight, not on a paler colour: a grey light
            // enough to read as "off" is too light to read.
            val emphasised = selected == null || selected.id == s.id
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = s.color, radius = size.minDimension / 2f)
                }
                Text(
                    text = s.label,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp),
                    fontWeight = if (selected?.id == s.id) FontWeight.Bold else FontWeight.Normal,
                    color = if (emphasised) colors.onSurface else colors.onSurfaceMuted
                )
            }
        }
    }

    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { entries() }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) { entries() }
    }
}

@Composable
private fun RadarPlot(
    dataSet: RadarDataSet,
    isPolygonGrid: Boolean,
    selected: RadarSeries?,
    onSelect: (RadarSeries?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        RadarChart(
            dataSet = dataSet,
            modifier = Modifier.fillMaxSize(),
            style = themedRadarStyle(
                gridStyle = if (isPolygonGrid) {
                    RadarGridStyle.Polygon
                } else RadarGridStyle.Circular,
                gridLevels = 5,
                fillFraction = 0.9f,
                dotRadius = 5.dp
            ),
            selectedSeries = selected,
            onSeriesSelected = onSelect
        )
    }
}

@Preview
@Composable
private fun RadarChartDemoScreenPreview() {
    RadarChartDemoScreen()
}
