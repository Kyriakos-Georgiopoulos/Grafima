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

package io.grafima.charts.gauge

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the default TalkBack/VoiceOver strings so they don't drift unnoticed. */
class GaugeA11yDefaultsTest {

    private val config = GaugeA11yConfig()

    @Test
    fun `the description states percent value and range`() {
        assertEquals(
            "Gauge at 30 percent. Value: 30 of 100.",
            config.descriptionBuilder(30f, 0f, 100f, emptyList())
        )
    }

    @Test
    fun `the percent accounts for a non-zero minimum`() {
        assertEquals(
            "Gauge at 50 percent. Value: 30 of 40.",
            config.descriptionBuilder(30f, 20f, 40f, emptyList())
        )
    }

    @Test
    fun `the description names the active zone`() {
        val zones = listOf(
            GaugeZone(id = "ok", label = "Healthy", range = 0f..50f, color = Color.Green),
            GaugeZone(id = "hot", label = "Critical", range = 50.01f..100f, color = Color.Red)
        )
        assertEquals(
            "Gauge at 75 percent. Value: 75 of 100. Zone: Critical.",
            config.descriptionBuilder(75f, 0f, 100f, zones)
        )
    }
}
