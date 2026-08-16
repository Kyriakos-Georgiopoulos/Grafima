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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.grafima.charts.LegendOrientation
import io.grafima.charts.assumePixelCapture
import io.grafima.charts.countColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BarLegendUiTest {

    private fun bar(id: String, xLabel: String, seriesId: String?, label: String?, color: Color?) =
        BarEntry(
            id = id,
            xLabel = xLabel,
            y = 1f,
            gradientColors = color?.let { listOf(it, it) },
            seriesId = seriesId,
            seriesLabel = label
        )

    private val grouped = BarDataSet(
        entries = listOf(
            bar("q1r", "Q1", "rev", "Revenue", Color.Blue),
            bar("q1c", "Q1", "cost", "Cost", Color.Red),
            bar("q2r", "Q2", "rev", "Revenue", Color.Blue),
            bar("q2c", "Q2", "cost", "Cost", Color.Red)
        )
    )

    @Test
    fun every_series_is_named_once_however_many_bars_it_has() = runComposeUiTest {
        setContent { BarLegend(dataSet = grouped) }

        // Unmerged, or every query resolves to the one merged legend node and this
        // passes for any number of entries — including an entry collapsed to nothing.
        onAllNodesWithText("Revenue", useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithText("Cost", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("Revenue", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun each_series_swatch_is_painted_in_that_series_own_colour() = runComposeUiTest {
        assumePixelCapture()
        setContent { BarLegend(dataSet = grouped, modifier = Modifier.size(200.dp)) }
        waitForIdle()

        val image = onRoot().captureToImage()
        assertTrue(image.countColor(Color.Blue) > 0, "the revenue swatch never painted")
        assertTrue(image.countColor(Color.Red) > 0, "the cost swatch never painted")
    }

    @Test
    fun a_removed_series_keeps_its_row_while_its_bars_are_still_shrinking() =
        runComposeUiTest {
            var data by mutableStateOf(grouped)
            // Hand-pumped, or the fade runs to completion before the assertion.
            mainClock.autoAdvance = false
            setContent { BarLegend(dataSet = data) }
            mainClock.advanceTimeByFrame()
            onAllNodesWithText("Cost", useUnmergedTree = true).assertCountEquals(1)

            // The chart holds a removed series' bars for the exit animation; dropping
            // the row at once would name fewer colours than are on screen.
            data = BarDataSet(entries = grouped.entries.filter { it.seriesId == "rev" })
            repeat(3) { mainClock.advanceTimeByFrame() }
            onAllNodesWithText("Cost", useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun a_removed_series_does_leave_the_key_once_its_bars_have_gone() = runComposeUiTest {
        var data by mutableStateOf(grouped)
        setContent { BarLegend(dataSet = data) }
        waitForIdle()

        data = BarDataSet(entries = grouped.entries.filter { it.seriesId == "rev" })
        waitForIdle()
        onAllNodesWithText("Cost", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("Revenue", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun a_departing_series_is_not_announced_to_a_screen_reader() = runComposeUiTest {
        var data by mutableStateOf(grouped)
        mainClock.autoAdvance = false
        setContent { BarLegend(dataSet = data) }
        mainClock.advanceTimeByFrame()

        data = BarDataSet(entries = grouped.entries.filter { it.seriesId == "rev" })
        repeat(3) { mainClock.advanceTimeByFrame() }
        // Still drawn, so the colours match the bars — but no longer spoken, since it
        // is on its way out of the dataset.
        onNodeWithContentDescription("Key: Revenue").assertExists()
    }

    @Test
    fun a_dataset_with_no_series_draws_no_key_at_all() = runComposeUiTest {
        val plain = BarDataSet(
            entries = listOf(
                bar("a", "Q1", null, null, Color.Blue),
                bar("b", "Q2", null, null, Color.Red)
            )
        )
        setContent { BarLegend(dataSet = plain) }

        // The axis already names these bars; a key repeating them says nothing.
        assertEquals(0, onAllNodes(hasText("Q1")).fetchSemanticsNodes().size)
        assertTrue(onRoot().fetchSemanticsNode().children.isEmpty(), "an empty key still took a slot")
    }

    @Test
    fun a_series_with_no_label_is_keyed_by_its_id() = runComposeUiTest {
        val unlabelled = BarDataSet(entries = listOf(bar("a", "Q1", "rev", null, Color.Blue)))
        setContent { BarLegend(dataSet = unlabelled) }

        onNode(hasText("rev")).assertExists()
    }

    @Test
    fun the_key_is_one_named_stop_rather_than_two_loose_nouns() = runComposeUiTest {
        setContent { BarLegend(dataSet = grouped) }

        // The bar chart's own description carries counts, not series names, so this
        // is where a listener meets them — it has to say what it is.
        onNodeWithContentDescription("Key: Revenue, Cost").assertExists()
    }

    @Test
    fun the_spoken_name_of_the_key_can_be_translated() = runComposeUiTest {
        setContent {
            BarLegend(dataSet = grouped, describe = { names -> "Υπόμνημα: ${names.size}" })
        }

        onNodeWithContentDescription("Υπόμνημα: 2").assertExists()
    }

    @Test
    fun colour_stops_key_the_series_rather_than_its_flat_gradient() = runComposeUiTest {
        assumePixelCapture()
        // The chart prefers stops over gradientColors; the key has to agree or it
        // paints one thing while the bars paint another.
        val stopped = BarDataSet(
            entries = listOf(
                BarEntry(
                    id = "a",
                    xLabel = "Q1",
                    y = 1f,
                    gradientColors = listOf(Color.Blue, Color.Blue),
                    colorStops = listOf(ColorStop(0f, Color.Green), ColorStop(1f, Color.Green)),
                    seriesId = "rev",
                    seriesLabel = "Revenue"
                )
            )
        )
        setContent { BarLegend(dataSet = stopped, modifier = Modifier.size(200.dp)) }
        waitForIdle()

        val image = onRoot().captureToImage()
        assertTrue(image.countColor(Color.Green) > 0, "the key ignored the series' stops")
        assertEquals(0, image.countColor(Color.Blue), "the key used the colours the stops replace")
    }

    @Test
    fun a_vertical_key_stacks_its_entries_rather_than_laying_them_in_a_row() =
        runComposeUiTest {
            setContent {
                Column {
                    BarLegend(dataSet = grouped, orientation = LegendOrientation.Horizontal)
                    BarLegend(dataSet = grouped, orientation = LegendOrientation.Vertical)
                }
            }

            val row = onAllNodesWithContentDescription("Key: Revenue, Cost")[0]
                .fetchSemanticsNode().size
            val column = onAllNodesWithContentDescription("Key: Revenue, Cost")[1]
                .fetchSemanticsNode().size
            assertTrue(column.height > row.height, "the vertical key was no taller")
            assertTrue(column.width < row.width, "the vertical key was no narrower")
        }

    @Test
    fun the_key_reads_right_to_left_in_rtl() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BarLegend(dataSet = grouped, modifier = Modifier.size(200.dp))
            }
        }
        waitForIdle()

        // The first series' swatch belongs on the right-hand end in RTL.
        val image = onRoot().captureToImage()
        val blue = image.meanColumn(Color.Blue)
        val red = image.meanColumn(Color.Red)
        assertTrue(blue > red, "the key did not mirror: blue at $blue, red at $red")
    }

    private fun ImageBitmap.meanColumn(color: Color): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        var sum = 0L
        var count = 0
        pixels.forEachIndexed { i, p -> if (p == target) { sum += i % width; count++ } }
        return if (count == 0) -1 else (sum / count).toInt()
    }

    @Test
    fun a_vertical_key_still_names_every_series() = runComposeUiTest {
        setContent {
            BarLegend(dataSet = grouped, orientation = LegendOrientation.Vertical)
        }

        onNode(hasText("Revenue")).assertExists()
        onNode(hasText("Cost")).assertExists()
    }
}
