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

package io.grafima.charts.line

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import io.grafima.charts.ExitTracker
import io.grafima.charts.Exiting
import io.grafima.charts.needsAnimatingTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Maps a data-space X value to a canvas X coordinate, mirroring for RTL.
 * Falls back to [chartLeft] when the axis range is degenerate.
 */
internal fun mapDataXToCanvas(
    dataX: Float,
    xMin: Float,
    xMax: Float,
    chartLeft: Float,
    chartRight: Float,
    isRtl: Boolean
): Float {
    val xRange = xMax - xMin
    val raw = if (xRange > 0f) {
        chartLeft + (dataX - xMin) / xRange * (chartRight - chartLeft)
    } else {
        chartLeft
    }
    return if (isRtl) chartRight - (raw - chartLeft) else raw
}

/**
 * Index of the point whose canvas X is nearest to [touchX], or -1 when there is
 * none to pick.
 *
 * With [restrictToAxis] a point whose own x lies outside [xMin]..[xMax] is not a
 * candidate. Its marks are not drawn, so selecting it would move the crosshair
 * somewhere the reader can see nothing.
 */
internal fun nearestAxisIndex(
    positions: FloatArray,
    touchX: Float,
    xMin: Float,
    xMax: Float,
    chartLeft: Float,
    chartRight: Float,
    isRtl: Boolean,
    restrictToAxis: Boolean = true
): Int {
    var nearest = -1
    var shortest = Float.MAX_VALUE
    for (i in positions.indices) {
        val x = positions[i]
        if (restrictToAxis && !isWithinAxis(x, xMin, xMax)) continue
        val distance = abs(mapDataXToCanvas(x, xMin, xMax, chartLeft, chartRight, isRtl) - touchX)
        if (distance < shortest) {
            shortest = distance
            nearest = i
        }
    }
    return nearest
}

/** [Dp.Unspecified] defers to the chart-wide value, as it does on the other charts. */
internal fun Dp.orElse(fallback: Dp): Dp = if (isSpecified) this else fallback

/**
 * Where every series stands on a shared x axis, resolved once for a dataset.
 *
 * Built instead of matching floats at lookup time: a tolerance that works for one
 * dataset is wrong for the next, since drift between two spellings of the same
 * reading and the real gap between two readings are the same size at some
 * magnitudes. Here the bound comes from the data itself, and every lookup after
 * that is an array read.
 */
internal class AxisIndex(
    /** Every x any series reaches, ascending, near-equal values merged. */
    val positions: FloatArray,
    private val perSeries: Map<String, IntArray>
) {
    /** This series' point at [position], or -1 where it has no reading. */
    fun pointIndex(seriesId: String, position: Int): Int {
        val slots = perSeries[seriesId] ?: return -1
        return if (position in slots.indices) slots[position] else -1
    }

    companion object {
        val Empty = AxisIndex(FloatArray(0), emptyMap())
    }
}

internal fun buildAxisIndex(series: List<LineSeries>): AxisIndex {
    val all = ArrayList<Float>()
    series.forEach { s -> s.points.forEach { if (it.x.isFinite()) all.add(it.x) } }
    if (all.isEmpty()) return AxisIndex.Empty
    all.sort()

    val tolerance = axisTolerance(all)
    val positions = ArrayList<Float>()
    all.forEach { x ->
        if (positions.isEmpty() || x - positions.last() > tolerance) positions.add(x)
    }
    val axis = positions.toFloatArray()

    val perSeries = LinkedHashMap<String, IntArray>(series.size)
    series.forEach { s ->
        val slots = IntArray(axis.size) { -1 }
        s.points.forEachIndexed { index, point ->
            if (!point.x.isFinite()) return@forEachIndexed
            val at = axis.positionOf(point.x, tolerance)
            // First wins, so a repeated x resolves the way selection reports it.
            if (at >= 0 && slots[at] < 0) slots[at] = index
        }
        perSeries[s.id] = slots
    }
    return AxisIndex(axis, perSeries)
}

/** Index of the axis position [x] falls on, or -1. Positions are ascending. */
private fun FloatArray.positionOf(x: Float, tolerance: Float): Int {
    var low = 0
    var high = size - 1
    while (low <= high) {
        val mid = (low + high) ushr 1
        val at = this[mid]
        when {
            abs(at - x) <= tolerance -> return mid
            at < x -> low = mid + 1
            else -> high = mid - 1
        }
    }
    return -1
}

/**
 * How close two x values must be to be one position.
 *
 * A quarter of the smallest real gap, so two readings never merge, capped at a
 * hundred-thousandth of the span, so two spellings of one reading do. Gaps under
 * that cap are read as drift rather than as spacing — the same span-relative
 * reasoning [isWithinAxis] uses, and the reason a fixed count of ulps cannot
 * serve both a month index and an epoch millisecond.
 */
private fun axisTolerance(sorted: List<Float>): Float {
    if (sorted.size < 2) return 0f
    val span = sorted.last() - sorted.first()
    if (span <= 0f) return 0f
    val drift = span * 1e-5f
    var smallestReal = Float.MAX_VALUE
    for (i in 1 until sorted.size) {
        val gap = sorted[i] - sorted[i - 1]
        if (gap > drift && gap < smallestReal) smallestReal = gap
    }
    return if (smallestReal == Float.MAX_VALUE) drift else min(drift, smallestReal / 4f)
}

/**
 * Rounds axis step to a "nice" number (1, 2, 5 * 10^n) and generates evenly
 * spaced tick values that fully contain the data range.
 */
internal fun computeNiceAxisTicks(dataMin: Float, dataMax: Float, tickCount: Int): List<Float> {
    if (tickCount <= 0 || dataMax <= dataMin) return listOf(dataMin, dataMax)
    val rawStep = (dataMax - dataMin) / tickCount
    if (rawStep <= 0f) return listOf(dataMin, dataMax)
    val magnitude = 10f.pow(floor(log10(rawStep)))
    val norm = rawStep / magnitude
    val niceStep = when {
        norm <= 1.0f -> magnitude
        norm <= 2.0f -> 2f * magnitude
        norm <= 5.0f -> 5f * magnitude
        else -> 10f * magnitude
    }
    val niceMin = floor(dataMin / niceStep) * niceStep
    val niceMax = ceil(dataMax / niceStep) * niceStep
    val count = ((niceMax - niceMin) / niceStep + 0.5f).toInt()
    return (0..count).map { niceMin + it * niceStep }
}

/**
 * Axis tick values, honouring a pinned [pinnedMin] or [pinnedMax].
 *
 * With neither pinned this is [computeNiceAxisTicks]. With either pinned the range
 * is divided into [tickCount] equal steps instead: nice-number rounding extends the
 * range outwards to reach a round step, which would move the edge the caller pinned.
 *
 * A pin that cannot produce a usable range — inverted, empty, or not finite — is
 * ignored in favour of the automatic range. Honouring it would collapse every point
 * onto one line, which reads as a broken chart rather than as bad input.
 */
internal fun computeAxisTicks(
    dataMin: Float,
    dataMax: Float,
    tickCount: Int,
    pinnedMin: Float?,
    pinnedMax: Float?
): List<Float> {
    if (pinnedMin == null && pinnedMax == null) {
        return computeNiceAxisTicks(dataMin, dataMax, tickCount)
    }
    val lo = pinnedMin ?: dataMin
    val hi = pinnedMax ?: dataMax
    if (!lo.isFinite() || !hi.isFinite() || hi <= lo) {
        return computeNiceAxisTicks(dataMin, dataMax, tickCount)
    }
    if (tickCount <= 0) return listOf(lo, hi)
    val step = (hi - lo) / tickCount
    // The last tick is [hi] itself: lo + tickCount * step lands an ulp short for
    // counts that do not divide the span, and a bound pinned to 31 labels as 30.
    return (0..tickCount).map { if (it == tickCount) hi else lo + it * step }
}

/**
 * Axis bounds honouring a pinned [pinnedMin] or [pinnedMax], falling back to the data
 * extent when the pin cannot produce a usable range — inverted, empty, or not finite.
 *
 * A span of zero or less divides every point onto one edge, which reads as a broken
 * chart rather than as bad input. [computeAxisTicks] rejects the same shapes for y.
 */
internal fun resolveAxisBounds(
    dataMin: Float,
    dataMax: Float,
    pinnedMin: Float?,
    pinnedMax: Float?
): ClosedFloatingPointRange<Float> {
    if (pinnedMin == null && pinnedMax == null) return dataMin..dataMax
    val lo = pinnedMin ?: dataMin
    val hi = pinnedMax ?: dataMax
    if (!lo.isFinite() || !hi.isFinite() || hi <= lo) return dataMin..dataMax
    return lo..hi
}

/**
 * One axis title trimmed to the space it has, kept across frames.
 *
 * Only the newest is worth holding: the width changes when the chart is resized,
 * and a cache keyed by width would grow for every pixel a window drag passes
 * through.
 */
internal class FittedTitle {
    var width: Int = -1
    var layout: TextLayoutResult? = null
}

/**
 * The plot rectangle, once labels and axis titles have taken their room.
 *
 * Mutable and reused: [computePlotInsets] runs on every frame, and a fresh object
 * per frame is a per-frame allocation the draw pass does not otherwise make.
 */
internal data class PlotInsets(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
) {
    /** How much of the asked-for dot clearance actually fitted, per direction. */
    var sideClearance: Float = 0f
    var stackClearance: Float = 0f
}

/**
 * Carves the plot rectangle out of the canvas, writing into [into].
 *
 * Y labels and the y title sit on the left, and mirror to the right in RTL. A y
 * title is drawn rotated, so the width it claims is [yTitleHeight] — its measured
 * height — not its length.
 *
 * A title costs a [gap] on each side — one clearing its labels, one clearing the
 * canvas edge — and contributes nothing when its size is zero, which keeps the
 * rectangle identical to the untitled case.
 */
internal fun computePlotInsets(
    into: PlotInsets,
    width: Float,
    height: Float,
    gap: Float,
    yLabelWidth: Float,
    xLabelHeight: Float,
    yTitleHeight: Float,
    xTitleHeight: Float,
    isRtl: Boolean,
    dotClearance: Float = 0f
): PlotInsets {
    // Twice, because a title needs clearing from its labels as well as from the edge.
    val yBand = yLabelWidth + if (yTitleHeight > 0f) yTitleHeight + gap * 2f else 0f
    val xBand = xLabelHeight + if (xTitleHeight > 0f) xTitleHeight + gap * 2f else 0f
    // Dots are drawn outside the clip, so a point sitting on a bound needs its radius
    // between the plot and whatever is next to it — the labels where there are labels,
    // the composable's own edge where there are not. The clearance is taken from both
    // sides and the gap from both again, so a third of what is left over is the most
    // that can be given away and still leave a plot: a radius wider than the chart
    // crowds it, never inverts it.
    val clearance = if (dotClearance.isFinite()) dotClearance.coerceAtLeast(0f) else 0f
    val sideRoom = ((width - yBand - gap * 2f) / 3f).coerceAtLeast(0f)
    val stackRoom = ((height - xBand - gap * 2f) / 3f).coerceAtLeast(0f)
    val side = clearance.coerceAtMost(sideRoom)
    val stack = clearance.coerceAtMost(stackRoom)
    into.sideClearance = side
    into.stackClearance = stack
    into.left = gap + side + if (isRtl) 0f else yBand
    into.top = gap + stack
    into.right = width - gap - side - if (isRtl) yBand else 0f
    into.bottom = height - gap - stack - xBand
    return into
}

/**
 * The boxes labels have already taken, so the next one can find a free spot.
 *
 * One set for the whole chart: two series crossing put their labels in the same
 * place. Reused across frames — [reset] before each pass.
 */
internal class LabelBoxes(capacity: Int) {
    private val edges = FloatArray(capacity * 4)
    private var count = 0

    fun reset() {
        count = 0
    }

    /** Takes the box when nothing drawn already overlaps it. */
    fun takeIfFree(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        for (i in 0 until count) {
            val at = i * 4
            val overlaps = left < edges[at + 2] &&
                right > edges[at] &&
                top < edges[at + 3] &&
                bottom > edges[at + 1]
            if (overlaps) return false
        }
        val at = count * 4
        if (at + 4 > edges.size) return false
        edges[at] = left
        edges[at + 1] = top
        edges[at + 2] = right
        edges[at + 3] = bottom
        count++
        return true
    }
}

/**
 * Where a value label's left edge sits: centred on its point, but never past the
 * edge of the plot, where half of it would be cut off.
 */
internal fun valueLabelLeft(
    pointX: Float,
    labelWidth: Float,
    chartLeft: Float,
    chartRight: Float
): Float = (pointX - labelWidth / 2f).coerceIn(chartLeft, max(chartLeft, chartRight - labelWidth))

/**
 * Where a vertical reference line's label sits: trailing the line, or the other side
 * when there is no room, clamped into the plot either way.
 */
internal fun referenceLabelLeft(
    lineX: Float,
    labelWidth: Float,
    gap: Float,
    chartLeft: Float,
    chartRight: Float,
    isRtl: Boolean
): Float {
    val before = lineX - gap - labelWidth
    val after = lineX + gap
    val preferred = if (isRtl) before else after
    val fits = if (isRtl) preferred >= chartLeft else preferred + labelWidth <= chartRight
    val chosen = if (fits) preferred else if (isRtl) after else before
    return chosen.coerceIn(chartLeft, max(chartLeft, chartRight - labelWidth))
}

/**
 * Where the label of a horizontal reference line sits: at the end the axis runs
 * towards, clamped into the plot.
 */
internal fun referenceLabelEndLeft(
    labelWidth: Float,
    gap: Float,
    chartLeft: Float,
    chartRight: Float,
    isRtl: Boolean
): Float {
    val end = if (isRtl) chartLeft + gap else chartRight - gap - labelWidth
    return end.coerceIn(chartLeft, max(chartLeft, chartRight - labelWidth))
}

/**
 * Draws a reference line's label if nothing else has taken the room, and claims it.
 * One wider than the plot is dropped rather than clipped.
 */
internal fun DrawScope.drawReferenceLabel(
    layout: TextLayoutResult,
    left: Float,
    top: Float,
    boxes: LabelBoxes,
    gap: Float,
    plotWidth: Float
) {
    if (layout.size.width > plotWidth) return
    val free = boxes.takeIfFree(
        left = left - gap,
        top = top,
        right = left + layout.size.width + gap,
        bottom = top + layout.size.height
    )
    if (free) drawText(textLayoutResult = layout, topLeft = Offset(x = left, y = top))
}

/**
 * The point to visit at [step], walking left to right across the screen.
 *
 * Data order runs the other way in RTL, and the label kept out of a colliding pair
 * is whichever is reached first — so without this the survivor would swap sides
 * with the layout direction.
 */
internal fun screenOrderIndex(step: Int, count: Int, isRtl: Boolean): Int =
    if (isRtl) count - 1 - step else step

/**
 * Which side of its point a value label reads better on.
 *
 * The label avoids other labels, but the line itself runs on through — printed
 * above a point in a valley it lands on the curve rising away on both sides. The
 * open side is the one the neighbours lean away from.
 *
 * Screen coordinates: a smaller y is higher up. An endpoint passes its own y for
 * the neighbour it does not have, leaving the one it does have to decide.
 */
internal fun valueLabelPrefersBelow(pointY: Float, previousY: Float, nextY: Float): Boolean =
    (previousY + nextY) / 2f < pointY

/**
 * Where a value label's top edge sits: [offset] clear of its point on the side it
 * reads better, or the other side when the plot has no room there.
 */
internal fun valueLabelTop(
    pointY: Float,
    labelHeight: Float,
    offset: Float,
    chartTop: Float,
    chartBottom: Float,
    preferBelow: Boolean
): Float {
    val above = pointY - offset - labelHeight
    val below = pointY + offset
    return if (preferBelow) {
        if (below + labelHeight <= chartBottom) below else above
    } else {
        if (above >= chartTop) above else below
    }
}

/**
 * Whether [ReferenceLine] falls on the axis it is fixed to, and so has a place on
 * the plot at all.
 *
 * A value off the axis is not drawn at the nearest edge: the line would then mark
 * a threshold nowhere near the one asked for.
 */
internal fun ReferenceLine.isOnAxis(xMin: Float, xMax: Float, yMin: Float, yMax: Float): Boolean {
    if (!value.isFinite()) return false
    return when (axis) {
        ReferenceLineAxis.X -> isWithinAxis(value, xMin, xMax)
        ReferenceLineAxis.Y -> isWithinAxis(value, yMin, yMax)
    }
}

/**
 * Appends [text] to a spoken description as a fresh sentence.
 *
 * A builder need not end in a full stop, and without one a screen reader runs the
 * next phrase into its last word unbroken. Only a letter or digit is unfinished —
 * any script's punctuation is not.
 */
internal fun StringBuilder.appendSentence(text: String) {
    if (text.isBlank()) return
    setLength(trimEnd().length)
    if (isNotEmpty()) {
        if (last().isLetterOrDigit()) append('.')
        append(' ')
    }
    append(text)
}

/**
 * Whether [value] falls inside the axis.
 *
 * Tested against the value rather than its animated screen position, so a morph
 * spring overshooting its target cannot blink a mark out. The tolerance covers a
 * bound that arrived by division — the last tick of a pinned range can land an ulp
 * short of the number the caller pinned, and the point sitting on it must still draw.
 */
internal fun isWithinAxis(value: Float, axisMin: Float, axisMax: Float): Boolean {
    val tolerance = (axisMax - axisMin) * 1e-5f
    return value >= axisMin - tolerance && value <= axisMax + tolerance
}

/**
 * Fritsch-Carlson monotone cubic tangent computation. Operates entirely on
 * pre-allocated [FloatArray] buffers with zero heap allocation.
 *
 * [deltas] is a scratch buffer of size >= n-1 that avoids a per-frame allocation.
 * The result is written into [tangents] in-place.
 */
internal fun computeMonotoneTangents(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, deltas: FloatArray, n: Int
) {
    if (n < 2) return
    for (i in 0 until n - 1) {
        val dx = xs[i + 1] - xs[i]
        deltas[i] = if (dx == 0f) 0f else (ys[i + 1] - ys[i]) / dx
    }
    tangents[0] = deltas[0]
    tangents[n - 1] = deltas[n - 2]
    for (i in 1 until n - 1) {
        tangents[i] = if (deltas[i - 1] * deltas[i] <= 0f) 0f
        else (deltas[i - 1] + deltas[i]) / 2f
    }
    // Monotonicity enforcement: clamp tangent magnitudes so alpha^2 + beta^2 <= 9
    for (i in 0 until n - 1) {
        if (deltas[i] == 0f) {
            tangents[i] = 0f; tangents[i + 1] = 0f
        } else {
            val a = tangents[i] / deltas[i]
            val b = tangents[i + 1] / deltas[i]
            val s = a * a + b * b
            if (s > 9f) {
                val tau = 3f / sqrt(s)
                tangents[i] = tau * a * deltas[i]
                tangents[i + 1] = tau * b * deltas[i]
            }
        }
    }
}

/** Builds a line path (stroke only) from pre-computed screen-space buffers. */
internal fun Path.buildCurve(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, n: Int,
    curveType: LineCurveType
) {
    if (n < 1) return
    moveTo(xs[0], ys[0])
    if (n == 1) return
    when (curveType) {
        LineCurveType.Linear -> for (i in 1 until n) lineTo(xs[i], ys[i])
        LineCurveType.MonotoneCubic -> for (i in 0 until n - 1) {
            val dx = (xs[i + 1] - xs[i]) / 3f
            cubicTo(
                xs[i] + dx, ys[i] + tangents[i] * dx,
                xs[i + 1] - dx, ys[i + 1] - tangents[i + 1] * dx,
                xs[i + 1], ys[i + 1]
            )
        }
    }
}

/** Builds a closed area path: curve on top, straight bottom edge at [chartBottom]. */
internal fun Path.buildArea(
    xs: FloatArray, ys: FloatArray, tangents: FloatArray, n: Int,
    chartBottom: Float, curveType: LineCurveType
) {
    if (n < 1) return
    buildCurve(xs = xs, ys = ys, tangents = tangents, n = n, curveType = curveType)
    lineTo(xs[n - 1], chartBottom)
    lineTo(xs[0], chartBottom)
    close()
}

/**
 * Manages per-point Y-value [Animatable] instances keyed as `"seriesId::pointIndex"`.
 *
 * Lifecycle (same pattern as PieChart/RadarChart):
 * - [syncAnimatables]: synchronous map housekeeping via [SideEffect], ensures
 *   animatable instances exist for current data before the first draw.
 * - [launchEntryAnimations]: staggered async animations via [LaunchedEffect] scope.
 *   Old coroutines cancel automatically when data changes.
 */
@Stable
internal class LineChartAnimationEngine {
    internal val yAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedKeys = mutableSetOf<String>()

    private val exitTracker = ExitTracker<LineSeries> { it.id }

    /** Series the dataset no longer contains but the chart is still drawing. */
    val exiting: List<Exiting<LineSeries>> get() = exitTracker.exiting

    /** Ensures animatables exist for all current points. Removes stale entries. */
    fun syncAnimatables(series: List<LineSeries>) {
        exitTracker.sync(series)

        val activeKeys = mutableSetOf<String>()
        renderSeries(series).forEach { s ->
            s.points.forEachIndexed { i, _ ->
                val key = "${s.id}::$i"
                activeKeys.add(key)
                yAnimatables.getOrPut(key) { Animatable(0f) }
            }
        }
        yAnimatables.keys.removeAll { it !in activeKeys }
        initializedKeys.removeAll { it !in activeKeys }
    }

    /** Draws a departing series back to the baseline: its entry animation in reverse. */
    fun launchExitAnimations(
        config: LineAnimationConfig,
        yBaseline: Float,
        scope: CoroutineScope
    ) {
        exitTracker.exiting.forEach { leaving ->
            val series = leaving.item
            val keys = series.points.indices.map { i -> "${series.id}::$i" }
            val anims = keys.mapNotNull { yAnimatables[it] }
            if (anims.isEmpty()) return@forEach
            if (anims.any { it.isRunning }) return@forEach

            // A cancelled coroutine can leave it at rest but still listed.
            if (anims.all { it.value == yBaseline }) {
                forget(leaving, keys)
                return@forEach
            }

            scope.launch {
                anims.map { anim ->
                    launch { anim.animateTo(yBaseline, config.entrySpec) }
                }.joinAll()
                forget(leaving, keys)
            }
        }
    }

    private fun forget(leaving: Exiting<LineSeries>, keys: List<String>) {
        keys.forEach { key ->
            yAnimatables.remove(key)
            initializedKeys.remove(key)
        }
        exitTracker.forget(leaving)
    }

    /** Draw order only: the crosshair and a11y stay on the dataset. */
    fun renderSeries(series: List<LineSeries>): List<LineSeries> = exitTracker.render(series)

    /**
     * Launches staggered entry or morph animations. New points animate from
     * [yBaseline]; existing points spring to their updated target.
     */
    fun launchEntryAnimations(
        series: List<LineSeries>,
        config: LineAnimationConfig,
        yBaseline: Float,
        scope: CoroutineScope
    ) {
        series.forEachIndexed { si, s ->
            s.points.forEachIndexed { pi, point ->
                val key = "${s.id}::$pi"
                val anim = yAnimatables[key] ?: return@forEachIndexed
                val isInitial = initializedKeys.add(key)
                scope.launch {
                    if (isInitial) {
                        anim.snapTo(yBaseline)
                        delay(config.startDelayMs + si * config.seriesStaggerMs + pi * config.staggerMs)
                        anim.animateTo(point.y, config.entrySpec)
                    } else if (anim.needsAnimatingTo(point.y)) {
                        anim.animateTo(point.y, config.morphSpec)
                    }
                }
            }
        }
    }
}
