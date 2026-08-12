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
import kotlin.test.assertNotEquals

class IndexAtXTest {

    private fun points(vararg xs: Float) = xs.map { LineDataPoint(x = it, y = 1f) }

    @Test
    fun finds_every_position_in_the_list() {
        val p = points(0f, 1f, 2f, 3f, 4f, 5f, 6f)
        p.forEachIndexed { i, point ->
            assertEquals(i, p.indexAtX(point.x), "x=${point.x}")
        }
    }

    @Test
    fun reports_nothing_for_an_x_the_series_does_not_reach() {
        val marker = points(7f)
        assertEquals(-1, marker.indexAtX(0f))
        assertEquals(-1, marker.indexAtX(6f))
        assertEquals(0, marker.indexAtX(7f))
        assertEquals(-1, marker.indexAtX(8f))
    }

    @Test
    fun reports_nothing_between_two_points_it_does_have() {
        assertEquals(-1, points(0f, 10f, 20f).indexAtX(10.5f))
    }

    @Test
    fun an_empty_series_is_at_no_x_at_all() {
        assertEquals(-1, emptyList<LineDataPoint>().indexAtX(0f))
    }

    @Test
    fun unevenly_spaced_points_are_found_by_value_not_by_position() {
        val p = points(0f, 0.5f, 12f, 13.75f, 200f)
        assertEquals(2, p.indexAtX(12f))
        assertEquals(4, p.indexAtX(200f))
        assertEquals(-1, p.indexAtX(2f))
    }

    @Test
    fun the_same_axis_position_reached_by_different_arithmetic_still_matches() {
        // 0.1f seven times is 0.70000005f; the literal 0.7f is 0.69999999f.
        var accumulated = 0f
        repeat(7) { accumulated += 0.1f }
        assertNotEquals(0.7f, accumulated, "pick a value the two routes disagree on")
        assertEquals(7, points(0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f).indexAtX(accumulated))
    }

    @Test
    fun negative_positions_are_ordered_the_same_way() {
        val p = points(-10f, -2.5f, 0f, 2.5f)
        assertEquals(0, p.indexAtX(-10f))
        assertEquals(1, p.indexAtX(-2.5f))
        assertEquals(-1, p.indexAtX(-5f))
    }
}
