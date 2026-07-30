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

package io.grafima.charts.bar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import io.grafima.charts.ExitTracker
import io.grafima.charts.Exiting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Y-axis maximum: 20% headroom over the tallest bar, rounded up to a tidy
 * step (5, 10, or 50 depending on magnitude) so axis labels stay round numbers.
 */
internal fun computeBarAxisMax(entries: List<BarEntry>): Float {
    val rawMax = entries.maxOfOrNull { it.y }?.takeIf { it > 0f } ?: 1f
    val maxWithHeadroom = rawMax * 1.2f
    val step = if (maxWithHeadroom > 100) 50f else if (maxWithHeadroom > 10) 10f else 5f
    return ceil(maxWithHeadroom / step) * step
}

/**
 * Bar thickness and gap along the layout axis. Gaps flank every bar, so
 * [count] bars produce count+1 gaps: thickness*count + gap*(count+1) == extent.
 */
internal fun barThicknessAndGap(
    extent: Float,
    count: Int,
    spacingFactor: Float
): Pair<Float, Float> = barThicknessAndGap(extent, count.toFloat(), spacingFactor)

/**
 * The same split over a fractional [count], which is what a slot collapsing after
 * a removal produces: the survivors widen across the collapse instead of jumping
 * once it completes.
 */
internal fun barThicknessAndGap(
    extent: Float,
    count: Float,
    spacingFactor: Float
): Pair<Float, Float> {
    val slots = count.coerceAtLeast(0.01f)
    val totalSpacing = extent * spacingFactor.coerceIn(0f, 0.9f)
    return (extent - totalSpacing) / slots to totalSpacing / (slots + 1f)
}

/** LTR offset of bar [index] along the layout axis, measured from [leadingInset]. */
internal fun barSlotOffset(index: Int, leadingInset: Float, thickness: Float, gap: Float): Float =
    barSlotOffset(index.toFloat(), leadingInset, thickness, gap)

/** The same offset for a fractional slot [position] — the slots ahead of this bar. */
internal fun barSlotOffset(
    position: Float,
    leadingInset: Float,
    thickness: Float,
    gap: Float
): Float = leadingInset + gap + position * (thickness + gap)

/** Mirrors an LTR offset across [totalExtent] for RTL layouts. */
internal fun mirrorForRtl(
    ltrOffset: Float,
    totalExtent: Float,
    thickness: Float,
    isRtl: Boolean
): Float = if (isRtl) totalExtent - ltrOffset - thickness else ltrOffset

/**
 * A summary, not a reading of the data: this node is a live region, so anything
 * here is repeated on every selection. Per-bar values live in the select actions
 * and in `stateDescription`.
 */
internal fun buildBarChartDescription(
    dataSet: BarDataSet,
    a11yConfig: A11yConfig
): String = buildString {
    append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
    append(a11yConfig.barCountDescriptionBuilder(dataSet.entries.size))
}

@Stable
internal class ChartAnimationEngine {
    val heightAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    val selectionAlphaAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedIds = mutableSetOf<String>()

    /** How much of its slot a departing bar still holds: 1 until it has sunk, then to 0. */
    val slotAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()

    private val exitTracker = ExitTracker<BarEntry> { it.id }

    /** Bars the dataset no longer contains but the chart is still drawing. */
    val exiting: List<Exiting<BarEntry>> get() = exitTracker.exiting

    fun syncAnimatables(entries: List<BarEntry>) {
        // An id that returns mid-exit rejoins the dataset and grows from where it got to.
        val (departed, returned) = exitTracker.sync(entries)
        departed.forEach { slotAnimatables[it.item.id] = Animatable(1f) }
        returned.forEach { slotAnimatables.remove(it.item.id) }

        val drawn = entries.mapTo(mutableSetOf()) { it.id } +
            exitTracker.exiting.mapTo(mutableSetOf()) { it.item.id }
        heightAnimatables.keys.removeAll { it !in drawn }
        selectionAlphaAnimatables.keys.removeAll { it !in drawn }
        initializedIds.removeAll { it !in drawn }

        entries.forEach { entry ->
            heightAnimatables.getOrPut(entry.id) { Animatable(0f) }
            selectionAlphaAnimatables.getOrPut(entry.id) { Animatable(1f) }
        }
    }

    /**
     * Removes a bar in two movements: its entry animation in reverse, then the slot
     * it held collapsing on [AnimationConfig.morphSpec] as the survivors widen in.
     */
    fun launchExitAnimations(config: AnimationConfig, scope: CoroutineScope) {
        exitTracker.exiting.forEach { bar ->
            val id = bar.item.id
            val height = heightAnimatables[id] ?: return@forEach
            val slot = slotAnimatables[id] ?: return@forEach
            if (height.isRunning || slot.isRunning) return@forEach

            // A cancelled coroutine can leave it at rest but still listed.
            if (slot.value == 0f) {
                forget(bar)
                return@forEach
            }

            scope.launch {
                height.animateTo(0f, config.initialEntrySpec)
                slot.animateTo(0f, config.morphSpec)
                forget(bar)
            }
        }
    }

    private fun forget(bar: Exiting<BarEntry>) {
        val id = bar.item.id
        heightAnimatables.remove(id)
        selectionAlphaAnimatables.remove(id)
        slotAnimatables.remove(id)
        initializedIds.remove(id)
        exitTracker.forget(bar)
    }

    /**
     * Dataset bars with the departing ones back in the positions they held. Draw
     * order only — touch handling and the accessibility description stay on the
     * dataset.
     */
    fun renderEntries(entries: List<BarEntry>): List<BarEntry> = exitTracker.render(entries)

    /** A bar's remaining claim on its slot. Read while drawing: it changes per frame. */
    fun slotOccupancy(id: String): Float = slotAnimatables[id]?.value ?: 1f

    /** Slots currently in use, counting a collapsing one as the fraction it still holds. */
    fun slotCount(renderEntries: List<BarEntry>): Float =
        if (exitTracker.exiting.isEmpty()) renderEntries.size.toFloat()
        else renderEntries.fold(0f) { acc, entry -> acc + slotOccupancy(entry.id) }

    fun launchEntryAnimations(entries: List<BarEntry>, config: AnimationConfig, scope: CoroutineScope) {
        // Stagger by position among the bars appearing now, not by dataset index.
        // Identical on first load; on a later append it saves the newcomer waiting
        // one stagger step per bar already drawn.
        var appearing = 0
        entries.forEach { entry ->
            val heightAnim = heightAnimatables[entry.id] ?: return@forEach

            if (initializedIds.add(entry.id)) {
                val position = appearing++
                scope.launch {
                    delay(config.startDelayMs + (position * config.staggerDelayMs))
                    heightAnim.animateTo(entry.y, config.initialEntrySpec)
                }
            } else if (heightAnim.targetValue != entry.y) {
                scope.launch { heightAnim.animateTo(entry.y, config.morphSpec) }
            }
        }
    }

    fun updateSelectionState(
        entries: List<BarEntry>,
        selectedEntry: BarEntry?,
        style: ChartStyle,
        config: AnimationConfig,
        scope: CoroutineScope
    ) {
        entries.forEach { entry ->
            val animatable = selectionAlphaAnimatables[entry.id] ?: return@forEach
            val isSelected = (selectedEntry?.id == entry.id)
            val targetAlpha =
                if (selectedEntry != null && !isSelected) style.unselectedAlpha else 1f

            if (animatable.targetValue != targetAlpha) {
                scope.launch { animatable.animateTo(targetAlpha, config.selectionSpec) }
            }
        }
    }
}
