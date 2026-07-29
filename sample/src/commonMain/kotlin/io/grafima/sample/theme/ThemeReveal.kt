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

package io.grafima.sample.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max

/** Where the theme change started, in root coordinates. */
class ThemeRevealState {
    var origin: Offset by mutableStateOf(Offset.Unspecified)
        internal set

    fun reportOrigin(offset: Offset) {
        origin = offset
    }
}

val LocalThemeReveal = staticCompositionLocalOf { ThemeRevealState() }

/**
 * Paints the app background as a circle growing out of the theme toggle.
 *
 * The screens themselves draw no background, so this is what's behind
 * everything. The new colour expands from wherever the toggle sits while the
 * rest of the palette cross-fades over the same window — the circle carries the
 * sense of direction, the fades keep it from looking like a hard wipe.
 */
@Composable
fun ThemeRevealBackground(darkTheme: Boolean) {
    val reveal = LocalThemeReveal.current

    // Dark is always the base; the light circle grows over it, or shrinks back
    // into the button. A single value means an interrupted tap reverses
    // smoothly from wherever it had reached.
    val lightRadius = remember { Animatable(if (darkTheme) 0f else 1f) }
    LaunchedEffect(darkTheme) {
        lightRadius.animateTo(
            targetValue = if (darkTheme) 0f else 1f,
            animationSpec = tween(THEME_SWEEP_MS, easing = FastOutSlowInEasing)
        )
    }

    Canvas(Modifier.fillMaxSize()) {
        drawRect(color = DarkBackground)

        val origin = reveal.origin.takeIf { it != Offset.Unspecified }
            ?: Offset(size.width * 0.9f, size.height * 0.08f)

        // Furthest corner, so the circle always finishes past the screen edge.
        val maxRadius = max(
            max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
            max(
                hypot(origin.x, size.height - origin.y),
                hypot(size.width - origin.x, size.height - origin.y)
            )
        )

        drawCircle(
            color = LightBackground,
            radius = maxRadius * lightRadius.value,
            center = origin
        )
    }
}
