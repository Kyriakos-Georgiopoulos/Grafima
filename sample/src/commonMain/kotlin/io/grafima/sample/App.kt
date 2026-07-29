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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.grafima.sample.theme.DemoTheme
import io.grafima.sample.theme.LocalThemeReveal
import io.grafima.sample.theme.ProvideDemoLayout
import io.grafima.sample.theme.SystemBarsEffect
import io.grafima.sample.theme.ThemeRevealBackground
import io.grafima.sample.theme.ThemeRevealState

@Composable
fun GrafimaApp() {
    // Follows the system on first launch, then whatever the user picked, and
    // survives rotation so the theme doesn't snap back mid-demo.
    val systemDark = isSystemInDarkTheme()
    var darkTheme by rememberSaveable { mutableStateOf(systemDark) }

    SystemBarsEffect(darkTheme = darkTheme)

    val reveal = remember { ThemeRevealState() }

    CompositionLocalProvider(LocalThemeReveal provides reveal) {
        DemoTheme(darkTheme = darkTheme) {
            // Material only shows through in ripples and other component
            // defaults here, but it still has to agree with the palette.
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ThemeRevealBackground(darkTheme = darkTheme)
                    ProvideDemoLayout {
                        ChartsDemoScreen(
                            darkTheme = darkTheme,
                            onToggleTheme = { darkTheme = !darkTheme }
                        )
                    }
                }
            }
        }
    }
}
