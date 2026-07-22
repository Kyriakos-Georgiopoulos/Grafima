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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
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
fun PieChartDemoScreen() {
    val oceanBrush = SliceBrush.Linear(listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)))
    val emeraldBrush = SliceBrush.Radial(listOf(Color(0xFF11998E), Color(0xFF38EF7D)))
    val sunsetBrush = SliceBrush.Linear(
        colors = listOf(Color(0xFFFF512F), Color(0xFFF09819), Color(0xFFFFB75E)),
        angleDegrees = 90f
    )
    val amethystBrush = SliceBrush.Sweep(
        listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))
    )
    val royalBrush = SliceBrush.Linear(listOf(Color(0xFF536976), Color(0xFF292E49)), angleDegrees = 135f)

    var dataSet by remember {
        mutableStateOf(
            PieDataSet(
                entries = listOf(
                    PieEntry("A", "Product A", 300f, brush = oceanBrush),
                    PieEntry("B", "Product B", 250f, brush = sunsetBrush),
                    PieEntry("C", "Product C", 400f, brush = amethystBrush),
                    PieEntry("D", "Product D", 150f, brush = emeraldBrush),
                    PieEntry("E", "Product E", 200f, brush = royalBrush)
                ),
                contentDescription = "Market Share Distribution"
            )
        )
    }

    var selectedSliceId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSliceData by remember {
        derivedStateOf { dataSet.entries.find { it.id == selectedSliceId } }
    }

    var isDonut by remember { mutableStateOf(true) }
    var useCalloutRenderer by remember { mutableStateOf(true) }

    val activeRenderer = remember(useCalloutRenderer) {
        if (useCalloutRenderer) ElbowCalloutPieSelectionRenderer() else TooltipPieSelectionRenderer()
    }

    val total = remember(dataSet) { dataSet.entries.sumOf { it.value.toInt() } }

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
                .height(480.dp)
                .background(Color.White, shape = RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Market Share",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF111827)
                        )
                        Text(
                            text = if (selectedSliceData != null) {
                                "Viewing metrics for ${selectedSliceData?.label}"
                            } else "Tap a slice to inspect.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PieChart(
                        dataSet = dataSet,
                        modifier = Modifier.fillMaxSize(),
                        style = PieChartStyle(
                            donutRatio = if (isDonut) 0.5f else 0f,
                            selectedScale = 1.05f,
                            fillFraction = 0.60f
                        ),
                        selectionRenderer = activeRenderer,
                        selectedEntry = selectedSliceData,
                        onSliceSelected = { entry -> selectedSliceId = entry?.id },
                        centerContent = if (isDonut) {
                            {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$total",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF111827)
                                    )
                                    Text(
                                        text = "Total",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }
                        } else null
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
                onClick = { isDonut = !isDonut },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (isDonut) "Pie Style" else "Donut Style",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = { useCalloutRenderer = !useCalloutRenderer },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (useCalloutRenderer) "Pill Text" else "Callout Line",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = {
                    val newEntries = dataSet.entries.map { entry ->
                        entry.copy(value = Random.nextInt(50, 500).toFloat())
                    }
                    dataSet = dataSet.copy(entries = newEntries)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = "Update",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}