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

    @Test
    fun `a selected point uses its label when present`() {
        assertEquals(
            "Revenue at Jan: 10. ",
            config.selectedPointDescriptionBuilder(0, listOf(series))
        )
    }

    @Test
    fun `an unlabelled point falls back to its x value`() {
        assertEquals(
            "Revenue at 1: 25. ",
            config.selectedPointDescriptionBuilder(1, listOf(series))
        )
    }

    @Test
    fun `an out-of-range index produces nothing`() {
        assertEquals("", config.selectedPointDescriptionBuilder(99, listOf(series)))
    }
}
