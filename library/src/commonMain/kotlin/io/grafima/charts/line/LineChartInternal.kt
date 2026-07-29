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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
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

/** Index of the point whose canvas X is nearest to [touchX]; 0 when [points] is empty. */
internal fun nearestPointIndex(
    points: List<LineDataPoint>,
    touchX: Float,
    xMin: Float,
    xMax: Float,
    chartLeft: Float,
    chartRight: Float,
    isRtl: Boolean
): Int = points.indices.minByOrNull {
    abs(mapDataXToCanvas(points[it].x, xMin, xMax, chartLeft, chartRight, isRtl) - touchX)
} ?: 0

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

/** A series dropped from the dataset that is still dropping to the baseline. */
@Stable
internal class ExitingLineSeries(val series: LineSeries, val index: Int)

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

    /** Series the dataset no longer contains but the chart is still drawing. */
    var exiting: List<ExitingLineSeries> by mutableStateOf(emptyList())
        private set

    private var lastSeries: List<LineSeries> = emptyList()

    /** Ensures animatables exist for all current points. Removes stale entries. */
    fun syncAnimatables(series: List<LineSeries>) {
        val currentIds = series.mapTo(mutableSetOf()) { it.id }
        val exitingIds = exiting.mapTo(mutableSetOf()) { it.series.id }

        val departed = lastSeries.withIndex()
            .filter { (_, s) -> s.id !in currentIds && s.id !in exitingIds }
            .map { (index, s) -> ExitingLineSeries(s, index) }
        val returned = exiting.filter { it.series.id in currentIds }
        if (departed.isNotEmpty() || returned.isNotEmpty()) {
            exiting = exiting - returned.toSet() + departed
        }

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
        lastSeries = series
    }

    /** Draws a departing series back to the baseline: its entry animation in reverse. */
    fun launchExitAnimations(
        config: LineAnimationConfig,
        yBaseline: Float,
        scope: CoroutineScope
    ) {
        exiting.forEach { leaving ->
            val keys = leaving.series.points.indices.map { i -> "${leaving.series.id}::$i" }
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

    private fun forget(leaving: ExitingLineSeries, keys: List<String>) {
        keys.forEach { key ->
            yAnimatables.remove(key)
            initializedKeys.remove(key)
        }
        exiting = exiting - leaving
    }

    /** Draw order only: the crosshair and a11y stay on the dataset. */
    fun renderSeries(series: List<LineSeries>): List<LineSeries> {
        val currentIds = series.mapTo(mutableSetOf()) { it.id }

        // Also reads the previous dataset: on the frame one is dropped the
        // SideEffect has not run yet, and the line would blink out.
        val pending = lastSeries.withIndex()
            .filter { (_, s) -> s.id !in currentIds }
            .map { (index, s) -> ExitingLineSeries(s, index) }

        val leaving = (exiting + pending).distinctBy { it.series.id }
        if (leaving.isEmpty()) return series

        val merged = series.toMutableList()
        leaving.sortedBy { it.index }.forEach { merged.add(it.index.coerceIn(0, merged.size), it.series) }
        return merged
    }

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
