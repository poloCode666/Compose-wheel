package com.polo.composewheel

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opensource.svgaplayer.SVGAConfig
import com.opensource.svgaplayer.SVGALoadState
import com.opensource.svgaplayer.SVGAManager
import com.polo.composewheel.ui.loading.OneCallPagingList
import com.polo.composewheel.ui.loading.PagingPullToRefreshScreen
import com.polo.composewheel.ui.svga.SvgaPlayer
import com.polo.composewheel.ui.theme.ComposeWheelTheme
import kotlinx.coroutines.delay

// SVGA 测试数据
data class SvgaTestItem(
    val name: String,
    val url: String
)

val svgaTestItems = listOf(
    SvgaTestItem("Loading 加载动画", "https://res.lukeelive.com/FlqZmNr7JDvu-i88UIEhkLTiJIaN?imageslim"),
    SvgaTestItem("Gift Combo 0 礼物连击", "https://res.lukeelive.com/FgyBzF2CA9-VrR6saYpC-McQe2z0?imageslim"),
    SvgaTestItem("Call Match Receive 匹配接收", "https://res.lukeelive.com/Fkvsw0YDcEWLhljrWVrFsadU9Od1?imageslim"),
    SvgaTestItem("Luck Gift Banner 100 幸运礼物", "https://res.lukeelive.com/FhTlxVQWhIByJoxVADeNGtMpDGFQ?imageslim"),
    SvgaTestItem("Rocket 1 火箭动画", "https://res.lukeelive.com/FunKZltNzrEhyu479PmFsb6UPq2O?imageslim"),
    SvgaTestItem("Send Gift Circle 送礼物圆圈", "https://res.lukeelive.com/FrsZq6vMGkFIAew03A1ijiZnKjOL?imageslim"),
    SvgaTestItem("Music CD 音乐CD", "https://res.lukeelive.com/Fk2BM2vOPptUbZr29s-ipfXWrqZW?imageslim"),
    SvgaTestItem("Rank Charm Banner 魅力排行榜", "https://res.lukeelive.com/Fm_AUWLC3htNTkangwMPMs4tK5g9?imageslim"),
    SvgaTestItem("Audio Room Notify Up Mic 上麦通知", "https://res.lukeelive.com/FjLrRICDy9ljy9eT1-wiUnCOj1wv?imageslim"),
    SvgaTestItem("Audio 音频", "https://res.lukeelive.com/FlhkOIv1345r62WlYgDcpUkLrYmX?imageslim"),
    SvgaTestItem("Gift Combo 500 礼物连击500", "https://res.lukeelive.com/FpeZhnez6T_gAT3O0KjDmEzvoEsS?imageslim"),
    SvgaTestItem("Gift Combo 1000 礼物连击1000", "https://res.lukeelive.com/FgN2tVDFmIbhpedvJmt3sqBPK7Ru?imageslim"),
    SvgaTestItem("Gift Refund 1000 礼物退款1000", "https://res.lukeelive.com/FtH4U3oMedmvp3pwU1lwqDqfH7S_?imageslim"),
    SvgaTestItem("Home Audio Live 首页语音直播", "https://res.lukeelive.com/FqWYfWOCwivi4zqwIkubZr3t6xJf?imageslim"),
    SvgaTestItem("Home Live Pager Online Num 首页在线人数", "https://res.lukeelive.com/FuE-ym3vdrbQ-8mwlba2FdsxZ-_Q?imageslim"),
    SvgaTestItem("IC Room Gift 房间礼物图标", "https://res.lukeelive.com/Fs65NU7ybKEacGWBZn3BOsk7D1SC?imageslim"),
    SvgaTestItem("Mic Speak 麦克风说话", "https://res.lukeelive.com/Fr7pwaI4o95evF1lyKauLfTSCg7B?imageslim"),
    SvgaTestItem("Music CD Mini 音乐CD小", "https://res.lukeelive.com/FoBz3YChN97SzqxftNfyv96DM8mo?imageslim"),
    SvgaTestItem("Rocket Transition 火箭过渡", "https://res.lukeelive.com/FvmYFprXSK-Rx0NfYFoQyMtSUtTz?imageslim"),
    SvgaTestItem("Send Gift 送礼物", "https://res.lukeelive.com/FvRS4nrf3wGx6NcAPVjcWrVnWPga?imageslim"),
)

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 SVGA Manager
        Log.d(TAG, "初始化 SVGAManager")
        SVGAManager.init(
            context = applicationContext,
            memoryCacheCount = 16,
            httpCacheSize = 256 * 1024 * 1024L,
            logEnabled = true
        )
        Log.d(TAG, "SVGAManager 初始化完成")
        
        enableEdgeToEdge()
        setContent {
            ComposeWheelTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("SVGA Compose 测试") }) }
                ) { innerPadding ->
                    val context = LocalContext.current
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 单个 SVGA 测试
//                            SingleSvgaTest()
                            
                            // SVGA 测试列表
                            SvgaTestList()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个 SVGA 测试组件，用于调试
 */
@Composable
fun SingleSvgaTest() {
    val context = LocalContext.current
    val testUrl = remember { 
        "https://res.lukeelive.com/FhTlxVQWhIByJoxVADeNGtMpDGFQ?imageslim" 
    }
    
    var loadState by remember { mutableStateOf<SVGALoadState>(SVGALoadState.Idle) }
    
    LaunchedEffect(Unit) {
        Log.d("SingleSvgaTest", "SingleSvgaTest 组件创建，URL: $testUrl")
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "单个 SVGA 测试（调试用）",
                style = MaterialTheme.typography.titleLarge
            )
            
            Text(
                text = "URL: $testUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 显示加载状态
            Text(
                text = when (loadState) {
                    is SVGALoadState.Idle -> "状态: 空闲"
                    is SVGALoadState.Loading -> "状态: 加载中 (${(loadState as SVGALoadState.Loading).progress}%)"
                    is SVGALoadState.Success -> "状态: 加载成功"
                    is SVGALoadState.Error -> "状态: 加载失败 - ${(loadState as SVGALoadState.Error).exception.message}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when (loadState) {
                    is SVGALoadState.Success -> MaterialTheme.colorScheme.primary
                    is SVGALoadState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            
            // SVGA 播放器
            SvgaPlayer(
                url = testUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                config = SVGAConfig(
                    loopCount = 0, // 无限循环
                    autoPlay = true,
                    isCacheToMemory = false // 不缓存到内存，节省资源
                ),
                onLoadState = { state ->
                    loadState = state
                    Log.d("SingleSvgaTest", "加载状态变化: ${state.javaClass.simpleName}")
                    when (state) {
                        is SVGALoadState.Loading -> {
                            Log.d("SingleSvgaTest", "加载中: ${state.progress}%")
                        }
                        is SVGALoadState.Success -> {
                            Log.d("SingleSvgaTest", "加载成功！")
                        }
                        is SVGALoadState.Error -> {
                            Log.e("SingleSvgaTest", "加载失败", state.exception)
                        }
                        else -> {
                            Log.d("SingleSvgaTest", "状态: ${state.javaClass.simpleName}")
                        }
                    }
                },
                onAnimationStart = {
                    Log.d("SingleSvgaTest", "动画开始播放")
                },
                onAnimationEnd = {
                    Log.d("SingleSvgaTest", "动画播放结束")
                },
                onAnimationRepeat = {
                    Log.d("SingleSvgaTest", "动画重复播放")
                },
                onError = { throwable ->
                    Log.e("SingleSvgaTest", "发生错误", throwable)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SvgaTestList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(svgaTestItems) { item ->
            SvgaTestItemCard(item)
        }
    }
}

@Composable
fun SvgaTestItemCard(item: SvgaTestItem) {
    var loadState by remember { mutableStateOf<SVGALoadState>(SVGALoadState.Idle) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium
            )
            
            // 显示加载状态
            Text(
                text = when (loadState) {
                    is SVGALoadState.Idle -> "状态: 空闲"
                    is SVGALoadState.Loading -> "加载中: ${(loadState as SVGALoadState.Loading).progress}%"
                    is SVGALoadState.Success -> {
                        val success = loadState as SVGALoadState.Success
                        "✓ 加载成功 (${success.loadTimeMs}ms)"
                    }
                    is SVGALoadState.Error -> {
                        val error = loadState as SVGALoadState.Error
                        "✗ 加载失败 [${error.stage}]: ${error.exception.message?.take(80) ?: "未知错误"}"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (loadState) {
                    is SVGALoadState.Success -> MaterialTheme.colorScheme.primary
                    is SVGALoadState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            // 如果是错误状态，显示详细错误信息（可展开）
            if (loadState is SVGALoadState.Error) {
                val error = loadState as SVGALoadState.Error
                var expanded by remember { mutableStateOf(false) }
                
                Column {
                    TextButton(
                        onClick = { expanded = !expanded }
                    ) {
                        Text(
                            text = if (expanded) "隐藏详细信息" else "显示详细信息",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    
                    if (expanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "错误阶段: ${error.stage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (error.url != null) {
                                Text(
                                    text = "URL: ${error.url}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "异常类型: ${error.exception.javaClass.simpleName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "异常消息: ${error.exception.message ?: "无"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (error.additionalInfo != null) {
                                Text(
                                    text = "详细信息: ${error.additionalInfo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // SVGA 播放器
            SvgaPlayer(
                url = item.url,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                config = SVGAConfig(
                    loopCount = 0, // 无限循环
                    autoPlay = true,
                    isCacheToMemory = false // 不缓存到内存，节省资源
                ),
                onLoadState = { state ->
                    loadState = state
                    Log.d("SvgaTestItemCard", "${item.name} - 状态: ${state.javaClass.simpleName}")
                    when (state) {
                        is SVGALoadState.Loading -> {
                            Log.d("SvgaTestItemCard", "${item.name} - 加载中: ${state.progress}%")
                        }
                        is SVGALoadState.Success -> {
                            Log.d("SvgaTestItemCard", "${item.name} - 加载成功")
                        }
                        is SVGALoadState.Error -> {
                            val errorMsg = state.getDetailedMessage()
                            Log.e("SvgaTestItemCard", "========== ${item.name} - 加载失败 ==========")
                            Log.e("SvgaTestItemCard", errorMsg)
                            Log.e("SvgaTestItemCard", "阶段: ${state.stage}, URL: ${state.url ?: item.url}")
                            if (state.additionalInfo != null) {
                                Log.e("SvgaTestItemCard", "详细信息: ${state.additionalInfo}")
                            }
                            state.exception.printStackTrace()
                            Log.e("SvgaTestItemCard", "==========================================")
                        }
                        else -> {}
                    }
                },
                onAnimationStart = {
                    Log.d("SvgaTestItemCard", "${item.name} - 动画开始")
                },
                onAnimationEnd = {
                    Log.d("SvgaTestItemCard", "${item.name} - 动画结束")
                }
            )
            
            Text(
                text = item.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
