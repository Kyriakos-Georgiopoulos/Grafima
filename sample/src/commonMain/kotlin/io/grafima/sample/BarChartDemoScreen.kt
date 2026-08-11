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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.grafima.charts.bar.A11yConfig
import io.grafima.charts.bar.AnimationConfig
import io.grafima.charts.bar.BarChart
import io.grafima.charts.bar.BarDataSet
import io.grafima.charts.bar.BarEntry
import io.grafima.charts.bar.BarGroupMode
import io.grafima.charts.bar.BarOrientation
import io.grafima.charts.bar.TooltipSelectionRenderer
import io.grafima.charts.bar.spokenSeriesLabel
import io.grafima.sample.theme.LocalDemoColors
import io.grafima.sample.theme.ProvideDemoLayout
import io.grafima.sample.theme.themedBarAxis
import io.grafima.sample.theme.themedBarStyle
import kotlin.random.Random

private val OceanGradient = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D))
private val EmeraldGradient = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
private val SunsetGradient = listOf(Color(0xFFFF512F), Color(0xFFF09819), Color(0xFFFFB75E))
private val AmethystGradient = listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))

private val BarGradients =
    listOf(SunsetGradient, OceanGradient, AmethystGradient, EmeraldGradient)

private const val MinBars = 2
private const val MaxBars = 12

private fun BarDataSet.randomized(): BarDataSet = copy(
    entries = entries.map { entry ->
        // A series is told apart by its colour, so only a loose bar takes a new one.
        val standalone = entry.seriesId == null
        entry.copy(
            y = Random.nextInt(20, 110).toFloat(),
            gradientColors = if (standalone) BarGradients.random() else entry.gradientColors
        )
    }
)

private fun BarDataSet.plusEntry(): BarDataSet {
    val label = MonthLabels.getOrElse(entries.size) { "M${entries.size + 1}" }
    return copy(
        entries = entries + BarEntry(
            id = label.uppercase(),
            xLabel = label,
            y = Random.nextInt(20, 110).toFloat(),
            gradientColors = BarGradients.random()
        )
    )
}

private fun BarDataSet.minusEntry(): BarDataSet = copy(entries = entries.dropLast(1))

private fun initialBarDataSet() = BarDataSet(
    entries = listOf(
        BarEntry(id = "JAN", xLabel = "Jan", y = 45f, gradientColors = OceanGradient),
        BarEntry(id = "FEB", xLabel = "Feb", y = 80f, gradientColors = SunsetGradient),
        BarEntry(id = "MAR", xLabel = "Mar", y = 55f, gradientColors = AmethystGradient),
        BarEntry(id = "APR", xLabel = "Apr", y = 95f, gradientColors = SunsetGradient),
        BarEntry(id = "MAY", xLabel = "May", y = 65f, gradientColors = EmeraldGradient)
    ),
    contentDescription = "Q1 and Q2 Revenue Trends"
)

private val RevenueGradient = OceanGradient
private val CostGradient = SunsetGradient

private fun groupedBarDataSet(mode: BarGroupMode) = BarDataSet(
    entries = listOf("Q1" to 45f, "Q2" to 80f, "Q3" to 55f, "Q4" to 95f)
        .flatMap { (quarter, revenue) ->
            listOf(
                BarEntry(
                    id = "$quarter-rev",
                    xLabel = quarter,
                    y = revenue,
                    gradientColors = RevenueGradient,
                    seriesId = "rev",
                    seriesLabel = "Revenue"
                ),
                BarEntry(
                    id = "$quarter-cost",
                    xLabel = quarter,
                    y = revenue * 0.62f,
                    gradientColors = CostGradient,
                    seriesId = "cost",
                    seriesLabel = "Cost"
                )
            )
        },
    contentDescription = "Revenue against cost by quarter",
    mode = mode
)

internal enum class DemoGrouping(val label: String) {
    Single("Single"),
    Grouped("Grouped"),
    Stacked("Stacked");

    fun dataSet(): BarDataSet = when (this) {
        Single -> initialBarDataSet()
        Grouped -> groupedBarDataSet(BarGroupMode.Grouped)
        Stacked -> groupedBarDataSet(BarGroupMode.Stacked)
    }
}

internal class BarChartViewModel : ViewModel() {
    var orientation by mutableStateOf(BarOrientation.Horizontal)
    var grouping by mutableStateOf(DemoGrouping.Grouped)
        private set
    var dataSet by mutableStateOf(DemoGrouping.Grouped.dataSet())
    var selectedBarId by mutableStateOf<String?>(null)

    fun selectGrouping(next: DemoGrouping) {
        if (next == grouping) return
        grouping = next
        dataSet = next.dataSet()
        selectedBarId = null
    }
}

@Composable
internal fun BarChartDemoScreen(
    viewModel: BarChartViewModel = viewModel { BarChartViewModel() }
) {
    val colors = LocalDemoColors.current

    val dataSet = viewModel.dataSet
    val orientation = viewModel.orientation

    val selectedBarData by remember(viewModel) {
        derivedStateOf { viewModel.dataSet.entries.find { it.id == viewModel.selectedBarId } }
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

    // Tighter than the defaults: a bar added to a chart already on screen should
    // land promptly, not at opening-cascade pace.
    val animationConfig = remember {
        AnimationConfig(
            initialEntrySpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            staggerDelayMs = 60L,
            startDelayMs = 80L
        )
    }

    val a11yConfig = remember {
        A11yConfig(
            chartDescriptionBuilder = { "Financial Revenue Chart for ${it.contentDescription}" },
            // Without the series, both bars of a group read the same.
            selectedStateDescription = { entry ->
                entry?.let {
                    val measure = it.spokenSeriesLabel ?: "revenue"
                    "${spokenMonth(it.xLabel)}, $measure: $${it.y.toInt()} thousand dollars."
                } ?: "No bar selected. Use the actions menu to choose a bar."
            }
        )
    }

    DemoScreenScaffold(
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoSegmentedControl(
                    groupName = "Orientation",
                    options = listOf(
                        BarOrientation.Vertical to "Vertical",
                        BarOrientation.Horizontal to "Horizontal"
                    ),
                    selected = orientation,
                    onSelect = { orient ->
                        viewModel.selectedBarId = null
                        viewModel.orientation = orient
                        viewModel.dataSet = dataSet.randomized()
                    }
                )
                DemoSegmentedControl(
                    groupName = "Grouping",
                    options = DemoGrouping.entries.map { it to it.label },
                    selected = viewModel.grouping,
                    onSelect = viewModel::selectGrouping
                )
            }
        },
        controls = {
            DemoControls { buttonModifier ->
                DemoAddButton(
                    text = "Add Bar",
                    enabled = dataSet.entries.size < MaxBars,
                    onClick = { viewModel.dataSet = dataSet.plusEntry() },
                    modifier = buttonModifier
                )
                DemoRemoveButton(
                    text = "Remove Bar",
                    enabled = dataSet.entries.size > MinBars,
                    onClick = {
                        viewModel.selectedBarId = null
                        viewModel.dataSet = dataSet.minusEntry()
                    },
                    modifier = buttonModifier
                )
            }

            Button(
                onClick = { viewModel.dataSet = dataSet.randomized() },
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
                            text = selectedBarData?.let { entry ->
                                entry.spokenSeriesLabel
                                    ?.let { "Viewing $it for ${entry.xLabel}" }
                                    ?: "Viewing metrics for ${entry.xLabel}"
                            } ?: "Tap a bar to inspect metrics.",
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
                    onBarSelected = { entry -> viewModel.selectedBarId = entry?.id },
                    animationConfig = animationConfig,
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

@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
private fun BarChartDemoScreenLandscapePreview() {
    ProvideDemoLayout { BarChartDemoScreen() }
}
