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

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.grafima.charts.assumePixelCapture
import io.grafima.charts.countColor
import io.grafima.charts.isReddish
import io.grafima.charts.onChartNode
import io.grafima.charts.withPixels
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every series here holds one point, so it draws a dot and nothing else: pixels of
 * a series' colour are its dot, and its widest row that dot's diameter. The axis is
 * pinned wider than the data so neither dot reaches an edge that would clip it.
 */
@OptIn(ExperimentalTestApi::class)
class LineSeriesDotRadiusUiTest {

    private val markerColor = Color.Green
    private val referenceColor = Color.Blue
    private val styleRadius = 4.dp

    private val insideTheEdges = LineAxisConfig(xMin = -1f, xMax = 2f, yMin = 0f, yMax = 10f)

    private val snapAnimations = LineAnimationConfig(
        entrySpec = snap(),
        morphSpec = snap(),
        staggerMs = 0L,
        startDelayMs = 0L,
        seriesStaggerMs = 0L
    )

    private fun dataSet(markerRadius: Dp) = LineDataSet(
        series = listOf(
            LineSeries(
                id = "reference",
                label = "Reference",
                points = listOf(LineDataPoint(x = 0f, y = 5f, label = "A")),
                color = referenceColor
            ),
            LineSeries(
                id = "marker",
                label = "You are here",
                points = listOf(LineDataPoint(x = 1f, y = 5f, label = "B")),
                color = markerColor,
                dotRadius = markerRadius
            )
        ),
        contentDescription = "Reference curve with a marker"
    )

    private fun style(showDots: Boolean = true) =
        LineChartStyle(showDots = showDots, dotRadius = styleRadius)

    @Test
    fun a_series_radius_sizes_that_series_dot_and_leaves_the_rest_alone() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            LineChart(
                dataSet = dataSet(markerRadius = styleRadius * 3f),
                modifier = Modifier.size(300.dp),
                style = style(),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        val image = onChartNode().captureToImage()
        val reference = image.widestRow(referenceColor)
        val marker = image.widestRow(markerColor)
        assertTrue(reference > 0, "the reference dot never painted")
        // The difference, not the ratio: antialiasing takes a constant bite out of
        // each diameter, which cancels in a subtraction and skews a division.
        val grown = with(density) { (styleRadius * 3f - styleRadius).toPx() } * 2f
        assertTrue(
            abs((marker - reference) - grown) <= 2f,
            "marker grew ${marker - reference}px over the reference, expected ${grown}px"
        )
    }

    @Test
    fun a_series_without_a_radius_takes_the_chart_wide_one() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            LineChart(
                dataSet = dataSet(markerRadius = Dp.Unspecified),
                modifier = Modifier.size(300.dp),
                style = style(),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        val image = onChartNode().captureToImage()
        val reference = image.widestRow(referenceColor)
        val marker = image.widestRow(markerColor)
        assertTrue(reference > 0, "the reference dot never painted")
        assertTrue(abs(marker - reference) <= 1, "marker ${marker}px, reference ${reference}px")
    }

    @Test
    fun a_radius_of_zero_drops_one_series_dot_and_keeps_the_others() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            LineChart(
                dataSet = dataSet(markerRadius = 0.dp),
                modifier = Modifier.size(300.dp),
                style = style(),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        val image = onChartNode().captureToImage()
        assertTrue(image.widestRow(referenceColor) > 0, "the reference dot never painted")
        assertEquals(0, image.countColor(markerColor), "a zero radius still painted a dot")
    }

    @Test
    fun dots_turned_off_chart_wide_beat_a_series_radius() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            LineChart(
                dataSet = dataSet(markerRadius = styleRadius * 3f),
                modifier = Modifier.size(300.dp),
                style = style(showDots = false),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        val image = onChartNode().captureToImage()
        assertEquals(0, image.countColor(markerColor), "a series radius drew a dot showDots had off")
        assertEquals(0, image.countColor(referenceColor), "showDots was off but a dot painted")
    }

    @Test
    fun a_marker_keeps_its_dot_when_a_later_series_fills_over_it() = runComposeUiTest {
        assumePixelCapture()
        // The marker is first, so drawing dots inside the per-series loop lets the
        // opaque fill of the series after it bury them.
        val markerThenWash = LineDataSet(
            series = listOf(
                LineSeries(
                    id = "marker",
                    label = "You are here",
                    points = listOf(LineDataPoint(x = 1f, y = 5f, label = "B")),
                    color = markerColor,
                    dotRadius = styleRadius * 3f
                ),
                LineSeries(
                    id = "wash",
                    label = "Wash",
                    points = listOf(
                        LineDataPoint(x = -1f, y = 9f, label = "L"),
                        LineDataPoint(x = 2f, y = 9f, label = "R")
                    ),
                    color = referenceColor,
                    fillAlpha = 1f
                )
            ),
            contentDescription = "A marker under a filled series"
        )
        setContent {
            LineChart(
                dataSet = markerThenWash,
                modifier = Modifier.size(300.dp),
                style = style(),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        // The wash sits at y=9 and fills opaquely down to the axis, so it covers
        // y=5 whole. The dot survives only if dots are drawn after every fill.
        val image = onChartNode().captureToImage()
        assertTrue(image.widestRow(markerColor) > 0, "a later fill buried the marker")
        assertTrue(image.countColor(referenceColor) > 0, "the wash never painted")
    }

    @Test
    fun a_large_dot_on_the_axis_bound_stays_whole_and_inside_the_chart() = runComposeUiTest {
        assumePixelCapture()
        // Both points sit on the pinned bounds, where the plot edge used to be, so
        // half of each dot fell outside the composable and over the axis labels.
        val onTheBounds = LineDataSet(
            series = listOf(
                LineSeries(
                    id = "edges",
                    label = "Edges",
                    points = listOf(
                        LineDataPoint(x = 0f, y = 0f, label = "A"),
                        LineDataPoint(x = 1f, y = 10f, label = "B")
                    ),
                    color = markerColor,
                    dotRadius = 14.dp
                )
            ),
            contentDescription = "Points on the bounds"
        )
        setContent {
            LineChart(
                dataSet = onTheBounds,
                modifier = Modifier.size(300.dp),
                style = style(),
                axisConfig = LineAxisConfig(xMin = 0f, xMax = 1f, yMin = 0f, yMax = 10f),
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        // The top dot is the one at risk: its centre sits on chartTop, so without
        // clearance its upper half is cut by the composable's own edge.
        val image = onChartNode().captureToImage()
        val radius = with(density) { 14.dp.toPx() }
        assertTrue(
            image.firstRowMatching { it == markerColor.toArgb() } > 0,
            "the topmost dot was cut off by the top edge"
        )
        assertTrue(
            abs(image.widestRow(markerColor) - radius * 2f) <= 3f,
            "a dot on the bound measured ${image.widestRow(markerColor)}px, expected ${radius * 2f}px"
        )
    }

    @Test
    fun a_chart_wide_unspecified_radius_still_draws_the_chart() = runComposeUiTest {
        assumePixelCapture()
        // Unspecified is Dp(NaN) and coerceAtLeast leaves NaN alone, so carried into
        // the plot insets it made every mapped coordinate NaN and the chart blank.
        setContent {
            LineChart(
                dataSet = dataSet(markerRadius = Dp.Unspecified),
                modifier = Modifier.size(300.dp),
                style = LineChartStyle(showDots = true, dotRadius = Dp.Unspecified),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        // The axis labels are drawn from the insets, so any of them proves the rect
        // survived; the dots themselves have no radius to draw with.
        val image = onChartNode().captureToImage()
        assertTrue(
            image.withPixels { pixels -> pixels.any { it != pixels[0] } },
            "the chart painted nothing at all"
        )
    }

    @Test
    fun an_unspecified_radius_before_a_large_one_still_reserves_the_large_one() =
        runComposeUiTest {
            assumePixelCapture()
            // Dp compares as IEEE floats, so an unspecified radius in the first slot
            // used to survive the max and leave the second series' dot no clearance.
            val unsetThenLarge = LineDataSet(
                series = listOf(
                    LineSeries(
                        id = "unset",
                        label = "Unset",
                        points = listOf(LineDataPoint(x = 0.5f, y = 5f, label = "M")),
                        color = referenceColor
                    ),
                    LineSeries(
                        id = "large",
                        label = "Large",
                        points = listOf(LineDataPoint(x = 0f, y = 10f, label = "A")),
                        color = markerColor,
                        dotRadius = 14.dp
                    )
                ),
                contentDescription = "An unset radius ahead of a large one"
            )
            setContent {
                LineChart(
                    dataSet = unsetThenLarge,
                    modifier = Modifier.size(300.dp),
                    style = LineChartStyle(showDots = true, dotRadius = Dp.Unspecified),
                    axisConfig = LineAxisConfig(xMin = 0f, xMax = 1f, yMin = 0f, yMax = 10f),
                    animationConfig = snapAnimations
                )
            }
            waitForIdle()

            // The large dot sits on the top-left corner of the plot; with no clearance
            // reserved for it, its upper half is cut by the composable's edge.
            val image = onChartNode().captureToImage()
            val top = image.firstRowMatching { it == markerColor.toArgb() }
            assertTrue(top in 1 until image.height, "the large dot was cut off at row $top")
        }

    @Test
    fun the_y_labels_stay_on_the_canvas_when_the_clearance_is_clamped() = runComposeUiTest {
        assumePixelCapture()
        // A radius this large is clamped by the insets. Standing the labels off by the
        // raw radius instead put them at a negative x, off the composable entirely.
        setContent {
            LineChart(
                dataSet = dataSet(markerRadius = 40.dp),
                modifier = Modifier.size(140.dp),
                style = style(),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        val image = onChartNode().captureToImage()
        val rows = 0 until image.height
        assertTrue(
            image.firstColumnMatching(rows) { it.isAxisLabelGrey() } < image.width,
            "the axis labels were pushed off the canvas"
        )
    }

    @Test
    fun a_dot_on_the_left_bound_does_not_paint_over_the_y_labels() = runComposeUiTest {
        assumePixelCapture()
        // Padding outside the label band is not enough: the labels move inward with
        // the plot, so the gap between dot and label has to grow as well.
        val onTheLeft = LineDataSet(
            series = listOf(
                LineSeries(
                    id = "edge",
                    label = "Edge",
                    points = listOf(
                        LineDataPoint(x = 0f, y = 5f, label = "A"),
                        LineDataPoint(x = 1f, y = 6f, label = "B")
                    ),
                    color = markerColor,
                    dotRadius = 14.dp
                )
            ),
            contentDescription = "A dot on the left bound"
        )
        setContent {
            LineChart(
                dataSet = onTheLeft,
                modifier = Modifier.size(300.dp),
                style = style(),
                axisConfig = LineAxisConfig(xMin = 0f, xMax = 1f, yMin = 0f, yMax = 10f),
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        // Only the upper rows: the x labels along the bottom are the same grey and
        // run the full width, so they would swamp the y labels being measured.
        val image = onChartNode().captureToImage()
        val yLabelRows = 0 until (image.height * 3 / 4)
        val dotLeft = image.firstColumnMatching(yLabelRows) { it == markerColor.toArgb() }
        // firstColumnMatching returns width on no match, so without this the assertion
        // is satisfied by a regression that stops drawing dots at all.
        assertTrue(dotLeft < image.width, "no dot painted to measure against the labels")
        val labelRight = image.lastColumnMatching(yLabelRows) { it.isAxisLabelGrey() }
        assertTrue(labelRight in 0 until dotLeft, "label ended at $labelRight, dot began at $dotLeft")
    }

    @Test
    fun an_unspecified_radius_still_places_the_value_labels() = runComposeUiTest {
        assumePixelCapture()
        // Unspecified is Dp(NaN). Carried into the label offset it makes every
        // coordinate NaN, and the labels paint nowhere at all.
        setContent {
            LineChart(
                dataSet = dataSet(markerRadius = Dp.Unspecified),
                modifier = Modifier.size(300.dp),
                style = LineChartStyle(
                    showDots = true,
                    // Unspecified on both, or the series falls back to a real radius
                    // and the NaN branch this names is never taken.
                    dotRadius = Dp.Unspecified,
                    valueLabels = LineValueLabelConfig(
                        enabled = true,
                        textStyle = TextStyle(color = Color.Red)
                    )
                ),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        val labelRows = onChartNode().captureToImage().lastRowMatching { it.isReddish() }
        assertTrue(labelRows >= 0, "no value label painted for an unspecified radius")
    }

    @Test
    fun a_value_label_clears_the_dot_its_own_series_asked_for() = runComposeUiTest {
        assumePixelCapture()
        // One series, so every label row on the capture belongs to this dot, and one
        // point is a flat neighbourhood, which puts the label above rather than below.
        val alone = LineDataSet(
            series = listOf(
                LineSeries(
                    id = "marker",
                    label = "You are here",
                    points = listOf(LineDataPoint(x = 1f, y = 5f, label = "B")),
                    color = markerColor,
                    dotRadius = styleRadius * 4f
                )
            ),
            contentDescription = "A marker on its own"
        )
        setContent {
            LineChart(
                dataSet = alone,
                modifier = Modifier.size(300.dp),
                style = LineChartStyle(
                    showDots = true,
                    // Unspecified on both, or the series falls back to a real radius
                    // and the NaN branch this names is never taken.
                    dotRadius = Dp.Unspecified,
                    valueLabels = LineValueLabelConfig(
                        enabled = true,
                        textStyle = TextStyle(color = Color.Red)
                    )
                ),
                axisConfig = insideTheEdges,
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        // Offsetting by the style's 4dp while drawing a 16dp dot prints the label
        // over the top half of that dot.
        val image = onChartNode().captureToImage()
        assertTrue(image.widestRow(markerColor) > 0, "the dot under test never painted")
        val labelBottom = image.lastRowMatching { it.isReddish() }
        val dotTop = image.firstRowMatching { it == markerColor.toArgb() }
        assertTrue(labelBottom in 0 until dotTop, "label ended at row $labelBottom, dot began at $dotTop")
    }

    private fun ImageBitmap.firstColumnMatching(rows: IntRange, match: (Int) -> Boolean): Int =
        withPixels { px ->
            (0 until width).firstOrNull { x -> rows.any { y -> match(px[y * width + x]) } } ?: width
        }

    private fun ImageBitmap.lastColumnMatching(rows: IntRange, match: (Int) -> Boolean): Int =
        withPixels { px ->
            (0 until width).lastOrNull { x -> rows.any { y -> match(px[y * width + x]) } } ?: -1
        }

    /** The tone axis labels are drawn in, allowing for antialiased edges. */
    private fun Int.isAxisLabelGrey(): Boolean {
        val r = (this shr 16) and 0xFF
        val g = (this shr 8) and 0xFF
        val b = this and 0xFF
        return r in 80..130 && g in 90..140 && b in 110..160
    }

    /** Widest run of [color] in any one row: the diameter of a dot drawn in it. */
    private fun ImageBitmap.widestRow(color: Color): Int = withPixels { pixels ->
        val target = color.toArgb()
        (0 until height).maxOf { y -> (0 until width).count { x -> pixels[y * width + x] == target } }
    }

    private fun ImageBitmap.firstRowMatching(match: (Int) -> Boolean): Int = withPixels { pixels ->
        (0 until height).firstOrNull { y ->
            (0 until width).any { x -> match(pixels[y * width + x]) }
        } ?: height
    }

    private fun ImageBitmap.lastRowMatching(match: (Int) -> Boolean): Int = withPixels { pixels ->
        (0 until height).lastOrNull { y ->
            (0 until width).any { x -> match(pixels[y * width + x]) }
        } ?: -1
    }
}
