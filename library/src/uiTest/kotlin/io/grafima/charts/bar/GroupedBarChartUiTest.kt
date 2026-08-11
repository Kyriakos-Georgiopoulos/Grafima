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

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.assumePixelCapture
import io.grafima.charts.customActionLabels
import io.grafima.charts.onChartNode
import io.grafima.charts.performCustomAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupedBarChartUiTest {

    private val entries = listOf(
        BarEntry("q1-rev", "Q1", 45f, seriesId = "rev", seriesLabel = "Revenue"),
        BarEntry("q1-cost", "Q1", 30f, seriesId = "cost", seriesLabel = "Cost"),
        BarEntry("q2-rev", "Q2", 80f, seriesId = "rev", seriesLabel = "Revenue"),
        BarEntry("q2-cost", "Q2", 52f, seriesId = "cost", seriesLabel = "Cost")
    )

    private fun dataSet(mode: BarGroupMode) = BarDataSet(
        entries = entries,
        contentDescription = "Quarterly figures",
        mode = mode
    )

    private val snapAnimations = AnimationConfig(
        initialEntrySpec = snap(),
        morphSpec = snap(),
        selectionSpec = snap(),
        staggerDelayMs = 0L,
        startDelayMs = 0L
    )

    @Test
    fun every_bar_of_a_group_gets_its_own_action_naming_its_series() = runComposeUiTest {
        setContent {
            BarChart(
                dataSet = dataSet(BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }

        val labels = onChartNode().customActionLabels()

        // Without the series in the label, the two Q1 actions would be identical
        // and a screen reader user could not tell which bar they were choosing.
        assertTrue(labels.contains("Select Q1, Revenue"), "actions were $labels")
        assertTrue(labels.contains("Select Q1, Cost"), "actions were $labels")
        assertTrue(labels.contains("Select Q2, Revenue"), "actions were $labels")
        assertTrue(labels.contains("Select Q2, Cost"), "actions were $labels")
        assertEquals(labels.size, labels.distinct().size, "action labels must be unique")
    }

    @Test
    fun the_select_action_label_can_be_replaced() = runComposeUiTest {
        setContent {
            BarChart(
                dataSet = dataSet(BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                a11yConfig = A11yConfig(
                    selectActionLabel = { "${it.spokenSeriesLabel} in ${it.xLabel}" }
                )
            )
        }

        // Hardcoded, this string was the last thing on the grouped path a
        // non-English app could not translate.
        assertTrue(
            onChartNode().customActionLabels().contains("Cost in Q1"),
            "actions were ${onChartNode().customActionLabels()}"
        )
    }

    @Test
    fun the_description_reports_the_grouping_rather_than_a_bare_bar_count() = runComposeUiTest {
        setContent {
            BarChart(
                dataSet = dataSet(BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations
            )
        }

        onNodeWithContentDescription("4 bars in 2 groups of 2", substring = true).assertExists()
    }

    @Test
    fun selecting_a_bar_by_action_reports_its_series_in_the_state() = runComposeUiTest {
        var selected: BarEntry? by mutableStateOf(null)
        setContent {
            BarChart(
                dataSet = dataSet(BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = selected,
                onBarSelected = { selected = it }
            )
        }

        onChartNode().performCustomAction("Select Q1, Cost")

        assertEquals("q1-cost", selected?.id)
    }

    @Test
    fun a_tap_lands_on_one_bar_of_a_group_not_the_whole_category() = runComposeUiTest {
        var selected: BarEntry? by mutableStateOf(null)
        setContent {
            BarChart(
                dataSet = dataSet(BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = selected,
                onBarSelected = { selected = it }
            )
        }

        // Left half of the first group is the first series, right half the second.
        onChartNode().performTouchInput { down(Offset(width * 0.22f, height * 0.75f)) }
        val first = selected
        onChartNode().performTouchInput { up() }

        assertNotNull(first, "a tap inside the first group selected nothing")
        assertEquals("rev", first.seriesId, "the left bar of the group is the first series")
    }

    @Test
    fun the_two_series_of_a_group_are_separately_reachable_by_tap() = runComposeUiTest {
        var selected: BarEntry? by mutableStateOf(null)
        setContent {
            BarChart(
                dataSet = dataSet(BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = selected,
                onBarSelected = { selected = it }
            )
        }

        val hits = mutableSetOf<String>()
        for (fraction in listOf(0.20f, 0.26f, 0.32f, 0.38f)) {
            onChartNode().performTouchInput { down(Offset(width * fraction, height * 0.8f)) }
            selected?.let { hits.add(it.id) }
            onChartNode().performTouchInput { up() }
        }

        // Sweeping across one group must reach both of its bars. A single hit means
        // the group is being treated as one wide bar.
        assertTrue(hits.size >= 2, "sweeping the group only ever selected $hits")
    }

    @Test
    fun a_stacked_chart_reaches_the_upper_segment_above_the_lower_one() = runComposeUiTest {
        var selected: BarEntry? by mutableStateOf(null)
        setContent {
            BarChart(
                dataSet = dataSet(BarGroupMode.Stacked),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = selected,
                onBarSelected = { selected = it }
            )
        }

        val hits = mutableSetOf<String>()
        for (fraction in listOf(0.45f, 0.55f, 0.65f, 0.75f, 0.85f)) {
            onChartNode().performTouchInput { down(Offset(width * 0.3f, height * fraction)) }
            selected?.let { hits.add(it.id) }
            onChartNode().performTouchInput { up() }
        }

        // Both segments of the Q1 stack have to be selectable, or the upper one is
        // decoration a user cannot inspect.
        assertTrue(hits.size >= 2, "the stack only ever selected $hits")
    }

    @Test
    fun a_mirrored_tap_in_rtl_finds_the_same_bar_of_the_same_group() = runComposeUiTest {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var selected: BarEntry? by mutableStateOf(null)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                BarChart(
                    dataSet = dataSet(BarGroupMode.Grouped),
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    selectedEntry = selected,
                    onBarSelected = { selected = it }
                )
            }
        }

        onChartNode().performTouchInput { down(Offset(width * 0.22f, height * 0.8f)) }
        val ltrHit = selected
        onChartNode().performTouchInput { up() }

        direction = LayoutDirection.Rtl
        selected = null
        waitForIdle()

        onChartNode().performTouchInput { down(Offset(width * 0.78f, height * 0.8f)) }
        val rtlHit = selected
        onChartNode().performTouchInput { up() }

        // The mirrored x of a bar must find that same bar. If grouping ignored RTL,
        // the mirrored tap would land on the other series of the group.
        assertNotNull(ltrHit, "the LTR tap selected nothing")
        assertNotNull(rtlHit, "the mirrored RTL tap selected nothing")
        assertEquals(ltrHit.id, rtlHit.id)
    }

    /** Blue bars, so the only red pixels in a capture belong to the tooltip. */
    private fun tooltipProbeDataSet(mode: BarGroupMode) = BarDataSet(
        entries = entries,
        defaultGradientColors = listOf(Color.Blue, Color.Blue),
        contentDescription = "Quarterly figures",
        mode = mode
    )

    private val redTooltip = TooltipSelectionRenderer(backgroundColor = Color.Red)

    @Test
    fun the_tooltip_of_a_grouped_bar_sits_over_the_group_it_belongs_to() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            BarChart(
                dataSet = tooltipProbeDataSet(BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectionRenderer = redTooltip,
                // Dataset index 1, but category 0.
                selectedEntry = entries[1]
            )
        }
        waitForIdle()

        val image = onChartNode().captureToImage()
        val centreX = image.redCentroidX()
        assertNotNull(centreX, "the tooltip was not drawn at all")
        assertTrue(
            centreX < image.width / 2f,
            "Q1's tooltip was drawn at x=$centreX of ${image.width}, outside the left " +
                "half of the chart that Q1's group occupies"
        )
    }

    @Test
    fun a_grouped_bar_is_drawn_in_the_mirrored_half_in_rtl() = runComposeUiTest {
        assumePixelCapture()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BarChart(
                    dataSet = tooltipProbeDataSet(BarGroupMode.Grouped),
                    modifier = Modifier.size(300.dp),
                    animationConfig = snapAnimations,
                    selectionRenderer = redTooltip,
                    selectedEntry = entries[1]
                )
            }
        }
        waitForIdle()

        val image = onChartNode().captureToImage()
        val centreX = image.redCentroidX()
        assertNotNull(centreX, "the tooltip was not drawn at all")
        // The first category leads from the right in RTL.
        assertTrue(
            centreX > image.width / 2f,
            "Q1's tooltip was drawn at x=$centreX of ${image.width}, still in the " +
                "left half — the group did not mirror"
        )
    }

    @Test
    fun a_horizontal_tooltip_mirrors_onto_the_growing_end_of_the_bar_in_rtl() = runComposeUiTest {
        assumePixelCapture()
        var direction by mutableStateOf(LayoutDirection.Ltr)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                BarChart(
                    dataSet = tooltipProbeDataSet(BarGroupMode.Grouped),
                    modifier = Modifier.size(300.dp),
                    orientation = BarOrientation.Horizontal,
                    animationConfig = snapAnimations,
                    selectionRenderer = redTooltip,
                    selectedEntry = entries[1]
                )
            }
        }
        waitForIdle()
        val image = onChartNode().captureToImage()
        val ltrX = image.redCentroidX()

        direction = LayoutDirection.Rtl
        waitForIdle()
        val rtlImage = onChartNode().captureToImage()
        val rtlX = rtlImage.redCentroidX()

        assertNotNull(ltrX, "no tooltip in LTR")
        assertNotNull(rtlX, "no tooltip in RTL")
        // The plot area mirrors exactly, so the growing end does too.
        val mirrored = image.width - rtlX
        assertTrue(
            kotlin.math.abs(mirrored - ltrX) < 8f,
            "the tooltip sat at x=$ltrX in LTR but at x=$rtlX (mirror $mirrored) in RTL"
        )
    }

    @Test
    fun the_tooltip_of_an_upper_stack_segment_sits_above_the_one_below_it() = runComposeUiTest {
        assumePixelCapture()
        var selected: BarEntry? by mutableStateOf(entries[0])
        setContent {
            BarChart(
                dataSet = tooltipProbeDataSet(BarGroupMode.Stacked),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectionRenderer = redTooltip,
                selectedEntry = selected
            )
        }
        waitForIdle()
        val lowerY = onChartNode().captureToImage().redCentroidY()

        selected = entries[1]
        waitForIdle()
        val upperY = onChartNode().captureToImage().redCentroidY()

        assertNotNull(lowerY, "no tooltip for the lower segment")
        assertNotNull(upperY, "no tooltip for the upper segment")
        // Ignoring the stack base inverts these, the upper segment being the shorter.
        assertTrue(
            upperY < lowerY,
            "the upper segment's tooltip was at y=$upperY, at or below the lower " +
                "segment's at y=$lowerY"
        )
    }

    /** Mean x of the tooltip's pixels, or null when none were painted. */
    private fun ImageBitmap.redCentroidX(): Float? = redCentroid()?.first

    /** Mean y of the tooltip's pixels, or null when none were painted. */
    private fun ImageBitmap.redCentroidY(): Float? = redCentroid()?.second

    private fun ImageBitmap.redCentroid(): Pair<Float, Float>? {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        var sumX = 0L
        var sumY = 0L
        var count = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r > 150 && g < 80 && b < 80) {
                sumX += i % width
                sumY += i / width
                count++
            }
        }
        return if (count == 0) null else (sumX.toFloat() / count) to (sumY.toFloat() / count)
    }

    @Test
    fun selecting_either_bar_of_a_group_keeps_that_group_s_label_lit() = runComposeUiTest {
        assumePixelCapture()
        var selected: BarEntry? by mutableStateOf(entries[0])
        setContent {
            BarChart(
                dataSet = tooltipProbeDataSet(BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                style = ChartStyle(labelTextStyle = TextStyle(color = Color.Red, fontSize = 12.sp)),
                animationConfig = snapAnimations,
                selectedEntry = selected
            )
        }
        waitForIdle()
        val onOpener = onChartNode().captureToImage().countOpaqueRed()

        // The second series of the same category: the label is the category's, so
        // reading its alpha off the first bar dims a group that holds the selection.
        selected = entries[1]
        waitForIdle()
        val onSecond = onChartNode().captureToImage().countOpaqueRed()

        assertTrue(onOpener > 0, "no axis labels drawn")
        assertEquals(onOpener, onSecond, "Q1's label dimmed when the selection moved within Q1")
    }

    @Test
    fun stacking_a_dataset_with_no_series_changes_nothing_about_it() = runComposeUiTest {
        val plain = BarDataSet(
            entries = listOf(BarEntry("jan", "Jan", 45f), BarEntry("feb", "Feb", 80f)),
            contentDescription = "Monthly"
        )
        var grouped: BarEntry? by mutableStateOf(null)
        var stacked: BarEntry? by mutableStateOf(null)

        setContent {
            BarChart(
                dataSet = plain.copy(mode = BarGroupMode.Grouped),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = grouped,
                onBarSelected = { grouped = it }
            )
        }
        // Just inside the label strip: the fat-finger tolerance every mode should give.
        onChartNode().performTouchInput { down(Offset(width * 0.3f, height * 0.906f)) }
        onChartNode().performTouchInput { up() }

        setContent {
            BarChart(
                dataSet = plain.copy(mode = BarGroupMode.Stacked),
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = stacked,
                onBarSelected = { stacked = it }
            )
        }
        onChartNode().performTouchInput { down(Offset(width * 0.3f, height * 0.906f)) }
        onChartNode().performTouchInput { up() }

        // `BarDataSet.mode` documents that it has no effect without a seriesId.
        assertEquals(grouped?.id, stacked?.id, "mode changed hit testing on plain data")
        assertNotNull(stacked, "the stacked tap found nothing")
    }

    @Test
    fun a_grouped_bar_too_narrow_for_its_value_drops_the_label() = runComposeUiTest {
        assumePixelCapture()
        // Six categories of three series each: every bar is far narrower than a
        // three-digit label, so the gate has to suppress all of them.
        val crowded = BarDataSet(
            entries = (1..6).flatMap { q ->
                listOf("rev", "cost", "tax").map { series ->
                    BarEntry("q$q-$series", "Q$q", 100f + q, seriesId = series)
                }
            },
            defaultGradientColors = listOf(Color.Blue, Color.Blue),
            contentDescription = "Crowded groups"
        )
        setContent {
            BarChart(
                dataSet = crowded,
                modifier = Modifier.size(300.dp),
                style = ChartStyle(valueTextStyle = TextStyle(color = Color.Red, fontSize = 14.sp)),
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        assertEquals(
            0,
            onChartNode().captureToImage().countOpaqueRed(),
            "value labels were drawn over bars too narrow to hold them"
        )
    }

    @Test
    fun a_narrow_bar_without_series_still_draws_its_value() = runComposeUiTest {
        assumePixelCapture()
        // Enough bars that each is narrower than its own three-digit label.
        val crowded = BarDataSet(
            entries = (1..8).map { BarEntry("b$it", "B$it", 100f + it) },
            defaultGradientColors = listOf(Color.Blue, Color.Blue),
            contentDescription = "Crowded"
        )
        setContent {
            BarChart(
                dataSet = crowded,
                modifier = Modifier.size(300.dp),
                style = ChartStyle(valueTextStyle = TextStyle(color = Color.Red, fontSize = 14.sp)),
                animationConfig = snapAnimations
            )
        }
        waitForIdle()

        // 1.1.1 drew these unconditionally; a fit gate that also covered ungrouped
        // charts silently removed every one of them.
        assertTrue(onChartNode().captureToImage().countOpaqueRed() > 0, "no value labels drawn")
    }

    /**
     * Red text at alpha 0.25 has the same red channel as at 1.0 in unpremultiplied
     * ARGB, so a brightness assertion has to read the alpha channel itself.
     */
    private fun ImageBitmap.countOpaqueRed(): Int {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.count { p ->
            val a = (p ushr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            a > 200 && r > 150 && g < 80 && b < 80
        }
    }

    @Test
    fun a_dataset_without_series_keeps_its_single_bar_actions() = runComposeUiTest {
        val plain = BarDataSet(
            entries = listOf(
                BarEntry("jan", "Jan", 45f),
                BarEntry("feb", "Feb", 80f)
            ),
            contentDescription = "Monthly revenue"
        )
        var selected: BarEntry? by mutableStateOf(null)
        setContent {
            BarChart(
                dataSet = plain,
                modifier = Modifier.size(300.dp),
                animationConfig = snapAnimations,
                selectedEntry = selected,
                onBarSelected = { selected = it }
            )
        }

        val labels = onChartNode().customActionLabels()

        // The wording a non-grouped chart had before series existed.
        assertTrue(labels.contains("Select Jan"), "actions were $labels")
        onNodeWithContentDescription("2 bars.", substring = true).assertExists()
        assertNull(selected)
    }
}
