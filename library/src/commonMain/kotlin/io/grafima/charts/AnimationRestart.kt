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
import androidx.compose.animation.core.AnimationVector1D

/**
 * Whether [target] still has to be animated to. `targetValue` alone is not enough:
 * a cancelled animation keeps the target it was aiming at, so an item left part-way
 * by a data change that cancelled its scope would never be driven the rest of the
 * way. Every chart animates inside a scope keyed on its dataset, so every chart
 * needs this.
 */
internal fun Animatable<Float, AnimationVector1D>.needsAnimatingTo(target: Float): Boolean =
    targetValue != target || (!isRunning && value != target)
