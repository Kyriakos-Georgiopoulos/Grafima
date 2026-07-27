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

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Runs [body] with an [AnimationTestHarness], cancelling any scopes it created
 * afterwards so `runTest` doesn't flag their still-active [Job]s as leaks.
 */
internal fun runEngineTest(body: suspend TestScope.(AnimationTestHarness) -> Unit): TestResult =
    runTest {
        val harness = AnimationTestHarness(this)
        try {
            body(harness)
        } finally {
            harness.dispose()
        }
    }

/**
 * Drives [androidx.compose.animation.core.Animatable] coroutines deterministically
 * inside [runTest]:
 *
 * - `delay(...)` inside the engines runs on the test scheduler's virtual time.
 * - `animateTo(...)` suspends on `withFrameNanos`, which resolves against the
 *   [BroadcastFrameClock] placed in the scope's context; [advanceFrames] pumps it
 *   in lockstep with virtual time.
 */
internal class AnimationTestHarness(private val testScope: TestScope) {
    val frameClock = BroadcastFrameClock()
    private val jobs = mutableListOf<Job>()

    /**
     * A scope equivalent to what a chart's `LaunchedEffect` provides: virtual-time
     * dispatcher, a cancellable [Job], and a frame clock.
     */
    fun launchScope(): CoroutineScope {
        val job = Job(parent = testScope.coroutineContext[Job])
        jobs += job
        return CoroutineScope(testScope.coroutineContext + job + frameClock)
    }

    /** Advances virtual time by [durationMs], emitting a frame every [frameMs]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun advanceFrames(durationMs: Long, frameMs: Long = 16L) {
        var elapsed = 0L
        while (elapsed < durationMs) {
            testScope.testScheduler.advanceTimeBy(frameMs)
            testScope.runCurrent()
            frameClock.sendFrame(testScope.testScheduler.currentTime * 1_000_000L)
            testScope.runCurrent()
            elapsed += frameMs
        }
    }

    fun dispose() {
        jobs.forEach { it.cancel() }
    }
}
