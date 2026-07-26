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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.bar.A11yConfig
import io.grafima.charts.bar.BarChart
import io.grafima.charts.bar.BarDataSet
import io.grafima.charts.bar.BarEntry
import io.grafima.charts.bar.BarOrientation
import io.grafima.charts.bar.TooltipSelectionRenderer
import kotlin.random.Random

// ==========================================
// 5. DEMO IMPLEMENTATION
// ==========================================

@Composable
fun BarChartDemoScreen() {
    val oceanGradient = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D))
    val emeraldGradient = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    val sunsetGradient = listOf(Color(0xFFFF512F), Color(0xFFF09819), Color(0xFFFFB75E))
    val amethystGradient = listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))

    var orientation by remember { mutableStateOf(BarOrientation.Horizontal) }

    var dataSet by remember {
        mutableStateOf(
            BarDataSet(
                entries = listOf(
                    BarEntry(id = "JAN", xLabel = "Jan", y = 45f, gradientColors = oceanGradient),
                    BarEntry(id = "FEB", xLabel = "Feb", y = 80f, gradientColors = sunsetGradient),
                    BarEntry(
                        id = "MAR",
                        xLabel = "Mar",
                        y = 55f,
                        gradientColors = amethystGradient
                    ),
                    BarEntry(id = "APR", xLabel = "Apr", y = 95f, gradientColors = sunsetGradient),
                    BarEntry(id = "MAY", xLabel = "May", y = 65f, gradientColors = emeraldGradient)
                ),
                contentDescription = "Q1 and Q2 Revenue Trends"
            )
        )
    }

    var selectedBarId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedBarData by remember {
        derivedStateOf { dataSet.entries.find { it.id == selectedBarId } }
    }

    val customPillRenderer = remember {
        TooltipSelectionRenderer(
            backgroundColor = Color(0xFF4F46E5),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            ),
            cornerRadius = 16.dp,
            horizontalPadding = 16.dp,
            verticalPadding = 8.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
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
                        dataSet = dataSet.copy(
                            entries = dataSet.entries.map { entry ->
                                entry.copy(
                                    y = Random.nextInt(20, 110).toFloat(),
                                    gradientColors = listOf(
                                        sunsetGradient,
                                        oceanGradient,
                                        amethystGradient,
                                        emeraldGradient
                                    ).random()
                                )
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(0xFF111827) else Color.Transparent,
                        contentColor = if (selected) Color.White else Color(0xFF6B7280)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(Color.White, shape = RoundedCornerShape(24.dp))
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
                            color = Color(0xFF111827)
                        )
                        Text(
                            text = if (selectedBarData != null) "Viewing metrics for ${selectedBarData?.xLabel}" else "Tap a bar to inspect metrics.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (selectedBarData != null) {
                        Text(
                            text = "$${selectedBarData?.y?.toInt()}k",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF111827)
                        )
                    }
                }

                BarChart(
                    dataSet = dataSet,
                    modifier = Modifier.fillMaxSize(),
                    orientation = orientation,
                    selectionRenderer = customPillRenderer,
                    selectedEntry = selectedBarData,
                    onBarSelected = { entry -> selectedBarId = entry?.id },
                    a11yConfig = A11yConfig(
                        chartDescriptionBuilder = { "Financial Revenue Chart for ${it.contentDescription}." },
                        barDescriptionBuilder = { "In ${it.xLabel}, revenue was $${it.y.toInt()} thousand dollars." },
                        selectedStateDescription = { entry ->
                            entry?.let { "You are inspecting ${it.xLabel}." }
                                ?: "Double tap and drag to explore metrics."
                        }
                    )
                )
            }
        }

        // The HorizontalDivider and one Spacer were removed here.
        // A single Spacer is kept to maintain a clean gap between the chart and button.
        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                val newEntries = dataSet.entries.map { entry ->
                    entry.copy(
                        y = Random.nextInt(20, 110).toFloat(),
                        gradientColors = listOf(
                            sunsetGradient,
                            oceanGradient,
                            amethystGradient,
                            emeraldGradient
                        ).random()
                    )
                }
                dataSet = dataSet.copy(entries = newEntries)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text("Update Data", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview
@Composable
private fun BarChartDemoScreenPreview() {
    BarChartDemoScreen()
}
