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

/** An item dropped from a chart's dataset that is still animating out of view. */
@Stable
internal class Exiting<T>(val item: T, val index: Int)

/** [ExitTracker.sync] result: what changed against the dataset seen last call. */
internal data class ExitSync<T>(val departed: List<Exiting<T>>, val returned: List<Exiting<T>>)

/**
 * Tracks dataset items dropped from a chart's data but still animating out, so
 * the chart can keep drawing them in their old slot until the exit finishes.
 *
 * Shared across Bar, Line, Pie and Radar: a bar, a pie slice, a line series and a
 * radar series all go through the same three states — present, exiting, forgotten
 * — and the bookkeeping for that is identical. What "exiting" actually *animates*
 * differs per chart, so that stays with each chart's own animation engine, which
 * owns one tracker and drives it from `syncAnimatables`/`launchExitAnimations`.
 */
@Stable
internal class ExitTracker<T>(private val idOf: (T) -> String) {

    /**
     * Items the dataset no longer contains but the chart is still drawing.
     *
     * Snapshot state, because dropping one at the end of its exit has to bring
     * the chart back for another frame.
     */
    var exiting: List<Exiting<T>> by mutableStateOf(emptyList())
        private set

    private var lastItems: List<T> = emptyList()

    /**
     * Reconciles [current] against the dataset seen last call: items it dropped
     * join [exiting], items already exiting that reappeared rejoin the dataset.
     *
     * Call once per composition, before touching per-item animatables — the
     * returned [ExitSync.departed] and [ExitSync.returned] are a chart's cue to
     * seed or drop whatever extra animatable state its own exit needs.
     */
    fun sync(current: List<T>): ExitSync<T> {
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

    /** Drops [item] once its exit animation finishes. */
    fun forget(item: Exiting<T>) {
        exiting = exiting - item
    }

    /**
     * [current] with the departing items reinserted at the indices they held.
     * Draw order only — touch handling and the accessibility description stay on
     * the dataset itself.
     *
     * Also reads the dataset from the last [sync]: on the frame an item is
     * dropped, `sync` has not run yet (it runs from a `SideEffect`), and without
     * this the item would blink out for one frame before its exit even starts.
     */
    fun render(current: List<T>): List<T> {
        val leaving = leaving(current)
        if (leaving.isEmpty()) return current

        val merged = current.toMutableList()
        leaving.sortedBy { it.index }.forEach { merged.add(it.index.coerceIn(0, merged.size), it.item) }
        return merged
    }

    private fun leaving(current: List<T>): List<Exiting<T>> {
        val currentIds = current.mapTo(mutableSetOf(), idOf)
        val pending = lastItems.withIndex()
            .filter { (_, item) -> idOf(item) !in currentIds }
            .map { (index, item) -> Exiting(item, index) }
        if (exiting.isEmpty() && pending.isEmpty()) return emptyList()
        return (exiting + pending).distinctBy { idOf(it.item) }
    }
}
