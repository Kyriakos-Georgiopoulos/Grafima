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

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrokeGradientTest {

    private fun series(stops: List<Color> = emptyList()) = LineSeries(
        id = "s",
        label = "S",
        points = listOf(LineDataPoint(x = 0f, y = 1f)),
        strokeGradientColors = stops
    )

    @Test
    fun `no stops leaves the stroke on its flat colour`() {
        assertFalse(series().hasStrokeGradient)
    }

    @Test
    fun `one stop is not a gradient because there is nothing to interpolate`() {
        assertFalse(series(listOf(Color.Green)).hasStrokeGradient)
    }

    @Test
    fun `two stops make a gradient`() {
        assertTrue(series(listOf(Color.Green, Color.Blue)).hasStrokeGradient)
    }

    @Test
    fun `more than two stops still make a gradient`() {
        assertTrue(series(listOf(Color.Green, Color.Blue, Color.Red)).hasStrokeGradient)
    }
}
