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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarGroupingTest {

    private fun grouped(label: String, series: String, y: Float) =
        BarEntry(
            id = "$label-$series",
            xLabel = label,
            y = y,
            seriesId = series,
            seriesLabel = series.replaceFirstChar { it.uppercase() }
        )

    private fun plain(label: String, y: Float) =
        BarEntry(id = label, xLabel = label, y = y)

    private val twoByTwo = listOf(
        grouped("Q1", "rev", 45f),
        grouped("Q1", "cost", 30f),
        grouped("Q2", "rev", 80f),
        grouped("Q2", "cost", 52f)
    )

    @Test
    fun `entries sharing a label and carrying a series land in one category`() {
        val layout = computeBarGroupLayout(twoByTwo)

        assertEquals(2, layout.categoryCount)
        assertContentEquals(intArrayOf(0, 0, 1, 1), layout.categoryOf)
    }

    @Test
    fun `an entry with no series stays a category of its own`() {
        val layout = computeBarGroupLayout(listOf(plain("Jan", 4f), plain("Jan", 9f)))

        assertEquals(2, layout.categoryCount, "a null seriesId must never merge two bars")
    }

    @Test
    fun `a label reused by a later run opens a new category`() {
        val entries = listOf(
            grouped("Q1", "rev", 45f),
            grouped("Q2", "rev", 80f),
            grouped("Q1", "cost", 30f)
        )

        assertEquals(
            3,
            computeBarGroupLayout(entries).categoryCount,
            "only the immediate predecessor may be joined"
        )
    }

    @Test
    fun `a series bar does not reach back into a plain bar sharing its label`() {
        val layout = computeBarGroupLayout(listOf(plain("Q1", 45f), grouped("Q1", "cost", 30f)))

        // Weakening the predecessor clause to "previous != null" merges these.
        assertEquals(2, layout.categoryCount, "a plain bar is a category in its own right")
        assertContentEquals(intArrayOf(0, 1), layout.categoryOf)
    }

    @Test
    fun `the stacked axis max clears the tallest stack rather than the tallest bar`() {
        // Grouped only has to clear 80. Stacked has to clear 45+30=75 and 80+52=132.
        assertEquals(100f, axisMaxForLayout(twoByTwo, computeBarGroupLayout(twoByTwo), BarGroupMode.Grouped))
        assertEquals(200f, axisMaxForLayout(twoByTwo, computeBarGroupLayout(twoByTwo), BarGroupMode.Stacked))
    }

    @Test
    fun `a dataset with no series gives the same axis max in either mode`() {
        val entries = listOf(plain("a", 45f), plain("b", 12f))

        assertEquals(
            axisMaxForLayout(entries, computeBarGroupLayout(entries), BarGroupMode.Grouped),
            axisMaxForLayout(entries, computeBarGroupLayout(entries), BarGroupMode.Stacked)
        )
    }

    @Test
    fun `a layout over plain entries reports one bar per category`() {
        val layout = computeBarGroupLayout(listOf(plain("a", 1f), plain("b", 2f)))

        assertContentEquals(intArrayOf(0, 1), layout.categoryOf)
        assertEquals(2, layout.categoryCount)
    }

    @Test
    fun `an empty dataset produces an empty layout rather than throwing`() {
        val layout = computeBarGroupLayout(emptyList())

        assertEquals(0, layout.categoryCount)
        assertEquals(0, layout.categoryOf.size)
    }

    @Test
    fun `categories of differing size are still numbered in order`() {
        val entries = listOf(
            grouped("Q1", "rev", 45f),
            grouped("Q1", "cost", 30f),
            grouped("Q1", "tax", 5f),
            grouped("Q2", "rev", 80f)
        )

        val layout = computeBarGroupLayout(entries)

        assertContentEquals(intArrayOf(0, 0, 0, 1), layout.categoryOf)
        assertEquals(2, layout.categoryCount)
    }

    @Test
    fun `a group's bars and inner gaps exactly fill its slot`() {
        val slot = 120f
        val count = 3
        val thickness = groupedBarThickness(slot, count.toFloat(), innerSpacingFactor = 0.08f)
        val gap = groupedBarGap(slot, count.toFloat(), innerSpacingFactor = 0.08f)

        assertEquals(slot, thickness * count + gap * (count - 1), 1e-3f)
    }

    @Test
    fun `a single-bar group takes the whole slot with no gap`() {
        assertEquals(120f, groupedBarThickness(120f, 1f, innerSpacingFactor = 0.08f))
        assertEquals(0f, groupedBarGap(120f, 1f, innerSpacingFactor = 0.08f))
    }

    @Test
    fun `an out-of-range inner spacing is clamped rather than inverting the bars`() {
        // 5f would make the spacing wider than the slot and the thickness negative.
        assertTrue(groupedBarThickness(120f, 2f, innerSpacingFactor = 5f) > 0f)
        assertTrue(groupedBarThickness(120f, 2f, innerSpacingFactor = -1f) > 0f)
        assertEquals(0f, groupedBarGap(120f, 2f, innerSpacingFactor = -1f))
    }

    @Test
    fun `the last bar of a group ends exactly on the slot boundary`() {
        val slot = 120f
        val f = 0.1f
        val thickness = groupedBarThickness(slot, 3f, f)
        val gap = groupedBarGap(slot, 3f, f)

        assertEquals(slot, groupedBarOffset(2f, thickness, gap) + thickness, 0.001f)
        // The same has to hold mid-departure, or the group spills out of its slot.
        val partial = groupedBarThickness(slot, 2.4f, f)
        val partialGap = groupedBarGap(slot, 2.4f, f)
        assertEquals(slot, groupedBarOffset(1.4f, partial, partialGap) + partial, 0.001f)
    }

    @Test
    fun `series order follows first appearance and drops duplicates`() {
        assertEquals(listOf("rev", "cost"), seriesOrder(twoByTwo))
        assertEquals(emptyList(), seriesOrder(listOf(plain("a", 1f))))
    }

    @Test
    fun `a negative stacked segment cannot pull the axis below a drawn bar`() {
        val entries = listOf(
            grouped("Q1", "up", 100f),
            grouped("Q1", "down", -50f)
        )

        // The stack totals 50, but a 100-tall segment is still drawn, so an axis
        // scaled to the total would clip it off the canvas.
        val max = axisMaxForLayout(entries, computeBarGroupLayout(entries), BarGroupMode.Stacked)
        assertTrue(max >= 100f, "stacked axis max was $max, below the tallest segment")
        assertEquals(axisMaxForLayout(entries, computeBarGroupLayout(entries), BarGroupMode.Grouped), max)
    }
}
