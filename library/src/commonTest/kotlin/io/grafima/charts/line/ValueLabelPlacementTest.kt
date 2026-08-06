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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where a value label lands, and which of two that want the same spot gets it. */
class ValueLabelPlacementTest {

    private val plotLeft = 100f
    private val plotRight = 300f

    private fun left(pointX: Float, labelWidth: Float = 40f) =
        valueLabelLeft(pointX, labelWidth, plotLeft, plotRight)

    @Test
    fun `a label is centred on its point`() {
        assertEquals(180f, left(pointX = 200f))
    }

    @Test
    fun `a label at the left edge is pulled back inside the plot`() {
        // Centred it would start at 80, with half of it cut off.
        assertEquals(plotLeft, left(pointX = 100f))
    }

    @Test
    fun `a label at the right edge is pulled back inside the plot`() {
        assertEquals(plotRight - 40f, left(pointX = 300f))
    }

    @Test
    fun `a label wider than the plot starts at its left edge`() {
        // The range to clamp into is empty, and an empty range must not invert.
        assertEquals(plotLeft, left(pointX = 200f, labelWidth = 500f))
    }

    private fun top(pointY: Float, preferBelow: Boolean = false, chartTop: Float = 0f) =
        valueLabelTop(
            pointY = pointY,
            labelHeight = 20f,
            offset = 10f,
            chartTop = chartTop,
            chartBottom = 200f,
            preferBelow = preferBelow
        )

    @Test
    fun `a label sits above its point`() {
        assertEquals(70f, top(pointY = 100f))
    }

    @Test
    fun `a label with no room above flips under its point`() {
        // Above would start at -5, off the top of the chart entirely.
        assertEquals(35f, top(pointY = 25f))
    }

    @Test
    fun `a label that exactly reaches the top stays above`() {
        assertEquals(0f, top(pointY = 30f))
    }

    @Test
    fun `a label asked for the underside sits below its point`() {
        assertEquals(110f, top(pointY = 100f, preferBelow = true))
    }

    @Test
    fun `a label with no room below goes back above`() {
        // Below would end at 215, past a chart bottom of 200.
        assertEquals(165f, top(pointY = 195f, preferBelow = true))
    }

    @Test
    fun `a point in a valley puts its label underneath`() {
        // Both neighbours sit higher up the screen, so the curve rises away on
        // each side and the label would land on it.
        assertTrue(valueLabelPrefersBelow(pointY = 100f, previousY = 50f, nextY = 60f))
    }

    @Test
    fun `a point on a peak keeps its label above`() {
        assertFalse(valueLabelPrefersBelow(pointY = 50f, previousY = 100f, nextY = 90f))
    }

    @Test
    fun `a point on a flat run keeps its label above`() {
        assertFalse(valueLabelPrefersBelow(pointY = 50f, previousY = 50f, nextY = 50f))
    }

    @Test
    fun `an end point is decided by the one neighbour it has`() {
        // The missing neighbour is passed as the point itself, leaving the other
        // to carry the average across the threshold or not.
        assertTrue(valueLabelPrefersBelow(pointY = 100f, previousY = 100f, nextY = 50f))
        assertFalse(valueLabelPrefersBelow(pointY = 100f, previousY = 100f, nextY = 150f))
    }

    @Test
    fun `points are walked in data order when the axis runs left to right`() {
        assertEquals(listOf(0, 1, 2), (0..2).map { screenOrderIndex(it, count = 3, isRtl = false) })
    }

    @Test
    fun `points are walked in reverse when the axis runs right to left`() {
        // The last point is the leftmost on screen in RTL, so it is reached first
        // and keeps its place when two labels want the same one.
        assertEquals(listOf(2, 1, 0), (0..2).map { screenOrderIndex(it, count = 3, isRtl = true) })
    }

    @Test
    fun `a single point is reached whichever way the axis runs`() {
        assertEquals(0, screenOrderIndex(step = 0, count = 1, isRtl = false))
        assertEquals(0, screenOrderIndex(step = 0, count = 1, isRtl = true))
    }

    @Test
    fun `the first label to ask for a box gets it`() {
        val boxes = LabelBoxes(capacity = 4)
        assertTrue(boxes.takeIfFree(left = 0f, top = 0f, right = 10f, bottom = 10f))
    }

    @Test
    fun `a label overlapping one already placed is turned away`() {
        val boxes = LabelBoxes(capacity = 4)
        boxes.takeIfFree(left = 0f, top = 0f, right = 10f, bottom = 10f)
        assertFalse(boxes.takeIfFree(left = 5f, top = 5f, right = 15f, bottom = 15f))
    }

    @Test
    fun `a label clear of everything placed is allowed`() {
        val boxes = LabelBoxes(capacity = 4)
        boxes.takeIfFree(left = 0f, top = 0f, right = 10f, bottom = 10f)
        assertTrue(boxes.takeIfFree(left = 11f, top = 0f, right = 20f, bottom = 10f))
    }

    @Test
    fun `two labels in the same column but at different heights both fit`() {
        // Two series crossing share an x. Testing columns alone would drop the
        // second even though there is nothing where it wants to go.
        val boxes = LabelBoxes(capacity = 4)
        boxes.takeIfFree(left = 0f, top = 0f, right = 10f, bottom = 10f)
        assertTrue(boxes.takeIfFree(left = 0f, top = 20f, right = 10f, bottom = 30f))
    }

    @Test
    fun `labels that only touch at the edge do not count as overlapping`() {
        val boxes = LabelBoxes(capacity = 4)
        boxes.takeIfFree(left = 0f, top = 0f, right = 10f, bottom = 10f)
        assertTrue(boxes.takeIfFree(left = 10f, top = 0f, right = 20f, bottom = 10f))
    }

    @Test
    fun `reset frees every box for the next frame`() {
        val boxes = LabelBoxes(capacity = 4)
        boxes.takeIfFree(left = 0f, top = 0f, right = 10f, bottom = 10f)
        boxes.reset()
        assertTrue(boxes.takeIfFree(left = 0f, top = 0f, right = 10f, bottom = 10f))
    }

    @Test
    fun `a label past the capacity is turned away rather than overrunning it`() {
        val boxes = LabelBoxes(capacity = 1)
        assertTrue(boxes.takeIfFree(left = 0f, top = 0f, right = 10f, bottom = 10f))
        assertFalse(boxes.takeIfFree(left = 20f, top = 0f, right = 30f, bottom = 10f))
    }

    @Test
    fun `no capacity at all turns everything away`() {
        assertFalse(LabelBoxes(capacity = 0).takeIfFree(0f, 0f, 10f, 10f))
    }
}
