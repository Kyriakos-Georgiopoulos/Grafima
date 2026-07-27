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

package io.grafima.charts.radar

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveRadarRadiusTest {

    @Test
    fun `an explicit outer radius wins and respects density`() {
        val style = RadarChartStyle(outerRadius = 80.dp)
        assertEquals(160f, resolveRadarRadius(style, 999f, 999f, labelSpace = 50f, Density(2f)))
    }

    @Test
    fun `label space is subtracted before applying the fill fraction`() {
        val style = RadarChartStyle(fillFraction = 1f)
        // min(400, 300)/2 = 150, minus 30 of label space.
        assertEquals(120f, resolveRadarRadius(style, 400f, 300f, labelSpace = 30f, Density(1f)))
    }

    @Test
    fun `the fill fraction is coerced`() {
        val zero = RadarChartStyle(fillFraction = 0f)
        // min side 200 → 100 available − 0 label space, coerced fraction 0.1.
        assertEquals(10f, resolveRadarRadius(zero, 200f, 200f, labelSpace = 0f, Density(1f)))
    }
}
