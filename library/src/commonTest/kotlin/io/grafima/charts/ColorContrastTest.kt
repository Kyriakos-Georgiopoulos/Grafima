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

import androidx.compose.ui.graphics.Color
import io.grafima.charts.bar.AxisConfig
import io.grafima.charts.bar.ChartStyle
import io.grafima.charts.bar.TooltipSelectionRenderer
import io.grafima.charts.line.LineCrosshairConfig
import io.grafima.charts.pie.TooltipPieSelectionRenderer
import io.grafima.charts.radar.TooltipRadarSelectionRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 contrast checks for the default palette.
 *
 * Tooltips control both foreground and background, so their ratios are a
 * guarantee the library makes. Axis and value labels are drawn on whatever
 * surface the host supplies; they're checked against white as the reference
 * light-theme background, which is what the sample and the Material default
 * surface use.
 */
class ColorContrastTest {

    /** WCAG 2.1 relative luminance. */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
                0.7152 * channel(color.green) +
                0.0722 * channel(color.blue)
    }

    /** WCAG 2.1 contrast ratio, 1.0 (identical) to 21.0 (black on white). */
    private fun contrastRatio(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertMeetsAA(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double = AA_NORMAL_TEXT
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            ratio >= minimum,
            "$label contrast is ${(ratio * 100).toInt() / 100.0}:1, below WCAG AA $minimum:1"
        )
    }

    @Test
    fun `the contrast formula matches known WCAG reference values`() {
        // Anchors the implementation: black-on-white is the 21:1 maximum,
        // identical colors are 1:1.
        assertTrue(contrastRatio(Color.Black, Color.White) > 20.9)
        assertTrue(contrastRatio(Color.White, Color.White) in 0.99..1.01)
        // #767676 on white is the canonical WCAG AA boundary example (~4.54:1).
        val boundary = contrastRatio(Color(0xFF767676), Color.White)
        assertTrue(boundary in 4.45..4.65, "boundary case computed as $boundary")
    }

    @Test
    fun `tooltip text meets AA against its own background`() {
        // Both colors are library-owned, so this is a guarantee, not a guess.
        val bar = TooltipSelectionRenderer()
        assertMeetsAA("Bar tooltip", bar.textStyle.color, bar.backgroundColor)

        val pie = TooltipPieSelectionRenderer()
        assertMeetsAA("Pie tooltip", pie.textStyle.color, pie.backgroundColor)

        val radar = TooltipRadarSelectionRenderer()
        assertMeetsAA("Radar tooltip", radar.textColor, radar.backgroundColor)

        val line = LineCrosshairConfig()
        assertMeetsAA("Line crosshair tooltip", line.tooltipTextColor, line.tooltipBackground)
    }

    @Test
    fun `axis and value labels meet AA on a white surface`() {
        val style = ChartStyle()
        assertMeetsAA("Bar x-axis label", style.labelTextStyle.color, Color.White)
        assertMeetsAA("Bar value label", style.valueTextStyle.color, Color.White)
        assertMeetsAA("Y-axis label", AxisConfig().axisLabelTextStyle.color, Color.White)
    }

    @Test
    fun `label colors keep a margin above the AA threshold`() {
        // The shared grey sits near the limit; this fails before a palette
        // tweak silently drops it under 4.5:1.
        val grey = ChartStyle().labelTextStyle.color
        val ratio = contrastRatio(grey, Color.White)
        assertTrue(
            ratio >= AA_NORMAL_TEXT,
            "label grey is ${(ratio * 100).toInt() / 100.0}:1 — below AA"
        )
    }

    private companion object {
        const val AA_NORMAL_TEXT = 4.5
    }
}
