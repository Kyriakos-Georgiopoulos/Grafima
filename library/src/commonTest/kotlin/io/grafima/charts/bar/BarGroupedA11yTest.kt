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

package io.grafima.charts.bar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarGroupedA11yTest {

    private val grouped = BarDataSet(
        entries = listOf(
            BarEntry("q1-rev", "Q1", 45f, seriesId = "rev", seriesLabel = "Revenue"),
            BarEntry("q1-cost", "Q1", 30f, seriesId = "cost", seriesLabel = "Cost"),
            BarEntry("q2-rev", "Q2", 80f, seriesId = "rev", seriesLabel = "Revenue"),
            BarEntry("q2-cost", "Q2", 52f, seriesId = "cost", seriesLabel = "Cost")
        ),
        contentDescription = "quarterly figures"
    )

    private val plain = BarDataSet(
        entries = listOf(BarEntry("jan", "Jan", 45f), BarEntry("feb", "Feb", 80f)),
        contentDescription = "monthly revenue"
    )

    @Test
    fun `a dataset with no series keeps the exact wording it had before grouping existed`() {
        assertEquals(
            "Bar Chart representing monthly revenue. 2 bars. Use the actions menu to select one.",
            buildBarChartDescription(plain, A11yConfig())
        )
    }

    @Test
    fun `a grouped dataset says how the bars are arranged and not just how many`() {
        assertEquals(
            "Bar Chart representing quarterly figures. " +
                "4 bars in 2 groups of 2. Use the actions menu to select one.",
            buildBarChartDescription(grouped, A11yConfig())
        )
    }

    @Test
    fun `the selected state names the series so two bars of a group are told apart`() {
        val describe = A11yConfig().selectedStateDescription

        assertEquals("Currently selected: Q1, Revenue, 45.", describe(grouped.entries[0]))
        assertEquals("Currently selected: Q1, Cost, 30.", describe(grouped.entries[1]))
    }

    @Test
    fun `the selected state of a bar with no series is unchanged`() {
        val describe = A11yConfig().selectedStateDescription

        assertEquals("Currently selected: Jan, 45.", describe(plain.entries[0]))
        assertEquals("No bar selected.", describe(null))
    }

    @Test
    fun `a series with no label falls back to its id rather than going unnamed`() {
        val entry = BarEntry("q1-rev", "Q1", 45f, seriesId = "rev")

        assertEquals("rev", entry.spokenSeriesLabel)
        assertEquals(
            "Currently selected: Q1, rev, 45.",
            A11yConfig().selectedStateDescription(entry)
        )
    }

    @Test
    fun `a blank series label is treated as absent rather than spoken as empty`() {
        val entry = BarEntry("q1-rev", "Q1", 45f, seriesId = "rev", seriesLabel = "   ")

        assertEquals("rev", entry.spokenSeriesLabel)
    }

    @Test
    fun `a bar with no series has nothing to speak for one`() {
        assertNull(plain.entries[0].spokenSeriesLabel)
    }

    @Test
    fun `a custom count builder still wins for a grouped dataset`() {
        val config = A11yConfig(
            groupedCountDescriptionBuilder = { bars, categories, series ->
                "$bars/$categories/$series"
            }
        )

        assertEquals(
            "Bar Chart representing quarterly figures. 4/2/2",
            buildBarChartDescription(grouped, config)
        )
    }
}
