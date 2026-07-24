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

/**
 * Whether the user has asked the platform to reduce or disable animations
 * (Android: animator duration scale set to 0; iOS: Reduce Motion enabled).
 *
 * Charts collapse their animation specs to [androidx.compose.animation.core.snap]
 * when this returns true.
 */
@Composable
internal expect fun rememberReduceMotion(): Boolean
