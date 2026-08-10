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

import android.os.Build
import org.junit.Assume.assumeTrue

/** Skips a test that screenshots the chart on a device that cannot. */
internal fun assumePixelCapture() {
    assumeTrue(
        "captureToImage needs PixelCopy, which is API 26+",
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    )
}
