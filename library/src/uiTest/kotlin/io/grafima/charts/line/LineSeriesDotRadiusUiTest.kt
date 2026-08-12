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
                    dotRadius = styleRadius,
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
                    dotRadius = styleRadius,
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
