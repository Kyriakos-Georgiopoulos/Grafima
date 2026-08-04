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

class CrosshairMathTest {

    private val points = listOf(
        LineDataPoint(x = 0f, y = 10f),
        LineDataPoint(x = 1f, y = 20f),
        LineDataPoint(x = 2f, y = 15f)
    )

    @Test
    fun `endpoints and midpoint map correctly in LTR`() {
        assertEquals(100f, mapDataXToCanvas(0f, 0f, 2f, 100f, 300f, isRtl = false))
        assertEquals(200f, mapDataXToCanvas(1f, 0f, 2f, 100f, 300f, isRtl = false))
        assertEquals(300f, mapDataXToCanvas(2f, 0f, 2f, 100f, 300f, isRtl = false))
    }

    @Test
    fun `RTL mirrors data points across the chart area`() {
        // In RTL the smallest data X renders at the RIGHT edge.
        assertEquals(300f, mapDataXToCanvas(0f, 0f, 2f, 100f, 300f, isRtl = true))
        assertEquals(100f, mapDataXToCanvas(2f, 0f, 2f, 100f, 300f, isRtl = true))
        assertEquals(200f, mapDataXToCanvas(1f, 0f, 2f, 100f, 300f, isRtl = true))
    }

    @Test
    fun `a degenerate range falls back to the chart left edge`() {
        assertEquals(100f, mapDataXToCanvas(7f, 7f, 7f, 100f, 300f, isRtl = false))
    }

    @Test
    fun `a touch at a point own position snaps to that point`() {
        for ((index, canvasX) in listOf(0 to 100f, 1 to 200f, 2 to 300f)) {
            assertEquals(
                index,
                nearestPointIndex(points, canvasX, 0f, 2f, 100f, 300f, isRtl = false)
            )
        }
    }

    @Test
    fun `touches between points choose the closer one`() {
        assertEquals(0, nearestPointIndex(points, 140f, 0f, 2f, 100f, 300f, isRtl = false))
        assertEquals(1, nearestPointIndex(points, 170f, 0f, 2f, 100f, 300f, isRtl = false))
    }

    @Test
    fun `the nearest point respects RTL mirroring`() {
        // Touch near the right edge in RTL selects the FIRST data point.
        assertEquals(0, nearestPointIndex(points, 290f, 0f, 2f, 100f, 300f, isRtl = true))
        assertEquals(2, nearestPointIndex(points, 110f, 0f, 2f, 100f, 300f, isRtl = true))
    }

    @Test
    fun `out-of-bounds touches clamp to the edge points`() {
        assertEquals(0, nearestPointIndex(points, -500f, 0f, 2f, 100f, 300f, isRtl = false))
        assertEquals(2, nearestPointIndex(points, 9999f, 0f, 2f, 100f, 300f, isRtl = false))
    }

    @Test
    fun `an empty list has no nearest point`() {
        assertEquals(-1, nearestPointIndex(emptyList(), 150f, 0f, 2f, 100f, 300f, isRtl = false))
    }

    @Test
    fun `a point outside the axis is never the nearest one`() {
        val straddling = listOf(
            LineDataPoint(x = -5f, y = 1f),
            LineDataPoint(x = 1f, y = 2f),
            LineDataPoint(x = 2f, y = 3f)
        )
        assertEquals(1, nearestPointIndex(straddling, 20f, 0f, 2f, 100f, 300f, isRtl = false))
    }

    @Test
    fun `an axis with no point inside it has no nearest point`() {
        val offAxis = listOf(LineDataPoint(x = 40f, y = 1f), LineDataPoint(x = 50f, y = 2f))
        assertEquals(-1, nearestPointIndex(offAxis, 200f, 0f, 2f, 100f, 300f, isRtl = false))
    }

    /** A pre-abstinence baseline at day -1: the axis has to start below zero. */
    private val baseline = listOf(
        LineDataPoint(x = -1f, y = 5f),
        LineDataPoint(x = 0f, y = 6f),
        LineDataPoint(x = 1f, y = 7f)
    )

    @Test
    fun `a negative axis minimum puts its own value on the left edge`() {
        assertEquals(100f, mapDataXToCanvas(-1f, -1f, 25f, 100f, 300f, isRtl = false))
    }

    @Test
    fun `a negative axis minimum lands on the right edge in RTL`() {
        assertEquals(300f, mapDataXToCanvas(-1f, -1f, 25f, 100f, 300f, isRtl = true))
    }

    @Test
    fun `an axis starting below the data insets the data from the edge`() {
        val atDataMin = mapDataXToCanvas(0f, 0f, 12f, 100f, 300f, isRtl = false)
        val pinnedBelow = mapDataXToCanvas(0f, -1f, 12f, 100f, 300f, isRtl = false)
        assertEquals(100f, atDataMin)
        assertEquals(115.384f, pinnedBelow, 1e-2f)
    }

    @Test
    fun `a touch at a point drawn position still snaps to it across a negative range`() {
        baseline.forEachIndexed { index, point ->
            val drawnX = mapDataXToCanvas(point.x, -1f, 25f, 100f, 300f, isRtl = false)
            assertEquals(
                index,
                nearestPointIndex(baseline, drawnX, -1f, 25f, 100f, 300f, isRtl = false),
                "point ${point.x} drawn at $drawnX did not snap back to index $index"
            )
        }
    }
}
