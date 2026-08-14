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

class AxisIndexTest {

    private fun series(id: String, vararg xs: Float) =
        LineSeries(id = id, label = id, points = xs.map { LineDataPoint(x = it, y = 1f) })

    @Test
    fun `one series gives one axis position per point in ascending order`() {
        val axis = buildAxisIndex(listOf(series("a", 0f, 1f, 2f, 3f)))
        assertEquals(listOf(0f, 1f, 2f, 3f), axis.positions.toList())
        repeat(4) { assertEquals(it, axis.pointIndex("a", it)) }
    }

    @Test
    fun `a newest-first series lands on the same ascending axis`() {
        val axis = buildAxisIndex(listOf(series("a", 3f, 2f, 1f, 0f)))
        assertEquals(listOf(0f, 1f, 2f, 3f), axis.positions.toList())
        // Position 0 is x=0, which is that series' last point.
        assertEquals(3, axis.pointIndex("a", 0))
        assertEquals(0, axis.pointIndex("a", 3))
    }

    @Test
    fun `an unsorted series is still placed on every x it reaches`() {
        // The bisect this replaced returned -1 for all but one of these.
        val axis = buildAxisIndex(listOf(series("a", 2f, 0f, 3f, 1f)))
        assertEquals(listOf(0f, 1f, 2f, 3f), axis.positions.toList())
        assertEquals(1, axis.pointIndex("a", 0))
        assertEquals(3, axis.pointIndex("a", 1))
        assertEquals(0, axis.pointIndex("a", 2))
        assertEquals(2, axis.pointIndex("a", 3))
    }

    @Test
    fun `series that share x share positions and each keeps its own point`() {
        val axis = buildAxisIndex(listOf(series("a", 0f, 1f, 2f), series("b", 0f, 1f, 2f)))
        assertEquals(3, axis.positions.size)
        repeat(3) {
            assertEquals(it, axis.pointIndex("a", it))
            assertEquals(it, axis.pointIndex("b", it))
        }
    }

    @Test
    fun `an x only a later series reaches becomes a position of its own`() {
        val axis = buildAxisIndex(listOf(series("curve", 0f, 1f, 2f), series("marker", 9f)))
        assertEquals(listOf(0f, 1f, 2f, 9f), axis.positions.toList())
        assertEquals(-1, axis.pointIndex("curve", 3))
        assertEquals(0, axis.pointIndex("marker", 3))
        assertEquals(-1, axis.pointIndex("marker", 0))
    }

    @Test
    fun `five-minute epoch millis keep one position each`() {
        // A fixed ulp tolerance was 8.7 minutes wide here and merged them all.
        val base = 1786492800000L
        val stamps = (0 until 288).map { (base + it * 300_000L).toFloat() }.toFloatArray()
        val axis = buildAxisIndex(listOf(series("t", *stamps)))
        assertEquals(288, axis.positions.size, "five-minute samples were merged")
        stamps.indices.forEach { assertEquals(it, axis.pointIndex("t", it), "sample $it") }
    }

    @Test
    fun `two spellings of the same position merge rather than doubling the axis`() {
        // One series writes its x down, the other accumulates and drifts.
        val written = (0 until 200).map { it * 0.1f }.toFloatArray()
        var acc = 0f
        val accumulated = FloatArray(200) { acc.also { _ -> acc += 0.1f } }
        assertTrue(written.last() != accumulated.last(), "pick x values that actually drift")

        val axis = buildAxisIndex(listOf(series("written", *written), series("drifted", *accumulated)))
        assertEquals(200, axis.positions.size, "drift split the axis into phantom stops")
        repeat(200) {
            assertEquals(it, axis.pointIndex("written", it))
            assertEquals(it, axis.pointIndex("drifted", it))
        }
    }

    @Test
    fun `a repeated x is one position resolving to the first of them`() {
        val axis = buildAxisIndex(listOf(series("a", 0f, 1f, 1f, 2f)))
        assertEquals(listOf(0f, 1f, 2f), axis.positions.toList())
        assertEquals(1, axis.pointIndex("a", 1))
    }

    @Test
    fun `an empty dataset has no positions`() {
        assertEquals(0, buildAxisIndex(emptyList()).positions.size)
        assertEquals(0, buildAxisIndex(listOf(series("a"))).positions.size)
        assertEquals(-1, buildAxisIndex(emptyList()).pointIndex("a", 0))
    }

    @Test
    fun `a non-finite x takes no position`() {
        val axis = buildAxisIndex(listOf(series("a", 0f, Float.NaN, 1f)))
        assertEquals(listOf(0f, 1f), axis.positions.toList())
        assertEquals(0, axis.pointIndex("a", 0))
        assertEquals(2, axis.pointIndex("a", 1))
    }

    @Test
    fun `a single point and a flat axis are both usable`() {
        assertEquals(listOf(5f), buildAxisIndex(listOf(series("a", 5f))).positions.toList())
        assertEquals(listOf(5f), buildAxisIndex(listOf(series("a", 5f, 5f))).positions.toList())
    }

    @Test
    fun `negative and unevenly spaced positions keep their order`() {
        val axis = buildAxisIndex(listOf(series("a", -10f, -2.5f, 0f, 13.75f)))
        assertEquals(listOf(-10f, -2.5f, 0f, 13.75f), axis.positions.toList())
        assertEquals(1, axis.pointIndex("a", 1))
    }
}
