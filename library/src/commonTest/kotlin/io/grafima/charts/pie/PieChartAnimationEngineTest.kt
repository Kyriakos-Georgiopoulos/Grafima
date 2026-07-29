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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import io.grafima.charts.runEngineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `a removed slice closes before it stops being drawn`() = runEngineTest { harness ->
        val engine = PieChartAnimationEngine()
        val config = snapConfig.copy(
            initialEntrySpec = tween(durationMillis = 200, easing = LinearEasing)
        )
        val data = entries("a" to 30f, "b" to 70f)

        engine.syncAnimatables(data)
        engine.launchEntryAnimations(data, config, harness.launchScope())
        harness.advanceFrames(400)

        val remaining = entries("a" to 30f)
        engine.syncAnimatables(remaining)
        engine.launchExitAnimations(config, harness.launchScope())

        // Part way shut, still drawn, and still counted in the total — that share
        // is what the surviving slices expand into as it is released.
        harness.advanceFrames(100)
        val midExit = engine.valueAnimatables.getValue("b").value
        assertTrue(midExit > 0f && midExit < 70f, "expected a partial close, got ${'$'}midExit")
        assertEquals(listOf("a", "b"), engine.renderEntries(remaining).map { it.id })
        assertEquals(midExit, engine.exitingValue(remaining))

        harness.advanceFrames(150)
        assertEquals(emptyList(), engine.exiting.map { it.entry.id })
        assertNull(engine.valueAnimatables["b"])
        assertEquals(0f, engine.exitingValue(remaining))
    }

    @Test
    fun `a slice is still drawn on the very frame it leaves the dataset`() {
        val engine = PieChartAnimationEngine()
        engine.syncAnimatables(entries("a" to 30f, "b" to 70f))

        // renderEntries runs during composition, before the SideEffect files "b"
        // under exiting. It must already report "b" or the slice blinks out.
        assertEquals(emptyList(), engine.exiting.map { it.entry.id })
        assertEquals(listOf("a", "b"), engine.renderEntries(entries("a" to 30f)).map { it.id })
    }

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
    fun `sync keeps surviving instances and hands a departed slice to the exit`() {
        val engine = PieChartAnimationEngine()
        engine.syncAnimatables(entries("a" to 30f, "b" to 70f))
        val survivor = engine.valueAnimatables.getValue("a")

        engine.syncAnimatables(entries("a" to 30f, "c" to 40f))

        assertSame(survivor, engine.valueAnimatables.getValue("a"))

        // "b" left the dataset but is still drawn while it closes, so its
        // animatables outlive the swap. Eviction is the exit's job.
        assertEquals(listOf("b"), engine.exiting.map { it.entry.id })
        assertEquals(setOf("a", "b", "c"), engine.valueAnimatables.keys)
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
