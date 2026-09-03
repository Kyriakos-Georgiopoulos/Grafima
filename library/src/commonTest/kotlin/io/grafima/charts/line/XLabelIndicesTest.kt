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

class XLabelIndicesTest {

    private fun points(count: Int) = List(count) { LineDataPoint(x = it.toFloat(), y = 1f) }

    private fun labelled(
        count: Int,
        maxLabels: Int,
        xMin: Float = 0f,
        xMax: Float = (count - 1).toFloat(),
        restrict: Boolean = false
    ): List<Int> = xLabelIndices(points(count), maxLabels, xMin, xMax, restrict).toList()

    @Test
    fun `a chart with room for every label labels every point`() {
        assertEquals(listOf(0, 1, 2, 3), labelled(count = 4, maxLabels = 12))
    }

    @Test
    fun `more points than labels are thinned to at most the maximum`() {
        val indices = labelled(count = 100, maxLabels = 12)
        assertTrue(indices.size <= 12, "12 labels asked for, ${indices.size} produced")
        assertEquals(listOf(0, 9, 18, 27, 36, 45, 54, 63, 72, 81, 90, 99), indices)
    }

    @Test
    fun `a window of five days out of seven hundred labels all five`() {
        // Counting the stride over the whole series instead would give every 59th
        // point, and a five-day window would carry at most one label.
        val indices = labelled(
            count = 700,
            maxLabels = 12,
            xMin = 695f,
            xMax = 699f,
            restrict = true
        )
        assertEquals(listOf(695, 696, 697, 698, 699), indices)
    }

    @Test
    fun `a label stays with its point as the window moves`() {
        val points = points(700)
        val at = { start: Int ->
            xLabelIndices(points, 12, start.toFloat(), (start + 39).toFloat(), true).toList()
        }
        // 40 visible points, 12 labels: every fourth point carries one, and which
        // points those are does not change as the window slides over them.
        val first = at(100)
        val second = at(101)
        assertTrue(first.all { it % 4 == 0 }, "labels drifted off the stride: $first")
        val bothShow = 101..139
        assertEquals(first.filter { it in bothShow }, second.filter { it in bothShow })
    }

    @Test
    fun `a window holding no points carries no labels`() {
        val far = listOf(LineDataPoint(0f, 1f), LineDataPoint(100f, 1f))
        assertTrue(xLabelIndices(far, 12, 40f, 60f, restrictToAxis = true).isEmpty())
    }

    @Test
    fun `an empty series and a chart asked for no labels both produce none`() {
        assertTrue(xLabelIndices(emptyList(), 12, 0f, 1f, restrictToAxis = false).isEmpty())
        assertTrue(labelled(count = 10, maxLabels = 0).isEmpty())
    }
}
