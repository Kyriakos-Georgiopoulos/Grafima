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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.derivedStateOf
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
import io.grafima.charts.pie.ElbowCalloutPieSelectionRenderer
import io.grafima.charts.pie.PieAnimationConfig
import io.grafima.charts.pie.PieChart
import io.grafima.charts.pie.PieChartStyle
import io.grafima.charts.pie.PieDataSet
import io.grafima.charts.pie.PieEntry
import io.grafima.charts.pie.SliceBrush
import io.grafima.charts.pie.TooltipPieSelectionRenderer
import io.grafima.sample.theme.LocalDemoColors
import kotlin.random.Random

private val OceanBrush = SliceBrush.Linear(
    colors = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D))
)
private val EmeraldBrush = SliceBrush.Radial(
    colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
)
private val SunsetBrush = SliceBrush.Linear(
    colors = listOf(Color(0xFFFF512F), Color(0xFFF09819), Color(0xFFFFB75E)),
    angleDegrees = 90f
)
private val AmethystBrush = SliceBrush.Sweep(
    colors = listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))
)
private val RoyalBrush = SliceBrush.Linear(
    colors = listOf(Color(0xFF536976), Color(0xFF292E49)),
    angleDegrees = 135f
)

private val CoralBrush = SliceBrush.Linear(
    colors = listOf(Color(0xFFFF6A88), Color(0xFFFF99AC))
)
private val VioletBrush = SliceBrush.Radial(
    colors = listOf(Color(0xFF7F00FF), Color(0xFFE100FF))
)
private val LimeBrush = SliceBrush.Linear(
    colors = listOf(Color(0xFF7EC850), Color(0xFFD9E86B)),
    angleDegrees = 45f
)

// One per reachable slice, so an added slice never repeats a colour on screen.
private val SliceBrushes = listOf(
    OceanBrush, SunsetBrush, AmethystBrush, EmeraldBrush,
    RoyalBrush, CoralBrush, VioletBrush, LimeBrush
)

private const val MinSlices = 2
private const val MaxSlices = 8

private fun PieDataSet.plusEntry(): PieDataSet {
    val index = entries.size
    val id = ('A' + index).toString()
    val used = entries.mapTo(mutableSetOf()) { it.brush }
    return copy(
        entries = entries + PieEntry(
            id = id,
            label = "Product $id",
            value = Random.nextInt(50, 500).toFloat(),
            brush = SliceBrushes.firstOrNull { it !in used }
                ?: SliceBrushes[index % SliceBrushes.size]
        )
    )
}

private fun PieDataSet.minusEntry(): PieDataSet = copy(entries = entries.dropLast(1))

private fun initialPieDataSet() = PieDataSet(
    entries = listOf(
        PieEntry(id = "A", label = "Product A", value = 300f, brush = OceanBrush),
        PieEntry(id = "B", label = "Product B", value = 250f, brush = SunsetBrush),
        PieEntry(id = "C", label = "Product C", value = 400f, brush = AmethystBrush),
        PieEntry(id = "D", label = "Product D", value = 150f, brush = EmeraldBrush),
        PieEntry(id = "E", label = "Product E", value = 200f, brush = RoyalBrush)
    ),
    contentDescription = "Market Share Distribution"
)

internal class PieChartViewModel : ViewModel() {
    var dataSet by mutableStateOf(initialPieDataSet())
    var selectedSliceId by mutableStateOf<String?>(null)
    var isDonut by mutableStateOf(true)
    var useCalloutRenderer by mutableStateOf(true)
}

@Composable
internal fun PieChartDemoScreen(
    viewModel: PieChartViewModel = viewModel { PieChartViewModel() }
) {
    val colors = LocalDemoColors.current

    var dataSet by viewModel::dataSet
    var selectedSliceId by viewModel::selectedSliceId
    var isDonut by viewModel::isDonut
    var useCalloutRenderer by viewModel::useCalloutRenderer

    val selectedSliceData by remember {
        derivedStateOf { dataSet.entries.find { it.id == selectedSliceId } }
    }

    val activeRenderer = remember(useCalloutRenderer, colors) {
        val labelStyle = TextStyle(
            color = colors.tooltipText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (useCalloutRenderer) {
            ElbowCalloutPieSelectionRenderer(
                lineColor = colors.onSurfaceMuted,
                pillBackgroundColor = colors.tooltipBackground,
                textStyle = labelStyle
            )
        } else {
            TooltipPieSelectionRenderer(
                backgroundColor = colors.tooltipBackground,
                textStyle = labelStyle
            )
        }
    }

    val chartStyle = remember(isDonut) {
        PieChartStyle(
            donutRatio = if (isDonut) 0.5f else 0f,
            selectedScale = 1.05f,
            // Short of the card: callouts draw outside the radius, and
            // selectedScale pushes the active slice further out.
            fillFraction = 0.74f
        )
    }

    // Tighter than the defaults: a slice added to a chart already on screen should
    // land promptly, not at opening-cascade pace.
    val animationConfig = remember {
        PieAnimationConfig(
            initialEntrySpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            staggerDelayMs = 60L,
            startDelayMs = 60L
        )
    }

    val total = remember(dataSet) { dataSet.entries.sumOf { it.value.toInt() } }

    DemoScreenScaffold(
        controls = {
            DemoControls { buttonModifier ->
                DemoAddButton(
                    text = "Add Slice",
                    enabled = dataSet.entries.size < MaxSlices,
                    onClick = { dataSet = dataSet.plusEntry() },
                    modifier = buttonModifier
                )
                DemoRemoveButton(
                    text = "Remove Slice",
                    enabled = dataSet.entries.size > MinSlices,
                    onClick = {
                        selectedSliceId = null
                        dataSet = dataSet.minusEntry()
                    },
                    modifier = buttonModifier
                )
            }

            DemoControls { buttonModifier ->
                Button(
                    onClick = { isDonut = !isDonut },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        text = if (isDonut) "Pie Style" else "Donut Style",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { useCalloutRenderer = !useCalloutRenderer },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm,
                        contentColor = colors.onAccentWarm
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        text = if (useCalloutRenderer) "Pill Text" else "Callout Line",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        dataSet = dataSet.copy(
                            entries = dataSet.entries.map { entry ->
                                entry.copy(value = Random.nextInt(50, 500).toFloat())
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
                        text = "Update",
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
                            "Market Share",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.onSurface
                        )
                        Text(
                            text = selectedSliceData?.let { "Viewing metrics for ${it.label}" }
                                ?: "Tap a slice to inspect.",
                            fontSize = 13.sp,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PieChart(
                        dataSet = dataSet,
                        modifier = Modifier.fillMaxSize(),
                        style = chartStyle,
                        animationConfig = animationConfig,
                        selectionRenderer = activeRenderer,
                        selectedEntry = selectedSliceData,
                        onSliceSelected = { entry -> selectedSliceId = entry?.id },
                        centerContent = if (isDonut) {
                            {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$total",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = "Total",
                                        fontSize = 12.sp,
                                        color = colors.onSurfaceMuted
                                    )
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PieChartDemoScreenPreview() {
    PieChartDemoScreen()
}
