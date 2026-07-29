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
import io.grafima.charts.runEngineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RadarChartAnimationEngineTest {

    private val snapConfig = RadarAnimationConfig(
        initialEntrySpec = snap(),
        morphSpec = snap(),
        selectionSpec = snap(),
        startDelayMs = 0L,
        seriesStaggerMs = 0L,
        vertexStaggerMs = 0L
    )

    private val axes = listOf(
        RadarAxis(id = "speed", label = "Speed"),
        RadarAxis(id = "power", label = "Power")
    )

    private fun series(id: String, values: Map<String, Float>) =
        RadarSeries(id = id, label = id.uppercase(), values = values)

    @Test
    fun `sync keys vertices by series and axis and alpha by series`() {
        val engine = RadarChartAnimationEngine()
        engine.syncAnimatables(axes, listOf(series("s1", mapOf("speed" to 50f))))

        assertEquals(setOf("s1::speed", "s1::power"), engine.valueAnimatables.keys)
        assertEquals(setOf("s1"), engine.alphaAnimatables.keys)
    }

    @Test
    fun `a removed series keeps its animatables while it collapses`() {
        val engine = RadarChartAnimationEngine()
        engine.syncAnimatables(axes, listOf(series("s1", emptyMap()), series("s2", emptyMap())))
        engine.syncAnimatables(axes, listOf(series("s1", emptyMap())))

        // "s2" left the dataset but is still drawn while it collapses to the
        // centre, so its animatables outlive the swap. Eviction is the exit's job.
        assertEquals(listOf("s2"), engine.exiting.map { it.series.id })
        assertNotNull(engine.valueAnimatables["s2::speed"])
        assertNotNull(engine.alphaAnimatables["s2"])
    }

    @Test
    fun `a series is still drawn on the very frame it leaves the dataset`() {
        val engine = RadarChartAnimationEngine()
        val data = listOf(series("s1", emptyMap()), series("s2", emptyMap()))
        engine.syncAnimatables(axes, data)

        // renderSeries runs during composition, before the SideEffect files "s2"
        // under exiting. It must already report "s2" or the shape blinks out.
        assertEquals(emptyList(), engine.exiting.map { it.series.id })
        assertEquals(
            listOf("s1", "s2"),
            engine.renderSeries(listOf(series("s1", emptyMap()))).map { it.id }
        )
    }

    @Test
    fun `a missing axis value animates the vertex to zero`() = runEngineTest { harness ->
        val engine = RadarChartAnimationEngine()
        val data = listOf(series("s1", mapOf("speed" to 80f)))
        engine.syncAnimatables(axes, data)

        engine.launchEntryAnimations(axes, data, snapConfig, harness.launchScope())
        harness.advanceFrames(100)

        assertEquals(80f, engine.valueAnimatables.getValue("s1::speed").value)
        assertEquals(0f, engine.valueAnimatables.getValue("s1::power").value)
    }

    @Test
    fun `changed vertices morph to their new values`() = runEngineTest { harness ->
        val engine = RadarChartAnimationEngine()
        val first = listOf(series("s1", mapOf("speed" to 80f, "power" to 40f)))
        engine.syncAnimatables(axes, first)
        engine.launchEntryAnimations(axes, first, snapConfig, harness.launchScope())
        harness.advanceFrames(100)

        val second = listOf(series("s1", mapOf("speed" to 20f, "power" to 40f)))
        engine.syncAnimatables(axes, second)
        engine.launchEntryAnimations(axes, second, snapConfig, harness.launchScope())
        harness.advanceFrames(100)

        assertEquals(20f, engine.valueAnimatables.getValue("s1::speed").value)
        assertEquals(40f, engine.valueAnimatables.getValue("s1::power").value)
    }

    @Test
    fun `selecting a series dims only the unselected ones`() = runEngineTest { harness ->
        val engine = RadarChartAnimationEngine()
        val style = RadarChartStyle(unselectedAlpha = 0.2f)
        val data = listOf(series("s1", emptyMap()), series("s2", emptyMap()))
        engine.syncAnimatables(axes, data)

        engine.launchSelectionAnimations(data, data[0], style, snapConfig, harness.launchScope())
        harness.advanceFrames(100)
        assertEquals(1f, engine.alphaAnimatables.getValue("s1").value)
        assertEquals(0.2f, engine.alphaAnimatables.getValue("s2").value)

        engine.launchSelectionAnimations(data, null, style, snapConfig, harness.launchScope())
        harness.advanceFrames(100)
        assertEquals(1f, engine.alphaAnimatables.getValue("s2").value)
    }
}
