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

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.grafima.charts.customActionLabels
import io.grafima.charts.performCustomAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BarChartUiTest {

    private val dataSet = BarDataSet(
        entries = listOf(
            BarEntry(id = "jan", xLabel = "Jan", y = 45f),
            BarEntry(id = "feb", xLabel = "Feb", y = 80f)
        ),
        contentDescription = "Monthly revenue"
    )

    private val snapAnimations = AnimationConfig(
        initialEntrySpec = snap(),
        morphSpec = snap(),
        selectionSpec = snap(),
        staggerDelayMs = 0L,
        startDelayMs = 0L
    )

    @Test
    fun the_chart_description_summarises_the_dataset() = runComposeUiTest {
        setContent {
            BarChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }
        // A summary, not a reading of the data — the node is a live region and
        // would otherwise repeat every bar on each selection.
        onNodeWithContentDescription("Monthly revenue", substring = true).assertExists()
        onNodeWithContentDescription("2 bars", substring = true).assertExists()
        onNodeWithContentDescription("Jan value is 45", substring = true).assertDoesNotExist()
    }


    @Test
    fun the_select_accessibility_action_reports_the_entry_to_the_callback() = runComposeUiTest {
        var selected: BarEntry? = null
        setContent {
            BarChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                onBarSelected = { selected = it }
            )
        }
        onNodeWithContentDescription("Monthly revenue", substring = true)
            .performCustomAction("Select Jan")
        waitForIdle()
        assertEquals("jan", selected?.id)
    }

    @Test
    fun the_clear_selection_action_appears_only_while_a_bar_is_selected_and_clears_it() =
        runComposeUiTest {
            var selected by mutableStateOf<BarEntry?>(null)
            setContent {
                BarChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    selectedEntry = selected,
                    onBarSelected = { selected = it }
                )
            }
            val chart = onNodeWithContentDescription("Monthly revenue", substring = true)

            assertFalse("Clear selection" in chart.customActionLabels())

            selected = dataSet.entries.first()
            waitForIdle()
            chart.performCustomAction("Clear selection")
            waitForIdle()
            assertNull(selected)
        }

    @Test
    fun removing_the_selected_entry_clears_the_selection() = runComposeUiTest {
        // Snapshot state so reassignment recomposes the chart.
        var dataState by mutableStateOf(dataSet)
        var selected by mutableStateOf<BarEntry?>(dataSet.entries.first())
        setContent {
            BarChart(
                dataSet = dataState,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = selected,
                onBarSelected = { selected = it }
            )
        }
        waitForIdle()

        dataState = BarDataSet(
            entries = listOf(BarEntry(id = "mar", xLabel = "Mar", y = 30f)),
            contentDescription = "Monthly revenue"
        )
        waitForIdle()
        assertNull(selected, "selection must clear when its entry leaves the dataset")
    }

    /**
     * Pixel-level regression for the iOS first-frame bug: bars used to stay blank
     * because the Canvas never snapshot-subscribed to the height animatables.
     * Bars are painted in solid red so that, once the entry animation settles,
     * a meaningful share of the canvas must be exactly that color — if the bars
     * never painted, the count is zero.
     */
    @Test
    fun bars_actually_paint_after_the_entry_animation() = runComposeUiTest {
        val solidRed = dataSet.copy(defaultGradientColors = listOf(Color.Red, Color.Red))
        setContent {
            BarChart(
                dataSet = solidRed,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        val image = onNodeWithContentDescription("Monthly revenue", substring = true)
            .captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val red = Color.Red.toArgb()
        val barPixels = pixels.count { it == red }
        assertTrue(
            barPixels > pixels.size / 100,
            "bars appear unpainted: $barPixels of ${pixels.size} pixels are the bar color"
        )
    }
}
