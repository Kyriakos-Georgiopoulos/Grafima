package io.grafima.sample

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.grafima.charts.gauge.GaugeAnimationConfig
import io.grafima.charts.gauge.GaugeChart
import io.grafima.charts.gauge.GaugeChartStyle
import io.grafima.charts.gauge.GaugeNeedleConfig
import io.grafima.charts.gauge.GaugeNeedleStyle
import io.grafima.charts.gauge.GaugeTickConfig
import io.grafima.charts.gauge.GaugeZone
import kotlinx.coroutines.delay
import kotlin.random.Random

// ==========================================
// 4. DEMO IMPLEMENTATION
// ==========================================

private data class GaugePreset(val label: String, val value: Float, val color: Long)

private val GaugePresets = listOf(
    GaugePreset(label = "Low", value = 18f, color = 0xFF22C55E),
    GaugePreset(label = "Normal", value = 48f, color = 0xFF3B82F6),
    GaugePreset(label = "High", value = 73f, color = 0xFFFBBF24),
    GaugePreset(label = "Critical", value = 92f, color = 0xFFEF4444)
)

@Composable
fun GaugeChartDemoScreen() {
    val zones = remember {
        listOf(
            GaugeZone(id = "low", label = "Low", range = 0f..30f, color = Color(0xFF22C55E)),
            GaugeZone(id = "normal", label = "Normal", range = 30f..60f, color = Color(0xFF3B82F6)),
            GaugeZone(id = "high", label = "High", range = 60f..80f, color = Color(0xFFFBBF24)),
            GaugeZone(
                id = "critical",
                label = "Critical",
                range = 80f..100f,
                color = Color(0xFFEF4444)
            )
        )
    }

    var currentValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(400)
        currentValue = 42f
    }

    val activeZone = remember(currentValue, zones) {
        zones.find { currentValue in it.range }
    }

    var useGradientArc by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(Color.White, shape = RoundedCornerShape(24.dp))
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
                    color = Color(0xFF111827),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = activeZone?.let { "Status: ${it.label}" } ?: "Initializing...",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
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
                        zones = if (useGradientArc) emptyList() else zones,
                        style = GaugeChartStyle(
                            startAngle = 135f,
                            sweepAngle = 270f,
                            arcWidth = 24.dp,
                            trackColor = Color(0xFFF1F5F9),
                            arcGradientColors = if (useGradientArc) listOf(
                                Color(0xFF22C55E),
                                Color(0xFF3B82F6),
                                Color(0xFFFBBF24),
                                Color(0xFFEF4444)
                            ) else emptyList(),
                            fillFraction = 0.82f,
                            centerContentOffset = 52.dp
                        ),
                        tickConfig = GaugeTickConfig(
                            majorTickCount = 10,
                            minorTicksPerMajor = 4,
                            majorTickLength = 10.dp,
                            minorTickLength = 5.dp,
                            majorTickColor = Color(0xFF9CA3AF),
                            minorTickColor = Color(0xFFD1D5DB),
                            labelColor = Color(0xFF6B7280),
                            labelFontSize = 10.sp
                        ),
                        needleConfig = GaugeNeedleConfig(
                            style = GaugeNeedleStyle.Tapered,
                            color = activeZone?.color ?: Color(0xFFDC2626),
                            baseColor = Color(0xFF374151),
                            baseRadius = 10.dp,
                            width = 5.dp,
                            lengthFraction = 0.78f,
                            tailFraction = 0.14f
                        ),
                        animationConfig = GaugeAnimationConfig(
                            needleSpec = spring(
                                dampingRatio = 0.42f,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                        centerContent = {
                            Column(
                                modifier = Modifier.padding(top = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${currentValue.toInt()}",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Black,
                                    color = activeZone?.color ?: Color(0xFF111827)
                                )
                                Text(
                                    text = "percent",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9CA3AF),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Preset buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GaugePresets.forEach { preset ->
                Button(
                    onClick = { currentValue = preset.value },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(preset.color)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(
                        text = preset.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (preset.color == 0xFFFBBF24) Color(0xFF78350F) else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { useGradientArc = !useGradientArc },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (useGradientArc) "Zone Mode" else "Gradient Mode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Button(
                onClick = { currentValue = Random.nextInt(0, 101).toFloat() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = "Random",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun GaugeChartDemoScreenPreview() {
    GaugeChartDemoScreen()
}
