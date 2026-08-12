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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.grafima.charts.assumePixelCapture
import io.grafima.charts.onChartNode
import io.grafima.charts.stateDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A marker covering one x of the curve it sits on. The crosshair anchors on the
 * first series, so everything here turns on the second being read at the anchor's
 * x rather than at its position in the list.
 */
@OptIn(ExperimentalTestApi::class)
class LineCrosshairAlignmentUiTest {

    private val markerColor = Color.Green

    // The marker sits at y=4, well clear of the curve's 10..15, so its dot's row
    // says which point was read and not merely that something was drawn.
    private val curve = LineSeries(
        id = "curve",
        label = "Curve",
        color = Color.Blue,
        points = (0..5).map { LineDataPoint(x = it.toFloat(), y = 10f + it, label = "P$it") }
    )

    private val marker = LineSeries(
        id = "marker",
        label = "You are here",
        color = markerColor,
        points = listOf(LineDataPoint(x = 3f, y = 4f, label = "P3"))
    )

    private val dataSet = LineDataSet(
        series = listOf(curve, marker),
        contentDescription = "A curve with a marker on it"
    )

    private val snapAnimations = LineAnimationConfig(
        entrySpec = snap(),
        morphSpec = snap(),
        staggerMs = 0L,
        startDelayMs = 0L,
        seriesStaggerMs = 0L
    )

    /** The border is chart-wide; only the inner dot takes the series' own colour. */
    private val neutralBorder = LineCrosshairConfig(dotBorderColor = Color.White)

    @Test
    fun a_marker_gets_no_crosshair_dot_at_an_x_it_does_not_cover() = runComposeUiTest {
        assumePixelCapture()
        var selected by mutableStateOf<Int?>(3)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                crosshairConfig = neutralBorder,
                selectedPointIndex = selected
            )
        }
        waitForIdle()

        val atOwnX = onChartNode().captureToImage()
        assertTrue(
            atOwnX.countColor(markerColor) > 0,
            "the marker got no crosshair dot at the x it does cover"
        )
        // y=4 on a 10..15 curve is the lower half of the plot; reading the marker at
        // the anchor's y would put its dot up among the curve's own points.
        assertTrue(
            atOwnX.meanRow(markerColor) > atOwnX.height / 2,
            "the marker's dot was drawn at the anchor series' y, not its own"
        )

        // Index 0 is x=0, and the marker's point is at list position 0 too, so
        // reading by position drew it here rather than at its own x=3.
        selected = 0
        waitForIdle()
        assertEquals(
            0,
            onChartNode().captureToImage().countColor(markerColor),
            "the marker was dragged to an x it has no point at"
        )
    }

    @Test
    fun a_marker_is_named_in_the_tooltip_only_where_it_stands() = runComposeUiTest {
        var selected by mutableStateOf<Int?>(0)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedPointIndex = selected
            )
        }
        waitForIdle()

        // The state description carries what the tooltip prints.
        assertFalse(
            onChartNode().stateDescription().contains("You are here"),
            "a point the marker has no reading at still announced one"
        )

        selected = 3
        waitForIdle()
        assertTrue(
            onChartNode().stateDescription().contains("You are here"),
            "the marker went unannounced at its own x"
        )
    }

    @Test
    fun the_anchor_series_is_still_read_at_the_selected_index() = runComposeUiTest {
        var selected by mutableStateOf<Int?>(0)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedPointIndex = selected
            )
        }
        waitForIdle()
        assertTrue(
            onChartNode().stateDescription().contains("Curve at P0: 10"),
            "the anchor lost its own selection: ${onChartNode().stateDescription()}"
        )

        selected = 4
        waitForIdle()
        assertTrue(
            onChartNode().stateDescription().contains("Curve at P4: 14"),
            "the anchor lost its own selection: ${onChartNode().stateDescription()}"
        )
    }

    /** Average row of [color], which is a dot's centre when only that dot uses it. */
    private fun ImageBitmap.meanRow(color: Color): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        var sum = 0L
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] == target) { sum += y; count++ }
            }
        }
        return if (count == 0) -1 else (sum / count).toInt()
    }

    private fun ImageBitmap.countColor(color: Color): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        return pixels.count { it == target }
    }
}
