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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.sample.theme.LocalDemoColors
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
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
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
            // Matches the wide column, so multiple button groups are spaced either way.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { controls() }
        }
    }
}

/**
 * A row of mutually exclusive options.
 *
 * [groupName] prefixes each option for a screen reader — "Grouping: Stacked" —
 * since several of these share a screen and the labels alone name no owner.
 */
@Composable
fun <T> DemoSegmentedControl(
    groupName: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDemoColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.Center
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.onSurface else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(value) }
                    )
                    .semantics { contentDescription = "$groupName: $label" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) colors.background else colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Grows the dataset. Disabled at the cap rather than silently doing nothing. */
@Composable
fun DemoAddButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDemoColors.current
    DatasetButton(
        text = text,
        enabled = enabled,
        onClick = onClick,
        container = colors.accent,
        content = colors.onAccent,
        modifier = modifier
    )
}

/** Shrinks the dataset, down to the floor each chart needs to stay readable. */
@Composable
fun DemoRemoveButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDemoColors.current
    DatasetButton(
        text = text,
        enabled = enabled,
        onClick = onClick,
        container = colors.accentWarm,
        content = colors.onAccentWarm,
        modifier = modifier
    )
}

@Composable
private fun DatasetButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier
) {
    val colors = LocalDemoColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.surfaceMuted,
            disabledContentColor = colors.onSurfaceMuted
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier.height(50.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
