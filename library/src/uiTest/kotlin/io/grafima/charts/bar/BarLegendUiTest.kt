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

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
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

        onNode(hasText("Revenue")).assertExists()
        onNode(hasText("Cost")).assertExists()
        assertEquals(1, onAllNodes(hasText("Revenue")).fetchSemanticsNodes().size)
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
    fun the_key_is_one_stop_for_a_screen_reader_rather_than_one_per_series() = runComposeUiTest {
        setContent { BarLegend(dataSet = grouped) }

        // Merged, so stepping through a chart screen does not spend two stops on the
        // key before reaching the chart itself.
        val root = onRoot().fetchSemanticsNode()
        assertEquals(1, root.children.size, "the key was not merged into one node")
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
