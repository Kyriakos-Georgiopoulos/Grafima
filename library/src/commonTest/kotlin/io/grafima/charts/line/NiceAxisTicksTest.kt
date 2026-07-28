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

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NiceAxisTicksTest {

    /** Representative ranges: round, awkward, negative, sub-one, and narrow. */
    private val cases = listOf(
        Triple(0f, 100f, 5),
        Triple(3f, 97f, 4),
        Triple(-50f, 50f, 4),
        Triple(0f, 0.7f, 4),
        Triple(12.3f, 12.9f, 3)
    )

    @Test
    fun `ticks always contain the data range`() {
        for ((min, max, count) in cases) {
            val ticks = computeNiceAxisTicks(min, max, count)
            assertTrue(ticks.first() <= min, "first tick ${ticks.first()} > dataMin $min")
            assertTrue(ticks.last() >= max, "last tick ${ticks.last()} < dataMax $max")
        }
    }

    @Test
    fun `the step is always a nice number`() {
        for ((min, max, count) in cases) {
            val ticks = computeNiceAxisTicks(min, max, count)
            val step = ticks[1] - ticks[0]
            val magnitude = 10f.pow(floor(log10(step)))
            val norm = step / magnitude
            assertTrue(
                listOf(1f, 2f, 5f, 10f).any { abs(norm - it) < 1e-4f },
                "step $step for range $min..$max is not 1/2/5×10ⁿ"
            )
        }
    }

    @Test
    fun `ticks are evenly spaced`() {
        for ((min, max, count) in cases) {
            val ticks = computeNiceAxisTicks(min, max, count)
            val step = ticks[1] - ticks[0]
            for (i in 1 until ticks.size) {
                assertEquals(step, ticks[i] - ticks[i - 1], 1e-4f, "uneven ticks in $min..$max")
            }
        }
    }

    @Test
    fun `a simple range produces the expected ticks`() {
        // rawStep 20 → magnitude 10, norm 2 → nice step 20.
        assertEquals(listOf(0f, 20f, 40f, 60f, 80f, 100f), computeNiceAxisTicks(0f, 100f, 5))
    }

    @Test
    fun `degenerate inputs fall back to min and max`() {
        assertEquals(listOf(5f, 10f), computeNiceAxisTicks(5f, 10f, 0))
        assertEquals(listOf(5f, 10f), computeNiceAxisTicks(5f, 10f, -1))
        assertEquals(listOf(7f, 7f), computeNiceAxisTicks(7f, 7f, 4))
        assertEquals(listOf(9f, 3f), computeNiceAxisTicks(9f, 3f, 4))
    }
}
