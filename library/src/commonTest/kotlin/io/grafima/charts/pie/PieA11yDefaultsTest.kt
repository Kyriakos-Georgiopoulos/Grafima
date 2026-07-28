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

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the default TalkBack/VoiceOver strings so they don't drift unnoticed. */
class PieA11yDefaultsTest {

    private val config = PieA11yConfig()
    private val entry = PieEntry(id = "design", label = "Design", value = 30f)

    @Test
    fun `the chart description names the dataset`() {
        val ds = PieDataSet(entries = listOf(entry), contentDescription = "Team budget")
        assertEquals("Pie Chart representing Team budget", config.chartDescriptionBuilder(ds))
    }

    @Test
    fun `a slice description includes its value and percentage`() {
        assertEquals(
            "Design, 30 (33% of total).",
            config.sliceDescriptionBuilder(entry, 33.7f)
        )
    }

    @Test
    fun `the selected state description covers both states`() {
        assertEquals("Currently selected: Design.", config.selectedStateDescription(entry))
        assertEquals("No slice selected.", config.selectedStateDescription(null))
    }
}
