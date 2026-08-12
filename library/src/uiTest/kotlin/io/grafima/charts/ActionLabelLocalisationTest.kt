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

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.grafima.charts.line.LineA11yConfig
import io.grafima.charts.line.LineAnimationConfig
import io.grafima.charts.line.LineChart
import io.grafima.charts.line.LineDataPoint
import io.grafima.charts.line.LineDataSet
import io.grafima.charts.line.LineSeries
import io.grafima.charts.pie.PieA11yConfig
import io.grafima.charts.pie.PieChart
import io.grafima.charts.pie.PieDataSet
import io.grafima.charts.pie.PieEntry
import io.grafima.charts.radar.RadarA11yConfig
import io.grafima.charts.radar.RadarAxis
import io.grafima.charts.radar.RadarChart
import io.grafima.charts.radar.RadarDataSet
import io.grafima.charts.radar.RadarSeries
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every chart's actions menu has to translate, or a localised app reads half its
 * charts in the user's language and half in English.
 */
@OptIn(ExperimentalTestApi::class)
class ActionLabelLocalisationTest {

    private fun assertLocalised(labels: List<String>) {
        assertTrue(labels.any { it == "Επιλογή A" }, "select action not localised: $labels")
        assertTrue(labels.any { it == "Καθαρισμός" }, "clear action not localised: $labels")
    }

    @Test
    fun the_line_chart_takes_its_action_labels_from_its_a11y_config() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = LineDataSet(
                    series = listOf(
                        LineSeries(
                            id = "s",
                            label = "S",
                            points = listOf(
                                LineDataPoint(x = 0f, y = 1f, label = "A"),
                                LineDataPoint(x = 1f, y = 2f, label = "B")
                            )
                        )
                    ),
                    contentDescription = "Localised line"
                ),
                modifier = Modifier.size(300.dp),
                animationConfig = LineAnimationConfig(entrySpec = snap(), morphSpec = snap()),
                a11yConfig = LineA11yConfig(
                    selectActionLabel = { point -> "Επιλογή ${point.label}" },
                    clearSelectionLabel = "Καθαρισμός"
                ),
                selectedPointIndex = 0
            )
        }
        assertLocalised(onChartNode().customActionLabels())
    }

    @Test
    fun the_pie_chart_takes_its_action_labels_from_its_a11y_config() = runComposeUiTest {
        setContent {
            PieChart(
                dataSet = PieDataSet(
                    entries = listOf(
                        PieEntry(id = "a", label = "A", value = 1f),
                        PieEntry(id = "b", label = "B", value = 2f)
                    ),
                    contentDescription = "Localised pie"
                ),
                modifier = Modifier.size(300.dp),
                a11yConfig = PieA11yConfig(
                    selectActionLabel = { entry -> "Επιλογή ${entry.label}" },
                    clearSelectionLabel = "Καθαρισμός"
                ),
                selectedEntry = PieEntry(id = "a", label = "A", value = 1f)
            )
        }
        assertLocalised(onChartNode().customActionLabels())
    }

    @Test
    fun the_radar_chart_takes_its_action_labels_from_its_a11y_config() = runComposeUiTest {
        setContent {
            RadarChart(
                dataSet = RadarDataSet(
                    axes = listOf(
                        RadarAxis(id = "x", label = "X"),
                        RadarAxis(id = "y", label = "Y"),
                        RadarAxis(id = "z", label = "Z")
                    ),
                    series = listOf(
                        RadarSeries(id = "a", label = "A", values = mapOf("x" to 1f, "y" to 2f, "z" to 3f)),
                        RadarSeries(id = "b", label = "B", values = mapOf("x" to 3f, "y" to 2f, "z" to 1f))
                    ),
                    contentDescription = "Localised radar"
                ),
                modifier = Modifier.size(300.dp),
                a11yConfig = RadarA11yConfig(
                    selectActionLabel = { series -> "Επιλογή ${series.label}" },
                    clearSelectionLabel = "Καθαρισμός"
                ),
                selectedSeries = RadarSeries(
                    id = "a",
                    label = "A",
                    values = mapOf("x" to 1f, "y" to 2f, "z" to 3f)
                )
            )
        }
        assertLocalised(onChartNode().customActionLabels())
    }
}
