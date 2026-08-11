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
import kotlin.test.assertFalse
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
        val categories = groupBarEntries(twoByTwo)

        assertEquals(2, categories.size)
        assertEquals(listOf("Q1", "Q2"), categories.map { it.xLabel })
        assertEquals(listOf("rev", "cost"), categories[0].entries.map { it.seriesId })
        assertEquals(listOf("rev", "cost"), categories[1].entries.map { it.seriesId })
    }

    @Test
    fun `an entry with no series stays a category of its own`() {
        val categories = groupBarEntries(listOf(plain("Jan", 4f), plain("Jan", 9f)))

        assertEquals(2, categories.size, "a null seriesId must never merge two bars")
    }

    @Test
    fun `a label reused by a later run opens a new category`() {
        val entries = listOf(
            grouped("Q1", "rev", 45f),
            grouped("Q2", "rev", 80f),
            grouped("Q1", "cost", 30f)
        )

        val categories = groupBarEntries(entries)

        assertEquals(3, categories.size, "only the immediate predecessor may be joined")
    }

    @Test
    fun `the stacked axis max clears the tallest stack rather than the tallest bar`() {
        // Grouped only has to clear 80. Stacked has to clear 45+30=75 and 80+52=132.
        assertEquals(100f, computeBarAxisMax(twoByTwo, BarGroupMode.Grouped))
        assertEquals(200f, computeBarAxisMax(twoByTwo, BarGroupMode.Stacked))
    }

    @Test
    fun `a dataset with no series gives the same axis max in either mode`() {
        val entries = listOf(plain("a", 45f), plain("b", 12f))

        assertEquals(
            computeBarAxisMax(entries, BarGroupMode.Grouped),
            computeBarAxisMax(entries, BarGroupMode.Stacked)
        )
    }

    @Test
    fun `the layout numbers each bar by category and position within it`() {
        val layout = computeBarGroupLayout(twoByTwo)

        assertContentEquals(intArrayOf(0, 0, 1, 1), layout.categoryOf)
        assertContentEquals(intArrayOf(0, 1, 0, 1), layout.positionInCategory)
        assertContentEquals(intArrayOf(2, 2, 2, 2), layout.categorySize)
        assertEquals(2, layout.categoryCount)
        assertTrue(layout.hasSeries)
    }

    @Test
    fun `a layout over plain entries reports one bar per category and no series`() {
        val layout = computeBarGroupLayout(listOf(plain("a", 1f), plain("b", 2f)))

        assertContentEquals(intArrayOf(0, 1), layout.categoryOf)
        assertContentEquals(intArrayOf(0, 0), layout.positionInCategory)
        assertContentEquals(intArrayOf(1, 1), layout.categorySize)
        assertEquals(2, layout.categoryCount)
        assertFalse(layout.hasSeries)
    }

    @Test
    fun `an empty dataset produces an empty layout rather than throwing`() {
        val layout = computeBarGroupLayout(emptyList())

        assertEquals(0, layout.categoryCount)
        assertEquals(0, layout.categoryOf.size)
        assertFalse(layout.hasSeries)
    }

    @Test
    fun `categories of differing size each report their own member count`() {
        val entries = listOf(
            grouped("Q1", "rev", 45f),
            grouped("Q1", "cost", 30f),
            grouped("Q1", "tax", 5f),
            grouped("Q2", "rev", 80f)
        )

        val layout = computeBarGroupLayout(entries)

        assertContentEquals(intArrayOf(3, 3, 3, 1), layout.categorySize)
        assertEquals(2, layout.categoryCount)
    }

    @Test
    fun `a group's bars and inner gaps exactly fill its slot`() {
        val slot = 120f
        val count = 3
        val thickness = groupedBarThickness(slot, count, innerSpacingFactor = 0.08f)
        val gap = groupedBarGap(slot, count, innerSpacingFactor = 0.08f)

        assertEquals(slot, thickness * count + gap * (count - 1), 1e-3f)
    }

    @Test
    fun `a single-bar group takes the whole slot with no gap`() {
        assertEquals(120f, groupedBarThickness(120f, 1, innerSpacingFactor = 0.08f))
        assertEquals(0f, groupedBarGap(120f, 1, innerSpacingFactor = 0.08f))
    }

    @Test
    fun `an out-of-range inner spacing is clamped rather than inverting the bars`() {
        // 5f would make the spacing wider than the slot and the thickness negative.
        assertTrue(groupedBarThickness(120f, 2, innerSpacingFactor = 5f) > 0f)
        assertTrue(groupedBarThickness(120f, 2, innerSpacingFactor = -1f) > 0f)
        assertEquals(0f, groupedBarGap(120f, 2, innerSpacingFactor = -1f))
    }

    @Test
    fun `the second bar of a group starts one thickness and one gap along`() {
        val thickness = groupedBarThickness(120f, 2, innerSpacingFactor = 0.1f)
        val gap = groupedBarGap(120f, 2, innerSpacingFactor = 0.1f)

        assertEquals(0f, groupedBarOffset(0, thickness, gap))
        assertEquals(thickness + gap, groupedBarOffset(1, thickness, gap))
    }

    @Test
    fun `series order follows first appearance and drops duplicates`() {
        assertEquals(listOf("rev", "cost"), seriesOrder(twoByTwo))
        assertEquals(emptyList(), seriesOrder(listOf(plain("a", 1f))))
    }

    /** Two separate walks; a chart whose axis and bars disagreed would draw nonsense. */
    @Test
    fun `the category builder and the draw layout describe the same categories`() {
        val datasets = mapOf(
            "empty" to emptyList(),
            "one plain bar" to listOf(plain("Jan", 10f)),
            "all plain" to listOf(plain("Jan", 10f), plain("Feb", 20f)),
            "two full groups" to twoByTwo,
            "a label reused by a later run" to listOf(
                grouped("Q1", "rev", 45f),
                grouped("Q2", "rev", 80f),
                grouped("Q1", "rev", 12f)
            ),
            "a plain bar between two groups" to listOf(
                grouped("Q1", "rev", 45f),
                grouped("Q1", "cost", 30f),
                plain("Ad hoc", 12f),
                grouped("Q2", "rev", 80f),
                grouped("Q2", "cost", 52f)
            ),
            "a lone series bar" to listOf(grouped("Q1", "rev", 45f)),
            // A series bar must not reach back into a plain bar that happens to
            // share its label: the plain one is a category in its own right.
            "a series bar behind a plain bar of the same label" to listOf(
                plain("Q1", 45f),
                grouped("Q1", "cost", 30f)
            ),
            "a group of three" to listOf(
                grouped("Q1", "rev", 45f),
                grouped("Q1", "cost", 30f),
                grouped("Q1", "tax", 8f)
            )
        )

        datasets.forEach { (name, entries) ->
            val categories = groupBarEntries(entries)
            val layout = computeBarGroupLayout(entries)

            assertEquals(categories.size, layout.categoryCount, "category count for $name")

            var index = 0
            categories.forEachIndexed { categoryIndex, category ->
                category.entries.forEachIndexed { position, entry ->
                    assertEquals(entry.id, entries[index].id, "order for $name")
                    assertEquals(categoryIndex, layout.categoryOf[index], "category of $index in $name")
                    assertEquals(position, layout.positionInCategory[index], "position of $index in $name")
                    assertEquals(
                        category.entries.size,
                        layout.categorySize[index],
                        "size at $index in $name"
                    )
                    index++
                }
            }
            assertEquals(entries.size, index, "every entry accounted for in $name")
        }
    }

    @Test
    fun `a group of a fractional size matches the whole sizes it sits between`() {
        val slot = 120f
        val f = 0.1f

        assertEquals(groupedBarThickness(slot, 1, f), groupedBarThickness(slot, 1f, f), 0.001f)
        assertEquals(groupedBarThickness(slot, 2, f), groupedBarThickness(slot, 2f, f), 0.001f)
        assertEquals(groupedBarThickness(slot, 3, f), groupedBarThickness(slot, 3f, f), 0.001f)
        assertEquals(groupedBarGap(slot, 2, f), groupedBarGap(slot, 2f, f), 0.001f)
        assertEquals(groupedBarGap(slot, 3, f), groupedBarGap(slot, 3f, f), 0.001f)
    }

    @Test
    fun `a bar widens monotonically as its neighbour leaves the group`() {
        val slot = 120f
        val f = 0.1f
        // 2.0 down to 1.0 is one bar of a pair finishing its exit.
        val widths = listOf(2f, 1.75f, 1.5f, 1.25f, 1f).map { groupedBarThickness(slot, it, f) }

        widths.zipWithNext { wide, wider ->
            assertTrue(wider > wide, "width went backwards across $widths")
        }
        // Landing exactly on the whole-slot width is what stops the final frame popping.
        assertEquals(slot, widths.last(), 0.001f)
        assertTrue(widths.last() < widths.first() * 2.3f, "the total jump is still a doubling")
    }

    @Test
    fun `a category takes its label from the bar that opens it`() {
        val categories = groupBarEntries(twoByTwo)
        assertEquals(listOf("Q1", "Q2"), categories.map { it.xLabel })
    }
}
