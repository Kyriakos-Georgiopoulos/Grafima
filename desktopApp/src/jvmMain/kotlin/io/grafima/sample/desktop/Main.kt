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

package io.grafima.sample.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.grafima.sample.GrafimaApp
import java.awt.Taskbar
import javax.imageio.ImageIO

fun main() {
    val windowIcon = useResource("grafima-icon.png") { ImageIO.read(it) }

    // Windows and Linux take the icon from the window. macOS ignores that and
    // shows a generic Java icon in the Dock until the taskbar is told directly,
    // and it masks nothing, so the Dock gets the rounded, inset variant.
    if (Taskbar.isTaskbarSupported()) {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            taskbar.iconImage = useResource("grafima-icon-macos.png") { ImageIO.read(it) }
        }
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Grafima",
            icon = BitmapPainter(windowIcon.toComposeImageBitmap()),
            state = rememberWindowState(
                size = DpSize(width = 1100.dp, height = 800.dp),
                position = WindowPosition(Alignment.Center)
            )
        ) {
            GrafimaApp()
        }
    }
}
