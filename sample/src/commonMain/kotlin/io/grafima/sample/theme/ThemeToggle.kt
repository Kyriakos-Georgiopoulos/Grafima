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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun that becomes a moon.
 *
 * The moon is the same circle with a second one punched out of it using
 * [BlendMode.DstOut], so the shape morphs by moving that cut-out rather than
 * cross-fading two icons.
 */
@Composable
fun ThemeToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDemoColors.current
    val reveal = LocalThemeReveal.current
    val progress by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        // Spring rather than tween: the overshoot gives the morph a bit of life.
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "themeToggle"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .onGloballyPositioned { reveal.reportOrigin(it.boundsInRoot().center) }
            .clip(CircleShape)
            .background(colors.surfaceMuted)
            // Publishes the on/off state, so a screen reader says which way the
            // switch currently sits and not just what tapping will do.
            .toggleable(
                value = isDark,
                role = Role.Switch,
                onValueChange = { onToggle() }
            )
            .semantics {
                contentDescription = "Dark theme"
                stateDescription = if (isDark) "On" else "Off"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(22.dp)
                // Turns through a quarter and settles at zero, which is where the
                // cut-out sits on the right and the moon's curve faces left. The
                // sun's eight rays are 45 degrees apart, so the same turn is
                // invisible on that end.
                .rotate((1f - progress) * -90f)
        ) {
            val c = size.minDimension / 2f
            val center = Offset(c, c)
            val p = progress.coerceIn(0f, 1f)

            val rayPhase = (1f - p / 0.6f).coerceIn(0f, 1f)
            if (rayPhase > 0f) {
                val bodyR = c * 0.55f
                repeat(8) { i ->
                    val angle = (i * 45f) * (kotlin.math.PI / 180f).toFloat()
                    val dx = cos(angle)
                    val dy = sin(angle)
                    val inner = bodyR * 1.5f
                    val outer = inner + (c - inner) * rayPhase
                    drawLine(
                        color = colors.onSurface.copy(alpha = rayPhase),
                        start = Offset(center.x + dx * inner, center.y + dy * inner),
                        end = Offset(center.x + dx * outer, center.y + dy * outer),
                        strokeWidth = size.minDimension * 0.085f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // The disc swells as the rays go, so it reads as one shape becoming
            // another rather than two icons swapping.
            val bodyRadius = c * (0.55f + 0.17f * p)

            drawContext.canvas.saveLayer(
                androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                androidx.compose.ui.graphics.Paint()
            )
            drawCircle(color = colors.onSurface, radius = bodyRadius, center = center, style = Fill)
            if (p > 0f) {
                // The cut-out slides in from outside the disc, so early frames
                // show a full sun rather than a chunk already missing.
                val bitePhase = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
                drawCircle(
                    color = Color.Black,
                    radius = bodyRadius * 0.9f,
                    center = Offset(
                        x = center.x + bodyRadius * (1.9f - 1.24f * bitePhase),
                        y = center.y
                    ),
                    blendMode = BlendMode.DstOut
                )
            }
            drawContext.canvas.restore()
        }
    }
}
