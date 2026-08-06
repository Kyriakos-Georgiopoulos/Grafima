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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * A dash and the gap after it, repeated along a line.
 *
 * ```
 * dashPattern = DashPattern(dash = 10.dp, gap = 5.dp)
 * ```
 *
 * Lengths are in dp so a dash is the same size on every screen, rather than a
 * third of it on a 3x phone.
 *
 * A pattern that cannot be drawn — a length that is negative or not finite, or
 * both of them zero — leaves the line solid, since the caller asked for a line
 * either way.
 *
 * This is the library's own description of a dash rather than a [PathEffect],
 * which compares by identity and so defeats the recomposition skipping every
 * chart config relies on, and which cannot even be constructed without the
 * graphics runtime loaded.
 *
 * @param dash Length of each drawn segment. Zero gives a dotted line wherever the
 *   stroke has round caps, which is every line this library dashes.
 * @param gap Length of the blank after each dash.
 */
@Immutable
class DashPattern(val dash: Dp, val gap: Dp) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is DashPattern && dash == other.dash && gap == other.gap)

    override fun hashCode(): Int = 31 * dash.hashCode() + gap.hashCode()

    override fun toString(): String = "DashPattern(dash=$dash, gap=$gap)"
}

/**
 * [pattern] in pixels, or null when it describes a solid line.
 *
 * A length that is negative or not finite, or a pattern with nothing drawn and
 * nothing skipped, would leave the renderer with no line at all — solid is the
 * truthful fallback, since the caller asked for a line either way.
 */
internal fun dashIntervalsOf(pattern: DashPattern?, density: Density): FloatArray? {
    if (pattern == null) return null
    val dash = with(density) { pattern.dash.toPx() }
    val gap = with(density) { pattern.gap.toPx() }
    if (!dash.isFinite() || !gap.isFinite() || dash < 0f || gap < 0f) return null
    return if (dash + gap > 0f) floatArrayOf(dash, gap) else null
}

/**
 * This pattern as a [PathEffect], or null for a solid line.
 *
 * Built once and held, never per frame: every chart that dashes something needs
 * it, and they have to agree or a legend key is drawn solid beside a dashed line.
 */
internal fun DashPattern?.toPathEffect(density: Density): PathEffect? =
    dashIntervalsOf(this, density)?.let(PathEffect::dashPathEffect)
