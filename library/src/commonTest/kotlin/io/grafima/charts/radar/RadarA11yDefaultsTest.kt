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

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the default TalkBack/VoiceOver strings so they don't drift unnoticed. */
class RadarA11yDefaultsTest {

    private val config = RadarA11yConfig()
    private val axes = listOf(
        RadarAxis(id = "speed", label = "Speed", maxValue = 200f),
        RadarAxis(id = "power", label = "Power")
    )

    @Test
    fun `series percentages are relative to each axis maximum`() {
        val series = RadarSeries(
            id = "s1",
            label = "Model A",
            values = mapOf("speed" to 100f, "power" to 60f)
        )
        // speed: 100/200 → 50%; power: 60/100 → 60%.
        assertEquals(
            "Model A (Speed: 50%, Power: 60%)",
            config.seriesDescriptionBuilder(series, axes)
        )
    }

    @Test
    fun `a missing axis value reads as zero percent`() {
        val series = RadarSeries(id = "s1", label = "Model A", values = mapOf("speed" to 100f))
        assertEquals(
            "Model A (Speed: 50%, Power: 0%)",
            config.seriesDescriptionBuilder(series, axes)
        )
    }

    @Test
    fun `the selected state description covers both states`() {
        // Spoken on its own when the selection changes, so it names every axis
        // value rather than just the series.
        val series = RadarSeries(
            id = "s1",
            label = "Model A",
            values = mapOf("speed" to 100f, "power" to 40f)
        )
        assertEquals(
            "Currently selected: Model A. Speed 100, Power 40.",
            config.selectedStateDescription(series, axes)
        )
        assertEquals(
            "No series selected. Use the actions menu to choose a series.",
            config.selectedStateDescription(null, axes)
        )
    }
}
