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

package io.grafima.charts.gauge

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GaugeMathTest {

    @Test
    fun `the needle angle maps the value linearly across the sweep`() {
        // Gauge defaults: start 135°, sweep 270°.
        assertEquals(135f, valueToAngle(0f, 0f, 100f, 135f, 270f, isRtl = false))
        assertEquals(270f, valueToAngle(50f, 0f, 100f, 135f, 270f, isRtl = false))
        assertEquals(405f, valueToAngle(100f, 0f, 100f, 135f, 270f, isRtl = false))
    }

    @Test
    fun `values outside the range clamp to the sweep ends`() {
        assertEquals(135f, valueToAngle(-42f, 0f, 100f, 135f, 270f, isRtl = false))
        assertEquals(405f, valueToAngle(999f, 0f, 100f, 135f, 270f, isRtl = false))
    }

    @Test
    fun `RTL mirrors the needle direction`() {
        // In RTL, value=min points to the END of the sweep and vice versa.
        assertEquals(405f, valueToAngle(0f, 0f, 100f, 135f, 270f, isRtl = true))
        assertEquals(135f, valueToAngle(100f, 0f, 100f, 135f, 270f, isRtl = true))
        assertEquals(270f, valueToAngle(50f, 0f, 100f, 135f, 270f, isRtl = true))
    }

    @Test
    fun `a non-zero minimum value is supported`() {
        assertEquals(270f, valueToAngle(30f, 20f, 40f, 135f, 270f, isRtl = false))
    }

    @Test
    fun `fewer than two colors produce no gradient stops`() {
        assertNull(computeArcGradientStops(emptyList(), 135f, 270f))
        assertNull(computeArcGradientStops(listOf(Color.Red), 135f, 270f))
    }

    @Test
    fun `a full circle spans stops from zero to one`() {
        val stops = computeArcGradientStops(listOf(Color.Red, Color.Blue), 0f, 360f)!!
        assertEquals(0f, stops.first().first)
        assertEquals(1f, stops.last().first)
    }

    @Test
    fun `stops wrapping past 360 degrees stay sorted within the unit range`() {
        // Gauge defaults: start 135° (0.375), sweep 270° (0.75) — the last stop
        // lands at 1.125 and must wrap to 0.125, then sort ahead of the first.
        val stops = computeArcGradientStops(listOf(Color.Red, Color.Green, Color.Blue), 135f, 270f)!!
        val positions = stops.map { it.first }
        assertEquals(positions.sorted(), positions, "stops must be sorted for sweepGradient")
        assertTrue(positions.all { it in 0f..1f }, "positions out of unit range: $positions")
        assertEquals(0.125f, positions[0], 1e-4f)
        assertEquals(0.375f, positions[1], 1e-4f)
        assertEquals(0.75f, positions[2], 1e-4f)
    }

    @Test
    fun `a negative start angle normalizes`() {
        val stops = computeArcGradientStops(listOf(Color.Red, Color.Blue), -90f, 180f)!!
        val positions = stops.map { it.first }
        // -90° normalizes to 270° → 0.75; end lands at 1.25 → wraps to 0.25.
        assertEquals(listOf(0.25f, 0.75f), positions)
    }
}
