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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Every colour the demo screens draw with. */
@Immutable
data class DemoColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentWarm: Color,
    val onAccentWarm: Color,
    val grid: Color,
    val axisLabel: Color,
    val chartTrack: Color,
    val tooltipBackground: Color,
    val tooltipText: Color
)

internal val LightBackground = Color(0xFFF3F4F6)
internal val DarkBackground = Color(0xFF0B1120)

private val LightColors = DemoColors(
    background = LightBackground,
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFE5E7EB),
    onSurface = Color(0xFF111827),
    onSurfaceMuted = Color(0xFF6B7280),
    accent = Color(0xFF4F46E5),
    onAccent = Color(0xFFFFFFFF),
    accentWarm = Color(0xFFF59E0B),
    onAccentWarm = Color(0xFF111827),
    grid = Color(0xFFE5E7EB),
    axisLabel = Color(0xFF6B7280),
    chartTrack = Color(0xFFF1F5F9),
    tooltipBackground = Color(0xFF111827),
    tooltipText = Color(0xFFFFFFFF)
)

private val DarkColors = DemoColors(
    background = DarkBackground,
    surface = Color(0xFF151C2C),
    surfaceMuted = Color(0xFF1F2937),
    onSurface = Color(0xFFF9FAFB),
    onSurfaceMuted = Color(0xFF9CA3AF),
    accent = Color(0xFF818CF8),
    onAccent = Color(0xFF0B1120),
    accentWarm = Color(0xFFFBBF24),
    onAccentWarm = Color(0xFF111827),
    grid = Color(0xFF283548),
    axisLabel = Color(0xFF9CA3AF),
    chartTrack = Color(0xFF1F2937),
    tooltipBackground = Color(0xFFF9FAFB),
    tooltipText = Color(0xFF111827)
)

/**
 * Not `staticCompositionLocalOf`: the palette changes on every frame of a theme
 * sweep, and a static local invalidates its entire subtree on each new value —
 * every screen the pager holds, chart canvases included. This one invalidates
 * only what actually reads it.
 */
val LocalDemoColors = compositionLocalOf { LightColors }

/**
 * Provides a palette that animates between light and dark rather than swapping.
 *
 * One animated fraction drives the whole palette, so the background, cards,
 * text, chart grid and tooltips sweep together instead of flipping — and the
 * transition costs a single running animation rather than one per colour.
 */
@Composable
fun DemoTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val fraction by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = THEME_SWEEP_MS, easing = FastOutSlowInEasing),
        label = "themeSweep"
    )

    val colors = remember(fraction) { lerp(LightColors, DarkColors, fraction) }

    CompositionLocalProvider(
        LocalDemoColors provides colors,
        content = content
    )
}

/**
 * Picks readable text for an arbitrary chart colour.
 *
 * The demo paints buttons in the same colours as the data they control, and
 * those span the whole luminance range — white on the amber zone is about 2:1,
 * far under the 4.5:1 WCAG asks for. Choosing by luminance keeps every label
 * legible without hand-maintaining a colour-to-colour table.
 */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.4f) Color(0xFF1F2937) else Color(0xFFFFFFFF)

private fun lerp(start: DemoColors, stop: DemoColors, fraction: Float): DemoColors =
    when (fraction) {
        // The constants at the ends, so a settled theme keeps one stable instance
        // for downstream `remember` keys.
        0f -> start
        1f -> stop
        else -> DemoColors(
            background = lerp(start.background, stop.background, fraction),
            surface = lerp(start.surface, stop.surface, fraction),
            surfaceMuted = lerp(start.surfaceMuted, stop.surfaceMuted, fraction),
            onSurface = lerp(start.onSurface, stop.onSurface, fraction),
            onSurfaceMuted = lerp(start.onSurfaceMuted, stop.onSurfaceMuted, fraction),
            accent = lerp(start.accent, stop.accent, fraction),
            onAccent = lerp(start.onAccent, stop.onAccent, fraction),
            accentWarm = lerp(start.accentWarm, stop.accentWarm, fraction),
            onAccentWarm = lerp(start.onAccentWarm, stop.onAccentWarm, fraction),
            grid = lerp(start.grid, stop.grid, fraction),
            axisLabel = lerp(start.axisLabel, stop.axisLabel, fraction),
            chartTrack = lerp(start.chartTrack, stop.chartTrack, fraction),
            tooltipBackground = lerp(start.tooltipBackground, stop.tooltipBackground, fraction),
            tooltipText = lerp(start.tooltipText, stop.tooltipText, fraction)
        )
    }

// Long enough for a circle crossing the screen to read as a sweep, not a flicker.
internal const val THEME_SWEEP_MS = 800
