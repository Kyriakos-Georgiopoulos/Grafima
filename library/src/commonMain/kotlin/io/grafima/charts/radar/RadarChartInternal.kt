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
import androidx.compose.ui.unit.Dp
import io.grafima.charts.ExitTracker
import io.grafima.charts.Exiting
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

    private val exitTracker = ExitTracker<RadarSeries> { it.id }

    /** Series the dataset no longer contains but the chart is still drawing. */
    val exiting: List<Exiting<RadarSeries>> get() = exitTracker.exiting

    /**
     * Ensures Animatable instances exist for all current (series, axis) pairs
     * and removes stale ones. Synchronous and idempotent.
     */
    fun syncAnimatables(axes: List<RadarAxis>, series: List<RadarSeries>) {
        exitTracker.sync(series)

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
    }

    /** Collapses a departing series to the centre: its entry animation in reverse. */
    fun launchExitAnimations(
        axes: List<RadarAxis>,
        config: RadarAnimationConfig,
        scope: CoroutineScope
    ) {
        exitTracker.exiting.forEach { leaving ->
            val keys = axes.map { "${leaving.item.id}::${it.id}" }
            val anims = keys.mapNotNull { valueAnimatables[it] }
            if (anims.isEmpty()) return@forEach
            if (anims.any { it.isRunning }) return@forEach

            // A cancelled coroutine can leave it at rest but still listed.
            if (anims.all { it.value == 0f }) {
                forget(leaving, keys)
                return@forEach
            }

            scope.launch {
                // Together, so the shape closes inward rather than unwinding axis by axis.
                anims.map { anim ->
                    launch { anim.animateTo(0f, config.initialEntrySpec) }
                }.joinAll()
                forget(leaving, keys)
            }
        }
    }

    private fun forget(leaving: Exiting<RadarSeries>, keys: List<String>) {
        keys.forEach { key ->
            valueAnimatables.remove(key)
            initializedKeys.remove(key)
        }
        alphaAnimatables.remove(leaving.item.id)
        exitTracker.forget(leaving)
    }

    /** Draw order only: touch handling and a11y stay on the dataset. */
    fun renderSeries(series: List<RadarSeries>): List<RadarSeries> = exitTracker.render(series)

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
