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

import androidx.compose.runtime.Composable

/**
 * Matches the system bars to the app's theme.
 *
 * On Android that means flipping the status bar icons between dark and light,
 * and hiding the navigation bar until the user swipes for it. iOS manages its
 * own status bar and a desktop window has no system bars at all, so the
 * actuals there do nothing.
 */
@Composable
expect fun SystemBarsEffect(darkTheme: Boolean)
