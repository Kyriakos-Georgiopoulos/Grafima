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
import io.grafima.charts.customActionLabels
import io.grafima.charts.onChartNode
import io.grafima.charts.performCustomAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LineChartUiTest {

    private val dataSet = LineDataSet(
        series = listOf(
            LineSeries(
                id = "revenue",
                label = "Revenue",
                points = listOf(
                    LineDataPoint(x = 0f, y = 10f, label = "Jan"),
                    LineDataPoint(x = 1f, y = 25f, label = "Feb"),
                    LineDataPoint(x = 2f, y = 18f, label = "Mar")
                )
            )
        ),
        contentDescription = "Quarterly revenue"
    )

    private val snapAnimations = LineAnimationConfig(
        entrySpec = snap(),
        morphSpec = snap(),
        staggerMs = 0L,
        startDelayMs = 0L,
        seriesStaggerMs = 0L
    )

    @Test
    fun each_point_offers_a_select_action_named_after_its_label() = runComposeUiTest {
        var selected by mutableStateOf<Int?>(null)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedPointIndex = selected,
                onPointSelected = { selected = it }
            )
        }

        // Naming the point is the whole point: stepping with next/previous leaves a
        // listener counting along the axis to work out where they landed.
        onChartNode().performCustomAction("Select Mar")
        waitForIdle()
        assertEquals(2, selected)

        onChartNode().performCustomAction("Select Jan")
        waitForIdle()
        assertEquals(0, selected)
    }

    @Test
    fun a_point_without_a_label_falls_back_to_its_x_value() = runComposeUiTest {
        var selected by mutableStateOf<Int?>(null)
        val unlabelled = LineDataSet(
            series = listOf(
                LineSeries(
                    id = "revenue",
                    label = "Revenue",
                    points = listOf(
                        LineDataPoint(x = 7f, y = 10f),
                        LineDataPoint(x = 8f, y = 25f)
                    )
                )
            ),
            contentDescription = "Unlabelled"
        )
        setContent {
            LineChart(
                dataSet = unlabelled,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedPointIndex = selected,
                onPointSelected = { selected = it }
            )
        }

        onChartNode().performCustomAction("Select 8")
        waitForIdle()
        assertEquals(1, selected)
    }

    @Test
    fun clear_selection_appears_only_while_a_point_is_selected_and_clears_it() =
        runComposeUiTest {
            var selected by mutableStateOf<Int?>(null)
            setContent {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    selectedPointIndex = selected,
                    onPointSelected = { selected = it }
                )
            }
            assertFalse("Clear selection" in onChartNode().customActionLabels())

            selected = 1
            waitForIdle()
            onChartNode().performCustomAction("Clear selection")
            waitForIdle()
            assertNull(selected)
        }

    @Test
    fun a_stale_selection_index_survives_a_dataset_shrink_without_crashing() = runComposeUiTest {
        // The index is hoisted, so the caller can hold one that no longer exists;
        // the chart must skip the crosshair rather than crash.
        var dataState by mutableStateOf(dataSet)
        setContent {
            LineChart(
                dataSet = dataState,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedPointIndex = 2
            )
        }
        waitForIdle()

        dataState = LineDataSet(
            series = listOf(dataSet.series.first().copy(points = dataSet.series.first().points.take(1))),
            contentDescription = "Quarterly revenue"
        )
        waitForIdle()
        onChartNode().assertExists()
    }

    private fun ImageBitmap.countColor(color: Color): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        return pixels.count { it == target }
    }

    /** Red pixels in the right third of the plot, where a stacked series draws none. */
    private fun ImageBitmap.countColorInRightThird(color: Color): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        return pixels.indices.count { it % width >= width * 2 / 3 && pixels[it] == target }
    }

    @Test
    fun an_unusable_pinned_x_range_falls_back_to_the_data_rather_than_stacking_points() =
        runComposeUiTest {
            // xMax below every x in the data leaves a negative span, which used to map
            // every point onto chartLeft and draw the series as one vertical stroke.
            val farFromOrigin = LineDataSet(
                series = listOf(
                    LineSeries(
                        id = "far",
                        label = "Far",
                        points = listOf(
                            LineDataPoint(x = 30f, y = 10f),
                            LineDataPoint(x = 35f, y = 25f),
                            LineDataPoint(x = 40f, y = 18f)
                        ),
                        color = Color.Red,
                        strokeWidth = 4.dp
                    )
                ),
                contentDescription = "Far from the origin"
            )
            setContent {
                LineChart(
                    dataSet = farFromOrigin,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    axisConfig = LineAxisConfig(xMax = 25f)
                )
            }
            waitForIdle()

            val reached = onChartNode().captureToImage().countColorInRightThird(Color.Red)
            assertTrue(reached > 0, "the series never reached the right of the plot")
        }

    private val overTheCeiling = LineDataSet(
        series = listOf(
            LineSeries(
                id = "symptom",
                label = "Symptom",
                points = listOf(
                    LineDataPoint(x = 0f, y = 4f, label = "Mon"),
                    LineDataPoint(x = 1f, y = 10.05f, label = "Tue"),
                    LineDataPoint(x = 2f, y = 6f, label = "Wed")
                )
            )
        ),
        contentDescription = "Symptom score"
    )

    private val greenRing = LineCrosshairConfig(
        showTooltip = false,
        dotBorderColor = Color.Green
    )

    @Test
    fun a_pinned_x_range_publishes_no_select_action_for_a_point_outside_it() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = overTheCeiling,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(xMin = 1f)
            )
        }
        waitForIdle()

        val labels = onChartNode().customActionLabels()
        assertFalse(labels.any { it.contains("Mon") }, "Mon is off the axis but got $labels")
        assertTrue(labels.any { it.contains("Tue") }, "Tue is on the axis but got $labels")
        assertTrue(labels.any { it.contains("Wed") }, "Wed is on the axis but got $labels")
    }

    @Test
    fun an_unpinned_chart_publishes_a_select_action_for_every_point() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = overTheCeiling,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        val labels = onChartNode().customActionLabels()
        listOf("Mon", "Tue", "Wed").forEach { day ->
            assertTrue(labels.any { it.contains(day) }, "$day missing from $labels")
        }
    }

    @Test
    fun a_point_inside_a_pinned_range_gets_its_crosshair_dot() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = overTheCeiling,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(yMin = 0f, yMax = 10f),
                crosshairConfig = greenRing,
                selectedPointIndex = 0
            )
        }
        waitForIdle()

        val ring = onChartNode().captureToImage().countColor(Color.Green)
        assertTrue(ring > 0, "the crosshair dot for an in-range point never painted")
    }

    @Test
    fun a_point_just_past_a_pinned_bound_gets_no_crosshair_dot() = runComposeUiTest {
        // 10.05 on a 0..10 axis sits about 1.4dp above chartTop, well inside the dot
        // radius that used to be tolerated, so the ring painted outside the plot.
        setContent {
            LineChart(
                dataSet = overTheCeiling,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(yMin = 0f, yMax = 10f),
                crosshairConfig = greenRing,
                selectedPointIndex = 1
            )
        }
        waitForIdle()

        val ring = onChartNode().captureToImage().countColor(Color.Green)
        assertEquals(0, ring, "a point above yMax still painted a crosshair dot")
    }
}
