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

/** Pins the default TalkBack/VoiceOver strings so they don't drift unnoticed. */
class LineA11yDefaultsTest {

    private val config = LineA11yConfig()
    private val series = LineSeries(
        id = "revenue",
        label = "Revenue",
        points = listOf(
            LineDataPoint(x = 0f, y = 10f, label = "Jan"),
            LineDataPoint(x = 1f, y = 25f),
            LineDataPoint(x = 2f, y = 18f)
        )
    )

    @Test
    fun `the chart description summarises each series range`() {
        val ds = LineDataSet(series = listOf(series), contentDescription = "Quarterly revenue")
        assertEquals(
            "Line Chart: Quarterly revenue. Revenue: range 10 to 25, 3 points. ",
            config.chartDescriptionBuilder(ds)
        )
    }

    /** What the chart hands the builder: each series' reading at that axis position. */
    private fun selectionAt(index: Int, series: List<LineSeries>) = series.mapNotNull { s ->
        s.points.getOrNull(index)?.let { SelectedPoint(s, it) }
    }

    @Test
    fun `a selected point uses its label when present`() {
        assertEquals(
            "Revenue at Jan: 10. ",
            config.selectedPointDescriptionBuilder(0, selectionAt(0, listOf(series)))
        )
    }

    @Test
    fun `a point speaks its content description in place of the drawn label`() {
        // The axis has room for "Apr" and no more; the listener still gets "April".
        val series = listOf(
            LineSeries(
                id = "rev",
                label = "Revenue",
                points = listOf(
                    LineDataPoint(x = 0f, y = 10f, label = "Apr", contentDescription = "April")
                )
            )
        )
        assertEquals(
            "Revenue at April: 10. ",
            config.selectedPointDescriptionBuilder(0, selectionAt(0, series))
        )
    }

    @Test
    fun `an unlabelled point falls back to its x value`() {
        assertEquals(
            "Revenue at 1: 25. ",
            config.selectedPointDescriptionBuilder(1, selectionAt(1, listOf(series)))
        )
    }

    @Test
    fun `a position no series has a reading at produces nothing`() {
        // The chart resolves before calling, so "nothing there" arrives as an empty
        // list rather than as an index the builder has to range-check.
        assertEquals("", config.selectedPointDescriptionBuilder(99, emptyList()))
    }

    private fun reference(description: String?) =
        ReferenceLine(value = 1f, axis = ReferenceLineAxis.X, contentDescription = description)

    @Test
    fun `a line is spoken by its label when it carries no description of its own`() {
        assertEquals(
            "Reference line: Target.",
            config.referenceLineDescriptionBuilder(
                listOf(ReferenceLine(value = 1f, axis = ReferenceLineAxis.Y, label = "Target"))
            )
        )
    }

    @Test
    fun `a description says what the drawn label cannot fit`() {
        val line = ReferenceLine(
            value = 1f,
            axis = ReferenceLineAxis.Y,
            label = "Target",
            contentDescription = "Target of 100 thousand euros"
        )
        assertEquals("Target of 100 thousand euros", line.spokenLabel)
    }

    @Test
    fun `renaming a line renames what is spoken`() {
        // A constructor default cannot do this: copy never re-runs one, so a line
        // built with a label and then renamed would go on announcing the old name,
        // and one given a label by copy would never be announced at all.
        val renamed = ReferenceLine(value = 1f, axis = ReferenceLineAxis.Y, label = "Target")
            .copy(label = "Limit")
        assertEquals("Limit", renamed.spokenLabel)

        val labelledLater = ReferenceLine(value = 1f, axis = ReferenceLineAxis.Y)
            .copy(label = "Target")
        assertEquals("Target", labelledLater.spokenLabel)
    }

    @Test
    fun `a line with neither a label nor a description is not spoken`() {
        assertNull(ReferenceLine(value = 1f, axis = ReferenceLineAxis.Y).spokenLabel)
        assertNull(
            ReferenceLine(value = 1f, axis = ReferenceLineAxis.Y, label = "  ").spokenLabel
        )
    }

    @Test
    fun `no reference lines are announced as nothing`() {
        assertEquals("", config.referenceLineDescriptionBuilder(emptyList()))
    }

    @Test
    fun `one reference line is named in the singular`() {
        assertEquals(
            "Reference line: Now.",
            config.referenceLineDescriptionBuilder(listOf(reference("Now")))
        )
    }

    @Test
    fun `several reference lines are listed together`() {
        assertEquals(
            "Reference lines: Now, Target.",
            config.referenceLineDescriptionBuilder(
                listOf(reference("Now"), reference("Target"))
            )
        )
    }

    @Test
    fun `an unnamed reference line is left unspoken`() {
        // Drawn but not described: announcing "reference line" alone would tell a
        // listener something is there without saying what it marks.
        assertEquals(
            "Reference line: Now.",
            config.referenceLineDescriptionBuilder(
                listOf(reference(null), reference("Now"), reference("  "))
            )
        )
    }

    @Test
    fun `a description that ends mid-word is closed before the next sentence`() {
        val spoken = buildString {
            append("Line Chart: Revenue")
            appendSentence("X axis: Days.")
        }
        assertEquals("Line Chart: Revenue. X axis: Days.", spoken)
    }

    @Test
    fun `a description that already ends in punctuation gains no second stop`() {
        val spoken = buildString {
            append("Line Chart: Revenue. ")
            appendSentence("X axis: Days.")
        }
        assertEquals("Line Chart: Revenue. X axis: Days.", spoken)
    }

    @Test
    fun `nothing to append leaves the description untouched`() {
        val spoken = buildString {
            append("Line Chart: Revenue. ")
            appendSentence("   ")
        }
        assertEquals("Line Chart: Revenue. ", spoken)
    }

    @Test
    fun `a first sentence is appended without a leading separator`() {
        assertEquals("X axis: Days.", buildString { appendSentence("X axis: Days.") })
    }
}
