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

package io.grafima.charts.radar

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
import io.grafima.charts.onChartNode
import io.grafima.charts.performCustomAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class RadarChartUiTest {

    private val dataSet = RadarDataSet(
        axes = listOf(
            RadarAxis(id = "speed", label = "Speed"),
            RadarAxis(id = "power", label = "Power"),
            RadarAxis(id = "range", label = "Range")
        ),
        series = listOf(
            RadarSeries(id = "s1", label = "Model A", values = mapOf("speed" to 80f)),
            RadarSeries(id = "s2", label = "Model B", values = mapOf("power" to 60f))
        ),
        contentDescription = "Model comparison"
    )

    private val snapAnimations = RadarAnimationConfig(
        initialEntrySpec = snap(),
        morphSpec = snap(),
        selectionSpec = snap(),
        startDelayMs = 0L,
        seriesStaggerMs = 0L,
        vertexStaggerMs = 0L
    )

    @Test
    fun `the select accessibility action reports the series to the callback`() = runComposeUiTest {
        var selected: RadarSeries? = null
        setContent {
            RadarChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                onSeriesSelected = { selected = it }
            )
        }
        onChartNode().performCustomAction("Select Model B")
        waitForIdle()
        assertEquals("s2", selected?.id)
    }

    @Test
    fun `the clear selection action clears the hoisted state`() = runComposeUiTest {
        var selected by mutableStateOf<RadarSeries?>(dataSet.series.first())
        setContent {
            RadarChart(
                dataSet = dataSet,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedSeries = selected,
                onSeriesSelected = { selected = it }
            )
        }
        onChartNode().performCustomAction("Clear selection")
        waitForIdle()
        assertNull(selected)
    }

    @Test
    fun `removing the selected series clears the selection`() = runComposeUiTest {
        var dataState by mutableStateOf(dataSet)
        var selected by mutableStateOf<RadarSeries?>(dataSet.series.first())
        setContent {
            RadarChart(
                dataSet = dataState,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedSeries = selected,
                onSeriesSelected = { selected = it }
            )
        }
        waitForIdle()

        dataState = dataSet.copy(series = listOf(dataSet.series[1]))
        waitForIdle()
        assertNull(selected, "selection must clear when its series leaves the dataset")
    }

    @Test
    fun `the chart announces data changes as a polite live region`() = runComposeUiTest {
        setContent {
            RadarChart(
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
