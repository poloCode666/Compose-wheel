package com.polo.composewheel.ui.loading

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel

import androidx.paging.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow



// ---------- ViewModel & Repository ----------
 class FeedViewModel : ViewModel() {
    private val repo = FeedRepository(FakeApi())

    // Compose 层直接 collectAsLazyPagingItems
    val pagerFlow: Flow<PagingData<String>> = repo.pagerFlow
}

class FeedRepository(private val api: FakeApi) {
    val pagerFlow: Flow<PagingData<String>> =
        Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = false,
                initialLoadSize = 40
            ),
            initialKey = 1,
            pagingSourceFactory = { FeedPagingSource(api) }
        ).flow
}

// ---------- PagingSource ----------
class FeedPagingSource(
    private val api: FakeApi
) : PagingSource<Int, String>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
        val page = params.key ?: 1
        val pageSize = params.loadSize.coerceAtMost(50)

        return try {
            val data = api.fetchPage(page = page, pageSize = pageSize)
            val nextKey = if (data.isEmpty()) null else page + 1
            val prevKey = if (page == 1) null else page - 1
            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, String>): Int? {
        // 让刷新尽量回到用户可见位置附近
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
    }
}

// ---------- 假数据源（模拟网络延迟/错误） ----------
class FakeApi {
    private val total = 200

    suspend fun fetchPage(page: Int, pageSize: Int): List<String> {
        delay(600) // 模拟网络耗时
        if (page == 3) {
            // 故意制造一次错误，看看重试逻辑
            // 注：正式项目请去掉
            // throw RuntimeException("Network error on page $page")
        }
        val start = (page - 1) * pageSize
        if (start >= total) return emptyList()
        val end = (start + pageSize).coerceAtMost(total)
        return (start until end).map { "Item #$it" }
    }
}

// ---------- UI 层 ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagingPullToRefreshScreen(vm: FeedViewModel = viewModel()) {
    val pagingItems = vm.pagerFlow.collectAsLazyPagingItems()

    // isRefreshing = 刷新阶段的加载状态（包括首屏）
    val isRefreshing = pagingItems.loadState.refresh is LoadState.Loading

    Scaffold(
        topBar = { TopAppBar(title = { Text("Paging + PullToRefresh") }) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                // 触发 Paging 刷新（重新请求第 1 页）
                pagingItems.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 列表内容（使用 index 访问 LazyPagingItems）
                items(pagingItems.itemCount) { idx ->
                    val text = pagingItems[idx]
                    if (text != null) {
                        ListItem(
                            headlineContent = { Text(text) }
                        )
                        Divider()
                    } else {
                        // 占位（可自定义骨架屏）
                        ListItem(
                            headlineContent = { Text("…") }
                        )
                        Divider()
                    }
                }

                // 底部：追加加载状态 Footer
                item(key = "appendFooter") {
                    AppendFooter(
                        loadState = pagingItems.loadState.append,
                        onRetry = { pagingItems.retry() }
                    )
                }
            }

            // 顶部：刷新阶段的错误（比如首屏出错）
            val refreshState = pagingItems.loadState.refresh
            if (refreshState is LoadState.Error && pagingItems.itemCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败：${refreshState.error.message ?: "未知错误"}")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { pagingItems.retry() }) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppendFooter(
    loadState: LoadState,
    onRetry: () -> Unit
) {
    when (loadState) {
        is LoadState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is LoadState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载更多失败：${loadState.error.message ?: "未知错误"}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }

        is LoadState.NotLoading -> {
            // 如果没有更多数据，可以给个“到底了”的提示（可选）
            /* if (noMore) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("没有更多了") }
            } */
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPaging() {
    MaterialTheme {
        // 预览不跑 Paging，放个空壳就好
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Preview")
            }
        }
    }
}
