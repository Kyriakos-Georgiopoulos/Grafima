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

import androidx.compose.ui.unit.dp
import io.grafima.charts.DashPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The grid's dash is described by value now.
 *
 * A `PathEffect` compares by identity, so two default configs were never equal and
 * every recomposition looked like a change.
 */
class GridDashPatternTest {

    @Test
    fun `two default configs are equal`() {
        assertEquals(AxisConfig(), AxisConfig())
    }

    @Test
    fun `a config carries its dash by value`() {
        assertEquals(DashPattern(dash = 5.dp, gap = 5.dp), AxisConfig().gridDashPattern)
        assertEquals(
            AxisConfig(gridDashPattern = DashPattern(2.dp, 3.dp)),
            AxisConfig(gridDashPattern = DashPattern(2.dp, 3.dp))
        )
    }

    @Test
    fun `a solid grid is asked for with a null gridDashPattern`() {
        assertNull(AxisConfig(gridDashPattern = null).gridDashPattern)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `a null dashEffect no longer asks for a solid grid`() {
        // It used to be the documented way, and now means "unset": the grid falls
        // through to gridDashPattern. Nothing in the compiler says so, which is why
        // the CHANGELOG carries the migration note this test exists to pin.
        val config = AxisConfig(dashEffect = null)
        assertNull(config.dashEffect)
        assertEquals(DashPattern(dash = 5.dp, gap = 5.dp), config.gridDashPattern)
    }
}
