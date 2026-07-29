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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.bar.A11yConfig
import io.grafima.charts.bar.BarChart
import io.grafima.charts.bar.BarDataSet
import io.grafima.charts.bar.BarEntry
import io.grafima.charts.bar.BarOrientation
import io.grafima.charts.bar.TooltipSelectionRenderer
import io.grafima.sample.theme.LocalDemoColors
import io.grafima.sample.theme.themedBarAxis
import io.grafima.sample.theme.themedBarStyle
import kotlin.random.Random

private val OceanGradient = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D))
private val EmeraldGradient = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
private val SunsetGradient = listOf(Color(0xFFFF512F), Color(0xFFF09819), Color(0xFFFFB75E))
private val AmethystGradient = listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))

private val BarGradients =
    listOf(SunsetGradient, OceanGradient, AmethystGradient, EmeraldGradient)

private fun BarDataSet.randomized(): BarDataSet = copy(
    entries = entries.map { entry ->
        entry.copy(
            y = Random.nextInt(20, 110).toFloat(),
            gradientColors = BarGradients.random()
        )
    }
)

@Composable
fun BarChartDemoScreen() {
    val colors = LocalDemoColors.current

    var orientation by remember { mutableStateOf(BarOrientation.Horizontal) }

    var dataSet by remember {
        mutableStateOf(
            BarDataSet(
                entries = listOf(
                    BarEntry(id = "JAN", xLabel = "Jan", y = 45f, gradientColors = OceanGradient),
                    BarEntry(id = "FEB", xLabel = "Feb", y = 80f, gradientColors = SunsetGradient),
                    BarEntry(id = "MAR", xLabel = "Mar", y = 55f, gradientColors = AmethystGradient),
                    BarEntry(id = "APR", xLabel = "Apr", y = 95f, gradientColors = SunsetGradient),
                    BarEntry(id = "MAY", xLabel = "May", y = 65f, gradientColors = EmeraldGradient)
                ),
                contentDescription = "Q1 and Q2 Revenue Trends"
            )
        )
    }

    var selectedBarId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedBarData by remember {
        derivedStateOf { dataSet.entries.find { it.id == selectedBarId } }
    }

    val customPillRenderer = remember(colors) {
        TooltipSelectionRenderer(
            backgroundColor = colors.accent,
            textStyle = TextStyle(
                color = colors.onAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            ),
            cornerRadius = 16.dp,
            horizontalPadding = 16.dp,
            verticalPadding = 8.dp
        )
    }

    val a11yConfig = remember {
        A11yConfig(
            chartDescriptionBuilder = { "Financial Revenue Chart for ${it.contentDescription}." },
            barDescriptionBuilder = { "In ${it.xLabel}, revenue was $${it.y.toInt()} thousand dollars." },
            selectedStateDescription = { entry ->
                entry?.let { "You are inspecting ${it.xLabel}." }
                    ?: "Double tap and drag to explore metrics."
            }
        )
    }

    DemoScreenScaffold(
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(
                    BarOrientation.Vertical to "Vertical",
                    BarOrientation.Horizontal to "Horizontal"
                ).forEach { (orient, label) ->
                    val selected = orientation == orient
                    Button(
                        onClick = {
                            selectedBarId = null
                            orientation = orient
                            dataSet = dataSet.randomized()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) colors.onSurface else Color.Transparent,
                            contentColor = if (selected) colors.background else colors.onSurfaceMuted
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp
                        ),
                        // Material's default 24dp side padding leaves no room
                        // for "Horizontal" in the narrow wide-layout column.
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        controls = {
            Button(
                onClick = { dataSet = dataSet.randomized() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.onSurface,
                    contentColor = colors.background
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text("Update Data", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface, shape = RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Revenue Overview",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.onSurface
                        )
                        Text(
                            text = selectedBarData?.let { "Viewing metrics for ${it.xLabel}" }
                                ?: "Tap a bar to inspect metrics.",
                            fontSize = 13.sp,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    selectedBarData?.let { entry ->
                        Text(
                            text = "$${entry.y.toInt()}k",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onSurface
                        )
                    }
                }

                BarChart(
                    dataSet = dataSet,
                    modifier = Modifier.fillMaxSize(),
                    orientation = orientation,
                    style = themedBarStyle(),
                    axisConfig = themedBarAxis(),
                    selectionRenderer = customPillRenderer,
                    selectedEntry = selectedBarData,
                    onBarSelected = { entry -> selectedBarId = entry?.id },
                    a11yConfig = a11yConfig
                )
            }
        }
    }
}

@Preview
@Composable
private fun BarChartDemoScreenPreview() {
    BarChartDemoScreen()
}
