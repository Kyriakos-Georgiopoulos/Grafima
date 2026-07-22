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
// 1. DATA MODELS & CONFIG
// ==========================================

/**
 * Defines how a pie slice is painted. Each variant maps to a different [Brush] type
 * at draw time. When no [SliceBrush] is set on a [PieEntry], the [PieDataSet.defaultBrush]
 * is used as fallback.
 *
 * ```
 * // Solid fill
 * PieEntry("a", "Sales", 300f, brush = SliceBrush.Solid(Color.Red))
 *
 * // Diagonal linear gradient
 * PieEntry("b", "Costs", 200f, brush = SliceBrush.Linear(
 *     colors = listOf(Color.Cyan, Color.Blue),
 *     angleDegrees = 135f
 * ))
 *
 * // Or use the extension shorthand
 * PieEntry("c", "Tax", 100f, brush = Color.Green.toSliceBrush())
 * ```
 */
sealed interface SliceBrush {

    /** Flat single-color fill. */
    @Immutable
    data class Solid(val color: Color) : SliceBrush

    /**
     * Linear gradient across the slice.
     * [angleDegrees] controls the gradient axis: 0 = left-to-right,
     * 90 = top-to-bottom, 45 = diagonal. Defaults to 45.
     */
    @Immutable
    data class Linear(
        val colors: List<Color>,
        val angleDegrees: Float = 45f
    ) : SliceBrush

    /**
     * Radial gradient expanding outward from the pie center.
     * Works well for donut charts where the gradient follows the ring.
     */
    @Immutable
    data class Radial(val colors: List<Color>) : SliceBrush

    /**
     * Sweep (conic) gradient rotating around the pie center.
     * Creates a color wheel effect when applied to all slices.
     */
    @Immutable
    data class Sweep(val colors: List<Color>) : SliceBrush
}

/** Shorthand to wrap a [Color] into a [SliceBrush.Solid]. */
fun Color.toSliceBrush(): SliceBrush = SliceBrush.Solid(this)

/**
 * A single slice in the pie chart.
 *
 * @param id Stable identifier used to track this entry across data updates and drive
 *   animations. Must be unique within the dataset. Changing the id is treated as a
 *   removal + insertion, not a value morph.
 * @param label Human-readable name shown in tooltips and accessibility descriptions.
 * @param value The raw numeric value. Does not need to sum to 100, the chart normalizes
 *   internally. Must be > 0; zero or negative values are effectively invisible.
 * @param brush Fill style for this slice. When null, [PieDataSet.defaultBrush] is used.
 */
@Immutable
data class PieEntry(
    val id: String,
    val label: String,
    val value: Float,
    val brush: SliceBrush? = null
)

/**
 * Holds the full dataset and chart-level defaults.
 *
 * @param entries The slices to render. Order determines drawing order (first entry starts
 *   at [PieChartStyle.startAngle]).
 * @param defaultBrush Fallback brush for entries that don't specify their own.
 * @param contentDescription Root accessibility label for the chart.
 */
@Immutable
data class PieDataSet(
    val entries: List<PieEntry>,
    val defaultBrush: SliceBrush = SliceBrush.Linear(
        colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
    ),
    val contentDescription: String = "Pie Chart"
)

/**
 * Visual tuning for the chart geometry.
 *
 * The chart size is resolved in this order:
 * 1. If [outerRadius] is specified (not [Dp.Unspecified]), it is used as the exact outer radius.
 * 2. Otherwise, the chart auto-fits to the Canvas using [fillFraction] as a 0..1 ratio
 *    of the available space.
 *
 * @param startAngle Angle in degrees where the first slice begins. -90 = 12 o'clock.
 * @param donutRatio Inner hole size as a fraction of the outer radius. 0 = solid pie,
 *   0.5 = half-width ring. Clamped to 0..0.99 internally.
 * @param sliceSpacingAngle Gap between slices in degrees. Ignored for single-entry datasets.
 * @param unselectedAlpha Alpha applied to non-selected slices when a selection is active.
 * @param selectedScale Scale factor applied to the selected slice. 1.05 = 5% larger.
 * @param outerRadius Explicit outer radius in Dp. When [Dp.Unspecified], the chart
 *   auto-sizes using [fillFraction] instead.
 * @param fillFraction 0..1 ratio of the smaller Canvas dimension used as the diameter.
 *   Only applies when [outerRadius] is [Dp.Unspecified]. Default 0.60.
 * @param minSliceAngle Minimum sweep angle in degrees. Slices smaller than this are
 *   bumped up so they remain visible and tappable. 0 disables the floor.
 */
@Immutable
data class PieChartStyle(
    val startAngle: Float = -90f,
    val donutRatio: Float = 0.45f,
    val sliceSpacingAngle: Float = 2f,
    val unselectedAlpha: Float = 0.3f,
    val selectedScale: Float = 1.05f,
    val outerRadius: Dp = Dp.Unspecified,
    val fillFraction: Float = 0.60f,
    val minSliceAngle: Float = 0f
)

/**
 * Timing configuration for all chart animations.
 *
 * @param initialEntrySpec Drives the first appearance of each slice (value animates from 0).
 * @param morphSpec Drives value changes on existing slices (e.g. data update).
 * @param selectionSpec Drives scale and alpha changes on tap.
 * @param staggerDelayMs Delay between successive slice entry animations.
 * @param startDelayMs Initial delay before the first slice begins animating.
 */
@Immutable
data class PieAnimationConfig(
    val initialEntrySpec: AnimationSpec<Float> = tween(
        durationMillis = 1000,
        easing = FastOutSlowInEasing
    ),
    val morphSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    ),
    val selectionSpec: AnimationSpec<Float> = tween(durationMillis = 200, easing = LinearEasing),
    val staggerDelayMs: Long = 120L,
    val startDelayMs: Long = 100L
)

/**
 * Accessibility text builders. Override individual lambdas to customize screen reader output
 * without replacing the entire a11y strategy.
 */
@Stable
data class PieA11yConfig(
    val chartDescriptionBuilder: (PieDataSet) -> String = { ds ->
        "Pie Chart representing ${ds.contentDescription}"
    },
    val sliceDescriptionBuilder: (PieEntry, Float) -> String = { entry, percentage ->
        "${entry.label}, ${entry.value.toInt()} (${percentage.toInt()}% of total)."
    },
    val selectedStateDescription: (PieEntry?) -> String = { entry ->
        entry?.let { "Currently selected: ${it.label}." } ?: "No slice selected."
    }
)

// ==========================================
// 2. SELECTION INDICATOR API
// ==========================================

/**
 * Rendering strategy for the visual indicator shown when a slice is selected.
 * Implement this to provide a custom selection treatment (e.g. a leader line,
 * a floating card, a glow effect).
 *
 * The chart calls [drawSelection] inside its [DrawScope] after all slices are drawn,
 * only when a selection is active.
 *
 * @see TooltipPieSelectionRenderer for a centered tooltip implementation.
 * @see ElbowCalloutPieSelectionRenderer for a leader-line + pill implementation.
 */
@Stable
fun interface PieChartSelectionRenderer {
    /**
     * @param entry The currently selected slice data.
     * @param pieCenter Center point of the pie in Canvas coordinates.
     * @param pieRadius Outer radius of the pie in pixels.
     * @param sliceCentroid A point on the slice's mid-angle at the ring's center radius.
     *   Useful as an anchor for lines or indicators.
     * @param midAngleDegrees The visual angle bisecting the selected slice, in degrees.
     *   Already accounts for RTL mirroring.
     * @param textMeasurer Shared [TextMeasurer] for laying out label text.
     * @param tooltipCache Reusable [TextLayoutResult] cache keyed by entry id + value.
     *   Avoids re-measuring identical text on every frame during animations.
     * @param layoutDirection Current layout direction. Use this when your renderer
     *   needs to flip text alignment or anchor logic for RTL.
     */
    fun DrawScope.drawSelection(
        entry: PieEntry,
        pieCenter: Offset,
        pieRadius: Float,
        sliceCentroid: Offset,
        midAngleDegrees: Float,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<String, TextLayoutResult>,
        layoutDirection: LayoutDirection
    )
}

/**
 * Renders a floating rounded-rect tooltip positioned radially outside the pie.
 * Pushes outward from the slice's mid-angle so it never overlaps the chart center,
 * and clamps to Canvas bounds to stay fully visible.
 */
@Stable
class TooltipPieSelectionRenderer(
    val backgroundColor: Color = Color(0xFF111827),
    val textStyle: TextStyle = TextStyle(
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    ),
    val cornerRadius: Dp = 8.dp,
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 8.dp,
    val radialOffset: Dp = 16.dp
) : PieChartSelectionRenderer {
    override fun DrawScope.drawSelection(
        entry: PieEntry,
        pieCenter: Offset,
        pieRadius: Float,
        sliceCentroid: Offset,
        midAngleDegrees: Float,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<String, TextLayoutResult>,
        layoutDirection: LayoutDirection
    ) {
        val cacheKey = "${entry.id}_${entry.value.toInt()}"
        val tooltipLayout = tooltipCache.getOrPut(cacheKey) {
            textMeasurer.measure(text = "${entry.label}: ${entry.value.toInt()}", style = textStyle)
        }
        val tooltipWidth = tooltipLayout.size.width + (horizontalPadding.toPx() * 2)
        val tooltipHeight = tooltipLayout.size.height + (verticalPadding.toPx() * 2)

        val angleRad = Math.toRadians(midAngleDegrees.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()

        val targetRadius = pieRadius + radialOffset.toPx()
        val anchorX = pieCenter.x + targetRadius * cosA
        val anchorY = pieCenter.y + targetRadius * sinA

        val rawLeft = anchorX - (tooltipWidth / 2f) + (cosA * tooltipWidth / 2f)
        val rawTop = anchorY - (tooltipHeight / 2f) + (sinA * tooltipHeight / 2f)

        val safeLeft = rawLeft.coerceIn(0f, size.width - tooltipWidth)
        val safeTop = rawTop.coerceIn(0f, size.height - tooltipHeight)

        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(safeLeft, safeTop),
            size = Size(tooltipWidth, tooltipHeight),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
        drawText(
            textLayoutResult = tooltipLayout,
            color = textStyle.color,
            topLeft = Offset(
                x = safeLeft + (tooltipWidth - tooltipLayout.size.width) / 2,
                y = safeTop + (tooltipHeight - tooltipLayout.size.height) / 2
            )
        )
    }
}

/**
 * Renders a two-segment leader line (radial + horizontal stub) ending in a bordered
 * pill-shaped label. The line originates at the slice centroid, breaks at a "knee"
 * point outside the pie, then runs horizontally to the text pill.
 *
 * Automatically flips the horizontal direction based on which side of the pie the
 * slice sits on, and clamps all geometry to Canvas bounds.
 */
@Stable
class ElbowCalloutPieSelectionRenderer(
    val lineColor: Color = Color(0xFF374151),
    val lineWidth: Dp = 2.dp,
    val radialExtension: Dp = 24.dp,
    val stubLength: Dp = 16.dp,
    val textStyle: TextStyle = TextStyle(
        color = Color(0xFF111827),
        fontSize = 13.sp,
        fontWeight = FontWeight.Black
    ),
    val pillRadius: Dp = 6.dp,
    val pillPaddingX: Dp = 10.dp,
    val pillPaddingY: Dp = 6.dp,
    val pillBackgroundColor: Color = Color.White
) : PieChartSelectionRenderer {

    private val reusablePath = Path()

    override fun DrawScope.drawSelection(
        entry: PieEntry,
        pieCenter: Offset,
        pieRadius: Float,
        sliceCentroid: Offset,
        midAngleDegrees: Float,
        textMeasurer: TextMeasurer,
        tooltipCache: MutableMap<String, TextLayoutResult>,
        layoutDirection: LayoutDirection
    ) {
        val cacheKey = "${entry.id}_${entry.value.toInt()}"
        val layout = tooltipCache.getOrPut(cacheKey) {
            textMeasurer.measure(text = "${entry.label}: ${entry.value.toInt()}", style = textStyle)
        }

        val angleRad = Math.toRadians(midAngleDegrees.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val isRightSide = cosA >= 0

        val dotAnchorPoint = sliceCentroid
        val dotRadius = 4.dp.toPx()

        val bgWidth = layout.size.width + (pillPaddingX.toPx() * 2)
        val bgHeight = layout.size.height + (pillPaddingY.toPx() * 2)
        val margin = 8.dp.toPx()

        val targetRadius = pieRadius + radialExtension.toPx()
        var kneeX = pieCenter.x + targetRadius * cosA
        var kneeY = pieCenter.y + targetRadius * sinA

        val minKneeY = margin + bgHeight / 2f
        val maxKneeY = size.height - margin - bgHeight / 2f
        kneeY = kneeY.coerceIn(minKneeY, maxKneeY)

        val stubPx = stubLength.toPx()
        var endX = kneeX + if (isRightSide) stubPx else -stubPx

        if (isRightSide) {
            val maxEndX = size.width - margin - bgWidth
            endX = min(endX, maxEndX)
            kneeX = min(kneeX, endX - stubPx)
            if (kneeX < dotAnchorPoint.x) {
                kneeX = dotAnchorPoint.x
                endX = kneeX + stubPx
            }
        } else {
            val minEndX = margin + bgWidth
            endX = max(endX, minEndX)
            kneeX = max(kneeX, endX + stubPx)
            if (kneeX > dotAnchorPoint.x) {
                kneeX = dotAnchorPoint.x
                endX = kneeX - stubPx
            }
        }

        val safeBoxLeft = if (isRightSide) endX else endX - bgWidth
        val safeBoxTop = kneeY - bgHeight / 2f

        val sliceColor = resolveEntryColor(entry) ?: lineColor

        reusablePath.apply {
            reset()
            moveTo(dotAnchorPoint.x, dotAnchorPoint.y)
            lineTo(kneeX, kneeY)
            lineTo(endX, kneeY)
        }

        drawPath(path = reusablePath, color = sliceColor, style = Stroke(width = lineWidth.toPx()))
        drawCircle(color = sliceColor, radius = dotRadius, center = dotAnchorPoint)

        drawRoundRect(
            color = pillBackgroundColor,
            topLeft = Offset(safeBoxLeft, safeBoxTop),
            size = Size(bgWidth, bgHeight),
            cornerRadius = CornerRadius(pillRadius.toPx(), pillRadius.toPx())
        )
        drawRoundRect(
            color = sliceColor,
            topLeft = Offset(safeBoxLeft, safeBoxTop),
            size = Size(bgWidth, bgHeight),
            cornerRadius = CornerRadius(pillRadius.toPx(), pillRadius.toPx()),
            style = Stroke(width = lineWidth.toPx())
        )
        drawText(
            textLayoutResult = layout,
            color = textStyle.color,
            topLeft = Offset(safeBoxLeft + pillPaddingX.toPx(), safeBoxTop + pillPaddingY.toPx())
        )
    }
}

// ==========================================
// 3. INTERNAL HELPERS
// ==========================================

/**
 * Resolves a [SliceBrush] into a Compose [Brush] for the given chart geometry.
 */
private fun resolveBrush(
    sliceBrush: SliceBrush,
    cx: Float,
    cy: Float,
    radius: Float
): Brush = when (sliceBrush) {
    is SliceBrush.Solid -> SolidColor(sliceBrush.color)
    is SliceBrush.Linear -> {
        val rad = Math.toRadians(sliceBrush.angleDegrees.toDouble())
        val dx = cos(rad).toFloat() * radius
        val dy = sin(rad).toFloat() * radius
        Brush.linearGradient(
            colors = sliceBrush.colors,
            start = Offset(cx - dx, cy - dy),
            end = Offset(cx + dx, cy + dy)
        )
    }
    is SliceBrush.Radial -> Brush.radialGradient(
        colors = sliceBrush.colors,
        center = Offset(cx, cy),
        radius = radius
    )
    is SliceBrush.Sweep -> Brush.sweepGradient(
        colors = sliceBrush.colors,
        center = Offset(cx, cy)
    )
}

/**
 * Extracts the first meaningful color from a [PieEntry]'s brush.
 * Used by renderers (e.g. callout line color) to match the slice visually.
 * Returns null if the entry has no brush set.
 */
private fun resolveEntryColor(entry: PieEntry): Color? = when (val b = entry.brush) {
    is SliceBrush.Solid -> b.color
    is SliceBrush.Linear -> b.colors.firstOrNull()
    is SliceBrush.Radial -> b.colors.firstOrNull()
    is SliceBrush.Sweep -> b.colors.firstOrNull()
    null -> null
}

/**
 * Resolves the outer radius in pixels based on [PieChartStyle] configuration.
 * If [PieChartStyle.outerRadius] is specified, converts it to px.
 * Otherwise, computes from the canvas dimensions and [PieChartStyle.fillFraction].
 */
private fun resolveOuterRadius(
    style: PieChartStyle,
    canvasWidth: Float,
    canvasHeight: Float,
    density: androidx.compose.ui.unit.Density
): Float {
    return if (style.outerRadius != Dp.Unspecified) {
        with(density) { style.outerRadius.toPx() }
    } else {
        (min(canvasWidth, canvasHeight) / 2f) * style.fillFraction.coerceIn(0.1f, 1f)
    }
}

/**
 * Computes the normalized sweep angle for a single entry, applying the minSliceAngle
 * floor and the pre-computed normalizer. Pure function, no allocations.
 */
private inline fun computeNormalizedSweep(
    animatedValue: Float,
    totalValue: Float,
    minSliceAngle: Float,
    normalizer: Float
): Float {
    var sweep = (animatedValue / totalValue) * 360f
    if (minSliceAngle > 0f && sweep > 0f) {
        sweep = sweep.coerceAtLeast(minSliceAngle)
    }
    return sweep * normalizer
}

// ==========================================
// 4. ANIMATION ENGINE
// ==========================================

/**
 * Manages per-slice [Animatable] instances for value, scale, and alpha.
 *
 * Designed to be [remember]ed inside [PieChart]. All methods must run on the
 * main thread, which is guaranteed by [SideEffect] and [LaunchedEffect] scopes.
 *
 * The lifecycle is split into two phases:
 * - [syncAnimatables]: synchronous map housekeeping, called via [SideEffect]
 *   so maps are ready before the first draw of each frame.
 * - [launchEntryAnimations] / [launchSelectionAnimations]: async coroutine
 *   launchers, called inside [LaunchedEffect] scopes that cancel correctly
 *   when keys change.
 */
@Stable
class PieChartAnimationEngine {
    internal val valueAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    internal val scaleAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    internal val alphaAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedIds = mutableSetOf<String>()

    /**
     * Ensures Animatable instances exist for all current [entries] and removes stale
     * ones from previous datasets. Synchronous and idempotent. Safe to call on every
     * composition via [SideEffect] because it only performs map get-or-put operations
     * and a single removeAll pass.
     */
    fun syncAnimatables(entries: List<PieEntry>) {
        val currentIds = entries.mapTo(mutableSetOf()) { it.id }
        valueAnimatables.keys.removeAll { it !in currentIds }
        scaleAnimatables.keys.removeAll { it !in currentIds }
        alphaAnimatables.keys.removeAll { it !in currentIds }
        initializedIds.removeAll { it !in currentIds }

        entries.forEach { entry ->
            valueAnimatables.getOrPut(entry.id) { Animatable(0f) }
            scaleAnimatables.getOrPut(entry.id) { Animatable(1f) }
            alphaAnimatables.getOrPut(entry.id) { Animatable(1f) }
        }
    }

    /**
     * Launches staggered value animations for each entry. New entries animate from 0
     * with a stagger delay; existing entries morph to their new target value.
     *
     * Must be called inside a [LaunchedEffect] keyed on [entries] so that coroutines
     * are cancelled and re-launched when data changes.
     */
    fun launchEntryAnimations(
        entries: List<PieEntry>,
        config: PieAnimationConfig,
        scope: CoroutineScope
    ) {
        entries.forEachIndexed { index, entry ->
            val valueAnim = valueAnimatables[entry.id] ?: return@forEachIndexed
            val isInitialLoad = initializedIds.add(entry.id)

            scope.launch {
                if (isInitialLoad) {
                    delay(config.startDelayMs + (index * config.staggerDelayMs))
                    valueAnim.animateTo(entry.value, config.initialEntrySpec)
                } else if (valueAnim.targetValue != entry.value) {
                    valueAnim.animateTo(entry.value, config.morphSpec)
                }
            }
        }
    }

    /**
     * Launches scale and alpha animations to reflect the current selection state.
     * The selected slice scales up; all others dim. When [selectedEntry] is null,
     * everything returns to default.
     *
     * Must be called inside a [LaunchedEffect] whose key includes [selectedEntry]
     * so that a selection change cancels in-flight animations and starts fresh.
     * The [entries] key should also be included so that new entries pick up the
     * current selection state.
     */
    fun launchSelectionAnimations(
        entries: List<PieEntry>,
        selectedEntry: PieEntry?,
        style: PieChartStyle,
        config: PieAnimationConfig,
        scope: CoroutineScope
    ) {
        entries.forEach { entry ->
            val scaleAnim = scaleAnimatables[entry.id] ?: return@forEach
            val alphaAnim = alphaAnimatables[entry.id] ?: return@forEach

            val isSelected = selectedEntry?.id == entry.id
            val targetScale = if (isSelected) style.selectedScale else 1f
            val targetAlpha = if (selectedEntry != null && !isSelected) style.unselectedAlpha else 1f

            if (scaleAnim.targetValue != targetScale) {
                scope.launch { scaleAnim.animateTo(targetScale, config.selectionSpec) }
            }
            if (alphaAnim.targetValue != targetAlpha) {
                scope.launch { alphaAnim.animateTo(targetAlpha, config.selectionSpec) }
            }
        }
    }
}

// ==========================================
// 5. THE CHART COMPONENT
// ==========================================

/**
 * A composable pie/donut chart with animated transitions, tap selection, gradient support,
 * RTL mirroring, and a pluggable selection indicator.
 *
 * Usage:
 * ```
 * val data = PieDataSet(
 *     entries = listOf(
 *         PieEntry("a", "Mobile", 620f, brush = SliceBrush.Linear(listOf(cyan, blue))),
 *         PieEntry("b", "Desktop", 380f, brush = Color.Red.toSliceBrush()),
 *     )
 * )
 *
 * var selected by remember { mutableStateOf<PieEntry?>(null) }
 *
 * PieChart(
 *     dataSet = data,
 *     modifier = Modifier.fillMaxWidth().height(300.dp),
 *     style = PieChartStyle(donutRatio = 0.5f),
 *     selectedEntry = selected,
 *     onSliceSelected = { selected = it },
 *     centerContent = {
 *         Text("Total\n${data.entries.sumOf { it.value.toInt() }}")
 *     }
 * )
 * ```
 *
 * @param dataSet The entries and default brush to render.
 * @param style Geometry and visual configuration.
 * @param animationConfig Timing for entry, morph, and selection animations.
 * @param a11yConfig Accessibility label builders.
 * @param selectionRenderer Strategy for drawing the selection indicator. Swap between
 *   [TooltipPieSelectionRenderer] and [ElbowCalloutPieSelectionRenderer], or provide
 *   your own [PieChartSelectionRenderer] implementation.
 * @param selectedEntry The currently selected slice, or null for no selection.
 *   Hoist this into the parent to control selection externally.
 * @param onSliceSelected Called when the user taps a slice (with the entry) or taps
 *   outside / re-taps the same slice (with null).
 * @param centerContent Optional composable rendered at the center of a donut chart.
 *   Only visible when [PieChartStyle.donutRatio] > 0. Receives no parameters;
 *   size yourself relative to the donut hole or use fixed dimensions.
 */
@Composable
fun PieChart(
    dataSet: PieDataSet,
    modifier: Modifier = Modifier,
    style: PieChartStyle = PieChartStyle(),
    animationConfig: PieAnimationConfig = PieAnimationConfig(),
    a11yConfig: PieA11yConfig = PieA11yConfig(),
    selectionRenderer: PieChartSelectionRenderer = remember { TooltipPieSelectionRenderer() },
    selectedEntry: PieEntry? = null,
    onSliceSelected: (PieEntry?) -> Unit = {},
    centerContent: @Composable (() -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val entries = dataSet.entries
    val animationEngine = remember { PieChartAnimationEngine() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // ── Stable state refs for long-lived lambdas (pointerInput, derivedStateOf) ──
    // These allow the pointer input coroutine and derived state lambda to always
    // read the latest values without restarting or re-creating.
    val currentSelectedEntry by rememberUpdatedState(selectedEntry)
    val currentOnSliceSelected by rememberUpdatedState(onSliceSelected)
    val currentStyle by rememberUpdatedState(style)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentDensity by rememberUpdatedState(density)
    val currentEntries by rememberUpdatedState(entries)

    val selectionCache = remember { mutableMapOf<String, TextLayoutResult>() }

    val targetTotalValue = remember(entries) {
        entries.sumOf { it.value.toDouble().coerceAtLeast(0.0) }.toFloat()
    }
    val currentTargetTotal by rememberUpdatedState(targetTotalValue)

    val chartDescription = remember(dataSet, selectedEntry, targetTotalValue, a11yConfig) {
        buildString {
            append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
            if (targetTotalValue > 0f) {
                entries.forEach { entry ->
                    val percentage = (entry.value / targetTotalValue) * 100
                    append(a11yConfig.sliceDescriptionBuilder(entry, percentage)).append(". ")
                }
            }
            append(a11yConfig.selectedStateDescription(selectedEntry))
        }
    }

    // ── Touch bounds computed reactively via derivedStateOf ──
    // Reads animatable snapshot state + rememberUpdatedState refs.
    // Evaluated lazily only when the pointer input handler accesses it on tap,
    // not on every animation frame. Zero cost during idle and animation.
    val sliceTouchBounds by remember {
        derivedStateOf {
            val ents = currentEntries
            val total = currentTargetTotal
            val startAngle = currentStyle.startAngle
            val minAngle = currentStyle.minSliceAngle

            if (ents.isEmpty() || total <= 0f) return@derivedStateOf emptyList()

            // First pass: compute floor-adjusted sum for normalization (no allocation)
            val hasMinAngle = minAngle > 0f
            var rawSweepSum = 0f
            ents.forEach { entry ->
                val v = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                var s = (v / total) * 360f
                if (hasMinAngle && s > 0f) s = s.coerceAtLeast(minAngle)
                rawSweepSum += s
            }
            val normalizer = if (rawSweepSum > 0f) 360f / rawSweepSum else 1f

            // Second pass: build bounds list
            val bounds = mutableListOf<Pair<PieEntry, ClosedFloatingPointRange<Float>>>()
            var logicalStart = startAngle

            ents.forEach { entry ->
                val v = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                val sweep = computeNormalizedSweep(v, total, minAngle, normalizer)

                val normStart = (logicalStart - startAngle) % 360f
                val normEnd = normStart + sweep

                if (normStart > normEnd) {
                    bounds.add(entry to (normStart..360f))
                    bounds.add(entry to (0f..normEnd))
                } else {
                    bounds.add(entry to (normStart..normEnd))
                }
                logicalStart += sweep
            }
            bounds
        }
    }

    // ── Animatable map housekeeping (synchronous, runs before draw) ──
    // SideEffect runs after every committed composition, before layout and draw.
    // This ensures animatable maps are in sync with the latest entries before the
    // Canvas reads them, eliminating the one-frame gap that LaunchedEffect would have.
    SideEffect {
        animationEngine.syncAnimatables(entries)
    }

    // ── Entry value animations ──
    // Scoped to entries: when data changes, old stagger coroutines are cancelled
    // and new ones start. No leaked animation coroutines across data updates.
    LaunchedEffect(entries) {
        selectionCache.clear()
        if (currentSelectedEntry != null && entries.none { it.id == currentSelectedEntry?.id }) {
            currentOnSliceSelected(null)
        }
        animationEngine.launchEntryAnimations(entries, animationConfig, this)
    }

    // ── Selection animations ──
    // Keyed on both entries AND selectedEntry. When selection changes, old scale/alpha
    // animations cancel and new ones start from the current animated value (no jump).
    // When entries change, new entries pick up the current selection state.
    LaunchedEffect(entries, selectedEntry) {
        animationEngine.launchSelectionAnimations(
            entries, selectedEntry, style, animationConfig, this
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    contentDescription = chartDescription
                }
                // ── pointerInput(Unit): never restarts ──
                // All external values are read through rememberUpdatedState refs,
                // so the gesture coroutine survives data/style/selection changes
                // without dropping touches during a restart window.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val touchPos = down.position

                        val activeStyle = currentStyle
                        val activeIsRtl = currentIsRtl
                        val activeDensity = currentDensity

                        val cx = size.width.toFloat() / 2f
                        val cy = size.height.toFloat() / 2f

                        val effectiveX = if (activeIsRtl) size.width.toFloat() - touchPos.x else touchPos.x
                        val dx = effectiveX - cx
                        val dy = touchPos.y - cy

                        val touchRadius = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        val maxRadius = resolveOuterRadius(
                            activeStyle, size.width.toFloat(), size.height.toFloat(), activeDensity
                        )
                        val minRadius = maxRadius * activeStyle.donutRatio.coerceIn(0f, 0.9f)

                        if (touchRadius !in minRadius..maxRadius) {
                            if (currentSelectedEntry != null) currentOnSliceSelected(null)
                            return@awaitEachGesture
                        }

                        var touchAngle = Math.toDegrees(
                            atan2(dy.toDouble(), dx.toDouble())
                        ).toFloat()
                        touchAngle = (touchAngle - activeStyle.startAngle) % 360f
                        if (touchAngle < 0f) touchAngle += 360f

                        // derivedStateOf evaluates here, on-demand at touch time
                        val tappedSlice = sliceTouchBounds.find { (_, bounds) ->
                            touchAngle in bounds
                        }?.first

                        if (tappedSlice != null) {
                            if (currentSelectedEntry?.id == tappedSlice.id) {
                                currentOnSliceSelected(null)
                            } else {
                                currentOnSliceSelected(tappedSlice)
                            }
                        } else {
                            currentOnSliceSelected(null)
                        }
                    }
                }
        ) {
            // ── Pure draw lambda: no state mutations ──
            if (entries.isEmpty() || targetTotalValue <= 0f) return@Canvas

            val canvasRadius = resolveOuterRadius(style, size.width, size.height, density)
            val cx = size.width / 2f
            val cy = size.height / 2f

            val safeDonutRatio = style.donutRatio.coerceIn(0f, 0.99f)
            val strokeWidth = canvasRadius * (1f - safeDonutRatio)
            val drawRadius = canvasRadius - (strokeWidth / 2f)

            val directionMultiplier = if (isRtl) -1f else 1f

            // Two-pass min-angle normalization without list allocation.
            // First pass: accumulate the floor-adjusted sweep sum.
            val minAngle = style.minSliceAngle
            val hasMinAngle = minAngle > 0f
            var rawSweepSum = 0f
            if (hasMinAngle) {
                entries.forEach { entry ->
                    val v = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                    var s = (v / targetTotalValue) * 360f
                    if (s > 0f) s = s.coerceAtLeast(minAngle)
                    rawSweepSum += s
                }
            }
            val normalizer = if (hasMinAngle && rawSweepSum > 0f) 360f / rawSweepSum else 1f

            // Second pass: draw arcs.
            var drawnStartAngle = style.startAngle

            entries.forEach { entry ->
                val animatedValue = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                val sweepAngle = computeNormalizedSweep(
                    animatedValue, targetTotalValue, minAngle, normalizer
                )

                val currentScale = animationEngine.scaleAnimatables[entry.id]?.value ?: 1f
                val currentAlpha = animationEngine.alphaAnimatables[entry.id]?.value ?: 1f
                val scaledDrawRadius = drawRadius * currentScale
                val scaledStrokeWidth = strokeWidth * currentScale

                if (sweepAngle > 0f) {
                    val spacing = if (entries.size > 1 && sweepAngle > style.sliceSpacingAngle) {
                        style.sliceSpacingAngle
                    } else 0f
                    val finalSweepAngle = (sweepAngle - spacing).coerceAtLeast(0f)

                    val brush = resolveBrush(
                        sliceBrush = entry.brush ?: dataSet.defaultBrush,
                        cx = cx,
                        cy = cy,
                        radius = canvasRadius
                    )

                    drawArc(
                        brush = brush,
                        startAngle = drawnStartAngle,
                        sweepAngle = finalSweepAngle * directionMultiplier,
                        useCenter = false,
                        topLeft = Offset(cx - scaledDrawRadius, cy - scaledDrawRadius),
                        size = Size(scaledDrawRadius * 2, scaledDrawRadius * 2),
                        style = Stroke(width = scaledStrokeWidth),
                        alpha = currentAlpha
                    )
                }

                drawnStartAngle += (sweepAngle * directionMultiplier)
            }

            // Draw selection indicator
            selectedEntry?.let { entry ->
                val animatedValue = animationEngine.valueAnimatables[entry.id]?.value ?: 0f
                if (animatedValue > 0f) {
                    var targetStartAngle = style.startAngle
                    for (e in entries) {
                        if (e.id == entry.id) break
                        val valAnim = animationEngine.valueAnimatables[e.id]?.value ?: 0f
                        targetStartAngle += (valAnim / targetTotalValue) * 360f * directionMultiplier
                    }

                    val sweepAngle = (animatedValue / targetTotalValue) * 360f * directionMultiplier
                    val midAngle = targetStartAngle + (sweepAngle / 2f)

                    val midAngleRad = Math.toRadians(midAngle.toDouble())
                    val centroidRadius = canvasRadius - (strokeWidth / 2f)
                    val centroidX = cx + (centroidRadius * cos(midAngleRad)).toFloat()
                    val centroidY = cy + (centroidRadius * sin(midAngleRad)).toFloat()

                    with(selectionRenderer) {
                        drawSelection(
                            entry = entry,
                            pieCenter = Offset(cx, cy),
                            pieRadius = canvasRadius,
                            sliceCentroid = Offset(centroidX, centroidY),
                            midAngleDegrees = midAngle,
                            textMeasurer = textMeasurer,
                            tooltipCache = selectionCache,
                            layoutDirection = layoutDirection
                        )
                    }
                }
            }
        }

        // Donut center content slot
        if (centerContent != null && style.donutRatio > 0f) {
            centerContent()
        }
    }
}

