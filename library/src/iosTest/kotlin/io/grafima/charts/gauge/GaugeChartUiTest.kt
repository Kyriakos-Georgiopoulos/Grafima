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

package io.grafima.charts.gauge

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GaugeChartUiTest {

    private val snapAnimations = GaugeAnimationConfig(
        needleSpec = snap(),
        initialDelayMs = 0L
    )

    private fun hasRangeInfo(current: Float, min: Float, max: Float) =
        SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(current = current, range = min..max)
        )

    @Test
    fun `the gauge exposes its value as progress semantics`() = runComposeUiTest {
        setContent {
            GaugeChart(
                value = 30f,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }
        onNode(hasRangeInfo(current = 30f, min = 0f, max = 100f)).assertExists()
    }

    @Test
    fun `a value change updates the progress semantics`() = runComposeUiTest {
        var value by mutableStateOf(30f)
        setContent {
            GaugeChart(
                value = value,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        value = 65f
        waitForIdle()
        onNode(hasRangeInfo(current = 65f, min = 0f, max = 100f)).assertExists()
    }

    @Test
    fun `out-of-range values clamp in the semantics`() = runComposeUiTest {
        setContent {
            GaugeChart(
                value = 150f,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }
        onNode(hasRangeInfo(current = 100f, min = 0f, max = 100f)).assertExists()
    }
}
