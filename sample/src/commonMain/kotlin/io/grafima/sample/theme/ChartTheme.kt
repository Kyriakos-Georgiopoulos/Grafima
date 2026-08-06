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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.bar.AxisConfig
import io.grafima.charts.bar.ChartStyle
import io.grafima.charts.line.LineAxisConfig
import io.grafima.charts.line.LineCrosshairConfig
import io.grafima.charts.radar.RadarChartStyle
import io.grafima.charts.radar.RadarGridStyle

/*
 * Grafima's defaults are tuned for a light surface. The demo runs in both, so
 * these rebuild the chart configs from the active palette.
 *
 * Each is remembered against the colours, so a recomposition that isn't a theme
 * change hands the chart back the identical instance and lets it skip. Every
 * parameter a demo varies is a parameter here for the same reason — a `.copy()`
 * at the call site would allocate a fresh config on every frame.
 */

@Composable
fun themedBarAxis(steps: Int = 4): AxisConfig {
    val colors = LocalDemoColors.current
    return remember(colors, steps) {
        AxisConfig(
            yAxisSteps = steps,
            axisColor = colors.grid,
            axisLabelTextStyle = TextStyle(
                color = colors.axisLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
fun themedBarStyle(): ChartStyle {
    val colors = LocalDemoColors.current
    return remember(colors) {
        ChartStyle(
            labelTextStyle = TextStyle(
                color = colors.onSurfaceMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            valueTextStyle = TextStyle(
                color = colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun themedLineAxis(
    yTickCount: Int = 5,
    dashedGrid: Boolean = false,
    xLabels: List<String> = emptyList(),
    xAxisTitle: String? = null,
    yAxisTitle: String? = null
): LineAxisConfig {
    val colors = LocalDemoColors.current
    return remember(colors, yTickCount, dashedGrid, xLabels, xAxisTitle, yAxisTitle) {
        LineAxisConfig(
            gridColor = colors.grid,
            axisColor = colors.grid,
            labelColor = colors.axisLabel,
            yTickCount = yTickCount,
            dashedGrid = dashedGrid,
            xLabelFormatter = { x -> xLabels.getOrElse(x.toInt()) { "" } },
            xAxisTitle = xAxisTitle,
            yAxisTitle = yAxisTitle
        )
    }
}

@Composable
fun themedCrosshair(): LineCrosshairConfig {
    val colors = LocalDemoColors.current
    return remember(colors) {
        LineCrosshairConfig(
            lineColor = colors.onSurfaceMuted,
            dotBorderColor = colors.surface,
            tooltipBackground = colors.tooltipBackground,
            tooltipTextColor = colors.tooltipText
        )
    }
}

@Composable
fun themedRadarStyle(
    gridStyle: RadarGridStyle = RadarGridStyle.Polygon,
    gridLevels: Int = 5,
    fillFraction: Float = 0.75f,
    dotRadius: Dp = 4.dp
): RadarChartStyle {
    val colors = LocalDemoColors.current
    return remember(colors, gridStyle, gridLevels, fillFraction, dotRadius) {
        RadarChartStyle(
            gridColor = colors.grid,
            axisColor = colors.grid,
            labelColor = colors.axisLabel,
            gridStyle = gridStyle,
            gridLevels = gridLevels,
            fillFraction = fillFraction,
            dotRadius = dotRadius
        )
    }
}
