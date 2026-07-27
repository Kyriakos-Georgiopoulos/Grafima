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

package io.grafima.charts.pie

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PieMathTest {

    @Test
    fun `sweep is proportional to the slice value`() {
        assertEquals(
            90f,
            computeNormalizedSweep(
                animatedValue = 25f,
                totalValue = 100f,
                minSliceAngle = 0f,
                normalizer = 1f
            )
        )
    }

    @Test
    fun `sweep applies the minimum slice angle floor`() {
        // Raw sweep would be 0.36° — visually invisible; floored to 10°.
        assertEquals(
            10f,
            computeNormalizedSweep(
                animatedValue = 1f,
                totalValue = 1000f,
                minSliceAngle = 10f,
                normalizer = 1f
            )
        )
    }

    @Test
    fun `a zero value stays at zero sweep despite the floor`() {
        assertEquals(
            0f,
            computeNormalizedSweep(
                animatedValue = 0f,
                totalValue = 100f,
                minSliceAngle = 10f,
                normalizer = 1f
            )
        )
    }

    @Test
    fun `the normalizer scales the sweep`() {
        assertEquals(
            45f,
            computeNormalizedSweep(
                animatedValue = 25f,
                totalValue = 100f,
                minSliceAngle = 0f,
                normalizer = 0.5f
            )
        )
    }

    @Test
    fun `an explicit radius wins and respects density`() {
        val style = PieChartStyle(outerRadius = 100.dp)
        assertEquals(200f, resolveOuterRadius(style, 999f, 999f, Density(2f)))
    }

    @Test
    fun `the radius fills a fraction of the smaller canvas side`() {
        val style = PieChartStyle(fillFraction = 0.5f)
        // min(400, 200) / 2 * 0.5
        assertEquals(50f, resolveOuterRadius(style, 400f, 200f, Density(1f)))
    }

    @Test
    fun `the fill fraction is coerced into sane bounds`() {
        val zero = PieChartStyle(fillFraction = 0f)
        val huge = PieChartStyle(fillFraction = 5f)
        // Coerced to 0.1 and 1.0 respectively; canvas min side 200 → half is 100.
        assertEquals(10f, resolveOuterRadius(zero, 200f, 200f, Density(1f)))
        assertEquals(100f, resolveOuterRadius(huge, 200f, 200f, Density(1f)))
    }
}
