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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class XWindowTest {

    private val extent = 0f..100f

    @Test
    fun `a window inside the data is shown as asked`() {
        val window = resolveXWindow(extent, 20f..25f)
        assertEquals(20f, window?.start)
        assertEquals(25f, window?.endInclusive)
    }

    @Test
    fun `a window running off the end slides back on and keeps its width`() {
        val window = resolveXWindow(extent, 98f..103f)
        assertEquals(95f, window?.start)
        assertEquals(100f, window?.endInclusive)
    }

    @Test
    fun `a window running off the start slides back on and keeps its width`() {
        val window = resolveXWindow(extent, (-7f)..(-2f))
        assertEquals(0f, window?.start)
        assertEquals(5f, window?.endInclusive)
    }

    @Test
    fun `a window as wide as the data is no window at all`() {
        assertNull(resolveXWindow(extent, 0f..100f))
        assertNull(resolveXWindow(extent, (-10f)..110f))
    }

    @Test
    fun `an unusable window is ignored rather than obeyed`() {
        assertNull(resolveXWindow(extent, 30f..20f))
        assertNull(resolveXWindow(extent, 30f..30f))
        assertNull(resolveXWindow(extent, Float.NaN..20f))
        assertNull(resolveXWindow(extent, 10f..Float.POSITIVE_INFINITY))
        assertNull(resolveXWindow(extent, Float.NEGATIVE_INFINITY..20f))
    }

    @Test
    fun `a dataset with no width has nothing to window`() {
        assertNull(resolveXWindow(5f..5f, 5f..5.1f))
    }

    @Test
    fun `panning moves the window by the delta it is given`() {
        val moved = shiftXWindow(20f..25f, extent, 7f)
        assertEquals(27f, moved.start)
        assertEquals(32f, moved.endInclusive)
    }

    @Test
    fun `panning stops at the end of the data instead of running past it`() {
        val moved = shiftXWindow(90f..100f, extent, 40f)
        assertEquals(90f, moved.start)
        assertEquals(100f, moved.endInclusive)
    }

    @Test
    fun `panning stops at the start of the data instead of running past it`() {
        val moved = shiftXWindow(0f..10f, extent, -40f)
        assertEquals(0f, moved.start)
        assertEquals(10f, moved.endInclusive)
    }

    /** How a fling knows it has arrived: the window it gets back is the one it had. */
    @Test
    fun `a pan with nowhere to go returns the window it was given`() {
        val atEnd = 90f..100f
        assertSame(atEnd, shiftXWindow(atEnd, extent, 40f))
        assertSame(atEnd, shiftXWindow(atEnd, extent, 0f))
        assertSame(atEnd, shiftXWindow(atEnd, extent, Float.NaN))

        // The same contract at the other end, which is what withholds "Show previous".
        val atStart = 0f..10f
        assertSame(atStart, shiftXWindow(atStart, extent, -40f))
    }

    @Test
    fun `a drag towards the end of the axis walks the window back towards its start`() {
        assertEquals(-2f, panDataDelta(dragPx = 100f, plotWidth = 500f, windowWidth = 10f, isRtl = false))
    }

    @Test
    fun `the same drag walks the window the other way in RTL`() {
        assertEquals(2f, panDataDelta(dragPx = 100f, plotWidth = 500f, windowWidth = 10f, isRtl = true))
    }

    @Test
    fun `a drag on a chart with no width moves nothing`() {
        assertEquals(0f, panDataDelta(dragPx = 100f, plotWidth = 0f, windowWidth = 10f, isRtl = false))
        assertEquals(0f, panDataDelta(dragPx = Float.NaN, plotWidth = 500f, windowWidth = 10f, isRtl = false))
    }

    @Test
    fun `a full window of a drag covers exactly the window's width`() {
        val delta = panDataDelta(dragPx = 300f, plotWidth = 300f, windowWidth = 5f, isRtl = false)
        assertEquals(-5f, delta)
    }

    @Test
    fun `the positions on the axis are the ones inside the window`() {
        val positions = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f)
        assertEquals(2..4, visiblePositions(positions, 2f, 4f))
    }

    @Test
    fun `a window between two positions holds none of them`() {
        val positions = floatArrayOf(0f, 10f)
        assertTrue(visiblePositions(positions, 3f, 6f).isEmpty())
    }
}
