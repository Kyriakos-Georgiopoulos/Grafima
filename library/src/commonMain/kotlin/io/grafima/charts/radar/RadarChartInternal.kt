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

package io.grafima.charts.radar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Pre-computed cosine/sine values for each axis angle.
 * Allocated once via [remember] and reused across every draw frame.
 */
@Stable
internal class AxisTrigCache(val cosA: FloatArray, val sinA: FloatArray)

/** A series dropped from the dataset that is still collapsing to the centre. */
@Stable
internal class ExitingSeries(val series: RadarSeries, val index: Int)

/**
 * Resolves the outer radius in pixels for the radar chart.
 */
internal fun resolveRadarRadius(
    style: RadarChartStyle,
    canvasWidth: Float,
    canvasHeight: Float,
    labelSpace: Float,
    density: androidx.compose.ui.unit.Density
): Float {
    return if (style.outerRadius != Dp.Unspecified) {
        with(density) { style.outerRadius.toPx() }
    } else {
        val available = (min(canvasWidth, canvasHeight) / 2f) - labelSpace
        available * style.fillFraction.coerceIn(0.1f, 1f)
    }
}

/**
 * Manages per-vertex [Animatable] instances for value and per-series alpha.
 *
 * Vertex values are keyed as `"seriesId::axisId"`. Series alpha is keyed by `seriesId`.
 *
 * Lifecycle split:
 * - [syncAnimatables]: synchronous map housekeeping, called via [SideEffect].
 * - [launchEntryAnimations]: async vertex animations, called in [LaunchedEffect] scope.
 * - [launchSelectionAnimations]: async alpha animations, called in [LaunchedEffect] scope.
 */
@Stable
internal class RadarChartAnimationEngine {
    internal val valueAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    internal val alphaAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedKeys = mutableSetOf<String>()

    /** Series the dataset no longer contains but the chart is still drawing. */
    var exiting: List<ExitingSeries> by mutableStateOf(emptyList())
        private set

    private var lastSeries: List<RadarSeries> = emptyList()

    /**
     * Ensures Animatable instances exist for all current (series, axis) pairs
     * and removes stale ones. Synchronous and idempotent.
     */
    fun syncAnimatables(axes: List<RadarAxis>, series: List<RadarSeries>) {
        val currentIds = series.mapTo(mutableSetOf()) { it.id }
        val exitingIds = exiting.mapTo(mutableSetOf()) { it.series.id }

        val departed = lastSeries.withIndex()
            .filter { (_, s) -> s.id !in currentIds && s.id !in exitingIds }
            .map { (index, s) -> ExitingSeries(s, index) }
        val returned = exiting.filter { it.series.id in currentIds }
        if (departed.isNotEmpty() || returned.isNotEmpty()) {
            exiting = exiting - returned.toSet() + departed
        }

        val drawn = renderSeries(series)
        val activeValueKeys = mutableSetOf<String>()
        val activeSeriesIds = drawn.mapTo(mutableSetOf()) { it.id }

        drawn.forEach { s ->
            axes.forEach { a ->
                val key = "${s.id}::${a.id}"
                activeValueKeys.add(key)
                valueAnimatables.getOrPut(key) { Animatable(0f) }
            }
            alphaAnimatables.getOrPut(s.id) { Animatable(1f) }
        }

        valueAnimatables.keys.removeAll { it !in activeValueKeys }
        alphaAnimatables.keys.removeAll { it !in activeSeriesIds }
        initializedKeys.removeAll { it !in activeValueKeys }
        lastSeries = series
    }

    /**
     * Collapses a departing series back to the centre — the reverse of the entry
     * that grew its vertices out of it — then forgets it.
     */
    fun launchExitAnimations(
        axes: List<RadarAxis>,
        config: RadarAnimationConfig,
        scope: CoroutineScope
    ) {
        exiting.forEach { leaving ->
            val keys = axes.map { "${leaving.series.id}::${it.id}" }
            val anims = keys.mapNotNull { valueAnimatables[it] }
            if (anims.isEmpty()) return@forEach
            if (anims.any { it.isRunning } || anims.all { it.value == 0f }) return@forEach

            scope.launch {
                // Every vertex collapses together, so the shape closes inward as
                // one rather than unwinding axis by axis.
                anims.map { anim ->
                    launch { anim.animateTo(0f, config.initialEntrySpec) }
                }.joinAll()

                keys.forEach { key ->
                    valueAnimatables.remove(key)
                    initializedKeys.remove(key)
                }
                alphaAnimatables.remove(leaving.series.id)
                exiting = exiting - leaving
            }
        }
    }

    /**
     * Dataset series with the departing ones back in the places they held. Draw
     * order only — touch handling and the accessibility description stay on the
     * dataset.
     */
    fun renderSeries(series: List<RadarSeries>): List<RadarSeries> {
        val currentIds = series.mapTo(mutableSetOf()) { it.id }

        // Runs during composition, before the SideEffect that files a departure
        // under `exiting`, so pick it up from the previous dataset too — otherwise
        // the series blinks out for a frame before it starts collapsing.
        val pending = lastSeries.withIndex()
            .filter { (_, s) -> s.id !in currentIds }
            .map { (index, s) -> ExitingSeries(s, index) }

        val leaving = (exiting + pending).distinctBy { it.series.id }
        if (leaving.isEmpty()) return series

        val merged = series.toMutableList()
        leaving.sortedBy { it.index }.forEach { merged.add(it.index.coerceIn(0, merged.size), it.series) }
        return merged
    }

    /**
     * Launches staggered vertex animations. New vertices animate from 0 with
     * a cascading delay across series and axes. Existing vertices morph to
     * their new target value.
     */
    fun launchEntryAnimations(
        axes: List<RadarAxis>,
        series: List<RadarSeries>,
        config: RadarAnimationConfig,
        scope: CoroutineScope
    ) {
        series.forEachIndexed { seriesIndex, s ->
            axes.forEachIndexed { axisIndex, a ->
                val key = "${s.id}::${a.id}"
                val anim = valueAnimatables[key] ?: return@forEachIndexed
                val target = s.values[a.id] ?: 0f
                val isInitial = initializedKeys.add(key)

                scope.launch {
                    if (isInitial) {
                        val totalDelay = config.startDelayMs +
                                (seriesIndex * config.seriesStaggerMs) +
                                (axisIndex * config.vertexStaggerMs)
                        delay(totalDelay)
                        anim.animateTo(target, config.initialEntrySpec)
                    } else if (anim.targetValue != target) {
                        anim.animateTo(target, config.morphSpec)
                    }
                }
            }
        }
    }

    /**
     * Launches alpha animations to reflect the current selection state.
     * The selected series stays at full alpha; all others dim.
     */
    fun launchSelectionAnimations(
        series: List<RadarSeries>,
        selectedSeries: RadarSeries?,
        style: RadarChartStyle,
        config: RadarAnimationConfig,
        scope: CoroutineScope
    ) {
        series.forEach { s ->
            val alphaAnim = alphaAnimatables[s.id] ?: return@forEach
            val targetAlpha = if (selectedSeries != null && selectedSeries.id != s.id) {
                style.unselectedAlpha
            } else 1f

            if (alphaAnim.targetValue != targetAlpha) {
                scope.launch { alphaAnim.animateTo(targetAlpha, config.selectionSpec) }
            }
        }
    }
}
