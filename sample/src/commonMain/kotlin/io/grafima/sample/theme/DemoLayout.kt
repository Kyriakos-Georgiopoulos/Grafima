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

package io.grafima.sample.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * True once there is enough width to stand the chart and its controls side by
 * side instead of stacking them.
 *
 * Static, unlike [LocalDemoColors]: this flips on rotation and never during an
 * animation, so the whole-subtree invalidation a static local causes is the
 * cheaper trade — every read skips snapshot tracking for it.
 */
val LocalIsWideLayout = staticCompositionLocalOf { false }

/**
 * Measured width rather than orientation, so a tablet gets the wide layout in
 * portrait too — where it has far more room than a phone ever does in landscape.
 */
private val WideLayoutMinWidth = 600.dp

@Composable
fun ProvideDemoLayout(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalIsWideLayout provides (maxWidth >= WideLayoutMinWidth),
            content = content
        )
    }
}
