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
internal class Exiting<T>(
    val item: T,
    val index: Int,
    /** Id of the item it followed when it left, or null if it led the list. */
    val predecessorId: String? = null,
    /** Id of the item it preceded, or null if it ended the list. */
    val successorId: String? = null
)

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

    /**
     * Set when a dataset is replaced outright rather than edited. The departing
     * items then have no slot in the new axis, so they are dropped from the draw
     * order instead of being threaded through a chart they share nothing with.
     */
    private var replaced: Boolean = false

    private val unchanged = ExitSync<T>(emptyList(), emptyList())

    /** Call once per composition, before touching per-item animatables. */
    fun sync(current: List<T>): ExitSync<T> {
        if (lastItems === current) return unchanged

        val currentIds = current.mapTo(mutableSetOf(), idOf)
        val exitingIds = exiting.mapTo(mutableSetOf()) { idOf(it.item) }

        val departed = lastItems.withIndex()
            .filter { (_, item) -> idOf(item) !in currentIds && idOf(item) !in exitingIds }
            .map { (index, item) ->
                Exiting(
                    item = item,
                    index = index,
                    predecessorId = lastItems.getOrNull(index - 1)?.let(idOf),
                    successorId = lastItems.getOrNull(index + 1)?.let(idOf)
                )
            }
        val returned = exiting.filter { idOf(it.item) in currentIds }
        // Everything the old dataset held is leaving and something else took its
        // place. An empty dataset is not a replacement: the departing items are all
        // that is left to draw, and they still animate out.
        replaced = current.isNotEmpty() &&
            lastItems.isNotEmpty() &&
            lastItems.none { idOf(it) in currentIds }

        if (departed.isNotEmpty() || returned.isNotEmpty()) {
            exiting = exiting - returned.toSet() + departed
        }
        lastItems = current
        return ExitSync(departed, returned)
    }

    fun forget(item: Exiting<T>) {
        val goneId = idOf(item.item)
        // Anything anchored on this one has to inherit its anchor, or it falls back
        // to an index from a dataset that has since changed.
        exiting = (exiting - item).map {
            when (goneId) {
                it.predecessorId -> Exiting(it.item, it.index, item.predecessorId, it.successorId)
                it.successorId -> Exiting(it.item, it.index, it.predecessorId, item.successorId)
                else -> it
            }
        }
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

        if (replaced) return current

        val merged = current.toMutableList()
        leaving.sortedBy { it.index }.forEach { merged.add(insertionPoint(merged, it), it.item) }
        return merged
    }

    /**
     * Where the departing item sat among the survivors, rather than the raw index it
     * held. An update that also adds or reorders items shifts every later index, and
     * for a bar chart that drops a departing bar inside a different category's run,
     * splitting a group that is still on screen.
     */
    /**
     * Where the departing item sat among the survivors, rather than the raw index it
     * held: an update that also adds or reorders shifts every later index, and for a
     * bar chart that drops a departing bar inside another category's run.
     *
     * The successor comes first. Anchoring after the predecessor puts the item at the
     * end of that neighbour's run, which is the wrong side when the same update
     * appended to it; going before the successor keeps it out.
     */
    private fun insertionPoint(merged: List<T>, exit: Exiting<T>): Int {
        exit.successorId?.let { successor ->
            val at = merged.indexOfFirst { idOf(it) == successor }
            if (at >= 0) return at
        }
        exit.predecessorId?.let { predecessor ->
            val at = merged.indexOfFirst { idOf(it) == predecessor }
            if (at >= 0) return at + 1
        }
        return exit.index.coerceIn(0, merged.size)
    }

    private fun leaving(current: List<T>): List<Exiting<T>> {
        if (lastItems === current) return exiting

        val currentIds = current.mapTo(mutableSetOf(), idOf)
        val pending = lastItems.withIndex()
            .filter { (_, item) -> idOf(item) !in currentIds }
            .map { (index, item) ->
                Exiting(
                    item = item,
                    index = index,
                    predecessorId = lastItems.getOrNull(index - 1)?.let(idOf),
                    successorId = lastItems.getOrNull(index + 1)?.let(idOf)
                )
            }
        if (exiting.isEmpty() && pending.isEmpty()) return emptyList()
        return (exiting + pending).distinctBy { idOf(it.item) }
    }
}
