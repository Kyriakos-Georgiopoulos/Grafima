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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
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

        // Unmerged, or both resolve to the merged legend node and this asserts
        // the same thing twice — an entry collapsed to zero width would pass.
        onNodeWithText("Revenue", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Expenses", useUnmergedTree = true).assertIsDisplayed()
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
    fun an_empty_dataset_announces_nothing() = runComposeUiTest {
        setContent {
            LineLegend(
                dataSet = LineDataSet(series = emptyList(), contentDescription = "Nothing"),
                modifier = Modifier.size(200.dp)
            )
        }
        waitForIdle()

        assertTrue(legendNode().spokenText().isEmpty(), "an empty legend still said something")
    }

    @Test
    fun a_screen_reader_reaches_the_legend_as_one_item_naming_every_series() = runComposeUiTest {
        // The chart's own description already names the series; N separate stops
        // would repeat them with no role and without the colour they map to.
        setContent { LineLegend(dataSet = dataSet()) }
        waitForIdle()

        onRoot().onChildren().assertCountEquals(1)
        val spoken = legendNode().spokenText()
        assertTrue("Revenue" in spoken, "the first series was not named: $spoken")
        assertTrue("Expenses" in spoken, "the second series was not named: $spoken")
    }

    /** How many pixels wide the reddish ink actually is, caps included. */
    private fun ImageBitmap.reddishSpan(): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        var minX = width
        var maxX = -1
        pixels.forEachIndexed { i, p ->
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r > 120 && r > g + 40 && r > b + 40) {
                val x = i % width
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
        }
        check(maxX >= minX) { "no swatch was painted" }
        return maxX - minX + 1
    }

    /** The legend's own node — root is its parent and carries no text itself. */
    private fun ComposeUiTest.legendNode(): SemanticsNodeInteraction =
        onRoot().onChildren().onFirst()

    /** Everything a screen reader would read out under this node. */
    private fun SemanticsNodeInteraction.spokenText(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }
            .orEmpty()

    @Test
    fun a_swatch_paints_no_wider_than_the_width_it_was_given() = runComposeUiTest {
        // Round caps reach half the stroke beyond each endpoint, and a Canvas is
        // not clipped, so an uninset line paints outside the node it lives in.
        val swatch = 40.dp
        var painted = 0
        setContent {
            painted = with(LocalDensity.current) { swatch.roundToPx() }
            LineLegend(
                dataSet = LineDataSet(
                    series = listOf(
                        LineSeries(
                            id = "s",
                            label = "",
                            points = listOf(LineDataPoint(x = 0f, y = 1f)),
                            color = Color.Red
                        )
                    ),
                    contentDescription = "One series"
                ),
                swatchWidth = swatch
            )
        }
        waitForIdle()

        val span = onRoot().captureToImage().reddishSpan()
        assertEquals(painted, span, "the swatch painted $span px for a ${painted}px width")
    }

    @Test
    fun entries_that_overflow_the_width_wrap_onto_further_lines() = runComposeUiTest {
        // A Row measures what it cannot fit at zero width, collapsing the swatch
        // and breaking the label one glyph per line.
        val many = LineDataSet(
            series = (0..5).map {
                LineSeries(
                    id = "s$it",
                    label = "Series number $it",
                    points = listOf(LineDataPoint(x = 0f, y = 1f))
                )
            },
            contentDescription = "Six series"
        )
        setContent { LineLegend(dataSet = many, modifier = Modifier.width(140.dp)) }
        waitForIdle()

        val first = onNodeWithText("Series number 0", useUnmergedTree = true)
            .fetchSemanticsNode().size
        val last = onNodeWithText("Series number 5", useUnmergedTree = true)
            .fetchSemanticsNode().size
        assertEquals(first, last, "the last entry was not laid out like the first")
    }

    @Test
    fun a_dashed_series_gets_a_dashed_swatch() = runComposeUiTest {
        // A key drawn solid beside a dashed line says the series is measured when
        // it is derived. The chart's own lengths are far too long for a swatch, so
        // the dash is sized to the swatch instead of copied from the line.
        var pattern by mutableStateOf<DashPattern?>(null)
        val swatch = 40.dp
        setContent {
            LineLegend(
                dataSet = LineDataSet(
                    series = listOf(
                        LineSeries(
                            id = "avg",
                            label = "",
                            points = listOf(LineDataPoint(x = 0f, y = 1f)),
                            color = Color.Red,
                            dashPattern = pattern
                        )
                    ),
                    contentDescription = "One series"
                ),
                swatchWidth = swatch
            )
        }
        waitForIdle()
        val solid = onRoot().captureToImage().countColor(Color.Red)

        pattern = DashPattern(dash = 10.dp, gap = 6.dp)
        waitForIdle()
        val dashed = onRoot().captureToImage().countColor(Color.Red)

        assertTrue(solid > 0, "the solid swatch painted nothing")
        assertTrue(dashed > 0, "the dashed swatch painted nothing at all")
        assertTrue(
            dashed < solid * 3 / 4,
            "the dashed swatch painted $dashed against a solid $solid — it reads as solid"
        )
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
            val painted = r < 120 && (g > b + 40 || b > g + 40)
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
