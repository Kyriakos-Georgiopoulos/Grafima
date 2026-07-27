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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.grafima.charts.bar.BarChart
import io.grafima.charts.bar.BarDataSet
import io.grafima.charts.bar.BarEntry
import io.grafima.charts.gauge.GaugeChart
import io.grafima.charts.line.LineChart
import io.grafima.charts.line.LineDataSet
import io.grafima.charts.pie.PieChart
import io.grafima.charts.pie.PieDataSet
import io.grafima.charts.radar.RadarChart
import io.grafima.charts.radar.RadarDataSet
import kotlin.test.Test

/**
 * Accessibility behaviour under non-default environments: large font scales,
 * right-to-left layouts, and empty datasets. These are the conditions real
 * users hit that a default-configuration test never exercises.
 */
@OptIn(ExperimentalTestApi::class)
class AccessibilityEnvironmentTest {

    private val barData = BarDataSet(
        entries = listOf(
            BarEntry(id = "jan", xLabel = "Jan", y = 45f),
            BarEntry(id = "feb", xLabel = "Feb", y = 80f)
        ),
        contentDescription = "Monthly revenue"
    )

    @Test
    fun the_chart_stays_accessible_at_a_200_percent_font_scale() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2f, fontScale = 2f)
            ) {
                BarChart(dataSet = barData, modifier = Modifier.size(300.dp))
            }
        }
        // Text measurement at large scales must not break layout or semantics.
        onNodeWithContentDescription("Monthly revenue", substring = true).assertExists()
        onNodeWithContentDescription("Feb value is 80", substring = true).assertExists()
    }

    @Test
    fun the_chart_stays_accessible_at_an_extreme_font_scale() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2f, fontScale = 3f)
            ) {
                BarChart(dataSet = barData, modifier = Modifier.size(200.dp))
            }
        }
        onNodeWithContentDescription("Monthly revenue", substring = true).assertExists()
    }

    @Test
    fun the_description_and_actions_survive_a_right_to_left_layout() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BarChart(dataSet = barData, modifier = Modifier.size(300.dp))
            }
        }
        val chart = onNodeWithContentDescription("Monthly revenue", substring = true)
        chart.assertExists()
        // Mirroring is a paint concern; the a11y contract must be unchanged.
        val labels = chart.customActionLabels()
        for (entry in barData.entries) {
            kotlin.test.assertTrue(
                "Select ${entry.xLabel}" in labels,
                "RTL lost the select action for ${entry.xLabel}: $labels"
            )
        }
    }

    @Test
    fun every_chart_stays_accessible_with_an_empty_dataset() {
        // Empty data must still announce something rather than crashing or
        // exposing a silent, unlabelled node.
        runComposeUiTest {
            setContent {
                BarChart(
                    dataSet = BarDataSet(entries = emptyList(), contentDescription = "No revenue"),
                    modifier = Modifier.size(300.dp)
                )
            }
            onNodeWithContentDescription("No revenue", substring = true).assertExists()
        }
        runComposeUiTest {
            setContent {
                PieChart(
                    dataSet = PieDataSet(entries = emptyList(), contentDescription = "No budget"),
                    modifier = Modifier.size(300.dp)
                )
            }
            onNodeWithContentDescription("No budget", substring = true).assertExists()
        }
        runComposeUiTest {
            setContent {
                LineChart(
                    dataSet = LineDataSet(series = emptyList(), contentDescription = "No trend"),
                    modifier = Modifier.size(300.dp)
                )
            }
            onNodeWithContentDescription("No trend", substring = true).assertExists()
        }
        runComposeUiTest {
            setContent {
                RadarChart(
                    dataSet = RadarDataSet(
                        axes = emptyList(),
                        series = emptyList(),
                        contentDescription = "No comparison"
                    ),
                    modifier = Modifier.size(300.dp)
                )
            }
            onNodeWithContentDescription("No comparison", substring = true).assertExists()
        }
    }

    @Test
    fun the_gauge_reports_progress_semantics_at_a_large_font_scale() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2f, fontScale = 2f)
            ) {
                GaugeChart(value = 42f, modifier = Modifier.size(300.dp))
            }
        }
        onNodeWithContentDescription("42", substring = true).assertExists()
    }
}
