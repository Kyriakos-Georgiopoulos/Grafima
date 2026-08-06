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

package io.grafima.charts

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * A pattern reaching Skia has to hold lengths that are finite, non-negative and not
 * both zero. Anything else is a solid line here rather than an assertion inside the
 * renderer.
 */
class DashPatternTest {

    private val density = Density(density = 2f)

    private fun intervals(dash: Dp, gap: Dp) = dashIntervalsOf(DashPattern(dash, gap), density)

    @Test
    fun `no pattern draws solid`() {
        assertNull(dashIntervalsOf(null, density))
    }

    @Test
    fun `a dash and a gap convert at the given density`() {
        assertContentEquals(floatArrayOf(20f, 10f), intervals(dash = 10.dp, gap = 5.dp))
    }

    @Test
    fun `a zero dash is kept so a round cap can draw it as a dot`() {
        assertContentEquals(floatArrayOf(0f, 12f), intervals(dash = 0.dp, gap = 6.dp))
    }

    @Test
    fun `an entirely blank pattern draws solid rather than nothing at all`() {
        assertNull(intervals(dash = 0.dp, gap = 0.dp))
    }

    @Test
    fun `a negative length draws solid`() {
        assertNull(intervals(dash = 10.dp, gap = (-5).dp))
        assertNull(intervals(dash = (-10).dp, gap = 5.dp))
    }

    @Test
    fun `two patterns of the same lengths are equal`() {
        // Not a data class, so this is hand-written and can be broken by hand. A
        // config holding an unequal pattern would defeat recomposition skipping.
        assertEquals(DashPattern(10.dp, 5.dp), DashPattern(10.dp, 5.dp))
        assertEquals(DashPattern(10.dp, 5.dp).hashCode(), DashPattern(10.dp, 5.dp).hashCode())
        assertNotEquals(DashPattern(10.dp, 5.dp), DashPattern(10.dp, 6.dp))
        assertNotEquals(DashPattern(10.dp, 5.dp), DashPattern(9.dp, 5.dp))
    }

    @Test
    fun `a pattern reads as its lengths`() {
        assertEquals("DashPattern(dash=10.0.dp, gap=5.0.dp)", DashPattern(10.dp, 5.dp).toString())
    }

    @Test
    fun `a length that is not a number draws solid`() {
        assertNull(intervals(dash = Dp(Float.NaN), gap = 5.dp))
        assertNull(intervals(dash = Dp(Float.POSITIVE_INFINITY), gap = 5.dp))
    }
}
