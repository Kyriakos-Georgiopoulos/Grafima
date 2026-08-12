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

/** One bar of a series, id derived so a category reads `Q1-rev`. */
internal fun seriesBar(
    label: String,
    series: String,
    y: Float,
    seriesLabel: String = series.replaceFirstChar { it.uppercase() }
) = BarEntry(
    id = "$label-$series",
    xLabel = label,
    y = y,
    seriesId = series,
    seriesLabel = seriesLabel
)

/** A bar standing on its own, which is every bar before series existed. */
internal fun plainBar(label: String, y: Float) = BarEntry(id = label, xLabel = label, y = y)

/**
 * The chart the grouping, animation and accessibility suites all talk about: two
 * quarters, revenue against cost.
 */
internal val twoByTwoEntries = listOf(
    seriesBar("Q1", "rev", 45f, "Revenue"),
    seriesBar("Q1", "cost", 30f, "Cost"),
    seriesBar("Q2", "rev", 80f, "Revenue"),
    seriesBar("Q2", "cost", 52f, "Cost")
)
