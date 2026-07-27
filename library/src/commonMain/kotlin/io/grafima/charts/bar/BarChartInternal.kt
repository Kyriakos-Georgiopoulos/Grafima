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
): Pair<Float, Float> {
    val totalSpacing = extent * spacingFactor.coerceIn(0f, 0.9f)
    return (extent - totalSpacing) / count to totalSpacing / (count + 1)
}

/** LTR offset of bar [index] along the layout axis, measured from [leadingInset]. */
internal fun barSlotOffset(index: Int, leadingInset: Float, thickness: Float, gap: Float): Float =
    leadingInset + gap + index * (thickness + gap)

/** Mirrors an LTR offset across [totalExtent] for RTL layouts. */
internal fun mirrorForRtl(
    ltrOffset: Float,
    totalExtent: Float,
    thickness: Float,
    isRtl: Boolean
): Float = if (isRtl) totalExtent - ltrOffset - thickness else ltrOffset

/**
 * The chart's accessibility description: summary plus one sentence per bar.
 *
 * Selection is deliberately excluded — it is exposed as a separate
 * `stateDescription`, so a screen reader announces only what changed on
 * selection instead of re-reading every bar.
 */
internal fun buildBarChartDescription(
    dataSet: BarDataSet,
    a11yConfig: A11yConfig
): String = buildString {
    append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
    dataSet.entries.forEach { append(a11yConfig.barDescriptionBuilder(it)).append(". ") }
}

@Stable
internal class ChartAnimationEngine {
    val heightAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    val selectionAlphaAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedIds = mutableSetOf<String>()

    fun syncAnimatables(entries: List<BarEntry>) {
        val currentIds = entries.mapTo(mutableSetOf()) { it.id }
        heightAnimatables.keys.removeAll { it !in currentIds }
        selectionAlphaAnimatables.keys.removeAll { it !in currentIds }
        initializedIds.removeAll { it !in currentIds }

        entries.forEach { entry ->
            heightAnimatables.getOrPut(entry.id) { Animatable(0f) }
            selectionAlphaAnimatables.getOrPut(entry.id) { Animatable(1f) }
        }
    }

    fun launchEntryAnimations(entries: List<BarEntry>, config: AnimationConfig, scope: CoroutineScope) {
        entries.forEachIndexed { index, entry ->
            val heightAnim = heightAnimatables[entry.id] ?: return@forEachIndexed
            val isInitialLoad = initializedIds.add(entry.id)

            scope.launch {
                if (isInitialLoad) {
                    delay(config.startDelayMs + (index * config.staggerDelayMs))
                    heightAnim.animateTo(entry.y, config.initialEntrySpec)
                } else if (heightAnim.targetValue != entry.y) {
                    heightAnim.animateTo(entry.y, config.morphSpec)
                }
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
