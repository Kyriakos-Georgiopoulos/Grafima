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

package io.grafima.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.grafima.sample.theme.LocalIsWideLayout

private val ControlsWidth = 208.dp

/**
 * Stacks the chart above its controls, or stands them side by side once there
 * is width for it.
 *
 * A phone in landscape has roughly 400dp of height. Stacked, the fixed chrome
 * alone spends more than that and the chart is measured to nothing — so the
 * wide layout moves the controls into a column beside the chart, which leaves
 * the full height for the chart itself.
 *
 * [header] is whatever sits above the chart when stacked; when wide it moves to
 * the top of the controls column.
 */
@Composable
fun DemoScreenScaffold(
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    chart: @Composable () -> Unit
) {
    if (LocalIsWideLayout.current) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { chart() }

            Column(
                modifier = Modifier
                    .width(ControlsWidth)
                    .fillMaxHeight()
                    // The gauge stacks six buttons; on a short landscape phone
                    // that overruns the height.
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                header?.invoke()
                controls()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (header != null) {
                header()
                Spacer(Modifier.height(20.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { chart() }
            Spacer(Modifier.height(20.dp))
            controls()
        }
    }
}

/**
 * Lays buttons out in a row under the chart, or stacked beside it when wide.
 *
 * [content] receives the modifier its buttons should carry — `weight` in the
 * row, `fillMaxWidth` in the column. Passing it down is what lets one block of
 * buttons serve both, since `weight` is only callable inside a `RowScope`.
 */
@Composable
fun DemoControls(
    modifier: Modifier = Modifier,
    content: @Composable (buttonModifier: Modifier) -> Unit
) {
    if (LocalIsWideLayout.current) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content(Modifier.weight(1f))
        }
    }
}
