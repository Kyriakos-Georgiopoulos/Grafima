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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Overrides the platform's reduce-motion setting for everything below it.
 *
 * Charts normally read the OS preference. Provide `true` or `false` here to
 * force the behaviour instead — useful for previews, screenshot tests, and
 * hosts that expose their own "reduce animations" toggle:
 *
 * ```
 * CompositionLocalProvider(LocalReduceMotion provides true) {
 *     BarChart(dataSet = data)
 * }
 * ```
 *
 * `null` (the default) defers to the platform.
 */
val LocalReduceMotion = staticCompositionLocalOf<Boolean?> { null }

/**
 * Whether the user has asked the platform to reduce or disable animations
 * (Android: animator duration scale set to 0; iOS: Reduce Motion enabled).
 *
 * Read once when the chart enters composition; changing the OS setting takes
 * effect the next time the chart is composed.
 */
@Composable
internal expect fun rememberReduceMotion(): Boolean

/**
 * The reduce-motion value charts actually use: [LocalReduceMotion] when a host
 * has provided one, otherwise the platform setting.
 *
 * Charts collapse their animation specs to [androidx.compose.animation.core.snap]
 * when this is true.
 */
@Composable
internal fun rememberEffectiveReduceMotion(): Boolean =
    LocalReduceMotion.current ?: rememberReduceMotion()
