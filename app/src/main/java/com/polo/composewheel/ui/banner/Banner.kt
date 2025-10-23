package com.polo.composewheel.ui.banner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue


/* --------------------- Banner 组件 --------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> Banner(
    items: List<T>,
    modifier: Modifier = Modifier,
    // 自动轮播：<=0 或 enabled=false 关闭
    autoPlayMillis: Long = 3000,
    autoPlayEnabled: Boolean = true,
    // 指示器
    indicatorAlignment: Alignment = Alignment.BottomCenter,
    indicatorActiveColor: Color = Color.White,
    indicatorInactiveColor: Color = Color.White.copy(alpha = 0.35f),
    indicatorSpacing: Dp = 6.dp,
    indicatorPadding: PaddingValues = PaddingValues(bottom = 10.dp),
    // 露边与间距
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    pageSpacing: Dp = 12.dp,
    // 点击
    onItemClick: (index: Int, item: T) -> Unit = { _, _ -> },
    // 每页内容
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    val realCount = items.size.coerceAtLeast(1)
    // 无限循环起点
    val startIndex = remember(realCount) {
        val mid = Int.MAX_VALUE / 2
        mid - mid % realCount
    }
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { Int.MAX_VALUE }
    )

    // 自动轮播（拖动时暂停）
    AutoPlayCompat(
            pagerState = pagerState,
        enable = autoPlayEnabled && autoPlayMillis > 0 && realCount > 1,
        interval = autoPlayMillis
    )

    Box(
        modifier = modifier

            .fillMaxWidth() // ✅ 宽度占满

    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),      // ✅ 子项容器同样铺满宽度
            contentPadding = contentPadding,
            pageSize = PageSize.Fill,
            beyondViewportPageCount = 1,
            pageSpacing = pageSpacing,
            verticalAlignment = Alignment.CenterVertically,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
            userScrollEnabled = true,
            reverseLayout = false,
            snapPosition = SnapPosition.Start,
        ) { page ->
            val index = page % realCount
            val item = items[index]

            // 轻微缩放/透明度
            val pageOffset = ((pagerState.currentPage - page) +
                    pagerState.currentPageOffsetFraction).absoluteValue
            val scale = 1f - 0.08f * pageOffset.coerceIn(0f, 1f)
            val alpha = 1f - 0.4f * pageOffset.coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()                   // ✅ 每页宽度满
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            ) {
                // 交由外部决定高度（外部传入 .height(...) 或 .aspectRatio(...)）
                itemContent(index, item)
            }
        }

        // 指示器（默认：底部居中）
        if (realCount > 1) {
            val current = (pagerState.currentPage % realCount).coerceAtLeast(0)
            Row(
                modifier = Modifier
                    .align(indicatorAlignment)
                    .padding(indicatorPadding),
                horizontalArrangement = Arrangement.spacedBy(indicatorSpacing)
            ) {
                repeat(realCount) { i ->
                    val active = i == current
                    Box(
                        Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .background(
                                color = if (active) indicatorActiveColor else indicatorInactiveColor,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutoPlayCompat(
    pagerState: PagerState,
    enable: Boolean,
    interval: Long
) {
    if (!enable) return
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(true) }

    // 拖动时暂停
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            playing = !scrolling
        }
    }
    // 定时推进
    LaunchedEffect(playing, interval) {
        if (!playing) return@LaunchedEffect
        while (isActive && playing) {
            delay(interval)
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }
}

/* --------------------- Mock & Preview --------------------- */

data class BannerMock(val title: String, val color: Color)

private fun mockBanner(): List<BannerMock> = listOf(
    BannerMock("Top Picks", Color(0xFF5B8FFF)),
    BannerMock("Super Sale", Color(0xFF7B61FF)),
    BannerMock("New Arrivals", Color(0xFFFF8FAB)),
    BannerMock("Daily Surprise", Color(0xFF3DDC84)),
)


