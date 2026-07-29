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

package io.grafima.charts.bar

import kotlin.test.Test
import kotlin.test.assertEquals

class BarLayoutMathTest {

    private fun entries(vararg ys: Float) =
        ys.mapIndexed { i, y -> BarEntry(id = "e$i", xLabel = "E$i", y = y) }

    @Test
    fun `the axis max rounds headroom up to the magnitude step`() {
        // 45 × 1.2 = 54 → step 10 → 60.
        assertEquals(60f, computeBarAxisMax(entries(45f, 12f)))
        // 500 × 1.2 = 600 → step 50 → 600 exactly.
        assertEquals(600f, computeBarAxisMax(entries(500f)))
        // 3 × 1.2 = 3.6 → step 5 → 5.
        assertEquals(5f, computeBarAxisMax(entries(3f)))
    }

    @Test
    fun `the axis max switches steps at the magnitude boundaries`() {
        // 8 × 1.2 = 9.6 ≤ 10 → step 5 → 10.
        assertEquals(10f, computeBarAxisMax(entries(8f)))
        // 9 × 1.2 = 10.8 > 10 → step 10 → 20.
        assertEquals(20f, computeBarAxisMax(entries(9f)))
        // 83 × 1.2 = 99.6 ≤ 100 → step 10 → 100.
        assertEquals(100f, computeBarAxisMax(entries(83f)))
        // 84 × 1.2 = 100.8 > 100 → step 50 → 150.
        assertEquals(150f, computeBarAxisMax(entries(84f)))
    }

    @Test
    fun `empty or non-positive data falls back to an axis max of five`() {
        assertEquals(5f, computeBarAxisMax(emptyList()))
        assertEquals(5f, computeBarAxisMax(entries(0f, 0f)))
    }

    @Test
    fun `bars and gaps exactly fill the extent`() {
        val extent = 300f
        val count = 4
        val (thickness, gap) = barThicknessAndGap(extent, count, spacingFactor = 0.35f)
        assertEquals(extent, thickness * count + gap * (count + 1), 1e-3f)
    }

    @Test
    fun `the spacing factor is coerced into its valid range`() {
        // A factor ≥ 1 would make bars vanish or go negative; it clamps at 0.9.
        val (overThickness, _) = barThicknessAndGap(100f, 2, spacingFactor = 5f)
        assertEquals(5f, overThickness, 1e-3f)
        // A negative factor clamps at 0: bars fill the extent, no gaps.
        val (negThickness, negGap) = barThicknessAndGap(100f, 2, spacingFactor = -1f)
        assertEquals(50f, negThickness, 1e-3f)
        assertEquals(0f, negGap, 1e-3f)
    }

    @Test
    fun `bar slots progress at a fixed pitch`() {
        val (thickness, gap) = barThicknessAndGap(300f, 3, spacingFactor = 0.3f)
        val first = barSlotOffset(0, leadingInset = 40f, thickness = thickness, gap = gap)
        val second = barSlotOffset(1, leadingInset = 40f, thickness = thickness, gap = gap)
        assertEquals(40f + gap, first, 1e-3f)
        assertEquals(thickness + gap, second - first, 1e-3f)
    }

    @Test
    fun `the RTL mirror is an involution and LTR is a passthrough`() {
        val ltr = 70f
        assertEquals(ltr, mirrorForRtl(ltr, totalExtent = 300f, thickness = 40f, isRtl = false))
        val mirrored = mirrorForRtl(ltr, totalExtent = 300f, thickness = 40f, isRtl = true)
        assertEquals(300f - 70f - 40f, mirrored)
        assertEquals(ltr, mirrorForRtl(mirrored, totalExtent = 300f, thickness = 40f, isRtl = true))
    }

    @Test
    fun `in RTL the first bar lands where the last bar sits in LTR`() {
        val (thickness, gap) = barThicknessAndGap(300f, 3, spacingFactor = 0.3f)
        val firstLtr = barSlotOffset(0, 0f, thickness, gap)
        val lastLtr = barSlotOffset(2, 0f, thickness, gap)
        val firstRtl = mirrorForRtl(firstLtr, totalExtent = 300f, thickness = thickness, isRtl = true)
        assertEquals(lastLtr, firstRtl, 1e-3f)
    }

    @Test
    fun `the description summarises the chart rather than reading every bar`() {
        // The node is a live region, so this is re-announced on every selection.
        // Enumerating the bars here meant hearing the whole chart again just to
        // learn which one is now selected; the values live behind the select
        // actions and in stateDescription instead.
        val dataSet = BarDataSet(
            entries = listOf(
                BarEntry(id = "jan", xLabel = "Jan", y = 45f),
                BarEntry(id = "feb", xLabel = "Feb", y = 80f)
            ),
            contentDescription = "Monthly revenue"
        )
        assertEquals(
            "Bar Chart representing Monthly revenue. 2 bars. Use the actions menu to select one.",
            buildBarChartDescription(dataSet, A11yConfig())
        )
    }
}
