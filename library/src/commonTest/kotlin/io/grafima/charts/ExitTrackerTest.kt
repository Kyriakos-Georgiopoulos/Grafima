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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ExitTrackerTest {

    private data class Item(val id: String)

    private fun items(vararg ids: String) = ids.map { Item(it) }

    @Test
    fun `sync reports nothing when the dataset is unchanged`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b"))

        val (departed, returned) = tracker.sync(items("a", "b"))

        assertEquals(emptyList(), departed)
        assertEquals(emptyList(), returned)
        assertEquals(emptyList(), tracker.exiting)
    }

    @Test
    fun `sync hands a dropped item to departed and files it under exiting`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b", "c"))

        val (departed, returned) = tracker.sync(items("a", "c"))

        assertEquals(listOf("b"), departed.map { it.item.id })
        assertEquals(emptyList(), returned)
        assertEquals(listOf("b"), tracker.exiting.map { it.item.id })
        // "b" held index 1 in the dataset it was dropped from.
        assertEquals(1, tracker.exiting.single().index)
    }

    @Test
    fun `an item still exiting is not reported as departed again`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b"))
        tracker.sync(items("a"))
        val exiting = tracker.exiting.single()

        val (departed, returned) = tracker.sync(items("a"))

        assertEquals(emptyList(), departed)
        assertEquals(emptyList(), returned)
        // Same instance survives, so a chart's per-item animatables stay attached.
        assertSame(exiting, tracker.exiting.single())
    }

    @Test
    fun `an item that reappears mid-exit is reported as returned and dropped from exiting`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b"))
        tracker.sync(items("a"))
        assertEquals(listOf("b"), tracker.exiting.map { it.item.id })

        val (departed, returned) = tracker.sync(items("a", "b"))

        assertEquals(emptyList(), departed)
        assertEquals(listOf("b"), returned.map { it.item.id })
        assertEquals(emptyList(), tracker.exiting)
    }

    @Test
    fun `forget drops only the given item from exiting`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b", "c"))
        tracker.sync(items("a"))
        val toForget = tracker.exiting.first { it.item.id == "b" }

        tracker.forget(toForget)

        assertEquals(listOf("c"), tracker.exiting.map { it.item.id })
    }

    @Test
    fun `render returns the dataset unchanged when nothing is leaving`() {
        val tracker = ExitTracker<Item> { it.id }
        val current = items("a", "b")
        tracker.sync(current)

        // Same reference, not just an equal one: a chart keys `remember` off this.
        assertSame(current, tracker.render(current))
    }

    @Test
    fun `render reinserts an exiting item at the index it held`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b", "c"))
        tracker.sync(items("a", "c"))

        assertEquals(listOf("a", "b", "c"), tracker.render(items("a", "c")).map { it.id })
    }

    @Test
    fun `render reinserts multiple exiting items at the indices they each held`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b", "c", "d"))
        tracker.sync(items("a", "d"))

        assertEquals(listOf("a", "b", "c", "d"), tracker.render(items("a", "d")).map { it.id })
    }

    @Test
    fun `render reports a dropped item before sync has run`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b"))

        // A chart's composition calls render() before the SideEffect that runs
        // sync() for this frame; without this, the item would blink out for one
        // frame before its exit animation even starts.
        assertEquals(emptyList(), tracker.exiting)
        assertEquals(listOf("a", "b"), tracker.render(items("a")).map { it.id })
    }

    @Test
    fun `every item leaving at once is replayed in the order it held`() {
        val tracker = ExitTracker<Item> { it.id }
        tracker.sync(items("a", "b", "c"))
        // Every item departs at once, so each is anchored on the one before it.
        tracker.sync(emptyList())

        assertEquals(listOf("a", "b", "c"), tracker.render(emptyList()).map { it.id })
    }

    @Test
    fun `a departing item stays beside the neighbour it followed`() {
        val tracker = ExitTracker<String> { it }
        val before = listOf("q1-a", "q1-b", "q2-a", "q2-b")
        tracker.sync(before)

        // A category prepended in the same update that removes q2-a: every later
        // index shifts, so the raw index would drop q2-a inside Q1's run.
        val after = listOf("q0-a", "q0-b", "q1-a", "q1-b", "q2-b")
        tracker.sync(after)

        assertEquals(
            listOf("q0-a", "q0-b", "q1-a", "q1-b", "q2-a", "q2-b"),
            tracker.render(after)
        )
    }

    @Test
    fun `a departing item keeps its place after its predecessor is forgotten`() {
        val tracker = ExitTracker<String> { it }
        tracker.sync(listOf("a", "b", "c", "d"))
        // b and c leave together, so c is anchored on b.
        tracker.sync(listOf("a", "d"))

        // b's exit finishes first and it is dropped, leaving c anchored on an id
        // that is now in no list at all.
        tracker.forget(tracker.exiting.single { it.item == "b" })

        assertEquals(listOf("a", "c", "d"), tracker.render(listOf("a", "d")))
    }

    @Test
    fun `a departing item stays out of a run its neighbour grew into`() {
        val tracker = ExitTracker<String> { it }
        tracker.sync(listOf("a1", "a2", "b1", "b2"))
        // The same update drops b1 and appends a3 to the run before it. Anchoring
        // after a2 would file b1 inside a's run and split the group on screen.
        val after = listOf("a1", "a2", "a3", "b2")
        tracker.sync(after)

        assertEquals(listOf("a1", "a2", "a3", "b1", "b2"), tracker.render(after))
    }
}
