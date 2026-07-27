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

package io.grafima.charts

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class MathUtilsTest {

    @Test
    fun `toRadians maps known degree values to radians`() {
        assertEquals(0.0, toRadians(0.0))
        assertEquals(PI, toRadians(180.0), 1e-9)
        assertEquals(PI / 2, toRadians(90.0), 1e-9)
        assertEquals(-PI, toRadians(-180.0), 1e-9)
        assertEquals(2 * PI, toRadians(360.0), 1e-9)
    }

    @Test
    fun `toDegrees maps known radian values to degrees`() {
        assertEquals(0.0, toDegrees(0.0))
        assertEquals(180.0, toDegrees(PI), 1e-9)
        assertEquals(90.0, toDegrees(PI / 2), 1e-9)
    }

    @Test
    fun `degrees survive a round trip through radians`() {
        for (deg in listOf(-720.0, -37.5, 0.0, 45.0, 123.456, 359.9, 1080.0)) {
            assertEquals(deg, toDegrees(toRadians(deg)), 1e-9)
        }
    }
}
