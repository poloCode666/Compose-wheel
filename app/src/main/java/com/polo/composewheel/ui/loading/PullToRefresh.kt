package com.polo.composewheel.ui.loading
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PullToRefresh {
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshScreen() {
    var isRefreshing by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf((1..20).map { "Item $it" }) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pull to Refresh (M3)") }) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRefreshing) {
                    isRefreshing = true
                    scope.launch {
                        // 模拟网络请求
                        delay(1200)
                        // 刷新逻辑：这里简单做成“加时间戳的前缀”
                        val new = (1..20).map { "Refreshed $it" }
                        data = new
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(data) { item ->
                    ListItem(
                        headlineContent = { Text(item) }
                    )
                }
            }
        }
    }
}