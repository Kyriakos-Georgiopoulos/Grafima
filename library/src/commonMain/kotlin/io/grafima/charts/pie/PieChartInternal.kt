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

package io.grafima.charts.pie

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import io.grafima.charts.toRadians
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Resolves a [SliceBrush] into a Compose [Brush] for the given chart geometry.
 */
internal fun resolveBrush(
    sliceBrush: SliceBrush,
    cx: Float,
    cy: Float,
    radius: Float
): Brush = when (sliceBrush) {
    is SliceBrush.Solid -> SolidColor(sliceBrush.color)
    is SliceBrush.Linear -> {
        val rad = toRadians(sliceBrush.angleDegrees.toDouble())
        val dx = cos(rad).toFloat() * radius
        val dy = sin(rad).toFloat() * radius
        Brush.linearGradient(
            colors = sliceBrush.colors,
            start = Offset(x = cx - dx, y = cy - dy),
            end = Offset(x = cx + dx, y = cy + dy)
        )
    }

    is SliceBrush.Radial -> Brush.radialGradient(
        colors = sliceBrush.colors,
        center = Offset(x = cx, y = cy),
        radius = radius
    )

    is SliceBrush.Sweep -> Brush.sweepGradient(
        colors = sliceBrush.colors,
        center = Offset(x = cx, y = cy)
    )
}

/**
 * Resolves the outer radius in pixels based on [PieChartStyle] configuration.
 * If [PieChartStyle.outerRadius] is specified, converts it to px.
 * Otherwise, computes from the canvas dimensions and [PieChartStyle.fillFraction].
 */
internal fun resolveOuterRadius(
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
internal fun computeNormalizedSweep(
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

/** A slice dropped from the dataset that is still closing. */
@Stable
internal class ExitingSlice(val entry: PieEntry, val index: Int)

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
internal class PieChartAnimationEngine {
    internal val valueAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    internal val scaleAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    internal val alphaAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedIds = mutableSetOf<String>()

    /**
     * Slices the dataset no longer contains but the chart is still drawing.
     *
     * Snapshot state, because dropping one at the end of its exit has to bring the
     * chart back for another frame.
     */
    var exiting: List<ExitingSlice> by mutableStateOf(emptyList())
        private set

    private var lastEntries: List<PieEntry> = emptyList()

    /**
     * Ensures Animatable instances exist for all current [entries] and removes stale
     * ones from previous datasets. Synchronous and idempotent. Safe to call on every
     * composition via [SideEffect] because it only performs map get-or-put operations
     * and a single removeAll pass.
     */
    fun syncAnimatables(entries: List<PieEntry>) {
        val currentIds = entries.mapTo(mutableSetOf()) { it.id }
        val exitingIds = exiting.mapTo(mutableSetOf()) { it.entry.id }

        val departed = lastEntries.withIndex()
            .filter { (_, e) -> e.id !in currentIds && e.id !in exitingIds }
            .map { (index, e) -> ExitingSlice(e, index) }
        val returned = exiting.filter { it.entry.id in currentIds }

        if (departed.isNotEmpty() || returned.isNotEmpty()) {
            exiting = exiting - returned.toSet() + departed
        }

        val drawn = currentIds + exiting.mapTo(mutableSetOf()) { it.entry.id }
        valueAnimatables.keys.removeAll { it !in drawn }
        scaleAnimatables.keys.removeAll { it !in drawn }
        alphaAnimatables.keys.removeAll { it !in drawn }
        initializedIds.removeAll { it !in drawn }

        entries.forEach { entry ->
            valueAnimatables.getOrPut(entry.id) { Animatable(0f) }
            scaleAnimatables.getOrPut(entry.id) { Animatable(1f) }
            alphaAnimatables.getOrPut(entry.id) { Animatable(1f) }
        }
        lastEntries = entries
    }

    /**
     * Closes a departing slice by running its entry animation backwards.
     *
     * No second movement, unlike a bar: a slice's value is its angle, so the
     * survivors widen as it closes — provided [exitingValue] keeps counting it.
     */
    fun launchExitAnimations(config: PieAnimationConfig, scope: CoroutineScope) {
        exiting.forEach { slice ->
            val value = valueAnimatables[slice.entry.id] ?: return@forEach
            if (value.isRunning) return@forEach

            // A cancelled coroutine can leave it at rest but still listed.
            if (value.value == 0f) {
                forget(slice)
                return@forEach
            }

            scope.launch {
                value.animateTo(0f, config.initialEntrySpec)
                forget(slice)
            }
        }
    }

    private fun forget(slice: ExitingSlice) {
        valueAnimatables.remove(slice.entry.id)
        scaleAnimatables.remove(slice.entry.id)
        alphaAnimatables.remove(slice.entry.id)
        initializedIds.remove(slice.entry.id)
        exiting = exiting - slice
    }

    /**
     * Dataset slices with the departing ones back in the places they held. Draw
     * order only — touch handling and the accessibility description stay on the
     * dataset.
     */
    fun renderEntries(entries: List<PieEntry>): List<PieEntry> {
        val leaving = leaving(entries)
        if (leaving.isEmpty()) return entries

        val merged = entries.toMutableList()
        leaving.sortedBy { it.index }.forEach { slice ->
            merged.add(slice.index.coerceIn(0, merged.size), slice.entry)
        }
        return merged
    }

    /**
     * The share the closing slices still hold. Read while drawing, not during
     * composition: it changes every frame.
     */
    fun exitingValue(entries: List<PieEntry>): Float {
        if (exiting.isEmpty()) return 0f
        return exiting.fold(0f) { acc, slice ->
            acc + (valueAnimatables[slice.entry.id]?.value ?: 0f)
        }
    }

    /**
     * Slices on their way out. Also reads the previous dataset: on the frame one is
     * dropped the SideEffect has not run yet, and the slice would blink out.
     */
    private fun leaving(entries: List<PieEntry>): List<ExitingSlice> {
        val currentIds = entries.mapTo(mutableSetOf()) { it.id }
        val pending = lastEntries.withIndex()
            .filter { (_, e) -> e.id !in currentIds }
            .map { (index, e) -> ExitingSlice(e, index) }
        if (exiting.isEmpty() && pending.isEmpty()) return emptyList()
        return (exiting + pending).distinctBy { it.entry.id }
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
        // Stagger by position among the slices appearing now, not by dataset index.
        // Identical on first load; on a later addition it saves the newcomer waiting
        // one stagger step per slice already drawn.
        var appearing = 0
        entries.forEach { entry ->
            val valueAnim = valueAnimatables[entry.id] ?: return@forEach

            if (initializedIds.add(entry.id)) {
                val position = appearing++
                scope.launch {
                    delay(config.startDelayMs + (position * config.staggerDelayMs))
                    valueAnim.animateTo(entry.value, config.initialEntrySpec)
                }
            } else if (valueAnim.targetValue != entry.value) {
                scope.launch { valueAnim.animateTo(entry.value, config.morphSpec) }
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
            val targetAlpha =
                if (selectedEntry != null && !isSelected) style.unselectedAlpha else 1f

            if (scaleAnim.targetValue != targetScale) {
                scope.launch { scaleAnim.animateTo(targetScale, config.selectionSpec) }
            }
            if (alphaAnim.targetValue != targetAlpha) {
                scope.launch { alphaAnim.animateTo(targetAlpha, config.selectionSpec) }
            }
        }
    }
}
