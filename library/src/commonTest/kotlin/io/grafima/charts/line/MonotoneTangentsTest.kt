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

class MonotoneTangentsTest {

    private fun tangentsFor(xs: FloatArray, ys: FloatArray): FloatArray {
        val n = xs.size
        val tangents = FloatArray(n)
        val deltas = FloatArray(maxOf(0, n - 1))
        computeMonotoneTangents(xs, ys, tangents, deltas, n)
        return tangents
    }

    @Test
    fun `flat data has zero tangents`() {
        val t = tangentsFor(floatArrayOf(0f, 1f, 2f, 3f), floatArrayOf(5f, 5f, 5f, 5f))
        assertTrue(t.all { it == 0f }, "expected all-zero tangents, got ${t.toList()}")
    }

    @Test
    fun `linear data has constant slope tangents`() {
        val slope = 3f
        val xs = floatArrayOf(0f, 1f, 2f, 3f)
        val ys = FloatArray(xs.size) { xs[it] * slope }
        val t = tangentsFor(xs, ys)
        t.forEach { assertEquals(slope, it, 1e-4f) }
    }

    @Test
    fun `a local peak has a zero tangent at the extremum`() {
        // Sign change in the deltas around index 1 must zero its tangent,
        // otherwise the cubic overshoots the peak.
        val t = tangentsFor(floatArrayOf(0f, 1f, 2f), floatArrayOf(0f, 1f, 0f))
        assertEquals(0f, t[1])
    }

    @Test
    fun `duplicate x values do not produce NaN`() {
        val t = tangentsFor(floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 5f, 6f))
        assertTrue(t.all { !it.isNaN() }, "NaN tangent in ${t.toList()}")
    }

    @Test
    fun `a steep step respects the Fritsch-Carlson clamp`() {
        // Near-flat then near-vertical: the unclamped average tangent at index 1
        // would overshoot the flat segment. After clamping, α² + β² ≤ 9.
        val xs = floatArrayOf(0f, 1f, 2f)
        val ys = floatArrayOf(0f, 0.001f, 100f)
        val t = tangentsFor(xs, ys)
        val delta0 = (ys[1] - ys[0]) / (xs[1] - xs[0])
        val alpha = t[0] / delta0
        val beta = t[1] / delta0
        assertTrue(alpha * alpha + beta * beta <= 9f + 1e-3f, "clamp violated: α=$alpha β=$beta")
    }

    @Test
    fun `fewer than two points is a no-op`() {
        val tangents = floatArrayOf(7f)
        computeMonotoneTangents(floatArrayOf(1f), floatArrayOf(2f), tangents, FloatArray(0), 1)
        assertEquals(7f, tangents[0])
    }
}
