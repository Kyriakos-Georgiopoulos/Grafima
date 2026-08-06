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
import androidx.compose.ui.unit.dp
import io.grafima.charts.runEngineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LineChartAnimationEngineTest {

    private val snapConfig = LineAnimationConfig(
        entrySpec = snap(),
        morphSpec = snap(),
        staggerMs = 0L,
        startDelayMs = 0L,
        seriesStaggerMs = 0L
    )

    private fun series(id: String, vararg ys: Float) = LineSeries(
        id = id,
        label = id.uppercase(),
        points = ys.mapIndexed { i, y -> LineDataPoint(x = i.toFloat(), y = y) }
    )

    @Test
    fun `a departing series is still rendered with its dash pattern`() {
        // The chart draws what renderSeries returns, so anything keyed off the
        // dataset instead misses a series on its way out — and a derived line
        // would go solid exactly as it leaves.
        val engine = LineChartAnimationEngine()
        val dashed = series("avg", 1f).copy(dashPattern = DashPattern(dash = 10.dp, gap = 6.dp))
        engine.syncAnimatables(listOf(series("s1", 1f), dashed))

        val rendered = engine.renderSeries(listOf(series("s1", 1f)))

        assertEquals(listOf("s1", "avg"), rendered.map { it.id })
        assertEquals(DashPattern(dash = 10.dp, gap = 6.dp), rendered.last().dashPattern)
    }

    @Test
    fun `sync keys animatables by series id and point index`() {
        val engine = LineChartAnimationEngine()
        engine.syncAnimatables(listOf(series("s1", 1f, 2f), series("s2", 3f)))

        assertEquals(setOf("s1::0", "s1::1", "s2::0"), engine.yAnimatables.keys)
    }

    @Test
    fun `shrinking a series removes its tail animatables`() {
        val engine = LineChartAnimationEngine()
        engine.syncAnimatables(listOf(series("s1", 1f, 2f, 3f)))
        val survivor = engine.yAnimatables.getValue("s1::0")

        engine.syncAnimatables(listOf(series("s1", 1f)))

        assertSame(survivor, engine.yAnimatables.getValue("s1::0"))
        assertNull(engine.yAnimatables["s1::1"])
        assertNull(engine.yAnimatables["s1::2"])
    }

    @Test
    fun `points animate from the baseline to their targets on first load`() = runEngineTest { harness ->
        val engine = LineChartAnimationEngine()
        val data = listOf(series("s1", 10f, 20f))
        engine.syncAnimatables(data)

        val delayed = snapConfig.copy(startDelayMs = 100L)
        engine.launchEntryAnimations(data, delayed, yBaseline = 5f, harness.launchScope())

        // One pump: snapTo(baseline) has run, the entry animation is still in its delay.
        harness.advanceFrames(16)
        assertEquals(5f, engine.yAnimatables.getValue("s1::0").value)
        assertEquals(5f, engine.yAnimatables.getValue("s1::1").value)

        harness.advanceFrames(200)
        assertEquals(10f, engine.yAnimatables.getValue("s1::0").value)
        assertEquals(20f, engine.yAnimatables.getValue("s1::1").value)
    }

    @Test
    fun `a changed point morphs to its new target`() = runEngineTest { harness ->
        val engine = LineChartAnimationEngine()
        val first = listOf(series("s1", 10f, 20f))
        engine.syncAnimatables(first)
        engine.launchEntryAnimations(first, snapConfig, yBaseline = 0f, harness.launchScope())
        harness.advanceFrames(100)

        val second = listOf(series("s1", 10f, 77f))
        engine.syncAnimatables(second)
        engine.launchEntryAnimations(second, snapConfig, yBaseline = 0f, harness.launchScope())
        harness.advanceFrames(100)

        assertEquals(10f, engine.yAnimatables.getValue("s1::0").value)
        assertEquals(77f, engine.yAnimatables.getValue("s1::1").value)
    }
}
