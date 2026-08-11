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
import kotlin.test.assertTrue

class BarGroupedA11yTest {

    private val grouped = BarDataSet(twoByTwoEntries, contentDescription = "quarterly figures")

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
    fun `one custom count builder covers a dataset with series and without`() {
        val config = A11yConfig(
            countDescriptionBuilder = { "${it.bars}/${it.categories}/${it.series}" }
        )

        // A single builder means adding a series to a localised chart cannot
        // silently fall back to the library's own wording.
        assertEquals(
            "Bar Chart representing quarterly figures. 4/2/2",
            buildBarChartDescription(grouped, config)
        )
        assertEquals(
            "Bar Chart representing monthly revenue. 2/2/0",
            buildBarChartDescription(plain, config)
        )
    }

    @Test
    fun `one bar per category is not announced as groups of one`() {
        val spread = BarDataSet(
            entries = (1..4).map { BarEntry("q$it", "Q$it", 10f * it, seriesId = "rev") },
            contentDescription = "revenue"
        )

        // Tagging a seriesId is how you get series-named actions; on an ungrouped
        // chart it must not turn the summary into "4 groups of 1".
        assertEquals(
            "Bar Chart representing revenue. 4 bars. Use the actions menu to select one.",
            buildBarChartDescription(spread, A11yConfig())
        )
    }

    @Test
    fun `a single category is announced in the singular`() {
        val one = BarDataSet(
            entries = listOf(
                BarEntry("q1-rev", "Q1", 45f, seriesId = "rev"),
                BarEntry("q1-cost", "Q1", 30f, seriesId = "cost")
            ),
            contentDescription = "one quarter"
        )

        assertEquals(
            "Bar Chart representing one quarter. 2 bars in 1 group of 2. " +
                "Use the actions menu to select one.",
            buildBarChartDescription(one, A11yConfig())
        )
    }

    @Test
    fun `ragged categories are not announced as a uniform group size`() {
        val ragged = BarDataSet(
            entries = listOf(
                BarEntry("q1-rev", "Q1", 45f, seriesId = "rev"),
                BarEntry("q1-cost", "Q1", 30f, seriesId = "cost"),
                BarEntry("adhoc", "Ad hoc", 12f)
            ),
            contentDescription = "mixed"
        )

        val summary = summarizeBars(ragged.entries)
        assertEquals(3, summary.bars)
        assertEquals(2, summary.categories)
        assertNull(summary.uniformGroupSize)
        assertTrue(
            buildBarChartDescription(ragged, A11yConfig()).contains("3 bars in 2 groups."),
            "said: ${buildBarChartDescription(ragged, A11yConfig())}"
        )
    }
}
