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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import io.grafima.sample.theme.LocalIsWideLayout
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

private val RailWidth = 88.dp
private val RailTabHeight = 56.dp

@Composable
fun ChartsDemoScreen(
    darkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val pages = ChartPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val onTabClick: (Int) -> Unit = { index ->
        coroutineScope.launch { pagerState.animateScrollToPage(index) }
    }

    val insets = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()

    if (LocalIsWideLayout.current) {
        Row(modifier = insets) {
            ChartRail(
                pages = pages,
                pagerState = pagerState,
                onTabClick = onTabClick,
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme
            )
            ChartPager(
                pages = pages,
                pagerState = pagerState,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(modifier = insets) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Grafima",
                    color = LocalDemoColors.current.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    modifier = Modifier.weight(1f)
                )
                ThemeToggle(isDark = darkTheme, onToggle = onToggleTheme)
            }

            ChartTabBar(pages = pages, pagerState = pagerState, onTabClick = onTabClick)

            ChartPager(
                pages = pages,
                pagerState = pagerState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ChartPager(
    pages: List<ChartPage>,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        when (pages[page]) {
            ChartPage.Bar -> BarChartDemoScreen()
            ChartPage.Pie -> PieChartDemoScreen()
            ChartPage.Radar -> RadarChartDemoScreen()
            ChartPage.Gauge -> GaugeChartDemoScreen()
            ChartPage.Line -> LineChartDemoScreen()
        }
    }
}

// `currentPageOffsetFraction` changes every frame of a swipe, so both bars read
// it only from inside a draw lambda — reading it in composition would rebuild
// the whole bar sixty times a second. `currentPage` is safe to read up front:
// it flips once, when the swipe crosses the halfway mark.

@Composable
private fun ChartTabBar(
    pages: List<ChartPage>,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabCount = pages.size
    val colors = LocalDemoColors.current
    val scrollPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
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
            ChartTab(
                page = page,
                index = index,
                selected = pagerState.currentPage == index,
                scrollPosition = scrollPosition,
                onClick = { onTabClick(index) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ChartRail(
    pages: List<ChartPage>,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabCount = pages.size
    val colors = LocalDemoColors.current
    val scrollPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    Column(
        modifier = modifier
            .width(RailWidth)
            .fillMaxHeight()
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ThemeToggle(isDark = darkTheme, onToggle = onToggleTheme)

        Spacer(Modifier.height(16.dp))

        // Tabs keep a fixed height and sit at the top. Dividing the rail's full
        // height between them instead would leave them stranded far apart on a
        // tall screen.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted, RoundedCornerShape(16.dp))
                .padding(4.dp)
                .drawBehind {
                    val tabHeight = RailTabHeight.toPx()
                    drawRoundRect(
                        color = colors.onSurface,
                        topLeft = Offset(x = 0f, y = tabHeight * scrollPosition()),
                        size = Size(width = size.width, height = tabHeight),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                }
                .selectableGroup()
        ) {
            pages.forEachIndexed { index, page ->
                ChartTab(
                    page = page,
                    index = index,
                    selected = pagerState.currentPage == index,
                    scrollPosition = scrollPosition,
                    onClick = { onTabClick(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RailTabHeight)
                )
            }
        }
    }
}

@Composable
private fun ChartTab(
    page: ChartPage,
    index: Int,
    selected: Boolean,
    scrollPosition: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDemoColors.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = page.title,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
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

@Preview
@Composable
private fun ChartsDemoScreenPreview() {
    ChartsDemoScreen()
}
