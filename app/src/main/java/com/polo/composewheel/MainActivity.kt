package com.polo.composewheel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.polo.composewheel.ui.loading.OneCallPagingList
import com.polo.composewheel.ui.loading.PagingPullToRefreshScreen
import com.polo.composewheel.ui.theme.ComposeWheelTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeWheelTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Paging + PullToRefresh") }) }
                ) { innerPadding ->
                    // Add a button that launches SvgaTestActivity and keep the existing list below it
                    val context = LocalContext.current
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Column {
                            Button(onClick = {
                                val intent = Intent(context, SvgaTestActivity::class.java)
                                context.startActivity(intent)
                            }) {
                                Text(text = "Open SVGA Test")
                            }

                            OneCallPagingList(
                                needRefresh = true,
                                needLoadMore = true,
                                requestPage = { size, pageNo ->
                                    fetchPage(size, pageNo)
                                },   // 你的真实请求（第一页=1）
                                pageSize = 20
                            ) { index, item ->            // 自定义行 UI：给你 index + item
                                ListItem(
                                    headlineContent = { Text(item) },
                                    supportingContent = { Text("index = $index") }
                                )
                            }
                        }
                    }
                }


            }
        }
    }
}

private suspend fun fetchPage(size: Int, pageNo: Int): List<String> {
    delay(1000) // 模拟网络
    val total = 120
    val start = (pageNo - 1) * size
    if (start >= total) return emptyList()
    val end = (start + size).coerceAtMost(total)
    return (start until end).map { "Item #$it (page=$pageNo,size=$size)" }
}

@Composable
fun Greeting( modifier: Modifier = Modifier) {
    Box(modifier){
        PagingPullToRefreshScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ComposeWheelTheme {
        Greeting()
    }
}
