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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlotInsetsTest {

    private fun insets(
        yTitleHeight: Float = 0f,
        xTitleHeight: Float = 0f,
        isRtl: Boolean = false,
        yLabelWidth: Float = 30f,
        xLabelHeight: Float = 12f,
        dotClearance: Float = 0f
    ) = computePlotInsets(
        into = PlotInsets(),
        width = 400f,
        height = 300f,
        gap = 8f,
        yLabelWidth = yLabelWidth,
        xLabelHeight = xLabelHeight,
        yTitleHeight = yTitleHeight,
        xTitleHeight = xTitleHeight,
        isRtl = isRtl,
        dotClearance = dotClearance
    )

    @Test
    fun `without titles the plot rectangle is unchanged`() {
        val r = insets()
        assertEquals(38f, r.left)
        assertEquals(8f, r.top)
        assertEquals(392f, r.right)
        assertEquals(280f, r.bottom)
    }

    @Test
    fun `a y title claims its height as width because it is drawn rotated`() {
        val plain = insets()
        val titled = insets(yTitleHeight = 14f)
        assertEquals(plain.left + 14f + 16f, titled.left)
        assertEquals(plain.right, titled.right)
        assertEquals(plain.bottom, titled.bottom)
    }

    @Test
    fun `an x title takes room below the x labels`() {
        val plain = insets()
        val titled = insets(xTitleHeight = 14f)
        assertEquals(plain.bottom - 14f - 16f, titled.bottom)
        assertEquals(plain.left, titled.left)
    }

    @Test
    fun `the y title moves to the right edge in RTL`() {
        val rtl = insets(yTitleHeight = 14f, isRtl = true)
        assertEquals(8f, rtl.left)
        // 400 - gap - labels - title - 2 gaps
        assertEquals(332f, rtl.right)
        assertEquals(280f, rtl.bottom)
    }

    @Test
    fun `an x title is unaffected by direction`() {
        // 300 - gap - labels - title - 2 gaps
        assertEquals(250f, insets(xTitleHeight = 14f).bottom)
        assertEquals(250f, insets(xTitleHeight = 14f, isRtl = true).bottom)
    }

    @Test
    fun `a zero-height title costs nothing but any height also costs a gap`() {
        assertEquals(38f, insets(yTitleHeight = 0f).left)
        assertEquals(38f + 1f + 16f, insets(yTitleHeight = 1f).left)
    }

    @Test
    fun `both titles together leave a plot rectangle that is still usable`() {
        val r = insets(yTitleHeight = 14f, xTitleHeight = 14f)
        assertEquals(68f, r.left)
        assertEquals(8f, r.top)
        assertEquals(392f, r.right)
        assertEquals(250f, r.bottom)
    }

    @Test
    fun `hidden labels give their room to the plot in either direction`() {
        val ltr = insets(yLabelWidth = 0f, xLabelHeight = 0f)
        assertEquals(8f, ltr.left)
        assertEquals(292f, ltr.bottom)

        val rtl = insets(yLabelWidth = 0f, xLabelHeight = 0f, isRtl = true)
        assertEquals(8f, rtl.left)
        assertEquals(392f, rtl.right)
    }

    @Test
    fun `a dot clearance holds the plot off every edge`() {
        val plain = insets()
        val cleared = insets(dotClearance = 20f)
        assertEquals(plain.left + 20f, cleared.left)
        assertEquals(plain.top + 20f, cleared.top)
        assertEquals(plain.right - 20f, cleared.right)
        assertEquals(plain.bottom - 20f, cleared.bottom)
    }

    @Test
    fun `a clearance leaves a plot even on a chart barely wider than its labels`() {
        // The clearance is taken from both sides and the gap from both again, so a
        // third of what is left is the most that can be given away.
        val tiny = computePlotInsets(
            into = PlotInsets(),
            width = 60f,
            height = 60f,
            gap = 8f,
            yLabelWidth = 30f,
            xLabelHeight = 12f,
            yTitleHeight = 0f,
            xTitleHeight = 0f,
            isRtl = false,
            dotClearance = 10_000f
        )
        assertTrue(tiny.right > tiny.left, "left ${tiny.left} right ${tiny.right}")
        assertTrue(tiny.bottom > tiny.top, "top ${tiny.top} bottom ${tiny.bottom}")
    }

    @Test
    fun `the applied clearance is reported so labels can stand off by the same amount`() {
        val roomy = insets(dotClearance = 20f)
        assertEquals(20f, roomy.sideClearance)
        assertEquals(20f, roomy.stackClearance)

        val clamped = insets(dotClearance = 10_000f)
        assertTrue(clamped.sideClearance < 10_000f, "side ${clamped.sideClearance} never clamped")
        assertEquals(clamped.left, 8f + clamped.sideClearance + 30f)
    }

    @Test
    fun `a clearance wider than the chart crowds the plot rather than inverting it`() {
        // A radius can exceed the chart it is drawn on; unclamped it drove right past
        // left, which maps the axis backwards and draws the curve mirrored.
        val huge = insets(dotClearance = 10_000f)
        assertTrue(huge.right > huge.left, "left ${huge.left} right ${huge.right}")
        assertTrue(huge.bottom > huge.top, "top ${huge.top} bottom ${huge.bottom}")
    }

    @Test
    fun `a clearance mirrors with the label band in RTL`() {
        val rtl = insets(dotClearance = 20f, isRtl = true)
        val plain = insets(isRtl = true)
        assertEquals(plain.left + 20f, rtl.left)
        assertEquals(plain.right - 20f, rtl.right)
    }
}
