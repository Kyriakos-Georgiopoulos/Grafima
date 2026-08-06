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
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun each_swatch_carries_the_colour_of_the_series_it_names() = runComposeUiTest {
        // Vertical, so the first series owns the top half and the second the
        // bottom. Asserting both colours exist somewhere would still pass with
        // the swatches swapped, which is the only failure that matters here.
        setContent {
            // Sized to content, so half the node really is the first entry.
            LineLegend(dataSet = dataSet(), orientation = LineLegendOrientation.Vertical)
        }
        waitForIdle()

        val image = onRoot().captureToImage()
        val half = image.height / 2
        assertTrue(image.countColor(Color.Red, 0 until half) > 0, "red is not in the top half")
        assertEquals(0, image.countColor(Color.Red, half until image.height), "red leaked downwards")
        assertTrue(
            image.countColor(Color.Blue, half until image.height) > 0,
            "blue is not in the bottom half"
        )
        assertEquals(0, image.countColor(Color.Blue, 0 until half), "blue leaked upwards")
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
    fun a_vertical_legend_is_taller_and_narrower_than_a_horizontal_one() = runComposeUiTest {
        var orientation by mutableStateOf(LineLegendOrientation.Horizontal)
        setContent { LineLegend(dataSet = dataSet(), orientation = orientation) }
        waitForIdle()
        val row = onRoot().fetchSemanticsNode().size

        orientation = LineLegendOrientation.Vertical
        waitForIdle()
        val column = onRoot().fetchSemanticsNode().size

        assertTrue(column.height > row.height, "column $column was no taller than row $row")
        assertTrue(column.width < row.width, "column $column was no narrower than row $row")
    }

    @Test
    fun a_gradient_swatch_runs_the_same_way_as_the_line_it_names() = runComposeUiTest {
        // Both the block and the gradient mirror, so compare the green end
        // against the swatch's own extent rather than against the canvas.
        var rtl by mutableStateOf(false)
        val gradient = LineDataSet(
            series = listOf(
                LineSeries(
                    id = "revenue",
                    label = "",
                    points = listOf(LineDataPoint(x = 0f, y = 1f)),
                    strokeGradientColors = listOf(Color.Green, Color.Blue)
                )
            ),
            contentDescription = "One gradient series"
        )
        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                LineLegend(dataSet = gradient, modifier = Modifier.size(200.dp))
            }
        }
        waitForIdle()
        val ltrGreenAtStart = onRoot().captureToImage().greenSitsAtSwatchStart()

        rtl = true
        waitForIdle()
        val rtlGreenAtStart = onRoot().captureToImage().greenSitsAtSwatchStart()

        assertTrue(ltrGreenAtStart, "the first stop was not at the swatch's left edge in LTR")
        assertFalse(rtlGreenAtStart, "the gradient did not mirror: the key runs against its line")
    }

    /** Whether the green end of the swatch sits at its left edge rather than its right. */
    private fun ImageBitmap.greenSitsAtSwatchStart(): Boolean {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        var minX = width
        var maxX = -1
        var greenSum = 0.0
        var greenCount = 0
        pixels.forEachIndexed { i, p ->
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val painted = (g > 100 || b > 100) && r < 120
            if (!painted) return@forEachIndexed
            val x = i % width
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (g > b + 40) { greenSum += x; greenCount++ }
        }
        check(greenCount > 0 && maxX > minX) { "no gradient swatch was painted" }
        return (greenSum / greenCount) < (minX + maxX) / 2.0
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

    private fun ImageBitmap.countColor(color: Color, rows: IntRange = 0 until height): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        return rows.sumOf { y -> (0 until width).count { x -> pixels[y * width + x] == target } }
    }
}
