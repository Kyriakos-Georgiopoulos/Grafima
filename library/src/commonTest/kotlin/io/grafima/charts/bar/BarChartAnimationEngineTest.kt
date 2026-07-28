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
import io.grafima.charts.runEngineTest
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BarChartAnimationEngineTest {

    private val snapConfig = AnimationConfig(
        initialEntrySpec = snap(),
        morphSpec = snap(),
        selectionSpec = snap(),
        staggerDelayMs = 0L,
        startDelayMs = 0L
    )

    private fun entries(vararg pairs: Pair<String, Float>) =
        pairs.map { (id, y) -> BarEntry(id = id, xLabel = id.uppercase(), y = y) }

    @Test
    fun `sync creates animatables for every entry`() {
        val engine = ChartAnimationEngine()
        engine.syncAnimatables(entries("a" to 10f, "b" to 20f))

        assertEquals(setOf("a", "b"), engine.heightAnimatables.keys)
        assertEquals(setOf("a", "b"), engine.selectionAlphaAnimatables.keys)
        assertEquals(0f, engine.heightAnimatables.getValue("a").value)
        assertEquals(1f, engine.selectionAlphaAnimatables.getValue("a").value)
    }

    @Test
    fun `sync removes stale animatables and keeps surviving instances`() {
        val engine = ChartAnimationEngine()
        engine.syncAnimatables(entries("a" to 10f, "b" to 20f))
        val survivor = engine.heightAnimatables.getValue("a")

        engine.syncAnimatables(entries("a" to 10f, "c" to 30f))

        // Same instance survives — that's what keeps morphs continuous across data swaps.
        assertSame(survivor, engine.heightAnimatables.getValue("a"))
        assertNull(engine.heightAnimatables["b"])
        assertEquals(setOf("a", "c"), engine.heightAnimatables.keys)
    }

    @Test
    fun `initial load animates every bar to its target value`() = runEngineTest { harness ->
        val engine = ChartAnimationEngine()
        val data = entries("a" to 10f, "b" to 20f)

        engine.syncAnimatables(data)
        engine.launchEntryAnimations(data, snapConfig, harness.launchScope())
        harness.advanceFrames(100)

        assertEquals(10f, engine.heightAnimatables.getValue("a").value)
        assertEquals(20f, engine.heightAnimatables.getValue("b").value)
    }

    @Test
    fun `a changed value morphs the bar to the new target`() = runEngineTest { harness ->
        val engine = ChartAnimationEngine()

        val first = entries("a" to 10f)
        engine.syncAnimatables(first)
        engine.launchEntryAnimations(first, snapConfig, harness.launchScope())
        harness.advanceFrames(100)

        val second = entries("a" to 42f)
        engine.syncAnimatables(second)
        engine.launchEntryAnimations(second, snapConfig, harness.launchScope())
        harness.advanceFrames(100)

        assertEquals(42f, engine.heightAnimatables.getValue("a").value)
    }

    @Test
    fun `stagger delays every bar entry animation`() = runEngineTest { harness ->
        val engine = ChartAnimationEngine()
        val config = snapConfig.copy(startDelayMs = 100L, staggerDelayMs = 100L)
        val data = entries("a" to 10f, "b" to 20f)

        engine.syncAnimatables(data)
        engine.launchEntryAnimations(data, config, harness.launchScope())

        // Frames pump in 16ms steps to t=160: past bar a's 100ms delay,
        // short of bar b's 200ms delay.
        harness.advanceFrames(150)
        assertEquals(10f, engine.heightAnimatables.getValue("a").value)
        assertEquals(0f, engine.heightAnimatables.getValue("b").value)

        harness.advanceFrames(100)
        assertEquals(20f, engine.heightAnimatables.getValue("b").value)
    }

    /**
     * Regression for the scope bug fixed on the KMP branch: BarChart used to pass
     * `rememberCoroutineScope()` here, so pending stagger coroutines survived a
     * dataset swap and later snapped bars back to the OLD values. The composable
     * now passes the LaunchedEffect scope, which cancels on data change — modelled
     * here by cancelling scope A before relaunching in scope B.
     */
    @Test
    fun `a cancelled scope never applies stale values after a dataset swap`() = runEngineTest { harness ->
        val engine = ChartAnimationEngine()
        val staggered = snapConfig.copy(startDelayMs = 200L, staggerDelayMs = 100L)

        val old = entries("a" to 10f, "b" to 20f)
        engine.syncAnimatables(old)
        val scopeA = harness.launchScope()
        engine.launchEntryAnimations(old, staggered, scopeA)

        // Swap datasets while every stagger coroutine is still inside its delay.
        harness.advanceFrames(50)
        scopeA.cancel()

        val new = entries("a" to 99f, "b" to 88f)
        engine.syncAnimatables(new)
        engine.launchEntryAnimations(new, snapConfig, harness.launchScope())

        // Run far past the old delays: had scope A survived, a=10f/b=20f would land now.
        harness.advanceFrames(1000)
        assertEquals(99f, engine.heightAnimatables.getValue("a").value)
        assertEquals(88f, engine.heightAnimatables.getValue("b").value)
    }

    @Test
    fun `selecting a bar dims the others and clearing restores them`() = runEngineTest { harness ->
        val engine = ChartAnimationEngine()
        val style = ChartStyle(unselectedAlpha = 0.25f)
        val data = entries("a" to 10f, "b" to 20f)
        engine.syncAnimatables(data)

        engine.updateSelectionState(data, data[0], style, snapConfig, harness.launchScope())
        harness.advanceFrames(100)
        assertEquals(1f, engine.selectionAlphaAnimatables.getValue("a").value)
        assertEquals(0.25f, engine.selectionAlphaAnimatables.getValue("b").value)

        engine.updateSelectionState(data, null, style, snapConfig, harness.launchScope())
        harness.advanceFrames(100)
        assertEquals(1f, engine.selectionAlphaAnimatables.getValue("a").value)
        assertEquals(1f, engine.selectionAlphaAnimatables.getValue("b").value)
    }
}
