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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
internal class Exiting<T>(val item: T, val index: Int)

internal data class ExitSync<T>(val departed: List<Exiting<T>>, val returned: List<Exiting<T>>)

/**
 * Keeps dataset items that are gone but still animating out, so a chart can go on
 * drawing them in the slot they held.
 */
@Stable
internal class ExitTracker<T>(private val idOf: (T) -> String) {

    /** Snapshot state: dropping the last one has to bring the chart back for a final frame. */
    var exiting: List<Exiting<T>> by mutableStateOf(emptyList())
        private set

    private var lastItems: List<T> = emptyList()

    private val unchanged = ExitSync<T>(emptyList(), emptyList())

    /** Call once per composition, before touching per-item animatables. */
    fun sync(current: List<T>): ExitSync<T> {
        if (lastItems === current) return unchanged

        val currentIds = current.mapTo(mutableSetOf(), idOf)
        val exitingIds = exiting.mapTo(mutableSetOf()) { idOf(it.item) }

        val departed = lastItems.withIndex()
            .filter { (_, item) -> idOf(item) !in currentIds && idOf(item) !in exitingIds }
            .map { (index, item) -> Exiting(item, index) }
        val returned = exiting.filter { idOf(it.item) in currentIds }

        if (departed.isNotEmpty() || returned.isNotEmpty()) {
            exiting = exiting - returned.toSet() + departed
        }
        lastItems = current
        return ExitSync(departed, returned)
    }

    fun forget(item: Exiting<T>) {
        exiting = exiting - item
    }

    /**
     * Draw order only — hit testing and the accessibility description stay on
     * [current].
     *
     * Also reads the dataset from the last [sync], which runs from a `SideEffect`
     * and so has not seen a just-dropped item yet; without that the item would
     * blink out for a frame before its exit starts.
     */
    fun render(current: List<T>): List<T> {
        val leaving = leaving(current)
        if (leaving.isEmpty()) return current

        val merged = current.toMutableList()
        leaving.sortedBy { it.index }.forEach { merged.add(it.index.coerceIn(0, merged.size), it.item) }
        return merged
    }

    private fun leaving(current: List<T>): List<Exiting<T>> {
        if (lastItems === current) return exiting

        val currentIds = current.mapTo(mutableSetOf(), idOf)
        val pending = lastItems.withIndex()
            .filter { (_, item) -> idOf(item) !in currentIds }
            .map { (index, item) -> Exiting(item, index) }
        if (exiting.isEmpty() && pending.isEmpty()) return emptyList()
        return (exiting + pending).distinctBy { idOf(it.item) }
    }
}
