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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.grafima.charts.LocalReduceMotion
import io.grafima.charts.customActionLabels
import io.grafima.charts.onChartNode
import io.grafima.charts.performCustomAction
import io.grafima.charts.stateDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LineXWindowUiTest {

    /** Twenty days, four of them on screen at a time. */
    private val dataSet = LineDataSet(
        series = listOf(
            LineSeries(
                id = "readings",
                label = "Readings",
                points = List(20) {
                    LineDataPoint(x = it.toFloat(), y = (it % 7).toFloat(), label = "Day $it")
                }
            )
        ),
        contentDescription = "Twenty days"
    )

    private val snapAnimations = LineAnimationConfig(
        entrySpec = snap(),
        morphSpec = snap(),
        staggerMs = 0L,
        startDelayMs = 0L,
        seriesStaggerMs = 0L
    )

    @Test
    fun only_the_points_inside_the_window_can_be_selected() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                xWindow = 5f..8f
            )
        }
        waitForIdle()

        val labels = onChartNode().customActionLabels()
        assertTrue(labels.contains("Select Day 5"), "the window's first day was not selectable: $labels")
        assertTrue(labels.contains("Select Day 8"), "the window's last day was not selectable: $labels")
        assertFalse(labels.contains("Select Day 4"), "a day outside the window was selectable: $labels")
        assertFalse(labels.contains("Select Day 9"), "a day outside the window was selectable: $labels")
    }

    @Test
    fun the_window_names_the_stretch_it_is_showing() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                xWindow = 5f..8f
            )
        }
        waitForIdle()

        assertEquals("Showing Day 5 to Day 8.", onChartNode().stateDescription())
    }

    @Test
    fun a_chart_showing_all_of_its_data_says_nothing_about_a_window() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        assertEquals("", onChartNode().stateDescription())
        val labels = onChartNode().customActionLabels()
        assertFalse(labels.contains("Show previous"), "an unwindowed chart offered a pan action: $labels")
        assertFalse(labels.contains("Show next"), "an unwindowed chart offered a pan action: $labels")
    }

    @Test
    fun the_pan_actions_move_the_window_by_a_full_window() = runComposeUiTest {
        var window by mutableStateOf(5f..8f)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                xWindow = window,
                onXWindowChange = { window = it }
            )
        }
        waitForIdle()

        onChartNode().performCustomAction("Show next")
        waitForIdle()
        assertEquals(8f..11f, window)

        onChartNode().performCustomAction("Show previous")
        waitForIdle()
        assertEquals(5f..8f, window)
    }

    @Test
    fun the_pan_action_is_withheld_at_the_end_of_the_data() = runComposeUiTest {
        var window by mutableStateOf(16f..19f)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                xWindow = window,
                onXWindowChange = { window = it }
            )
        }
        waitForIdle()

        val labels = onChartNode().customActionLabels()
        assertTrue(labels.contains("Show previous"), "there was history to go back to: $labels")
        assertFalse(labels.contains("Show next"), "the window was already at the last day: $labels")
    }

    @Test
    fun a_drag_towards_the_end_of_the_axis_walks_the_window_back() = runComposeUiTest {
        var window by mutableStateOf(8f..11f)
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it }
                )
            }
        }
        waitForIdle()

        onChartNode().performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            moveTo(Offset(width * 0.7f, height * 0.5f))
            up()
        }
        waitForIdle()

        assertTrue(window.start < 8f, "a rightward drag left the window at ${window.start}")
        assertEquals(
            3f,
            window.endInclusive - window.start,
            absoluteTolerance = 1e-3f,
            message = "the window changed width while panning"
        )
    }

    @Test
    fun a_drag_in_rtl_walks_the_window_the_other_way() = runComposeUiTest {
        var window by mutableStateOf(8f..11f)
        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalReduceMotion provides true
            ) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it }
                )
            }
        }
        waitForIdle()

        onChartNode().performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            moveTo(Offset(width * 0.7f, height * 0.5f))
            up()
        }
        waitForIdle()

        assertTrue(window.start > 8f, "the same drag in RTL left the window at ${window.start}")
    }

    @Test
    fun the_window_stops_at_the_start_of_the_data() = runComposeUiTest {
        var window by mutableStateOf(1f..4f)
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it }
                )
            }
        }
        waitForIdle()

        repeat(3) {
            onChartNode().performTouchInput {
                down(Offset(width * 0.1f, height * 0.5f))
                moveTo(Offset(width * 0.9f, height * 0.5f))
                up()
            }
            waitForIdle()
        }

        assertEquals(0f..3f, window)
    }

    @Test
    fun a_plain_drag_on_a_windowed_chart_selects_nothing() = runComposeUiTest {
        var window by mutableStateOf(8f..11f)
        val selections = mutableListOf<Int?>()
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it },
                    onPointSelected = { selections.add(it) }
                )
            }
        }
        waitForIdle()

        onChartNode().performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            moveTo(Offset(width * 0.7f, height * 0.5f))
            up()
        }
        waitForIdle()

        assertTrue(selections.isEmpty(), "a drag meant to pan also moved the crosshair: $selections")
        assertTrue(window.start < 8f, "the drag did not reach the chart at all")
    }

    @Test
    fun a_long_press_on_a_windowed_chart_still_selects() = runComposeUiTest {
        var window by mutableStateOf(8f..11f)
        val selections = mutableListOf<Int?>()
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it },
                    onPointSelected = { selections.add(it) }
                )
            }
        }
        waitForIdle()

        onChartNode().performTouchInput { longClick(Offset(width * 0.5f, height * 0.5f)) }
        waitForIdle()

        assertEquals(8f..11f, window, "a long press moved the window")
        val selected = selections.firstOrNull()
        assertNotNull(selected, "a long press selected nothing: $selections")
        assertTrue(selected in 8..11, "the long press selected day $selected, outside the window")
        assertEquals(null, selections.last(), "the selection outlived the finger")
    }

    @Test
    fun a_window_moved_between_touches_selects_from_where_it_now_is() = runComposeUiTest {
        var window by mutableStateOf(1f..4f)
        val selections = mutableListOf<Int?>()
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it },
                    onPointSelected = { selections.add(it) }
                )
            }
        }
        waitForIdle()

        // The first touch leaves the gesture loop holding the window it saw.
        onChartNode().performTouchInput { longClick(Offset(width * 0.5f, height * 0.5f)) }
        waitForIdle()
        selections.clear()

        window = 15f..18f
        waitForIdle()

        onChartNode().performTouchInput { longClick(Offset(width * 0.5f, height * 0.5f)) }
        waitForIdle()

        val selected = selections.firstOrNull()
        assertNotNull(selected, "the second long press selected nothing: $selections")
        assertTrue(selected in 15..18, "day $selected was selected, outside the window on screen")
    }

    @Test
    fun a_drag_after_a_window_was_set_elsewhere_carries_on_from_it() = runComposeUiTest {
        var window by mutableStateOf(1f..4f)
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it }
                )
            }
        }
        waitForIdle()

        onChartNode().performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            moveTo(Offset(width * 0.5f, height * 0.5f))
            up()
        }
        waitForIdle()

        window = 12f..15f
        waitForIdle()

        onChartNode().performTouchInput {
            down(Offset(width * 0.5f, height * 0.5f))
            moveTo(Offset(width * 0.3f, height * 0.5f))
            up()
        }
        waitForIdle()

        assertTrue(
            window.start > 12f,
            "a drag forward from a window set at 12 ended at ${window.start}"
        )
    }

    @Test
    fun a_window_pans_over_the_pinned_range_rather_than_the_data() = runComposeUiTest {
        var window by mutableStateOf(25f..28f)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                axisConfig = LineAxisConfig(xMin = -5f, xMax = 40f),
                xWindow = window,
                onXWindowChange = { window = it }
            )
        }
        waitForIdle()

        // Past the last day, but well inside the pinned axis, so there is pinned
        // range on both sides and nothing to select.
        val labels = onChartNode().customActionLabels()
        assertTrue(labels.contains("Show previous"), "the pinned range before 25 was unreachable: $labels")
        assertTrue(labels.contains("Show next"), "the pinned range after 28 was unreachable: $labels")
        assertFalse(labels.any { it.startsWith("Select") }, "a day was selectable off the data: $labels")

        onChartNode().performCustomAction("Show next")
        waitForIdle()
        assertEquals(28f..31f, window, "the window stopped at the data instead of the pinned axis")
    }

    @Test
    fun a_window_between_two_points_still_offers_a_way_out() = runComposeUiTest {
        val gapped = LineDataSet(
            series = listOf(
                LineSeries(
                    id = "sparse",
                    label = "Sparse",
                    points = listOf(
                        LineDataPoint(x = 0f, y = 1f, label = "First"),
                        LineDataPoint(x = 40f, y = 2f, label = "Last")
                    )
                )
            ),
            contentDescription = "Two readings far apart"
        )
        setContent {
            LineChart(
                dataSet = gapped,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                xWindow = 18f..22f
            )
        }
        waitForIdle()

        val labels = onChartNode().customActionLabels()
        assertTrue(labels.contains("Show previous"), "stranded in a gap with no way back: $labels")
        assertTrue(labels.contains("Show next"), "stranded in a gap with no way on: $labels")
        assertEquals("", onChartNode().stateDescription(), "a window with no points named one")
    }

    @Test
    fun panning_inside_the_same_run_of_points_announces_nothing_new() = runComposeUiTest {
        var window by mutableStateOf(5.4f..8.4f)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                xWindow = window
            )
        }
        waitForIdle()
        val before = onChartNode().stateDescription()

        // Still days 6 to 8: a live region that spoke here would speak on every
        // frame of a drag.
        window = 5.5f..8.5f
        waitForIdle()

        assertEquals(before, onChartNode().stateDescription())
        assertEquals("Showing Day 6 to Day 8.", before)
    }

    @Test
    fun a_selection_that_has_panned_off_screen_is_not_announced() = runComposeUiTest {
        var window by mutableStateOf(5f..8f)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                xWindow = window,
                onXWindowChange = { window = it },
                selectedPointIndex = 6
            )
        }
        waitForIdle()
        assertEquals(
            "Readings at Day 6: 6. Showing Day 5 to Day 8.",
            onChartNode().stateDescription(),
            "a windowed chart should name the reading first and the window behind it"
        )

        onChartNode().performCustomAction("Show next")
        waitForIdle()

        assertEquals(
            "Showing Day 8 to Day 11.",
            onChartNode().stateDescription(),
            "day 6 was still announced after it scrolled out of the window"
        )
    }

    @Test
    fun a_flick_towards_the_end_of_the_data_settles_exactly_on_it() = runComposeUiTest {
        var window by mutableStateOf(15f..18f)
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                xWindow = window,
                onXWindowChange = { window = it }
            )
        }
        waitForIdle()

        onChartNode().performTouchInput {
            down(Offset(width * 0.9f, height * 0.5f))
            repeat(6) { moveBy(Offset(-width * 0.12f, 0f)) }
            up()
        }
        waitForIdle()

        assertEquals(16f..19f, window, "the flick did not come to rest on the last day")
    }

    @Test
    fun a_vertical_drag_neither_pans_nor_selects() = runComposeUiTest {
        var window by mutableStateOf(8f..11f)
        val selections = mutableListOf<Int?>()
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it },
                    onPointSelected = { selections.add(it) }
                )
            }
        }
        waitForIdle()

        // A chart inside a scrolling column must not swallow the scroll.
        onChartNode().performTouchInput {
            down(Offset(width * 0.5f, height * 0.2f))
            repeat(4) { moveBy(Offset(0f, height * 0.15f)) }
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }
        waitForIdle()

        assertEquals(8f..11f, window, "a vertical drag panned the chart")
        assertTrue(selections.isEmpty(), "a vertical drag selected a point: $selections")
    }

    @Test
    fun a_drag_moves_the_window_by_roughly_the_share_of_it_the_finger_crossed() = runComposeUiTest {
        var window by mutableStateOf(8f..11f)
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LineChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    xWindow = window,
                    onXWindowChange = { window = it }
                )
            }
        }
        waitForIdle()

        onChartNode().performTouchInput {
            down(Offset(width * 0.15f, height * 0.5f))
            moveTo(Offset(width * 0.65f, height * 0.5f))
            up()
        }
        waitForIdle()

        // Half the chart, less the slop the drag spends declaring itself, over a plot
        // a little narrower than the node: about 1.4 of the window's three days. The
        // band is wide enough for any density and label width, which means it pins
        // the gain's order rather than the exact plot rectangle.
        val travelled = 8f - window.start
        assertTrue(
            travelled in 1.1f..1.9f,
            "a drag over half the chart moved the window $travelled of its three days"
        )
    }

    @Test
    fun the_crosshair_can_be_turned_on_after_the_chart_is_composed() = runComposeUiTest {
        var crosshair by mutableStateOf(false)
        val selections = mutableListOf<Int?>()
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                crosshairConfig = LineCrosshairConfig(enabled = crosshair),
                onPointSelected = { selections.add(it) }
            )
        }
        waitForIdle()

        onChartNode().performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        waitForIdle()
        assertTrue(selections.isEmpty(), "a disabled crosshair still selected: $selections")

        crosshair = true
        waitForIdle()

        onChartNode().performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        waitForIdle()
        assertTrue(selections.isNotEmpty(), "the crosshair never came back on")
    }

    @Test
    fun a_chart_with_no_pointer_work_to_do_still_answers_a_touch() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                crosshairConfig = LineCrosshairConfig(enabled = false)
            )
        }
        waitForIdle()

        // Leaving the gesture loop without having suspended spins it on the ui
        // thread, so this hangs rather than fails when it regresses.
        onChartNode().performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        waitForIdle()

        assertEquals("", onChartNode().stateDescription())
    }

    @Test
    fun a_flick_carries_the_window_on_after_the_finger_lifts() {
        val flung = flickedWindowStart(reduceMotion = false)
        val stopped = flickedWindowStart(reduceMotion = true)
        assertTrue(
            flung < stopped,
            "the flick settled at $flung, no further than the same drag without one at $stopped"
        )
    }

    /** Where a flick from 15..18 leaves the window, with and without the settle. */
    private fun flickedWindowStart(reduceMotion: Boolean): Float {
        var landed = 15f
        runComposeUiTest {
            var window by mutableStateOf(15f..18f)
            setContent {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    LineChart(
                        dataSet = dataSet,
                        modifier = Modifier.size(300.dp),
                        animationConfig = snapAnimations,
                        xWindow = window,
                        onXWindowChange = { window = it }
                    )
                }
            }
            waitForIdle()

            onChartNode().performTouchInput {
                down(Offset(width * 0.2f, height * 0.5f))
                repeat(4) { moveBy(Offset(width * 0.09f, 0f)) }
                up()
            }
            waitForIdle()
            landed = window.start
        }
        return landed
    }
}
