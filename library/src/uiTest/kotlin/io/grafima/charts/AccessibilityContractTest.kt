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

package io.grafima.charts

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.grafima.charts.bar.BarChart
import io.grafima.charts.bar.BarDataSet
import io.grafima.charts.bar.BarEntry
import io.grafima.charts.gauge.GaugeChart
import io.grafima.charts.line.LineChart
import io.grafima.charts.line.LineDataPoint
import io.grafima.charts.line.LineDataSet
import io.grafima.charts.line.LineSeries
import io.grafima.charts.pie.PieChart
import io.grafima.charts.pie.PieDataSet
import io.grafima.charts.pie.PieEntry
import io.grafima.charts.radar.RadarAxis
import io.grafima.charts.radar.RadarChart
import io.grafima.charts.radar.RadarDataSet
import io.grafima.charts.radar.RadarSeries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-chart accessibility guarantees. The per-chart suites verify specific
 * labels and actions; these pin the contracts every chart must satisfy, so a
 * new chart type can't ship without them.
 */
@OptIn(ExperimentalTestApi::class)
class AccessibilityContractTest {

    private val barData = BarDataSet(
        entries = listOf(
            BarEntry(id = "jan", xLabel = "Jan", y = 45f),
            BarEntry(id = "feb", xLabel = "Feb", y = 80f),
            BarEntry(id = "mar", xLabel = "Mar", y = 60f)
        ),
        contentDescription = "Monthly revenue"
    )

    private val pieData = PieDataSet(
        entries = listOf(
            PieEntry(id = "design", label = "Design", value = 30f),
            PieEntry(id = "dev", label = "Development", value = 70f)
        ),
        contentDescription = "Team budget"
    )

    private val radarData = RadarDataSet(
        axes = listOf(RadarAxis(id = "speed", label = "Speed")),
        series = listOf(
            RadarSeries(id = "s1", label = "Model A", values = mapOf("speed" to 80f)),
            RadarSeries(id = "s2", label = "Model B", values = mapOf("speed" to 40f))
        ),
        contentDescription = "Model comparison"
    )

    private val lineData = LineDataSet(
        series = listOf(
            LineSeries(
                id = "revenue",
                label = "Revenue",
                points = listOf(LineDataPoint(0f, 10f), LineDataPoint(1f, 25f))
            )
        ),
        contentDescription = "Quarterly revenue"
    )

    /** Every chart, keyed by name so failures identify the culprit. */
    private val allCharts: List<Pair<String, @Composable () -> Unit>> = listOf(
        "BarChart" to { BarChart(dataSet = barData, modifier = Modifier.size(300.dp)) },
        "PieChart" to { PieChart(dataSet = pieData, modifier = Modifier.size(300.dp)) },
        "RadarChart" to { RadarChart(dataSet = radarData, modifier = Modifier.size(300.dp)) },
        "LineChart" to { LineChart(dataSet = lineData, modifier = Modifier.size(300.dp)) },
        "GaugeChart" to { GaugeChart(value = 42f, modifier = Modifier.size(300.dp)) }
    )

    @Test
    fun every_chart_exposes_a_content_description() {
        for ((name, chart) in allCharts) {
            runComposeUiTest {
                setContent { chart() }
                val nodes = onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
                ).fetchSemanticsNodes()
                assertTrue(nodes.isNotEmpty(), "$name exposes no content description")
            }
        }
    }

    @Test
    fun every_chart_merges_into_a_single_accessibility_node() {
        // mergeDescendants keeps screen readers from walking chart internals.
        for ((name, chart) in allCharts) {
            runComposeUiTest {
                setContent { chart() }
                val nodes = onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
                ).fetchSemanticsNodes()
                assertEquals(1, nodes.size, "$name exposes ${nodes.size} nodes, expected a merged one")
            }
        }
    }

    @Test
    fun custom_action_labels_are_unique_within_a_chart() {
        // Duplicate labels collapse in the TalkBack action menu, making
        // entries unreachable.
        for ((name, chart) in listOf(allCharts[0], allCharts[1], allCharts[2], allCharts[3])) {
            runComposeUiTest {
                setContent { chart() }
                val labels = onChartNode().customActionLabels()
                assertEquals(
                    labels.size, labels.distinct().size,
                    "$name has duplicate custom action labels: $labels"
                )
            }
        }
    }

    @Test
    fun the_bar_chart_offers_one_select_action_per_entry() = runComposeUiTest {
        setContent { BarChart(dataSet = barData, modifier = Modifier.size(300.dp)) }
        val selectActions = onChartNode().customActionLabels().filter { it.startsWith("Select ") }
        assertEquals(barData.entries.size, selectActions.size)
        for (entry in barData.entries) {
            assertTrue(
                "Select ${entry.xLabel}" in selectActions,
                "no select action for ${entry.xLabel}"
            )
        }
    }

    @Test
    fun the_description_changes_when_the_data_changes() = runComposeUiTest {
        // The charts are polite live regions; that only helps if the announced
        // text actually differs after an update.
        var data by mutableStateOf(barData)
        setContent { BarChart(dataSet = data, modifier = Modifier.size(300.dp)) }
        waitForIdle()
        onNodeWithContentDescription("Feb value is 80", substring = true).assertExists()

        data = BarDataSet(
            entries = listOf(BarEntry(id = "jan", xLabel = "Jan", y = 999f)),
            contentDescription = "Monthly revenue"
        )
        waitForIdle()

        onNodeWithContentDescription("Jan value is 999", substring = true).assertExists()
        onNodeWithContentDescription("Feb value is 80", substring = true).assertDoesNotExist()
    }

    @Test
    fun the_description_reports_the_current_selection_state() = runComposeUiTest {
        var selected by mutableStateOf<BarEntry?>(null)
        setContent {
            BarChart(
                dataSet = barData,
                modifier = Modifier.size(300.dp),
                selectedEntry = selected,
                onBarSelected = { selected = it }
            )
        }
        waitForIdle()
        onNodeWithContentDescription("No bar selected", substring = true).assertExists()

        selected = barData.entries[1]
        waitForIdle()
        onNodeWithContentDescription("Currently selected: Feb", substring = true).assertExists()
    }
}
