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

package io.grafima.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grafima.sample.theme.LocalDemoColors
import io.grafima.sample.theme.ThemeToggle
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class ChartPage(val title: String) {
    Bar("Bar"),
    Pie("Pie"),
    Radar("Radar"),
    Gauge("Gauge"),
    Line("Line")
}

@Composable
fun ChartsDemoScreen(
    darkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val pages = ChartPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val colors = LocalDemoColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Grafima",
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )
            ThemeToggle(isDark = darkTheme, onToggle = onToggleTheme)
        }

        ChartTabBar(
            pages = pages,
            pagerState = pagerState,
            onTabClick = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (pages[page]) {
                ChartPage.Bar -> BarChartDemoScreen()
                ChartPage.Pie -> PieChartDemoScreen()
                ChartPage.Radar -> RadarChartDemoScreen()
                ChartPage.Gauge -> GaugeChartDemoScreen()
                ChartPage.Line -> LineChartDemoScreen()
            }
        }
    }
}

@Composable
private fun ChartTabBar(
    pages: List<ChartPage>,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabCount = pages.size
    val colors = LocalDemoColors.current

    // `currentPageOffsetFraction` changes on every frame of a swipe, so it is
    // only ever read from inside a draw lambda — reading it up here would
    // rebuild the whole bar sixty times a second. `currentPage` is safe to read
    // in composition: it flips once, when the swipe crosses the halfway mark.
    val scrollPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .background(colors.surfaceMuted, RoundedCornerShape(16.dp))
            .padding(4.dp)
            .drawBehind {
                val tabWidth = size.width / tabCount
                drawRoundRect(
                    color = colors.onSurface,
                    topLeft = Offset(x = tabWidth * scrollPosition(), y = 0f),
                    size = Size(width = tabWidth, height = size.height),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            }
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pages.forEachIndexed { index, page ->
            val selected = pagerState.currentPage == index

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onTabClick(index) }
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = page.title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    // Resolved during draw, so the label tracks the swipe
                    // continuously without recomposing to do it.
                    color = {
                        lerp(
                            start = colors.background,
                            stop = colors.onSurfaceMuted,
                            fraction = abs(scrollPosition() - index).coerceIn(0f, 1f)
                        )
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun ChartsDemoScreenPreview() {
    ChartsDemoScreen()
}
