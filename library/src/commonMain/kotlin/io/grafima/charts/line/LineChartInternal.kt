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

package io.grafima.charts.line

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult
import io.grafima.charts.ExitTracker
import io.grafima.charts.Exiting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Maps a data-space X value to a canvas X coordinate, mirroring for RTL.
 * Falls back to [chartLeft] when the axis range is degenerate.
 */
internal fun mapDataXToCanvas(
    dataX: Float,
    xMin: Float,
    xMax: Float,
    chartLeft: Float,
    chartRight: Float,
    isRtl: Boolean
): Float {
    val xRange = xMax - xMin
    val raw = if (xRange > 0f) {
        chartLeft + (dataX - xMin) / xRange * (chartRight - chartLeft)
    } else {
        chartLeft
    }
    return if (isRtl) chartRight - (raw - chartLeft) else raw
}

/**
 * Index of the point whose canvas X is nearest to [touchX], or -1 when there is
 * none to pick.
 *
 * With [restrictToAxis] a point whose own x lies outside [xMin]..[xMax] is not a
 * candidate. Its marks are not drawn, so selecting it would move the crosshair
 * somewhere the reader can see nothing.
 */
internal fun nearestPointIndex(
    points: List<LineDataPoint>,
    touchX: Float,
    xMin: Float,
    xMax: Float,
    chartLeft: Float,
    chartRight: Float,
    isRtl: Boolean,
    restrictToAxis: Boolean = true
): Int {
    var nearest = -1
    var shortest = Float.MAX_VALUE
    for (i in points.indices) {
        if (restrictToAxis && !isWithinAxis(points[i].x, xMin, xMax)) continue
        val distance =
            abs(mapDataXToCanvas(points[i].x, xMin, xMax, chartLeft, chartRight, isRtl) - touchX)
        if (distance < shortest) {
            shortest = distance
            nearest = i
        }
    }
    return nearest
}

/**
 * Rounds axis step to a "nice" number (1, 2, 5 * 10^n) and generates evenly
 * spaced tick values that fully contain the data range.
 */
internal fun computeNiceAxisTicks(dataMin: Float, dataMax: Float, tickCount: Int): List<Float> {
    if (tickCount <= 0 || dataMax <= dataMin) return listOf(dataMin, dataMax)
    val rawStep = (dataMax - dataMin) / tickCount
    if (rawStep <= 0f) return listOf(dataMin, dataMax)
    val magnitude = 10f.pow(floor(log10(rawStep)))
    val norm = rawStep / magnitude
    val niceStep = when {
        norm <= 1.0f -> magnitude
        norm <= 2.0f -> 2f * magnitude
        norm <= 5.0f -> 5f * magnitude
        else -> 10f * magnitude
    }
    val niceMin = floor(dataMin / niceStep) * niceStep
    val niceMax = ceil(dataMax / niceStep) * niceStep
    val count = ((niceMax - niceMin) / niceStep + 0.5f).toInt()
    return (0..count).map { niceMin + it * niceStep }
}

/**
 * Axis tick values, honouring a pinned [pinnedMin] or [pinnedMax].
 *
 * With neither pinned this is [computeNiceAxisTicks]. With either pinned the range
 * is divided into [tickCount] equal steps instead: nice-number rounding extends the
 * range outwards to reach a round step, which would move the edge the caller pinned.
 *
 * A pin that cannot produce a usable range — inverted, empty, or not finite — is
 * ignored in favour of the automatic range. Honouring it would collapse every point
 * onto one line, which reads as a broken chart rather than as bad input.
 */
internal fun computeAxisTicks(
    dataMin: Float,
    dataMax: Float,
    tickCount: Int,
    pinnedMin: Float?,
    pinnedMax: Float?
): List<Float> {
    if (pinnedMin == null && pinnedMax == null) {
        return computeNiceAxisTicks(dataMin, dataMax, tickCount)
    }
    val lo = pinnedMin ?: dataMin
    val hi = pinnedMax ?: dataMax
    if (!lo.isFinite() || !hi.isFinite() || hi <= lo) {
        return computeNiceAxisTicks(dataMin, dataMax, tickCount)
    }
    if (tickCount <= 0) return listOf(lo, hi)
    val step = (hi - lo) / tickCount
    // The last tick is [hi] itself: lo + tickCount * step lands an ulp short for
    // counts that do not divide the span, and a bound pinned to 31 labels as 30.
    return (0..tickCount).map { if (it == tickCount) hi else lo + it * step }
}

/**
 * Axis bounds honouring a pinned [pinnedMin] or [pinnedMax], falling back to the data
 * extent when the pin cannot produce a usable range — inverted, empty, or not finite.
 *
 * A span of zero or less divides every point onto one edge, which reads as a broken
 * chart rather than as bad input. [computeAxisTicks] rejects the same shapes for y.
 */
internal fun resolveAxisBounds(
    dataMin: Float,
    dataMax: Float,
    pinnedMin: Float?,
    pinnedMax: Float?
): ClosedFloatingPointRange<Float> {
    if (pinnedMin == null && pinnedMax == null) return dataMin..dataMax
    val lo = pinnedMin ?: dataMin
    val hi = pinnedMax ?: dataMax
    if (!lo.isFinite() || !hi.isFinite() || hi <= lo) return dataMin..dataMax
    return lo..hi
}

/**
 * One axis title trimmed to the space it has, kept across frames.
 *
 * Only the newest is worth holding: the width changes when the chart is resized,
 * and a cache keyed by width would grow for every pixel a window drag passes
 * through.
 */
internal class FittedTitle {
    var width: Int = -1
    var layout: TextLayoutResult? = null
}

/**
 * The plot rectangle, once labels and axis titles have taken their room.
 *
 * Mutable and reused: [computePlotInsets] runs on every frame, and a fresh object
 * per frame is a per-frame allocation the draw pass does not otherwise make.
 */
internal data class PlotInsets(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
)

/**
 * Carves the plot rectangle out of the canvas, writing into [into].
 *
 * Y labels and the y title sit on the left, and mirror to the right in RTL. A y
 * title is drawn rotated, so the width it claims is [yTitleHeight] — its measured
 * height — not its length.
 *
 * Each element costs [gap] on top of its own size, and contributes nothing when
 * its size is zero, which keeps the rectangle identical to the untitled case.
 */
internal fun computePlotInsets(
    into: PlotInsets,
    width: Float,
    height: Float,
    gap: Float,
    yLabelWidth: Float,
    xLabelHeight: Float,
    yTitleHeight: Float,
    xTitleHeight: Float,
    isRtl: Boolean
): PlotInsets {
    val yBand = yLabelWidth + if (yTitleHeight > 0f) yTitleHeight + gap else 0f
    val xBand = xLabelHeight + if (xTitleHeight > 0f) xTitleHeight + gap else 0f
    into.left = gap + if (isRtl) 0f else yBand
    into.top = gap
    into.right = width - gap - if (isRtl) yBand else 0f
    into.bottom = height - gap - xBand
    return into
}

/**
 * Whether [value] falls inside the axis.
 *
 * Tested against the value rather than its animated screen position, so a morph
 * spring overshooting its target cannot blink a mark out. The tolerance covers a
 * bound that arrived by division — the last tick of a pinned range can land an ulp
 * short of the number the caller pinned, and the point sitting on it must still draw.
 */
internal fun isWithinAxis(value: Float, axisMin: Float, axisMax: Float): Boolean {
    val tolerance = (axisMax - axisMin) * 1e-5f
    return value >= axisMin - tolerance && value <= axisMax + tolerance
}

/**
 * Fritsch-Carlson monotone cubic tangent computation. Operates entirely on
 * pre-allocated [FloatArray] buffers with zero heap allocation.
 *
 * [deltas] is a scratch buffer of size >= n-1 that avoids a per-frame allocation.
 * The result is written into [tangents] in-place.
 */
internal fun computeMonotoneTangents(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, deltas: FloatArray, n: Int
) {
    if (n < 2) return
    for (i in 0 until n - 1) {
        val dx = xs[i + 1] - xs[i]
        deltas[i] = if (dx == 0f) 0f else (ys[i + 1] - ys[i]) / dx
    }
    tangents[0] = deltas[0]
    tangents[n - 1] = deltas[n - 2]
    for (i in 1 until n - 1) {
        tangents[i] = if (deltas[i - 1] * deltas[i] <= 0f) 0f
        else (deltas[i - 1] + deltas[i]) / 2f
    }
    // Monotonicity enforcement: clamp tangent magnitudes so alpha^2 + beta^2 <= 9
    for (i in 0 until n - 1) {
        if (deltas[i] == 0f) {
            tangents[i] = 0f; tangents[i + 1] = 0f
        } else {
            val a = tangents[i] / deltas[i]
            val b = tangents[i + 1] / deltas[i]
            val s = a * a + b * b
            if (s > 9f) {
                val tau = 3f / sqrt(s)
                tangents[i] = tau * a * deltas[i]
                tangents[i + 1] = tau * b * deltas[i]
            }
        }
    }
}

/** Builds a line path (stroke only) from pre-computed screen-space buffers. */
internal fun Path.buildCurve(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, n: Int,
    curveType: LineCurveType
) {
    if (n < 1) return
    moveTo(xs[0], ys[0])
    if (n == 1) return
    when (curveType) {
        LineCurveType.Linear -> for (i in 1 until n) lineTo(xs[i], ys[i])
        LineCurveType.MonotoneCubic -> for (i in 0 until n - 1) {
            val dx = (xs[i + 1] - xs[i]) / 3f
            cubicTo(
                xs[i] + dx, ys[i] + tangents[i] * dx,
                xs[i + 1] - dx, ys[i + 1] - tangents[i + 1] * dx,
                xs[i + 1], ys[i + 1]
            )
        }
    }
}

/** Builds a closed area path: curve on top, straight bottom edge at [chartBottom]. */
internal fun Path.buildArea(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, n: Int,
    chartBottom: Float, curveType: LineCurveType
) {
    if (n < 1) return
    buildCurve(xs = xs, ys = ys, tangents = tangents, n = n, curveType = curveType)
    lineTo(xs[n - 1], chartBottom)
    lineTo(xs[0], chartBottom)
    close()
}

/**
 * Manages per-point Y-value [Animatable] instances keyed as `"seriesId::pointIndex"`.
 *
 * Lifecycle (same pattern as PieChart/RadarChart):
 * - [syncAnimatables]: synchronous map housekeeping via [SideEffect], ensures
 *   animatable instances exist for current data before the first draw.
 * - [launchEntryAnimations]: staggered async animations via [LaunchedEffect] scope.
 *   Old coroutines cancel automatically when data changes.
 */
@Stable
internal class LineChartAnimationEngine {
    internal val yAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedKeys = mutableSetOf<String>()

    private val exitTracker = ExitTracker<LineSeries> { it.id }

    /** Series the dataset no longer contains but the chart is still drawing. */
    val exiting: List<Exiting<LineSeries>> get() = exitTracker.exiting

    /** Ensures animatables exist for all current points. Removes stale entries. */
    fun syncAnimatables(series: List<LineSeries>) {
        exitTracker.sync(series)

        val activeKeys = mutableSetOf<String>()
        renderSeries(series).forEach { s ->
            s.points.forEachIndexed { i, _ ->
                val key = "${s.id}::$i"
                activeKeys.add(key)
                yAnimatables.getOrPut(key) { Animatable(0f) }
            }
        }
        yAnimatables.keys.removeAll { it !in activeKeys }
        initializedKeys.removeAll { it !in activeKeys }
    }

    /** Draws a departing series back to the baseline: its entry animation in reverse. */
    fun launchExitAnimations(
        config: LineAnimationConfig,
        yBaseline: Float,
        scope: CoroutineScope
    ) {
        exitTracker.exiting.forEach { leaving ->
            val series = leaving.item
            val keys = series.points.indices.map { i -> "${series.id}::$i" }
            val anims = keys.mapNotNull { yAnimatables[it] }
            if (anims.isEmpty()) return@forEach
            if (anims.any { it.isRunning }) return@forEach

            // A cancelled coroutine can leave it at rest but still listed.
            if (anims.all { it.value == yBaseline }) {
                forget(leaving, keys)
                return@forEach
            }

            scope.launch {
                anims.map { anim ->
                    launch { anim.animateTo(yBaseline, config.entrySpec) }
                }.joinAll()
                forget(leaving, keys)
            }
        }
    }

    private fun forget(leaving: Exiting<LineSeries>, keys: List<String>) {
        keys.forEach { key ->
            yAnimatables.remove(key)
            initializedKeys.remove(key)
        }
        exitTracker.forget(leaving)
    }

    /** Draw order only: the crosshair and a11y stay on the dataset. */
    fun renderSeries(series: List<LineSeries>): List<LineSeries> = exitTracker.render(series)

    /**
     * Launches staggered entry or morph animations. New points animate from
     * [yBaseline]; existing points spring to their updated target.
     */
    fun launchEntryAnimations(
        series: List<LineSeries>,
        config: LineAnimationConfig,
        yBaseline: Float,
        scope: CoroutineScope
    ) {
        series.forEachIndexed { si, s ->
            s.points.forEachIndexed { pi, point ->
                val key = "${s.id}::$pi"
                val anim = yAnimatables[key] ?: return@forEachIndexed
                val isInitial = initializedKeys.add(key)
                scope.launch {
                    if (isInitial) {
                        anim.snapTo(yBaseline)
                        delay(config.startDelayMs + si * config.seriesStaggerMs + pi * config.staggerMs)
                        anim.animateTo(point.y, config.entrySpec)
                    } else if (anim.targetValue != point.y) {
                        anim.animateTo(point.y, config.morphSpec)
                    }
                }
            }
        }
    }
}
