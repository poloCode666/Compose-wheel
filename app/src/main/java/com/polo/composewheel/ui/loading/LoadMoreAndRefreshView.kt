@file:OptIn(ExperimentalMaterial3Api::class)

package com.polo.composewheel.ui.loading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.collectAsLazyPagingItems

/**
 * 一次调用即可运行的分页列表（5 个参数：含 index+item 的行 UI）
 */
@Composable
fun <T : Any> OneCallPagingList(
    needRefresh: Boolean = true,
    needLoadMore: Boolean = true,
    pageSize: Int = 20,
    requestPage: suspend (size: Int, pageNo: Int) -> List<T>,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    // 内部自建 Pager + PagingSource
    val flow = remember(needRefresh, needLoadMore, pageSize, requestPage) {
        Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize ,
                prefetchDistance = (pageSize / 4).coerceAtLeast(1),
                enablePlaceholders = false
            ),
            initialKey = 1,
            pagingSourceFactory = {
                object : PagingSource<Int, T>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
                        val pageNo = params.key ?: 1
                        if (!needLoadMore && pageNo > 1) {
                            return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
                        }
                        return try {
                            val size = params.loadSize.coerceAtLeast(1)
                            val data = requestPage(size, pageNo)
                            val prevKey = if (pageNo == 1) null else pageNo - 1
                            val nextKey = if (data.isEmpty() || !needLoadMore) null else pageNo + 1
                            LoadResult.Page(data, prevKey, nextKey)
                        } catch (t: Throwable) {
                            LoadResult.Error(t)
                        }
                    }

                    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
                        val anchor = state.anchorPosition ?: return null
                        val page = state.closestPageToPosition(anchor) ?: return null
                        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
                    }
                }
            }
        ).flow
    }

    val pagingItems = flow.collectAsLazyPagingItems()
    val isRefreshing = pagingItems.loadState.refresh is LoadState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (needRefresh) pagingItems.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ✅ 不用 itemsIndexed 扩展；用标准 items(count) + 索引访问，版本无关
            items(count = pagingItems.itemCount) { index ->
                val item = pagingItems[index]
                if (item != null) {
                    itemContent(index, item)
                } else {
                    // 可选：骨架占位
                    ListItem(headlineContent = { Text("…") })
                }
                Divider()
            }

            // 底部 Footer：加载更多状态
            item {
                when (val s = pagingItems.loadState.append) {
                    is LoadState.Loading -> Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is LoadState.Error -> Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("加载更多失败：${s.error.message ?: "未知错误"}")
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { pagingItems.retry() }) { Text("重试") }
                        }
                    }


                    else -> Unit
                }
            }
        }

        // 首屏/刷新失败且没有任何数据时显示整页错误
        val rs = pagingItems.loadState.refresh
        if (rs is LoadState.Error && pagingItems.itemCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败：${rs.error.message ?: "未知错误"}")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { pagingItems.retry() }) { Text("重试") }
                }
            }
        }
    }
}
