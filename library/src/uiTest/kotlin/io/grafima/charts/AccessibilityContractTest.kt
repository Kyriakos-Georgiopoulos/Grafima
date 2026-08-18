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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.grafima.charts.bar.BarChart
import io.grafima.charts.bar.BarDataSet
import io.grafima.charts.bar.BarEntry
import io.grafima.charts.gauge.GaugeChart
import io.grafima.charts.line.LineA11yConfig
import io.grafima.charts.line.LineChart
import io.grafima.charts.line.LineDataPoint
import io.grafima.charts.line.LineDataSet
import io.grafima.charts.line.LineSeries
import io.grafima.charts.line.SelectedPoint
import io.grafima.charts.pie.PieChart
import io.grafima.charts.pie.PieDataSet
import io.grafima.charts.pie.PieEntry
import io.grafima.charts.radar.RadarAxis
import io.grafima.charts.radar.RadarChart
import io.grafima.charts.radar.RadarDataSet
import io.grafima.charts.radar.RadarSeries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun every_chart_exposes_exactly_one_described_accessibility_node() {
        for ((name, chart) in allCharts) {
            runComposeUiTest {
                setContent { chart() }
                val nodes = onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
                ).fetchSemanticsNodes()
                assertEquals(1, nodes.size, "$name exposed ${nodes.size} described nodes")
            }
        }
    }

    @Test
    fun interactive_charts_announce_updates_as_polite_live_regions() {
        for ((name, chart) in allCharts.filterNot { it.first == "GaugeChart" }) {
            runComposeUiTest {
                setContent { chart() }
                onChartNode().assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.LiveRegion,
                        LiveRegionMode.Polite
                    ),
                    messagePrefixOnError = { "$name is not a polite live region" }
                )
            }
        }
    }

    /**
     * Per-item detail each chart must keep out of its description: every item's own
     * value, plus for the line chart the point text that belongs in
     * `stateDescription` instead.
     */
    private fun forbiddenPerItemText(): Map<String, List<String>> = mapOf(
        "BarChart" to barData.entries.map { it.y.toInt().toString() },
        "PieChart" to pieData.entries.map { it.value.toInt().toString() },
        "RadarChart" to radarData.series.flatMap { s ->
            radarData.axes.map { (s.values[it.id] ?: 0f).toInt().toString() }
        },
        "LineChart" to lineData.series.first().points.indices.map { index ->
            val selected = lineData.series.mapNotNull { s ->
                s.points.getOrNull(index)?.let { SelectedPoint(s, it) }
            }
            LineA11yConfig().selectedPointDescriptionBuilder(index, selected)
        }
    )

    @Test
    fun a_chart_description_does_not_read_out_every_entry() {
        val forbidden = forbiddenPerItemText()
        for ((name, chart) in allCharts) {
            val banned = forbidden[name] ?: continue
            runComposeUiTest {
                setContent { chart() }
                val description = onChartNode()
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.ContentDescription]
                    .joinToString(" ")
                banned.forEach { text ->
                    assertFalse(
                        description.contains(text.trim()),
                        "$name reads out \"${text.trim()}\" in its live-region description"
                    )
                }
            }
        }
    }

    @Test
    fun custom_action_labels_are_unique_within_a_chart() {
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
    fun the_announced_text_changes_when_the_data_changes() = runComposeUiTest {
        var data by mutableStateOf(barData)
        setContent { BarChart(dataSet = data, modifier = Modifier.size(300.dp)) }
        waitForIdle()
        assertTrue(onChartNode().customActionLabels().contains("Select Feb"))

        data = BarDataSet(
            entries = listOf(BarEntry(id = "jan", xLabel = "Jan", y = 999f)),
            contentDescription = "Monthly revenue"
        )
        waitForIdle()

        val labels = onChartNode().customActionLabels()
        assertTrue(labels.contains("Select Jan"))
        assertFalse(labels.contains("Select Feb"))
        onNodeWithContentDescription("1 bars", substring = true).assertExists()
    }

    @Test
    fun selection_is_reported_as_state_rather_than_in_the_description() {
        runComposeUiTest {
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
            val chart = onChartNode()
            chart.assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription, "No bar selected."
                )
            )

            selected = barData.entries[1]
            waitForIdle()
            chart.assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription, "Currently selected: Feb, 80."
                )
            )
            onNodeWithContentDescription("Currently selected", substring = true)
                .assertDoesNotExist()
        }
    }

    @Test
    fun every_chart_declares_an_image_role() {
        for ((name, chart) in allCharts) {
            runComposeUiTest {
                setContent { chart() }
                onChartNodeWithRole().assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image),
                    messagePrefixOnError = { "$name does not declare Role.Image" }
                )
            }
        }
    }
}
