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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinnedAxisTicksTest {

    @Test
    fun `with neither bound pinned the ticks are the nice-number ones`() {
        assertEquals(
            computeNiceAxisTicks(3f, 97f, 4),
            computeAxisTicks(3f, 97f, 4, pinnedMin = null, pinnedMax = null)
        )
    }

    @Test
    fun `a pinned range is used exactly rather than rounded outwards`() {
        val ticks = computeAxisTicks(0f, 7.3f, 5, pinnedMin = 0f, pinnedMax = 10f)
        assertEquals(0f, ticks.first())
        assertEquals(10f, ticks.last())
        assertEquals(listOf(0f, 2f, 4f, 6f, 8f, 10f), ticks)
    }

    @Test
    fun `two charts on the same pinned range produce identical ticks whatever their data`() {
        val quiet = computeAxisTicks(0f, 4f, 5, pinnedMin = 0f, pinnedMax = 10f)
        val loud = computeAxisTicks(0f, 9f, 5, pinnedMin = 0f, pinnedMax = 10f)
        assertEquals(quiet, loud)
    }

    @Test
    fun `pinning only the top leaves the bottom on the data`() {
        val ticks = computeAxisTicks(2f, 41f, 4, pinnedMin = null, pinnedMax = 60f)
        assertEquals(2f, ticks.first())
        assertEquals(60f, ticks.last())
    }

    @Test
    fun `pinning only the bottom leaves the top on the data`() {
        val ticks = computeAxisTicks(-1f, 25f, 4, pinnedMin = -5f, pinnedMax = null)
        assertEquals(-5f, ticks.first())
        assertEquals(25f, ticks.last())
    }

    @Test
    fun `a pinned range narrower than the data still ends at the pinned bounds`() {
        val ticks = computeAxisTicks(-40f, 400f, 4, pinnedMin = 0f, pinnedMax = 100f)
        assertEquals(0f, ticks.first())
        assertEquals(100f, ticks.last())
    }

    @Test
    fun `pinned ticks are evenly spaced and there are tickCount of them`() {
        val ticks = computeAxisTicks(0f, 3f, 6, pinnedMin = -1f, pinnedMax = 5f)
        assertEquals(7, ticks.size)
        val step = ticks[1] - ticks[0]
        for (i in 1 until ticks.size) {
            assertEquals(step, ticks[i] - ticks[i - 1], 1e-4f, "uneven pinned ticks")
        }
    }

    @Test
    fun `a negative pinned minimum survives into the tick values`() {
        val ticks = computeAxisTicks(0f, 10f, 4, pinnedMin = -1f, pinnedMax = 25f)
        assertTrue(ticks.first() < 0f, "expected a negative first tick, got ${ticks.first()}")
        assertEquals(-1f, ticks.first())
    }

    @Test
    fun `a tick count of zero falls back to the two bounds`() {
        assertEquals(listOf(0f, 10f), computeAxisTicks(0f, 5f, 0, pinnedMin = 0f, pinnedMax = 10f))
    }

    @Test
    fun `an inverted pinned range is ignored rather than collapsing the chart`() {
        val auto = computeNiceAxisTicks(0f, 5f, 4)
        assertEquals(auto, computeAxisTicks(0f, 5f, 4, pinnedMin = 9f, pinnedMax = 3f))
        assertEquals(auto, computeAxisTicks(0f, 5f, 4, pinnedMin = 4f, pinnedMax = 4f))
    }

    @Test
    fun `a maximum pinned below the data is ignored rather than inverting the axis`() {
        val ticks = computeAxisTicks(20f, 50f, 5, pinnedMin = null, pinnedMax = 10f)
        assertTrue(ticks.first() < ticks.last(), "axis inverted: $ticks")
        assertEquals(computeNiceAxisTicks(20f, 50f, 5), ticks)
    }

    @Test
    fun `a bound that is not finite is ignored rather than poisoning every tick`() {
        val auto = computeNiceAxisTicks(0f, 5f, 4)
        assertEquals(auto, computeAxisTicks(0f, 5f, 4, pinnedMin = Float.NaN, pinnedMax = 10f))
        assertEquals(
            auto,
            computeAxisTicks(0f, 5f, 4, pinnedMin = 0f, pinnedMax = Float.POSITIVE_INFINITY)
        )
    }
}
