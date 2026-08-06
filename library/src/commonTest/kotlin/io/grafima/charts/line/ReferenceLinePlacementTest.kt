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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which reference lines have a place on the plot.
 *
 * Each is tested against its own axis only: a y of 500 says nothing about a line
 * standing at x = 5.
 */
class ReferenceLinePlacementTest {

    private fun ReferenceLine.onAxis() = isOnAxis(xMin = 0f, xMax = 10f, yMin = 0f, yMax = 100f)

    private fun line(value: Float, axis: ReferenceLineAxis) = ReferenceLine(value, axis)

    @Test
    fun `a vertical line inside the x range is drawn`() {
        assertTrue(line(5f, ReferenceLineAxis.X).onAxis())
    }

    @Test
    fun `a horizontal line inside the y range is drawn`() {
        assertTrue(line(50f, ReferenceLineAxis.Y).onAxis())
    }

    @Test
    fun `a line on either bound is drawn`() {
        assertTrue(line(0f, ReferenceLineAxis.X).onAxis())
        assertTrue(line(10f, ReferenceLineAxis.X).onAxis())
        assertTrue(line(0f, ReferenceLineAxis.Y).onAxis())
        assertTrue(line(100f, ReferenceLineAxis.Y).onAxis())
    }

    @Test
    fun `a line past its axis is not drawn`() {
        assertFalse(line(11f, ReferenceLineAxis.X).onAxis())
        assertFalse(line(-1f, ReferenceLineAxis.X).onAxis())
        assertFalse(line(101f, ReferenceLineAxis.Y).onAxis())
        assertFalse(line(-1f, ReferenceLineAxis.Y).onAxis())
    }

    @Test
    fun `each axis is judged on its own range`() {
        // 50 is off the x axis and on the y axis; 5 is the other way about.
        assertFalse(line(50f, ReferenceLineAxis.X).onAxis())
        assertTrue(line(50f, ReferenceLineAxis.Y).onAxis())
        assertTrue(line(5f, ReferenceLineAxis.X).onAxis())
        assertTrue(line(5f, ReferenceLineAxis.Y).onAxis())
    }

    @Test
    fun `a value that is not a number is not drawn`() {
        assertFalse(line(Float.NaN, ReferenceLineAxis.X).onAxis())
        assertFalse(line(Float.POSITIVE_INFINITY, ReferenceLineAxis.Y).onAxis())
        assertFalse(line(Float.NEGATIVE_INFINITY, ReferenceLineAxis.Y).onAxis())
    }
}
