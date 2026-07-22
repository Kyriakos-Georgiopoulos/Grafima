package io.grafima.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun ChartsDemoScreen() {
    val pages = ChartPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFF3F4F6))
        .statusBarsPadding()) {
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .background(Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
            .padding(4.dp)
            .drawBehind {
                val tabWidth = size.width / tabCount
                val scrollPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
                drawRoundRect(
                    color = Color(0xFF111827),
                    topLeft = Offset(tabWidth * scrollPosition, 0f),
                    size = Size(tabWidth, size.height),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        pages.forEachIndexed { index, page ->
            val scrollPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val distance = abs(scrollPosition - index).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabClick(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = page.title,
                    color = lerp(Color.White, Color(0xFF6B7280), distance),
                    fontWeight = if (distance < 0.5f) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}