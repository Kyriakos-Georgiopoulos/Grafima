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

package io.grafima.charts.pie

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.grafima.charts.onChartNode
import io.grafima.charts.performCustomAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class PieChartUiTest {

    private val dataSet = PieDataSet(
        entries = listOf(
            PieEntry(id = "design", label = "Design", value = 30f),
            PieEntry(id = "dev", label = "Development", value = 70f)
        ),
        contentDescription = "Team budget"
    )

    private val snapAnimations = PieAnimationConfig(
        initialEntrySpec = snap(),
        morphSpec = snap(),
        selectionSpec = snap(),
        staggerDelayMs = 0L,
        startDelayMs = 0L
    )

    @Test
    fun a_donut_with_centre_content_still_exposes_one_node_that_can_select() =
        runComposeUiTest {
            var selected: PieEntry? = null
            setContent {
                PieChart(
                    dataSet = dataSet,
                    modifier = Modifier.size(300.dp),
                    style = PieChartStyle(donutRatio = 0.5f),
                    animationConfig = snapAnimations,
                    onSliceSelected = { selected = it },
                    centerContent = { BasicText("Total 100") }
                )
            }

            // The centre content is the Canvas's sibling. Merging on the Canvas left
            // it as a second focusable node competing with the chart, and only one of
            // them carried the select actions.
            onChartNode().performCustomAction("Select Development")
            waitForIdle()
            assertEquals("dev", selected?.id)
        }

    @Test
    fun the_select_accessibility_action_reports_the_slice_to_the_callback() = runComposeUiTest {
        var selected: PieEntry? = null
        setContent {
            PieChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                onSliceSelected = { selected = it }
            )
        }
        onChartNode().performCustomAction("Select Development")
        waitForIdle()
        assertEquals("dev", selected?.id)
    }

    @Test
    fun the_clear_selection_action_clears_the_hoisted_state() = runComposeUiTest {
        var selected by mutableStateOf<PieEntry?>(dataSet.entries.first())
        setContent {
            PieChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = selected,
                onSliceSelected = { selected = it }
            )
        }
        onChartNode().performCustomAction("Clear selection")
        waitForIdle()
        assertNull(selected)
    }

    @Test
    fun removing_the_selected_slice_clears_the_selection() = runComposeUiTest {
        var dataState by mutableStateOf(dataSet)
        var selected by mutableStateOf<PieEntry?>(dataSet.entries.first())
        setContent {
            PieChart(
                dataSet = dataState,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = selected,
                onSliceSelected = { selected = it }
            )
        }
        waitForIdle()

        dataState = PieDataSet(
            entries = listOf(PieEntry(id = "qa", label = "QA", value = 10f)),
            contentDescription = "Team budget"
        )
        waitForIdle()
        assertNull(selected, "selection must clear when its slice leaves the dataset")
    }

}
