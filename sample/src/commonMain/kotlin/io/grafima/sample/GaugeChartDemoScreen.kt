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

package io.grafima.sample

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.grafima.charts.gauge.GaugeAnimationConfig
import io.grafima.charts.gauge.GaugeChart
import io.grafima.charts.gauge.GaugeChartStyle
import io.grafima.charts.gauge.GaugeNeedleConfig
import io.grafima.charts.gauge.GaugeNeedleStyle
import io.grafima.charts.gauge.GaugeTickConfig
import io.grafima.charts.gauge.GaugeZone
import io.grafima.sample.theme.LocalDemoColors
import io.grafima.sample.theme.ProvideDemoLayout
import io.grafima.sample.theme.onColorFor
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class GaugePreset(val label: String, val value: Float, val color: Color)

private val GaugePresets = listOf(
    GaugePreset(label = "Low", value = 18f, color = Color(0xFF22C55E)),
    GaugePreset(label = "Normal", value = 48f, color = Color(0xFF3B82F6)),
    GaugePreset(label = "High", value = 73f, color = Color(0xFFFBBF24)),
    GaugePreset(label = "Critical", value = 92f, color = Color(0xFFEF4444))
)

private val GaugeZones = listOf(
    GaugeZone(id = "low", label = "Low", range = 0f..30f, color = Color(0xFF22C55E)),
    GaugeZone(id = "normal", label = "Normal", range = 30f..60f, color = Color(0xFF3B82F6)),
    GaugeZone(id = "high", label = "High", range = 60f..80f, color = Color(0xFFFBBF24)),
    GaugeZone(id = "critical", label = "Critical", range = 80f..100f, color = Color(0xFFEF4444))
)

private val ArcGradient = listOf(
    Color(0xFF22C55E),
    Color(0xFF3B82F6),
    Color(0xFFFBBF24),
    Color(0xFFEF4444)
)

internal class GaugeChartViewModel : ViewModel() {
    var currentValue by mutableFloatStateOf(0f)
    var useGradientArc by mutableStateOf(false)
    var introPlayed = false
}

@Composable
internal fun GaugeChartDemoScreen(
    viewModel: GaugeChartViewModel = viewModel { GaugeChartViewModel() }
) {
    val colors = LocalDemoColors.current

    val currentValue = viewModel.currentValue
    val useGradientArc = viewModel.useGradientArc

    LaunchedEffect(Unit) {
        if (!viewModel.introPlayed) {
            delay(400)
            viewModel.currentValue = 42f
            viewModel.introPlayed = true
        }
    }

    val activeZone = remember(currentValue) {
        GaugeZones.find { currentValue in it.range }
    }

    val chartStyle = remember(useGradientArc, colors) {
        GaugeChartStyle(
            startAngle = 135f,
            sweepAngle = 270f,
            arcWidth = 24.dp,
            trackColor = colors.chartTrack,
            arcGradientColors = if (useGradientArc) ArcGradient else emptyList(),
            fillFraction = 0.92f,
            centerContentOffset = 84.dp
        )
    }

    val tickConfig = remember(colors) {
        GaugeTickConfig(
            majorTickCount = 10,
            minorTicksPerMajor = 4,
            majorTickLength = 10.dp,
            minorTickLength = 5.dp,
            majorTickColor = colors.onSurfaceMuted,
            minorTickColor = colors.grid,
            labelColor = colors.onSurfaceMuted,
            labelFontSize = 10.sp
        )
    }

    val needleConfig = remember(activeZone, colors) {
        GaugeNeedleConfig(
            style = GaugeNeedleStyle.Tapered,
            color = activeZone?.color ?: colors.onSurface,
            baseColor = colors.onSurfaceMuted,
            baseRadius = 10.dp,
            width = 5.dp,
            lengthFraction = 0.78f,
            tailFraction = 0.14f
        )
    }

    val animationConfig = remember {
        GaugeAnimationConfig(
            needleSpec = spring(
                dampingRatio = 0.42f,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    DemoScreenScaffold(
        controls = {
            DemoControls { buttonModifier ->
                GaugePresets.forEach { preset ->
                    Button(
                        onClick = { viewModel.currentValue = preset.value },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = preset.color,
                            contentColor = onColorFor(preset.color)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = buttonModifier.height(50.dp)
                    ) {
                        Text(
                            text = preset.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            DemoControls { buttonModifier ->
                Button(
                    onClick = { viewModel.useGradientArc = !useGradientArc },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        text = if (useGradientArc) "Zone Mode" else "Gradient Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { viewModel.currentValue = Random.nextInt(0, 101).toFloat() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.onSurface,
                        contentColor = colors.background
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = buttonModifier.height(50.dp)
                ) {
                    Text(
                        text = "Random",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface, shape = RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Performance",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = activeZone?.let { "Status: ${it.label}" } ?: "Initializing...",
                    fontSize = 13.sp,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    GaugeChart(
                        value = currentValue,
                        minValue = 0f,
                        maxValue = 100f,
                        modifier = Modifier.fillMaxSize(),
                        zones = if (useGradientArc) emptyList() else GaugeZones,
                        style = chartStyle,
                        tickConfig = tickConfig,
                        needleConfig = needleConfig,
                        animationConfig = animationConfig,
                        centerContent = {
                            Column(
                                modifier = Modifier.padding(top = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${currentValue.toInt()}",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Black,
                                    color = activeZone?.color ?: colors.onSurface
                                )
                                Text(
                                    text = "percent",
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun GaugeChartDemoScreenPreview() {
    GaugeChartDemoScreen()
}

@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
private fun GaugeChartDemoScreenLandscapePreview() {
    ProvideDemoLayout { GaugeChartDemoScreen() }
}
