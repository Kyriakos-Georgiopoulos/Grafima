package io.grafima.sample

import io.grafima.charts.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ==========================================
// 6. DEMO IMPLEMENTATION
// ==========================================

@Composable
fun RadarChartDemoScreen() {
    val defaultAxes = listOf(
        RadarAxis("atk", "Attack"),
        RadarAxis("def", "Defense"),
        RadarAxis("spd", "Speed"),
        RadarAxis("mag", "Magic"),
        RadarAxis("sta", "Stamina"),
        RadarAxis("lck", "Luck")
    )

    var dataSet by remember {
        mutableStateOf(
            RadarDataSet(
                axes = defaultAxes,
                series = listOf(
                    RadarSeries(
                        id = "warrior",
                        label = "Warrior",
                        values = mapOf(
                            "atk" to 88f, "def" to 82f, "spd" to 55f,
                            "mag" to 20f, "sta" to 75f, "lck" to 45f
                        ),
                        color = Color(0xFFEF4444),
                        fillAlpha = 0.15f
                    ),
                    RadarSeries(
                        id = "mage",
                        label = "Mage",
                        values = mapOf(
                            "atk" to 30f, "def" to 35f, "spd" to 60f,
                            "mag" to 95f, "sta" to 45f, "lck" to 70f
                        ),
                        color = Color(0xFF6366F1),
                        fillAlpha = 0.15f
                    ),
                    RadarSeries(
                        id = "rogue",
                        label = "Rogue",
                        values = mapOf(
                            "atk" to 65f, "def" to 30f, "spd" to 92f,
                            "mag" to 40f, "sta" to 50f, "lck" to 85f
                        ),
                        color = Color(0xFF10B981),
                        fillAlpha = 0.15f
                    )
                ),
                contentDescription = "Character Class Comparison"
            )
        )
    }

    var selectedSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSeriesData by remember {
        derivedStateOf {
            dataSet.series.find { it.id == selectedSeriesId }
        }
    }

    var isPolygonGrid by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .background(Color.White, shape = RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        "Character Stats",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = if (selectedSeriesData != null) {
                            "Viewing ${selectedSeriesData?.label} build"
                        } else "Tap a vertex to inspect a class.",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Legend
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    dataSet.series.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(10.dp)) {
                                drawCircle(color = s.color, radius = size.minDimension / 2f)
                            }
                            Text(
                                text = s.label,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 6.dp),
                                fontWeight = if (selectedSeriesData?.id == s.id) {
                                    FontWeight.Bold
                                } else FontWeight.Normal,
                                color = if (selectedSeriesData == null || selectedSeriesData?.id == s.id) {
                                    Color(0xFF374151)
                                } else Color(0xFFD1D5DB)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    RadarChart(
                        dataSet = dataSet,
                        modifier = Modifier.fillMaxSize(),
                        style = RadarChartStyle(
                            gridStyle = if (isPolygonGrid) {
                                RadarGridStyle.Polygon
                            } else RadarGridStyle.Circular,
                            fillFraction = 0.78f,
                            gridLevels = 5,
                            dotRadius = 5.dp
                        ),
                        selectedSeries = selectedSeriesData,
                        onSeriesSelected = { s -> selectedSeriesId = s?.id }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { isPolygonGrid = !isPolygonGrid },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (isPolygonGrid) "Circle Grid" else "Polygon Grid",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = {
                    val newSeries = dataSet.series.map { s ->
                        s.copy(
                            values = dataSet.axes.associate { a ->
                                a.id to Random.nextInt(15, 100).toFloat()
                            }
                        )
                    }
                    dataSet = dataSet.copy(series = newSeries)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = "Randomize",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = {
                    val currentSeries = dataSet.series
                    if (currentSeries.size >= 5) {
                        dataSet = dataSet.copy(series = currentSeries.dropLast(1))
                    } else {
                        val colors = listOf(
                            Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899)
                        )
                        val names = listOf("Paladin", "Ranger", "Bard")
                        val index = currentSeries.size - 3
                        val newId = "class_${currentSeries.size}"
                        val newSeries = RadarSeries(
                            id = newId,
                            label = names.getOrElse(index) { "Class ${currentSeries.size + 1}" },
                            values = dataSet.axes.associate { a ->
                                a.id to Random.nextInt(20, 95).toFloat()
                            },
                            color = colors.getOrElse(index) { Color(0xFF64748B) },
                            fillAlpha = 0.15f
                        )
                        dataSet = dataSet.copy(series = currentSeries + newSeries)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (dataSet.series.size >= 5) "Remove" else "Add Class",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}