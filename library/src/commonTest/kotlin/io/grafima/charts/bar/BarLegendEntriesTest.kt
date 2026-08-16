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

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarLegendEntriesTest {

    private val revenue = listOf(Color.Blue, Color.Cyan)
    private val cost = listOf(Color.Red, Color.Magenta)

    private fun bar(
        id: String,
        xLabel: String,
        seriesId: String? = null,
        seriesLabel: String? = null,
        colors: List<Color>? = null,
        stops: List<ColorStop>? = null
    ) = BarEntry(
        id = id,
        xLabel = xLabel,
        y = 1f,
        gradientColors = colors,
        colorStops = stops,
        seriesId = seriesId,
        seriesLabel = seriesLabel
    )

    @Test
    fun `each series is listed once in the order it first appears`() {
        val data = BarDataSet(
            entries = listOf(
                bar("q1r", "Q1", "rev", "Revenue", revenue),
                bar("q1c", "Q1", "cost", "Cost", cost),
                bar("q2r", "Q2", "rev", "Revenue", revenue),
                bar("q2c", "Q2", "cost", "Cost", cost)
            )
        )
        assertEquals(listOf("rev", "cost"), barLegendEntries(data).map { it.seriesId })
        assertEquals(listOf("Revenue", "Cost"), barLegendEntries(data).map { it.label })
    }

    @Test
    fun `a series takes the colours of its first bar`() {
        val data = BarDataSet(
            entries = listOf(
                bar("a", "Q1", "rev", "Revenue", revenue),
                bar("b", "Q2", "rev", "Revenue", listOf(Color.Green))
            )
        )
        assertEquals(revenue, barLegendEntries(data).single().gradientColors)
    }

    @Test
    fun `colour stops are carried through rather than flattened`() {
        val stops = listOf(ColorStop(0f, Color.Red), ColorStop(1f, Color.Blue))
        val data = BarDataSet(entries = listOf(bar("a", "Q1", "rev", "Revenue", stops = stops)))
        assertEquals(stops, barLegendEntries(data).single().colorStops)
    }

    @Test
    fun `a dataset with no series has no key`() {
        val data = BarDataSet(entries = listOf(bar("a", "Q1"), bar("b", "Q2")))
        assertTrue(barLegendEntries(data).isEmpty())
    }

    @Test
    fun `a blank series id is a series when it has a name to show`() {
        // The chart groups on seriesId != null, blank included, so the key has to as
        // well or it omits a colour the chart is drawing.
        val data = BarDataSet(
            entries = listOf(
                bar("a", "Q1", seriesId = "", seriesLabel = "Revenue", colors = revenue),
                bar("b", "Q1", seriesId = "cost", seriesLabel = "Cost", colors = cost)
            )
        )
        assertEquals(listOf("Revenue", "Cost"), barLegendEntries(data).map { it.label })
    }

    @Test
    fun `a series with neither id nor label to show is skipped`() {
        val data = BarDataSet(entries = listOf(bar("a", "Q1", seriesId = "   ")))
        assertTrue(barLegendEntries(data).isEmpty())
    }

    @Test
    fun `a series with no colours takes the palette gradient its bars are painted with`() {
        val data = BarDataSet(
            entries = listOf(
                bar("a", "Q1", "rev", "Revenue"),
                bar("b", "Q1", "cost", "Cost")
            )
        )
        val keyed = barLegendEntries(data).map { it.gradientColors }
        assertEquals(DefaultSeriesPalette[0], keyed[0])
        assertEquals(DefaultSeriesPalette[1], keyed[1])
        assertTrue(keyed[0] != keyed[1], "two series were keyed with the same swatch")
    }

    @Test
    fun `the palette cycles when there are more series than gradients`() {
        val many = (0..DefaultSeriesPalette.size).map {
            bar("b$it", "Q1", "s$it", "S$it")
        }
        val keyed = barLegendEntries(BarDataSet(entries = many)).map { it.gradientColors }
        assertEquals(keyed.first(), keyed.last())
    }

    @Test
    fun `an empty palette falls back to the dataset default`() {
        val fallback = listOf(Color.Yellow, Color.Black)
        val data = BarDataSet(
            entries = listOf(bar("a", "Q1", "rev", "Revenue")),
            defaultGradientColors = fallback,
            seriesPalette = emptyList()
        )
        assertEquals(fallback, barLegendEntries(data).single().gradientColors)
    }

    @Test
    fun `a single colour is widened into a gradient Compose will accept`() {
        // Brush needs two stops; one colour is a fair way to ask for a flat bar.
        val data = BarDataSet(
            entries = listOf(bar("a", "Q1", "rev", "Revenue", colors = listOf(Color.Red)))
        )
        assertEquals(listOf(Color.Red, Color.Red), barLegendEntries(data).single().gradientColors)
    }

    @Test
    fun `empty colours fall through rather than reaching the brush`() {
        val data = BarDataSet(
            entries = listOf(bar("a", "Q1", "rev", "Revenue", colors = emptyList())),
            seriesPalette = emptyList(),
            defaultGradientColors = emptyList()
        )
        assertTrue(barLegendEntries(data).single().gradientColors.size >= 2)
    }

    @Test
    fun `fewer than two colour stops are not used as stops`() {
        val one = listOf(ColorStop(0f, Color.Red))
        val data = BarDataSet(entries = listOf(bar("a", "Q1", "rev", "Revenue", stops = one)))
        // A lone stop renders on skiko and throws on Android; neither is a key.
        assertEquals(null, barLegendEntries(data).single().colorStops)
    }

    @Test
    fun `a series with no label is named by its id`() {
        val data = BarDataSet(entries = listOf(bar("a", "Q1", seriesId = "rev")))
        assertEquals("rev", barLegendEntries(data).single().label)
    }

    @Test
    fun `bars without a series do not disturb the ones that have one`() {
        val data = BarDataSet(
            entries = listOf(
                bar("loose", "Q1"),
                bar("a", "Q2", "rev", "Revenue", revenue),
                bar("other", "Q3")
            )
        )
        assertEquals(listOf("Revenue"), barLegendEntries(data).map { it.label })
    }

    @Test
    fun `an empty dataset has no key`() {
        assertTrue(barLegendEntries(BarDataSet(entries = emptyList())).isEmpty())
    }
}
