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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.grafima.charts.customActionLabels
import io.grafima.charts.onChartNode
import io.grafima.charts.performCustomAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class LineChartUiTest {

    private val dataSet = LineDataSet(
        series = listOf(
            LineSeries(
                id = "revenue",
                label = "Revenue",
                points = listOf(
                    LineDataPoint(x = 0f, y = 10f),
                    LineDataPoint(x = 1f, y = 25f),
                    LineDataPoint(x = 2f, y = 18f)
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
    fun `select next walks forward and clamps at the last point`() = runComposeUiTest {
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

        repeat(5) {
            onChartNode().performCustomAction("Select next point")
            waitForIdle()
        }
        // 3 points: after five "next" presses the index clamps at 2.
        assertEquals(2, selected)
    }

    @Test
    fun `select previous starts at the end and then clamps at zero`() = runComposeUiTest {
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

        onChartNode().performCustomAction("Select previous point")
        waitForIdle()
        assertEquals(2, selected, "previous from no selection starts at the last point")

        repeat(5) {
            onChartNode().performCustomAction("Select previous point")
            waitForIdle()
        }
        assertEquals(0, selected)
    }

    @Test
    fun `clear selection appears only while a point is selected and clears it`() =
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
    fun `a stale selection index survives a dataset shrink without crashing`() = runComposeUiTest {
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

    @Test
    fun `the chart announces data changes as a polite live region`() = runComposeUiTest {
        setContent {
            LineChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }
        onChartNode().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
    }
}
