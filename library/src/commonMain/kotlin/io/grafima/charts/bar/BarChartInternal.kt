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

package io.grafima.charts.bar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import io.grafima.charts.ExitTracker
import io.grafima.charts.Exiting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** One position on the category axis, holding the bars drawn at it. */
internal class BarCategory(val xLabel: String, val entries: List<BarEntry>)

/**
 * Whether [entry] continues the category that [previous] belongs to. Both the axis
 * and the draw pass group by this, and must not diverge.
 *
 * Matching only the immediate predecessor keeps two runs that reuse a label apart.
 */
internal fun joinsCategory(previous: BarEntry?, entry: BarEntry): Boolean =
    entry.seriesId != null &&
        previous?.seriesId != null &&
        previous.xLabel == entry.xLabel

/** Splits entries into the categories drawn along the axis. */
internal fun groupBarEntries(entries: List<BarEntry>): List<BarCategory> {
    val grouped = mutableListOf<MutableList<BarEntry>>()
    entries.forEach { entry ->
        val open = grouped.lastOrNull()
        if (joinsCategory(open?.last(), entry)) open?.add(entry)
        else grouped.add(mutableListOf(entry))
    }
    return grouped.map { BarCategory(it.first().xLabel, it) }
}

/** Distinct series in list order. Empty when nothing carries one. */
internal fun seriesOrder(entries: List<BarEntry>): List<String> =
    entries.mapNotNull { it.seriesId }.distinct()

/**
 * Y-axis maximum: 20% headroom over the tallest bar, rounded up to a tidy
 * step (5, 10, or 50 depending on magnitude) so axis labels stay round numbers.
 */
internal fun computeBarAxisMax(
    entries: List<BarEntry>,
    mode: BarGroupMode = BarGroupMode.Grouped
): Float = axisMaxForLayout(entries, computeBarGroupLayout(entries), mode)

/**
 * The same maximum over a layout the caller already has, which is how the chart
 * itself asks: it groups its render list once and both the bars and the axis read
 * that one partition.
 */
internal fun axisMaxForLayout(
    entries: List<BarEntry>,
    layout: BarGroupLayout,
    mode: BarGroupMode
): Float {
    val stacked = mode == BarGroupMode.Stacked
    var rawMax = 0f
    var index = 0
    while (index < entries.size) {
        val category = layout.categoryOf[index]
        var extent = 0f
        while (index < entries.size && layout.categoryOf[index] == category) {
            val y = entries[index].y
            if (stacked) extent += y else if (y > extent) extent = y
            index++
        }
        if (extent > rawMax) rawMax = extent
    }
    val maxWithHeadroom = (if (rawMax > 0f) rawMax else 1f) * 1.2f
    val step = if (maxWithHeadroom > 100) 50f else if (maxWithHeadroom > 10) 10f else 5f
    return ceil(maxWithHeadroom / step) * step
}

/**
 * Bar thickness and gap along the layout axis. Gaps flank every bar, so
 * [count] bars produce count+1 gaps: thickness*count + gap*(count+1) == extent.
 */
internal fun barThicknessAndGap(
    extent: Float,
    count: Int,
    spacingFactor: Float
): Pair<Float, Float> = barThicknessAndGap(extent, count.toFloat(), spacingFactor)

/**
 * The same split over a fractional [count], which is what a slot collapsing after
 * a removal produces: the survivors widen across the collapse instead of jumping
 * once it completes.
 */
internal fun barThicknessAndGap(
    extent: Float,
    count: Float,
    spacingFactor: Float
): Pair<Float, Float> {
    val slots = count.coerceAtLeast(0.01f)
    val totalSpacing = extent * spacingFactor.coerceIn(0f, 0.9f)
    return (extent - totalSpacing) / slots to totalSpacing / (slots + 1f)
}

/** LTR offset of bar [index] along the layout axis, measured from [leadingInset]. */
internal fun barSlotOffset(index: Int, leadingInset: Float, thickness: Float, gap: Float): Float =
    barSlotOffset(index.toFloat(), leadingInset, thickness, gap)

/** The same offset for a fractional slot [position] — the slots ahead of this bar. */
internal fun barSlotOffset(
    position: Float,
    leadingInset: Float,
    thickness: Float,
    gap: Float
): Float = leadingInset + gap + position * (thickness + gap)

/** Mirrors an LTR offset across [totalExtent] for RTL layouts. */
internal fun mirrorForRtl(
    ltrOffset: Float,
    totalExtent: Float,
    thickness: Float,
    isRtl: Boolean
): Float = if (isRtl) totalExtent - ltrOffset - thickness else ltrOffset

/**
 * Thickness of one bar when a category slot is split across [seriesCount] of them.
 * Unlike [barThicknessAndGap] no gap flanks the group, because the slot's own gaps
 * already separate it from its neighbours; the whole slot goes to the bars and the
 * spacing between them.
 *
 * Returned as two scalars rather than a pair because the draw pass calls this once
 * per category per frame, and a `Pair` there is an allocation per frame.
 */
internal fun groupedBarThickness(
    slotThickness: Float,
    seriesCount: Int,
    innerSpacingFactor: Float
): Float = groupedBarThickness(slotThickness, seriesCount.toFloat(), innerSpacingFactor)

/**
 * The same split over a fractional [seriesCount], which is what a group holds while
 * one of its bars is leaving. The survivors widen across the departure rather than
 * doubling on the frame it completes.
 */
internal fun groupedBarThickness(
    slotThickness: Float,
    seriesCount: Float,
    innerSpacingFactor: Float
): Float {
    val count = seriesCount.coerceAtLeast(1f)
    return (slotThickness - slotThickness * innerGapFraction(count, innerSpacingFactor)) / count
}

/**
 * Share of the slot given over to inner gaps. Tapers to zero as the count falls to
 * one, without which a group of one would jump by the whole spacing factor.
 */
private fun innerGapFraction(count: Float, innerSpacingFactor: Float): Float =
    innerSpacingFactor.coerceIn(0f, 0.9f) * (count - 1f).coerceIn(0f, 1f)

/** Gap between two side-by-side bars of one category. Zero for a single-bar category. */
internal fun groupedBarGap(
    slotThickness: Float,
    seriesCount: Int,
    innerSpacingFactor: Float
): Float = groupedBarGap(slotThickness, seriesCount.toFloat(), innerSpacingFactor)

internal fun groupedBarGap(
    slotThickness: Float,
    seriesCount: Float,
    innerSpacingFactor: Float
): Float {
    val count = seriesCount.coerceAtLeast(1f)
    if (count <= 1f) return 0f
    return slotThickness * innerGapFraction(count, innerSpacingFactor) / (count - 1f)
}

/** LTR offset of the bar at series [position] within its category slot. */
internal fun groupedBarOffset(position: Int, thickness: Float, gap: Float): Float =
    groupedBarOffset(position.toFloat(), thickness, gap)

/** The same offset over the fractional share of the group that precedes this bar. */
internal fun groupedBarOffset(position: Float, thickness: Float, gap: Float): Float =
    position * (thickness + gap)

/**
 * Where each bar sits within its category. Built once per dataset from the drawn
 * entries, exiting ones included, so the draw pass only does arithmetic. Every
 * array is indexed by position in the render list.
 */
internal class BarGroupLayout(
    val categoryOf: IntArray,
    val positionInCategory: IntArray,
    val categorySize: IntArray,
    val categoryCount: Int,
    val hasSeries: Boolean
)

/** The single-bar-per-category layout, which is what a dataset with no series gets. */
internal fun computeBarGroupLayout(renderEntries: List<BarEntry>): BarGroupLayout {
    val count = renderEntries.size
    val categoryOf = IntArray(count)
    val positionInCategory = IntArray(count)
    val categorySize = IntArray(count)
    var category = -1
    var position = 0
    var hasSeries = false

    for (i in 0 until count) {
        val entry = renderEntries[i]
        if (entry.seriesId != null) hasSeries = true
        val previous = if (i > 0) renderEntries[i - 1] else null
        if (joinsCategory(previous, entry)) {
            position++
        } else {
            category++
            position = 0
        }
        categoryOf[i] = category
        positionInCategory[i] = position
    }

    val sizes = IntArray(category + 1)
    for (i in 0 until count) sizes[categoryOf[i]]++
    for (i in 0 until count) categorySize[i] = sizes[categoryOf[i]]

    return BarGroupLayout(categoryOf, positionInCategory, categorySize, category + 1, hasSeries)
}

/**
 * Walks the render list one category at a time, handing [body] the half-open range
 * of its bars and the three quantities every pass needs: how many bars share the
 * slot ([members], summed so a departing bar counts by what it still holds), how
 * long the slot itself lasts ([occupancy], the longest-lived bar), and how lit the
 * category is ([alpha], its most selected bar).
 *
 * Inline, because the draw pass runs this every frame.
 */
internal inline fun forEachBarCategory(
    renderEntries: List<BarEntry>,
    layout: BarGroupLayout,
    engine: ChartAnimationEngine,
    body: (first: Int, end: Int, members: Float, occupancy: Float, alpha: Float) -> Unit
) {
    var index = 0
    while (index < renderEntries.size) {
        val category = layout.categoryOf[index]
        val first = index
        var occupancy = 0f
        var members = 0f
        var alpha = 0f
        while (index < renderEntries.size && layout.categoryOf[index] == category) {
            val id = renderEntries[index].id
            val held = engine.slotOccupancy(id)
            if (held > occupancy) occupancy = held
            members += held
            val selection = engine.selectionAlphaAnimatables[id]?.value ?: 1f
            if (selection > alpha) alpha = selection
            index++
        }
        body(first, index, members, occupancy, alpha)
    }
}

/**
 * A summary, not a reading of the data: this node is a live region, so anything
 * here is repeated on every selection. Per-bar values live in the select actions
 * and in `stateDescription`.
 */
internal fun buildBarChartDescription(
    dataSet: BarDataSet,
    a11yConfig: A11yConfig
): String = buildString {
    append(a11yConfig.chartDescriptionBuilder(dataSet)).append(". ")
    append(a11yConfig.countDescriptionBuilder(summarizeBars(dataSet.entries)))
}

/** Counts the entries hold, without claiming a group size ragged data lacks. */
internal fun summarizeBars(entries: List<BarEntry>): BarChartSummary {
    val sizes = groupBarEntries(entries).map { it.entries.size }
    return BarChartSummary(
        bars = entries.size,
        categories = sizes.size,
        series = seriesOrder(entries).size,
        uniformGroupSize = sizes.distinct().singleOrNull()
    )
}

@Stable
internal class ChartAnimationEngine {
    val heightAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    val selectionAlphaAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val initializedIds = mutableSetOf<String>()

    /** How much of its slot a departing bar still holds: 1 until it has sunk, then to 0. */
    val slotAnimatables = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()

    private val exitTracker = ExitTracker<BarEntry> { it.id }

    /** Bars the dataset no longer contains but the chart is still drawing. */
    val exiting: List<Exiting<BarEntry>> get() = exitTracker.exiting

    fun syncAnimatables(entries: List<BarEntry>) {
        // An id that returns mid-exit rejoins the dataset and grows from where it got to.
        val (departed, returned) = exitTracker.sync(entries)
        departed.forEach { slotAnimatables[it.item.id] = Animatable(1f) }
        returned.forEach { slotAnimatables.remove(it.item.id) }

        val drawn = entries.mapTo(mutableSetOf()) { it.id } +
            exitTracker.exiting.mapTo(mutableSetOf()) { it.item.id }
        heightAnimatables.keys.removeAll { it !in drawn }
        selectionAlphaAnimatables.keys.removeAll { it !in drawn }
        initializedIds.removeAll { it !in drawn }

        entries.forEach { entry ->
            heightAnimatables.getOrPut(entry.id) { Animatable(0f) }
            selectionAlphaAnimatables.getOrPut(entry.id) { Animatable(1f) }
        }
    }

    /**
     * Removes a bar in two movements: its entry animation in reverse, then the slot
     * it held collapsing on [AnimationConfig.morphSpec] as the survivors widen in.
     */
    fun launchExitAnimations(config: AnimationConfig, scope: CoroutineScope) {
        exitTracker.exiting.forEach { bar ->
            val id = bar.item.id
            val height = heightAnimatables[id] ?: return@forEach
            val slot = slotAnimatables[id] ?: return@forEach
            if (height.isRunning || slot.isRunning) return@forEach

            // A cancelled coroutine can leave it at rest but still listed.
            if (slot.value == 0f) {
                forget(bar)
                return@forEach
            }

            scope.launch {
                height.animateTo(0f, config.initialEntrySpec)
                slot.animateTo(0f, config.morphSpec)
                forget(bar)
            }
        }
    }

    private fun forget(bar: Exiting<BarEntry>) {
        val id = bar.item.id
        heightAnimatables.remove(id)
        selectionAlphaAnimatables.remove(id)
        slotAnimatables.remove(id)
        initializedIds.remove(id)
        exitTracker.forget(bar)
    }

    /**
     * Dataset bars with the departing ones back in the positions they held. Draw
     * order only — touch handling and the accessibility description stay on the
     * dataset.
     */
    fun renderEntries(entries: List<BarEntry>): List<BarEntry> = exitTracker.render(entries)

    /** A bar's remaining claim on its slot. Read while drawing: it changes per frame. */
    fun slotOccupancy(id: String): Float = slotAnimatables[id]?.value ?: 1f

    /** Slots currently in use, counting a collapsing one as the fraction it still holds. */
    fun slotCount(renderEntries: List<BarEntry>): Float =
        if (exitTracker.exiting.isEmpty()) renderEntries.size.toFloat()
        else renderEntries.fold(0f) { acc, entry -> acc + slotOccupancy(entry.id) }

    /**
     * The same count over categories rather than bars. A grouped category holds its
     * slot until its last bar has gone, so it collapses on the survivor's occupancy
     * rather than shrinking a step per bar removed.
     */
    fun categorySlotCount(renderEntries: List<BarEntry>, layout: BarGroupLayout): Float {
        if (exitTracker.exiting.isEmpty()) return layout.categoryCount.toFloat()
        var total = 0f
        forEachBarCategory(renderEntries, layout, this) { _, _, _, occupancy, _ ->
            total += occupancy
        }
        return total
    }

    /**
     * Pixels of stack already drawn below the bar at [index]. Summed from the
     * animated heights rather than the data, so segments stay contiguous while they
     * animate at different rates instead of tearing apart mid-flight.
     */
    fun stackedBase(
        renderEntries: List<BarEntry>,
        layout: BarGroupLayout,
        index: Int,
        maxBarValue: Float,
        chartExtent: Float
    ): Float {
        var base = 0f
        var i = index - 1
        while (i >= 0 && layout.categoryOf[i] == layout.categoryOf[index]) {
            val value = heightAnimatables[renderEntries[i].id]?.value ?: 0f
            base += (value / maxBarValue) * chartExtent
            i--
        }
        return base
    }

    fun launchEntryAnimations(entries: List<BarEntry>, config: AnimationConfig, scope: CoroutineScope) {
        // Stagger by position among the bars appearing now, not by dataset index.
        // Identical on first load; on a later append it saves the newcomer waiting
        // one stagger step per bar already drawn.
        var appearing = 0
        entries.forEach { entry ->
            val heightAnim = heightAnimatables[entry.id] ?: return@forEach

            if (initializedIds.add(entry.id)) {
                val position = appearing++
                scope.launch {
                    delay(config.startDelayMs + (position * config.staggerDelayMs))
                    heightAnim.animateTo(entry.y, config.initialEntrySpec)
                }
            } else if (heightAnim.targetValue != entry.y) {
                scope.launch { heightAnim.animateTo(entry.y, config.morphSpec) }
            }
        }
    }

    /**
     * Every bar being drawn, departing ones included. A bar that left before the
     * selection was made would otherwise keep full alpha and, being the brightest
     * thing in its category, hold that category's axis label lit.
     */
    fun updateSelectionState(
        selectedEntry: BarEntry?,
        style: ChartStyle,
        config: AnimationConfig,
        scope: CoroutineScope
    ) {
        val selectedId = selectedEntry?.id
        selectionAlphaAnimatables.forEach { (id, animatable) ->
            val targetAlpha =
                if (selectedId != null && id != selectedId) style.unselectedAlpha else 1f

            if (animatable.targetValue != targetAlpha) {
                scope.launch { animatable.animateTo(targetAlpha, config.selectionSpec) }
            }
        }
    }
}
