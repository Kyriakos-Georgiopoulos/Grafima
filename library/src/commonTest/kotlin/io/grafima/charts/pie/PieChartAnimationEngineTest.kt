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
import io.grafima.charts.runEngineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PieChartAnimationEngineTest {

    private val snapConfig = PieAnimationConfig(
        initialEntrySpec = snap(),
        morphSpec = snap(),
        selectionSpec = snap(),
        staggerDelayMs = 0L,
        startDelayMs = 0L
    )

    private fun entries(vararg pairs: Pair<String, Float>) =
        pairs.map { (id, value) -> PieEntry(id = id, label = id.uppercase(), value = value) }

    @Test
    fun `sync creates value scale and alpha animatables`() {
        val engine = PieChartAnimationEngine()
        engine.syncAnimatables(entries("a" to 30f, "b" to 70f))

        assertEquals(setOf("a", "b"), engine.valueAnimatables.keys)
        assertEquals(setOf("a", "b"), engine.scaleAnimatables.keys)
        assertEquals(setOf("a", "b"), engine.alphaAnimatables.keys)
        assertEquals(0f, engine.valueAnimatables.getValue("a").value)
        assertEquals(1f, engine.scaleAnimatables.getValue("a").value)
        assertEquals(1f, engine.alphaAnimatables.getValue("a").value)
    }

    @Test
    fun `sync removes stale animatables and keeps surviving instances`() {
        val engine = PieChartAnimationEngine()
        engine.syncAnimatables(entries("a" to 30f, "b" to 70f))
        val survivor = engine.valueAnimatables.getValue("a")

        engine.syncAnimatables(entries("a" to 30f, "c" to 40f))

        assertSame(survivor, engine.valueAnimatables.getValue("a"))
        assertNull(engine.valueAnimatables["b"])
        assertNull(engine.scaleAnimatables["b"])
        assertNull(engine.alphaAnimatables["b"])
    }

    @Test
    fun `slices animate in on first load and morph on value change`() = runEngineTest { harness ->
        val engine = PieChartAnimationEngine()

        val first = entries("a" to 30f, "b" to 70f)
        engine.syncAnimatables(first)
        engine.launchEntryAnimations(first, snapConfig, harness.launchScope())
        harness.advanceFrames(100)
        assertEquals(30f, engine.valueAnimatables.getValue("a").value)
        assertEquals(70f, engine.valueAnimatables.getValue("b").value)

        val second = entries("a" to 55f, "b" to 70f)
        engine.syncAnimatables(second)
        engine.launchEntryAnimations(second, snapConfig, harness.launchScope())
        harness.advanceFrames(100)
        assertEquals(55f, engine.valueAnimatables.getValue("a").value)
        assertEquals(70f, engine.valueAnimatables.getValue("b").value)
    }

    @Test
    fun `selecting a slice scales it up and dims the others`() = runEngineTest { harness ->
        val engine = PieChartAnimationEngine()
        val style = PieChartStyle(unselectedAlpha = 0.3f, selectedScale = 1.05f)
        val data = entries("a" to 30f, "b" to 70f)
        engine.syncAnimatables(data)

        engine.launchSelectionAnimations(data, data[0], style, snapConfig, harness.launchScope())
        harness.advanceFrames(100)
        assertEquals(1.05f, engine.scaleAnimatables.getValue("a").value)
        assertEquals(1f, engine.alphaAnimatables.getValue("a").value)
        assertEquals(1f, engine.scaleAnimatables.getValue("b").value)
        assertEquals(0.3f, engine.alphaAnimatables.getValue("b").value)

        engine.launchSelectionAnimations(data, null, style, snapConfig, harness.launchScope())
        harness.advanceFrames(100)
        assertEquals(1f, engine.scaleAnimatables.getValue("a").value)
        assertEquals(1f, engine.alphaAnimatables.getValue("b").value)
    }
}
