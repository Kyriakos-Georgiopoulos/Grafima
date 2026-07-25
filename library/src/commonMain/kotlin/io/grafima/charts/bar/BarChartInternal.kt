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
