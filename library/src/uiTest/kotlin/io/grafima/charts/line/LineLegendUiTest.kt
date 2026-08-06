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

package io.grafima.charts.line

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LineLegendUiTest {

    private fun dataSet(
        gradient: List<Color> = emptyList(),
        color: Color = Color.Red
    ) = LineDataSet(
        series = listOf(
            LineSeries(
                id = "revenue",
                label = "Revenue",
                points = listOf(LineDataPoint(x = 0f, y = 1f)),
                color = color,
                strokeGradientColors = gradient
            ),
            LineSeries(
                id = "expenses",
                label = "Expenses",
                points = listOf(LineDataPoint(x = 0f, y = 2f)),
                color = Color.Blue
            )
        ),
        contentDescription = "Revenue and expenses"
    )

    @Test
    fun every_series_is_named() = runComposeUiTest {
        setContent { LineLegend(dataSet = dataSet()) }

        onNodeWithText("Revenue").assertIsDisplayed()
        onNodeWithText("Expenses").assertIsDisplayed()
    }

    @Test
    fun a_swatch_is_painted_in_the_colour_of_its_series() = runComposeUiTest {
        setContent { LineLegend(dataSet = dataSet(), modifier = Modifier.size(200.dp)) }
        waitForIdle()

        val image = onRoot().captureToImage()
        assertTrue(image.countColor(Color.Red) > 0, "the first series' colour is missing")
        assertTrue(image.countColor(Color.Blue) > 0, "the second series' colour is missing")
    }

    @Test
    fun a_gradient_series_gets_a_gradient_swatch_not_its_flat_colour() = runComposeUiTest {
        // color is what a naive swatch would use; the line is drawn from the
        // gradient, so the key would not match it.
        setContent {
            LineLegend(
                dataSet = dataSet(gradient = listOf(Color.Green, Color.Blue), color = Color.Red),
                modifier = Modifier.size(200.dp)
            )
        }
        waitForIdle()

        val image = onRoot().captureToImage()
        assertEquals(0, image.countColor(Color.Red), "the flat colour was used for a gradient series")
        // A gradient only reaches its endpoint colour at the very edge, and the
        // round cap blends even that, so match on the dominant channel.
        assertTrue(image.countGreenish() > 0, "the gradient was not painted")
    }

    @Test
    fun a_single_gradient_colour_falls_back_to_the_flat_colour_as_the_chart_does() =
        runComposeUiTest {
            // The chart needs two stops to build a gradient and draws `color`
            // otherwise. A swatch that disagreed would name the wrong line.
            setContent {
                LineLegend(
                    dataSet = dataSet(gradient = listOf(Color.Green), color = Color.Red),
                    modifier = Modifier.size(200.dp)
                )
            }
            waitForIdle()

            val image = onRoot().captureToImage()
            assertTrue(image.countColor(Color.Red) > 0, "one stop should fall back to color")
            assertEquals(0, image.countGreenish(), "one stop was drawn as a gradient")
        }

    @Test
    fun an_empty_dataset_draws_no_entries() = runComposeUiTest {
        setContent {
            LineLegend(
                dataSet = LineDataSet(series = emptyList(), contentDescription = "Nothing"),
                modifier = Modifier.size(200.dp)
            )
        }
        waitForIdle()

        onRoot().onChildren().assertCountEquals(0)
    }

    @Test
    fun a_vertical_legend_is_taller_than_it_is_wide_for_the_same_data() = runComposeUiTest {
        setContent {
            LineLegend(
                dataSet = dataSet(),
                orientation = LineLegendOrientation.Vertical
            )
        }
        waitForIdle()

        val size = onRoot().fetchSemanticsNode().size
        assertTrue(
            size.height > size.width / 2,
            "a column legend measured ${size.width} by ${size.height}"
        )
    }

    private fun ImageBitmap.countGreenish(): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.count { p ->
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            g > 120 && g > r + 40 && g > b + 40
        }
    }

    private fun ImageBitmap.countColor(color: Color): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        return pixels.count { it == target }
    }
}
