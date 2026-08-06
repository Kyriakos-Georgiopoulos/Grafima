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

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.charts.DashPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reference lines, dashed strokes and always-on value labels.
 *
 * Colour matching is tolerant throughout: a stroke is antialiased against its
 * background and a glyph is mostly edge, so an exact match finds far less ink than
 * was painted — and on some densities none at all.
 */
@OptIn(ExperimentalTestApi::class)
class LineAnnotationsUiTest {

    private val snapAnimations = LineAnimationConfig(
        entrySpec = snap(),
        morphSpec = snap(),
        staggerMs = 0L,
        startDelayMs = 0L,
        seriesStaggerMs = 0L
    )

    private fun dataSet(
        dashPattern: DashPattern? = null,
        fill: List<Color> = emptyList()
    ) = LineDataSet(
        series = listOf(
            LineSeries(
                id = "revenue",
                label = "Revenue",
                points = listOf(
                    LineDataPoint(x = 0f, y = 10f, label = "Jan"),
                    LineDataPoint(x = 1f, y = 25f, label = "Feb"),
                    LineDataPoint(x = 2f, y = 18f, label = "Mar")
                ),
                color = Color.Blue,
                strokeWidth = 4.dp,
                dashPattern = dashPattern,
                fillGradientColors = fill
            )
        ),
        contentDescription = "Quarterly revenue"
    )

    @Composable
    private fun Chart(
        data: LineDataSet = dataSet(),
        axisConfig: LineAxisConfig = LineAxisConfig(showGrid = false),
        style: LineChartStyle = LineChartStyle()
    ) {
        LineChart(
            dataSet = data,
            modifier = Modifier.size(300.dp),
            animationConfig = snapAnimations,
            axisConfig = axisConfig,
            style = style
        )
    }

    private fun redLine(value: Float, axis: ReferenceLineAxis) = ReferenceLine(
        value = value,
        axis = axis,
        color = Color.Red,
        strokeWidth = 3.dp
    )

    private fun labelStyle(
        formatter: (LineSeries, LineDataPoint) -> String = { _, p -> p.y.toInt().toString() }
    ) = LineChartStyle(
        valueLabels = LineValueLabelConfig(
            enabled = true,
            formatter = formatter,
            textStyle = TextStyle(color = Color.Red, fontSize = 14.sp)
        )
    )

    // ── Reference lines ──

    @Test
    fun a_vertical_reference_line_stands_at_its_x_value() = runComposeUiTest {
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    referenceLines = listOf(redLine(1f, ReferenceLineAxis.X))
                )
            )
        }
        waitForIdle()

        // x = 1 is the middle of a 0..2 axis, so the line belongs at the middle of
        // the plot, not of the canvas — the y labels take room on the left, which
        // puts the plot's centre to the right of the image's.
        val image = onRoot().captureToImage()
        val span = assertNotNull(image.reddishColumns(), "no reference line was drawn")
        assertTrue(
            span.first > image.width / 2 && span.last < image.width * 3 / 4,
            "the line landed at ${span.first}..${span.last} of ${image.width}"
        )
    }

    @Test
    fun a_horizontal_reference_line_lies_at_its_y_value() = runComposeUiTest {
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    yMin = 0f,
                    yMax = 40f,
                    referenceLines = listOf(redLine(30f, ReferenceLineAxis.Y))
                )
            )
        }
        waitForIdle()

        // 30 of a pinned 0..40 sits three quarters up: a quarter of the way down.
        val image = onRoot().captureToImage()
        val rows = assertNotNull(image.reddishRows(), "no reference line was drawn")
        assertTrue(
            rows.first > image.height / 8 && rows.last < image.height / 2,
            "the line landed at rows ${rows.first}..${rows.last} of ${image.height}"
        )
    }

    @Test
    fun a_target_above_the_data_is_drawn_because_the_axis_reaches_it() = runComposeUiTest {
        // The data peaks at 25. An axis fitted to it alone leaves a target of 60
        // off the chart, which is the one case a reference line is drawn for.
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    referenceLines = listOf(redLine(60f, ReferenceLineAxis.Y))
                )
            )
        }
        waitForIdle()

        assertTrue(
            onRoot().captureToImage().countReddish() > 0,
            "a target above the data was left off the chart"
        )
    }

    @Test
    fun a_line_that_opts_out_of_the_range_leaves_the_axis_alone() = runComposeUiTest {
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    referenceLines = listOf(
                        redLine(60f, ReferenceLineAxis.Y).copy(includeInRange = false)
                    )
                )
            )
        }
        waitForIdle()

        assertEquals(
            0,
            onRoot().captureToImage().countReddish(),
            "the axis widened for a line that opted out"
        )
    }

    @Test
    fun a_dash_with_a_gap_narrower_than_its_stroke_still_shows_gaps() = runComposeUiTest {
        // Round caps reach half the stroke past each dash and close a short gap,
        // drawing a line that is solid on the chart and dashed in the legend.
        var pattern by mutableStateOf<DashPattern?>(null)
        setContent { Chart(data = dataSet(dashPattern = pattern)) }
        waitForIdle()
        val solid = onRoot().captureToImage().countBluish()

        pattern = DashPattern(dash = 6.dp, gap = 2.dp)
        waitForIdle()
        val dashed = onRoot().captureToImage().countBluish()

        assertTrue(
            dashed < solid * 9 / 10,
            "a 2dp gap on a 4dp stroke painted $dashed against a solid $solid"
        )
    }

    @Test
    fun a_reference_line_off_the_axis_is_not_drawn_at_the_edge_instead() = runComposeUiTest {
        // Clamping would put the line on a boundary, marking a threshold that is
        // nowhere near the value the caller gave.
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    // Pinned, so the axis cannot widen to reach them.
                    xMin = 0f,
                    xMax = 2f,
                    yMin = 0f,
                    yMax = 40f,
                    referenceLines = listOf(
                        redLine(99f, ReferenceLineAxis.X),
                        redLine(-99f, ReferenceLineAxis.Y)
                    )
                )
            )
        }
        waitForIdle()

        assertEquals(0, onRoot().captureToImage().countReddish(), "an off-axis line was drawn")
    }

    @Test
    fun a_reference_line_is_drawn_over_the_series_it_qualifies() = runComposeUiTest {
        // Behind an opaque area fill a threshold disappears exactly where the data
        // is, which is the only place it is worth reading.
        setContent {
            Chart(
                data = dataSet(fill = listOf(Color.Blue, Color.Blue)),
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    referenceLines = listOf(redLine(5f, ReferenceLineAxis.Y))
                )
            )
        }
        waitForIdle()

        assertTrue(
            onRoot().captureToImage().countReddish() > 0,
            "the reference line was buried under the area fill"
        )
    }

    @Test
    fun a_vertical_reference_line_travels_with_the_axis_in_rtl() = runComposeUiTest {
        // The line is fixed to a data value, not to a side of the screen.
        var rtl by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Chart(
                    axisConfig = LineAxisConfig(
                        showGrid = false,
                        referenceLines = listOf(redLine(0.4f, ReferenceLineAxis.X))
                    )
                )
            }
        }
        waitForIdle()
        val image = onRoot().captureToImage()
        val ltrColumn = image.reddishColumns()!!.let { (it.first + it.last) / 2 }

        rtl = true
        waitForIdle()
        val rtlColumn = onRoot().captureToImage().reddishColumns()!!
            .let { (it.first + it.last) / 2 }

        val middle = image.width / 2
        assertTrue(ltrColumn < middle, "a fifth along the axis was drawn at $ltrColumn in LTR")
        assertTrue(rtlColumn > middle, "a fifth along the axis was drawn at $rtlColumn in RTL")
    }

    @Test
    fun a_named_reference_line_is_announced_to_a_screen_reader() = runComposeUiTest {
        // The line is only a mark on the screen; without this a listener never
        // learns the threshold is there.
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    referenceLines = listOf(
                        ReferenceLine(
                            value = 1f,
                            axis = ReferenceLineAxis.X,
                            contentDescription = "Now"
                        ),
                        ReferenceLine(value = 2f, axis = ReferenceLineAxis.X)
                    )
                )
            )
        }
        waitForIdle()

        val spoken = onRoot().onChildren().onFirst().fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        assertTrue("Reference line: Now." in spoken, "the line was not announced: $spoken")
        assertTrue("Quarterly revenue" in spoken, "the chart's own description was lost: $spoken")
    }

    @Test
    fun a_reference_line_off_the_axis_is_not_announced_either() = runComposeUiTest {
        // It is not on the chart, so telling a listener about it hands them a
        // threshold nobody else can see.
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    yMin = 0f,
                    yMax = 40f,
                    referenceLines = listOf(
                        ReferenceLine(
                            value = 20f,
                            axis = ReferenceLineAxis.Y,
                            contentDescription = "Target"
                        ),
                        ReferenceLine(
                            value = 500f,
                            axis = ReferenceLineAxis.Y,
                            contentDescription = "Ceiling"
                        )
                    )
                )
            )
        }
        waitForIdle()

        val spoken = onRoot().onChildren().onFirst().fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        assertTrue("Target" in spoken, "the drawn line was not announced: $spoken")
        assertFalse("Ceiling" in spoken, "a line that is not drawn was announced: $spoken")
    }

    @Test
    fun an_empty_chart_announces_no_reference_line() = runComposeUiTest {
        // It draws nothing at all, its reference lines included, so speaking of a
        // threshold would describe something nobody can see.
        setContent {
            Chart(
                data = LineDataSet(series = emptyList(), contentDescription = "Empty"),
                axisConfig = LineAxisConfig(
                    referenceLines = listOf(
                        ReferenceLine(
                            value = 1f,
                            axis = ReferenceLineAxis.Y,
                            contentDescription = "Target"
                        )
                    )
                )
            )
        }
        waitForIdle()

        val spoken = onRoot().onChildren().onFirst().fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        assertFalse("Target" in spoken, "an empty chart announced a threshold: $spoken")
    }

    @Test
    fun a_named_reference_line_is_drawn_with_its_name() = runComposeUiTest {
        // contentDescription only reaches a listener. A sighted reader was left
        // with an unexplained line across the plot.
        var label by mutableStateOf<String?>(null)
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    showXLabels = false,
                    showYLabels = false,
                    yMin = 0f,
                    yMax = 40f,
                    referenceLines = listOf(
                        ReferenceLine(
                            value = 30f,
                            axis = ReferenceLineAxis.Y,
                            label = label,
                            color = Color.Red
                        )
                    )
                )
            )
        }
        waitForIdle()
        val lineOnly = onRoot().captureToImage().countReddish()

        label = "Target"
        waitForIdle()
        val withLabel = onRoot().captureToImage().countReddish()

        assertTrue(lineOnly > 0, "the reference line was not drawn")
        assertTrue(
            withLabel > lineOnly,
            "the label painted no ink of its own: $withLabel against $lineOnly"
        )
    }

    @Test
    fun a_reference_line_label_names_it_to_a_screen_reader_too() = runComposeUiTest {
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    referenceLines = listOf(
                        ReferenceLine(value = 1f, axis = ReferenceLineAxis.X, label = "Now")
                    )
                )
            )
        }
        waitForIdle()

        val spoken = onRoot().onChildren().onFirst().fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        assertTrue("Reference line: Now." in spoken, "naming a line did not announce it: $spoken")
    }

    @Test
    fun the_grid_dash_is_asked_for_in_dp_like_every_other_chart() = runComposeUiTest {
        var pattern by mutableStateOf<DashPattern?>(null)
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    gridColor = Color.Red,
                    showXLabels = false,
                    showYLabels = false,
                    gridDashPattern = pattern
                )
            )
        }
        waitForIdle()
        val solid = onRoot().captureToImage().countReddish()

        pattern = DashPattern(dash = 4.dp, gap = 4.dp)
        waitForIdle()
        val dashed = onRoot().captureToImage().countReddish()

        assertTrue(solid > 0, "no grid was drawn")
        assertTrue(dashed < solid, "the grid dash painted $dashed against a solid $solid")
    }

    // ── Dashed series ──

    @Test
    fun a_dashed_stroke_leaves_gaps_a_solid_one_does_not() = runComposeUiTest {
        var pattern by mutableStateOf<DashPattern?>(null)
        setContent { Chart(data = dataSet(dashPattern = pattern)) }
        waitForIdle()
        val solid = onRoot().captureToImage().countBluish()

        pattern = DashPattern(dash = 6.dp, gap = 6.dp)
        waitForIdle()
        val dashed = onRoot().captureToImage().countBluish()

        assertTrue(solid > 0, "the solid stroke painted nothing")
        assertTrue(dashed > 0, "the dashed stroke painted nothing at all")
        assertTrue(dashed < solid, "the dashed stroke painted $dashed against a solid $solid")
    }

    @Test
    fun a_pattern_that_draws_and_skips_nothing_stays_solid() = runComposeUiTest {
        // Both lengths zero leaves Skia with no line to draw at all.
        var pattern by mutableStateOf<DashPattern?>(null)
        setContent { Chart(data = dataSet(dashPattern = pattern)) }
        waitForIdle()
        val solid = onRoot().captureToImage().countBluish()

        pattern = DashPattern(dash = 0.dp, gap = 0.dp)
        waitForIdle()
        assertEquals(
            solid,
            onRoot().captureToImage().countBluish(),
            "an undrawable pattern changed the stroke"
        )
    }

    @Test
    fun the_area_fill_under_a_dashed_stroke_is_not_dashed() = runComposeUiTest {
        // The dash says the line is derived. Punching the same holes in the region
        // beneath it would say something about the data instead.
        var pattern by mutableStateOf<DashPattern?>(null)
        setContent {
            Chart(data = dataSet(dashPattern = pattern, fill = listOf(Color.Red, Color.Red)))
        }
        waitForIdle()
        // Deep inside the fill and clear of the curve: over the stroke itself a
        // dash reveals the fill through its gaps, which would count as more ink
        // rather than less.
        val image = onRoot().captureToImage()
        val band = (image.height * 65 / 100) until (image.height * 85 / 100)
        val solid = image.countReddish(band)

        pattern = DashPattern(dash = 6.dp, gap = 6.dp)
        waitForIdle()
        assertTrue(solid > 0, "no fill was painted")
        assertEquals(
            solid,
            onRoot().captureToImage().countReddish(band),
            "the fill was dashed along with its stroke"
        )
    }

    // ── Value labels ──

    @Test
    fun a_point_prints_its_value_only_when_asked() = runComposeUiTest {
        var show by mutableStateOf(false)
        setContent {
            Chart(style = if (show) labelStyle() else LineChartStyle())
        }
        waitForIdle()
        assertEquals(0, onRoot().captureToImage().countReddish(), "a label was drawn unasked")

        show = true
        waitForIdle()
        assertTrue(onRoot().captureToImage().countReddish() > 0, "no value label was drawn")
    }

    @Test
    fun a_style_naming_no_colour_keeps_the_default_tone() = runComposeUiTest {
        // TextStyle() leaves its colour unspecified, and so does every
        // MaterialTheme.typography style, so this is the commonest way to ask for a
        // font. It must not be read as asking for the series' colour.
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    showXLabels = false,
                    showYLabels = false
                ),
                style = LineChartStyle(
                    valueLabels = LineValueLabelConfig(
                        enabled = true,
                        textStyle = TextStyle(fontSize = 14.sp)
                    )
                )
            )
        }
        waitForIdle()

        assertTrue(
            onRoot().captureToImage().countSlateish() > 0,
            "a style with no colour of its own did not keep the default tone"
        )
    }

    @Test
    fun labels_can_take_the_colour_of_the_series_they_name() = runComposeUiTest {
        var useSeriesColor by mutableStateOf(false)
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    showXLabels = false,
                    showYLabels = false
                ),
                style = LineChartStyle(
                    valueLabels = LineValueLabelConfig(
                        enabled = true,
                        textStyle = TextStyle(fontSize = 14.sp),
                        useSeriesColor = useSeriesColor
                    )
                )
            )
        }
        waitForIdle()
        val defaultTone = onRoot().captureToImage().countSlateish()

        useSeriesColor = true
        waitForIdle()
        assertTrue(defaultTone > 0, "the default tone drew nothing")
        assertEquals(
            0,
            onRoot().captureToImage().countSlateish(),
            "the labels kept the default tone instead of taking the series' colour"
        )
    }

    @Test
    fun a_value_label_sits_above_the_point_it_names() = runComposeUiTest {
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    showXLabels = false,
                    showYLabels = false,
                    yMax = 40f
                ),
                style = labelStyle()
            )
        }
        waitForIdle()

        // Headroom above the peak, so no label has to flip and the topmost ink of
        // each kind belongs to that peak.
        val image = onRoot().captureToImage()
        val label = assertNotNull(image.reddishRows(), "no value label was drawn")
        val line = assertNotNull(image.bluishRows(), "no line was drawn")
        assertTrue(
            label.first < line.first,
            "the label started at row ${label.first} and the line at ${line.first}"
        )
    }

    @Test
    fun a_label_with_no_room_above_flips_under_its_point() = runComposeUiTest {
        // Pinned so the highest point sits on the top of the plot: kept above, its
        // label would be drawn off the chart.
        setContent {
            Chart(
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    showXLabels = false,
                    showYLabels = false,
                    yMax = 25f
                ),
                style = labelStyle()
            )
        }
        waitForIdle()

        val image = onRoot().captureToImage()
        val label = assertNotNull(image.reddishRows(), "no value label was drawn")
        val line = assertNotNull(image.bluishRows(), "no line was drawn")
        assertTrue(
            label.first > line.first,
            "the topmost label began at row ${label.first}, above the point's ${line.first}"
        )
    }

    @Test
    fun labels_that_would_collide_are_dropped_rather_than_stacked() = runComposeUiTest {
        // Identical text on every point, so the ink is a straight count of how many
        // were drawn.
        var points by mutableStateOf(3)
        setContent {
            Chart(
                data = flatSeries(points),
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    showXLabels = false,
                    showYLabels = false
                ),
                style = labelStyle { _, _ -> "88888" }
            )
        }
        waitForIdle()
        val sparse = onRoot().captureToImage().countReddish()

        points = 30
        waitForIdle()
        val crowded = onRoot().captureToImage().countReddish()

        assertTrue(sparse > 0, "the sparse chart drew no labels")
        assertTrue(
            crowded < sparse * 4,
            "30 points drew $crowded ink against $sparse for 3 — labels are stacking"
        )
    }

    @Test
    fun labels_from_different_series_do_not_print_over_each_other() = runComposeUiTest {
        // Two series through the same points put every label in the same place. A
        // rule that only looked within one series would draw each of them twice.
        var copies by mutableStateOf(1)
        setContent {
            Chart(
                data = flatSeries(points = 3, copies = copies),
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    showXLabels = false,
                    showYLabels = false
                ),
                style = labelStyle { _, _ -> "88888" }
            )
        }
        waitForIdle()
        val one = onRoot().captureToImage().countReddish()

        copies = 2
        waitForIdle()
        val two = onRoot().captureToImage().countReddish()

        assertTrue(one > 0, "the single series drew no labels")
        // Its labels want a band a little above the first series', which overlaps.
        // Drawn, they would add most of another set; dropped, they add nothing but
        // the odd pixel where the second line changes what the glyphs blend with.
        assertTrue(
            two < one * 12 / 10,
            "a second series printed $two ink against $one for one series"
        )
    }

    @Test
    fun a_point_off_a_pinned_axis_prints_no_value() = runComposeUiTest {
        // Its dot and its crosshair are already suppressed. A value floating where
        // no point is drawn reads as belonging to the line.
        var pinned by mutableStateOf<Float?>(null)
        setContent {
            Chart(
                data = flatSeries(3),
                axisConfig = LineAxisConfig(
                    showGrid = false,
                    showXLabels = false,
                    showYLabels = false,
                    yMax = pinned
                ),
                style = labelStyle { _, _ -> "88888" }
            )
        }
        waitForIdle()
        val all = onRoot().captureToImage().countReddish()

        pinned = 20f
        waitForIdle()
        val clipped = onRoot().captureToImage().countReddish()

        assertTrue(all > 0, "no labels were drawn to begin with")
        assertTrue(clipped < all, "an off-axis point still printed $clipped ink of $all")
    }

    /**
     * [copies] identical series of [points] points along a flat line, the last one
     * raised clear of a 20f pin.
     */
    private fun flatSeries(points: Int, copies: Int = 1) = LineDataSet(
        series = List(copies) { copy ->
            LineSeries(
                id = "s$copy",
                label = "S$copy",
                points = List(points) { i ->
                    LineDataPoint(
                        x = i.toFloat(),
                        // Each copy a little above the last, so a label it drew
                        // would land in a band of its own rather than on top of
                        // the same pixels, where it could not be told apart.
                        y = if (i == points - 1) 30f else 10f + copy
                    )
                },
                color = Color.Blue
            )
        },
        contentDescription = "Flat"
    )

    // ── Pixel helpers ──

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

    private fun Int.isReddish(): Boolean {
        val r = (this shr 16) and 0xFF
        val g = (this shr 8) and 0xFF
        val b = this and 0xFF
        return r > 120 && r > g + 60 && r > b + 60
    }

    private fun Int.isBluish(): Boolean {
        val r = (this shr 16) and 0xFF
        val g = (this shr 8) and 0xFF
        val b = this and 0xFF
        return b > 120 && b > r + 60 && b > g + 60
    }

    private fun ImageBitmap.countReddish(): Int = pixels().count { it.isReddish() }

    private fun ImageBitmap.countReddish(rows: IntRange): Int {
        val pixels = pixels()
        return rows.sumOf { y -> (0 until width).count { x -> pixels[y * width + x].isReddish() } }
    }

    private fun ImageBitmap.countBluish(): Int = pixels().count { it.isBluish() }

    /** The default value-label tone: a dark slate, neither blue nor red. */
    private fun ImageBitmap.countSlateish(): Int = pixels().count { p ->
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        r in 30..90 && g in 45..105 && b in 65..125 && b > r
    }

    private fun ImageBitmap.reddishColumns(): IntRange? = extent(byRow = false) { it.isReddish() }

    private fun ImageBitmap.reddishRows(): IntRange? = extent(byRow = true) { it.isReddish() }

    private fun ImageBitmap.bluishRows(): IntRange? = extent(byRow = true) { it.isBluish() }

    /** The rows, or columns, that matching ink spans. Null when none was painted. */
    private fun ImageBitmap.extent(byRow: Boolean, matches: (Int) -> Boolean): IntRange? {
        val pixels = pixels()
        var lo = Int.MAX_VALUE
        var hi = -1
        pixels.forEachIndexed { i, p ->
            if (!matches(p)) return@forEachIndexed
            val at = if (byRow) i / width else i % width
            if (at < lo) lo = at
            if (at > hi) hi = at
        }
        return if (hi < lo) null else lo..hi
    }
}
