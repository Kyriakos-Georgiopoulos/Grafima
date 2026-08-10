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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.grafima.charts.assumePixelCapture
import io.grafima.charts.contentDescription
import io.grafima.charts.customActionLabels
import io.grafima.charts.onChartNode
import io.grafima.charts.performCustomAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    /**
     * The centre of the band of columns holding [color], or null when absent.
     * The midpoint, not the first maximum: a stroke several pixels wide would
     * otherwise report its left edge.
     */
    private fun ImageBitmap.columnOf(color: Color): Float? {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        val perColumn = IntArray(width)
        pixels.forEachIndexed { i, p -> if (p == target) perColumn[i % width]++ }
        val peak = perColumn.maxOrNull() ?: return null
        if (peak == 0) return null
        val hit = perColumn.indices.filter { perColumn[it] == peak }
        return (hit.first() + hit.last()) / 2f
    }

    private fun ImageBitmap.countColor(color: Color): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val target = color.toArgb()
        return pixels.count { it == target }
    }

    private fun Int.isReddish(): Boolean {
        val r = (this shr 16) and 0xFF
        val g = (this shr 8) and 0xFF
        val b = this and 0xFF
        return r > 120 && g < 90 && b < 90
    }

    private fun ImageBitmap.reddishInRows(rows: IntRange): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return rows.sumOf { y ->
            (0 until width).count { x -> pixels[y * width + x].isReddish() }
        }
    }

    /**
     * Pixels that are recognisably red, per horizontal third. Rotated text is
     * resampled, so an exact colour match finds almost none of its glyphs.
     */
    private fun ImageBitmap.reddishPerThird(): List<Int> {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val thirds = IntArray(3)
        pixels.forEachIndexed { i, p ->
            if (p.isReddish()) thirds[minOf(2, (i % width) * 3 / width)]++
        }
        return thirds.toList()
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
            assumePixelCapture()
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
        assumePixelCapture()
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
        assumePixelCapture()
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

    @Test
    fun axis_titles_are_spoken_so_the_numbers_carry_their_unit() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(xAxisTitle = "Month", yAxisTitle = "Euros")
            )
        }
        waitForIdle()

        val spoken = onChartNode().contentDescription()
        assertTrue("X axis: Month." in spoken, "x axis title was not announced: $spoken")
        assertTrue("Y axis: Euros." in spoken, "y axis title was not announced: $spoken")
    }

    @Test
    fun a_chart_without_axis_titles_announces_exactly_what_the_builder_produced() =
        runComposeUiTest {
            setContent {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations
                )
            }
            waitForIdle()

            val spoken = onChartNode().contentDescription()
            assertEquals(LineA11yConfig().chartDescriptionBuilder(dataSet), spoken)
        }

    @Test
    fun the_y_axis_title_is_painted_on_the_right_in_a_right_to_left_layout() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    axisConfig = LineAxisConfig(
                        showXLabels = false,
                        showYLabels = false,
                        showGrid = false,
                        labelColor = Color.Red,
                        yAxisTitle = "Euros"
                    )
                )
            }
        }
        waitForIdle()

        val (left, _, right) = onChartNode().captureToImage().reddishPerThird()
        assertTrue(right > 0, "the y title was not painted on the right")
        assertEquals(0, left, "the y title was painted on the left as well")
    }

    @Test
    fun a_long_y_axis_title_is_trimmed_rather_than_drawn_past_the_plot() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(120.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(
                    showXLabels = false,
                    showYLabels = false,
                    showGrid = false,
                    labelColor = Color.Red,
                    yAxisTitle = "An axis title far longer than this chart is tall"
                )
            )
        }
        waitForIdle()

        // Rotated, so an untrimmed title would run off the top and the bottom.
        val image = onChartNode().captureToImage()
        assertEquals(0, image.reddishInRows(0 until 4), "the title ran off the top")
        assertEquals(
            0,
            image.reddishInRows(image.height - 4 until image.height),
            "the title ran off the bottom"
        )
    }

    @Test
    fun the_x_axis_title_is_painted_below_the_plot_not_across_it() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(
                    showXLabels = false,
                    showYLabels = false,
                    showGrid = false,
                    labelColor = Color.Red,
                    xAxisTitle = "Month"
                )
            )
        }
        waitForIdle()

        val image = onChartNode().captureToImage()
        val bottomBand = (image.height * 4 / 5) until image.height
        assertTrue(image.reddishInRows(bottomBand) > 0, "the x title was not painted at the bottom")
        assertEquals(0, image.reddishInRows(0 until image.height * 4 / 5), "the x title sat too high")
    }

    @Test
    fun an_overridden_axis_title_builder_replaces_the_default_wording() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                a11yConfig = LineA11yConfig(
                    chartDescriptionBuilder = { "Revenue by month." },
                    axisTitleDescriptionBuilder = { x, y ->
                        listOfNotNull(x?.let { "Across: $it." }, y?.let { "Up: $it." })
                            .joinToString(" ")
                    }
                ),
                axisConfig = LineAxisConfig(xAxisTitle = "Month", yAxisTitle = "Euros")
            )
        }
        waitForIdle()

        val spoken = onChartNode().contentDescription()
        assertEquals("Revenue by month. Across: Month. Up: Euros.", spoken)
        assertFalse("X axis" in spoken, "the default wording survived the override")
    }

    @Test
    fun a_tooltip_is_remeasured_when_its_text_style_changes() = runComposeUiTest {
        assumePixelCapture()
        // A theme flip while the crosshair is up: the panel behind the text is
        // drawn straight from the config, so a cached layout leaves the old
        // colour on the new background.
        var textColor by mutableStateOf(Color.Red)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                crosshairConfig = LineCrosshairConfig(tooltipTextColor = textColor),
                selectedPointIndex = 1
            )
        }
        waitForIdle()
        assertTrue(onChartNode().captureToImage().countColor(Color.Red) > 0, "no tooltip text")

        textColor = Color.Green
        waitForIdle()

        val image = onChartNode().captureToImage()
        assertTrue(image.countColor(Color.Green) > 0, "the tooltip kept its old colour")
        assertEquals(0, image.countColor(Color.Red), "the old colour is still painted")
    }

    @Test
    fun a_description_ending_in_other_punctuation_keeps_it() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                a11yConfig = LineA11yConfig(chartDescriptionBuilder = { "Revenue by month;" }),
                axisConfig = LineAxisConfig(xAxisTitle = "Month")
            )
        }
        waitForIdle()

        assertEquals("Revenue by month; X axis: Month.", onChartNode().contentDescription())
    }

    @Test
    fun a_custom_description_does_not_run_into_the_axis_titles() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                a11yConfig = LineA11yConfig(chartDescriptionBuilder = { "Revenue by month" }),
                axisConfig = LineAxisConfig(xAxisTitle = "Month")
            )
        }
        waitForIdle()

        val spoken = onChartNode().contentDescription()
        assertTrue("month. X axis" in spoken, "the title ran into the description: $spoken")
    }

    @Test
    fun a_blank_axis_title_is_treated_as_absent_rather_than_announced_empty() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(xAxisTitle = "   ", yAxisTitle = "")
            )
        }
        waitForIdle()

        val spoken = onChartNode().contentDescription()
        assertFalse("X axis:" in spoken, "a blank title was announced: $spoken")
        assertFalse("Y axis:" in spoken, "a blank title was announced: $spoken")
    }

    @Test
    fun the_crosshair_lands_under_the_finger_when_a_y_axis_title_is_set() = runComposeUiTest {
        assumePixelCapture()
        val many = LineDataSet(
            series = listOf(
                LineSeries(
                    id = "revenue",
                    label = "Revenue",
                    points = (0..39).map { LineDataPoint(x = it.toFloat(), y = (it % 5).toFloat()) }
                )
            ),
            contentDescription = "Forty points"
        )
        var picked by mutableStateOf<Int?>(null)
        setContent {
            LineChart(
                dataSet = many,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(
                    yAxisTitle = "Discomfort strength",
                    showGrid = false
                ),
                crosshairConfig = LineCrosshairConfig(
                    lineColor = Color.Magenta,
                    lineWidth = 5.dp,
                    showTooltip = false
                ),
                selectedPointIndex = picked,
                onPointSelected = { picked = it }
            )
        }

        val width = onChartNode().fetchSemanticsNode().size.width
        val tapX = width * 0.4f
        onChartNode().performTouchInput { down(Offset(tapX, 4f)) }
        waitForIdle()

        assertNotNull(picked, "the touch selected nothing")
        val crosshairX = onChartNode().captureToImage().columnOf(Color.Magenta)
        assertNotNull(crosshairX, "no crosshair was drawn for selection $picked")
        val tolerance = width * 0.02f
        assertTrue(
            kotlin.math.abs(crosshairX - tapX) <= tolerance,
            "crosshair drew at $crosshairX for a touch at $tapX (point $picked)"
        )
    }

    @Test
    fun an_axis_title_is_actually_painted_and_not_merely_reserved_space() = runComposeUiTest {
        assumePixelCapture()
        // Labels off, so the only text the label colour can come from is a title.
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(
                    showXLabels = false,
                    showYLabels = false,
                    showGrid = false,
                    labelColor = Color.Red,
                    xAxisTitle = "Month"
                )
            )
        }
        waitForIdle()

        val painted = onChartNode().captureToImage().countColor(Color.Red)
        assertTrue(painted > 0, "the x axis title reserved space but drew nothing")
    }

    @Test
    fun a_chart_with_no_axis_title_paints_none_of_the_title_colour() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(
                    showXLabels = false,
                    showYLabels = false,
                    showGrid = false,
                    labelColor = Color.Red
                )
            )
        }
        waitForIdle()

        assertEquals(0, onChartNode().captureToImage().countColor(Color.Red))
    }

    @Test
    fun axis_titles_survive_a_right_to_left_layout() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    axisConfig = LineAxisConfig(xAxisTitle = "Month", yAxisTitle = "Euros")
                )
            }
        }
        waitForIdle()

        // Mirroring is a paint concern; the a11y contract must be unchanged.
        val spoken = onChartNode().contentDescription()
        assertTrue("X axis: Month." in spoken, "RTL lost the x axis title: $spoken")
        assertTrue("Y axis: Euros." in spoken, "RTL lost the y axis title: $spoken")
    }
}
