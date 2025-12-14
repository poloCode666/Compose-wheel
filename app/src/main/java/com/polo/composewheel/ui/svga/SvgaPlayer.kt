package com.polo.composewheel.ui.svga

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.opensource.svgaplayer.SVGAConfig
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGALoadState
import com.opensource.svgaplayer.SVGAVideoEntity
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * SVGA 动画播放器（Compose 版本）
 * 自动选择线程池：有缓存使用 Default（更快），需要下载使用 IO
 * 
 * @param url SVGA 文件的 URL
 * @param modifier Modifier
 * @param config SVGA 配置
 * @param onLoadState 加载状态回调（可选）
 * @param onAnimationStart 动画开始回调（可选）
 * @param onAnimationEnd 动画结束回调（可选）
 * @param onAnimationRepeat 动画重复回调（可选）
 * @param onError 错误回调（可选）
 */
@Composable
fun SvgaPlayer(
    url: String?,
    modifier: Modifier = Modifier,
    config: SVGAConfig? = null,
    onLoadState: ((SVGALoadState) -> Unit)? = null,
    onAnimationStart: (() -> Unit)? = null,
    onAnimationEnd: (() -> Unit)? = null,
    onAnimationRepeat: (() -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null
) {
    val TAG = "SvgaPlayer"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var svgaView by remember { mutableStateOf<SVGAImageView?>(null) }
    var loadState by remember { mutableStateOf<SVGALoadState>(SVGALoadState.Idle) }
    var isVisible by remember { mutableStateOf(false) }
    
    // 渐变动画：当加载成功时，从0f渐变到1f，持续400ms
    val alpha by animateFloatAsState(
        targetValue = if (loadState is SVGALoadState.Success) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "SvgaPlayerAlpha"
    )

    // 加载 SVGA 文件的辅助函数
    fun loadSvga(view: SVGAImageView, urlToLoad: String) {
        Log.d(TAG, "开始加载 SVGA，URL: $urlToLoad, View width: ${view.width}, height: ${view.height}")
        view.loadFromUrlFlow(urlToLoad, config)
            .onEach { state ->
                loadState = state
                Log.d(TAG, "Flow 状态更新: ${state.javaClass.simpleName}, URL: $urlToLoad")
                onLoadState?.invoke(state)
                
                when (state) {
                    is SVGALoadState.Success -> {
                        Log.d(TAG, "SVGA 加载成功，耗时: ${state.loadTimeMs}ms，设置回调，URL: $urlToLoad")
                        // 设置回调
                        view.callback = object : com.opensource.svgaplayer.SVGACallback {
                            override fun onStart() {
                                Log.d(TAG, "动画开始，URL: $urlToLoad")
                                onAnimationStart?.invoke()
                            }
                            override fun onPause() {
                                Log.d(TAG, "动画暂停，URL: $urlToLoad")
                            }
                            override fun onFinished() {
                                Log.d(TAG, "动画结束，URL: $urlToLoad")
                                onAnimationEnd?.invoke()
                            }
                            override fun onRepeat() {
                                Log.d(TAG, "动画重复，URL: $urlToLoad NAME")
                                onAnimationRepeat?.invoke()
                            }
                            override fun onStep(frame: Int, percentage: Double) {
                                // 不记录每一步，避免日志过多
                            }
                        }
                        Log.d(TAG, "回调设置完成，URL: $urlToLoad")
                    }
                    is SVGALoadState.Error -> {
                        val errorMsg = state.getDetailedMessage()
                        Log.e(TAG, "========== SVGA 加载失败 ==========")
                        Log.e(TAG, errorMsg)
                        Log.e(TAG, "完整错误信息:")
                        Log.e(TAG, "  阶段: ${state.stage}")
                        Log.e(TAG, "  URL: ${state.url ?: urlToLoad}")
                        Log.e(TAG, "  异常类型: ${state.exception.javaClass.name}")
                        Log.e(TAG, "  异常消息: ${state.exception.message ?: "无"}")
                        if (state.additionalInfo != null) {
                            Log.e(TAG, "  额外信息: ${state.additionalInfo}")
                        }
                        Log.e(TAG, "  堆栈跟踪:")
                        state.exception.printStackTrace()
                        Log.e(TAG, "====================================")
                        onError?.invoke(state.exception)
                    }
                    is SVGALoadState.Loading -> {
                        Log.d(TAG, "SVGA 加载中: ${state.progress}%, URL: $urlToLoad")
                    }
                    else -> {
                        Log.d(TAG, "SVGA 状态: ${state.javaClass.simpleName}, URL: $urlToLoad")
                    }
                }
            }
            .launchIn(scope)
    }

    // 监听生命周期，在不可见时暂停动画，可见时恢复
    DisposableEffect(lifecycleOwner, svgaView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "Lifecycle ON_PAUSE，暂停动画")
                    svgaView?.pauseAnimation()
                }
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "Lifecycle ON_RESUME，恢复动画（如果可见）")
                    if (loadState is SVGALoadState.Success && isVisible) {
                        svgaView?.resumeAnimation()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Log.d(TAG, "Lifecycle ON_DESTROY，清理资源")
                    svgaView?.clear()
                    svgaView = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            svgaView?.clear()
            svgaView = null
        }
    }
    
    // 监听可见性变化，控制动画播放/暂停
    LaunchedEffect(isVisible, loadState) {
        val currentView = svgaView
        if (currentView == null || loadState !is SVGALoadState.Success) {
            return@LaunchedEffect
        }
        
        if (isVisible) {
            Log.d(TAG, "View 变为可见，恢复动画")
            currentView.resumeAnimation()
        } else {
            Log.d(TAG, "View 变为不可见，暂停动画")
            currentView.pauseAnimation()
        }
    }

    // 当 url 或 view 变化时，重新加载
    LaunchedEffect(url, svgaView) {
        val currentUrl = url
        val currentView = svgaView
        
        Log.d(TAG, "LaunchedEffect 触发，URL: $currentUrl, View: $currentView")
        
        if (currentUrl.isNullOrEmpty() || currentView == null) {
            if (currentUrl.isNullOrEmpty()) {
                Log.d(TAG, "URL 为空，重置状态")
                loadState = SVGALoadState.Idle
            } else {
                Log.d(TAG, "View 未创建，等待创建")
            }
            return@LaunchedEffect
        }
        
        Log.d(TAG, "开始调用 loadSvga，URL: $currentUrl")
        loadSvga(currentView, currentUrl)
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                // 检查 View 是否在可见区域内
                val parentLayoutCoordinates = coordinates.parentLayoutCoordinates
                if (parentLayoutCoordinates != null) {
                    val windowBounds = parentLayoutCoordinates.size
                    val bounds = coordinates.boundsInParent()
                    
                    // 判断是否在可见区域内（至少有一部分可见）
                    val visible = bounds.top < windowBounds.height && 
                                  bounds.bottom > 0 && 
                                  bounds.left < windowBounds.width && 
                                  bounds.right > 0 &&
                                  bounds.width > 0 && 
                                  bounds.height > 0
                    
                    if (isVisible != visible) {
                        isVisible = visible
                        Log.d(TAG, "可见性变化: $visible, bounds: $bounds, windowBounds: $windowBounds")
                    }
                } else {
                    // 如果无法获取父布局坐标，默认认为可见
                    if (!isVisible) {
                        isVisible = true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                Log.d(TAG, "AndroidView factory 调用，创建 SVGAImageView")
                SVGAImageView(ctx).apply {
                    svgaView = this
                    Log.d(TAG, "SVGAImageView 创建完成，width: $width, height: $height")
                    // 设置清理策略，节省资源
                    clearsAfterDetached = true
                    clearsLastSourceOnDetached = true
                    // 使用 Forward 模式，动画结束后显示第一帧（节省内存）
                    fillMode = SVGAImageView.FillMode.Forward
                    Log.d(TAG, "SVGAImageView 配置完成")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            update = { view ->
                // 使用 View 的可见性检测
                val wasVisible = isVisible
                val nowVisible = view.isShown && view.visibility == View.VISIBLE
                
                // 进一步检查 View 是否真的在屏幕上可见
                val rect = Rect()
                val actuallyVisible = nowVisible && view.getGlobalVisibleRect(rect) && 
                                     rect.width() > 0 && rect.height() > 0
                
                if (wasVisible != actuallyVisible) {
                    isVisible = actuallyVisible
                    Log.d(TAG, "View 可见性变化: $actuallyVisible, rect: $rect, isShown: ${view.isShown}, visibility: ${view.visibility}")
                }
            }
        )

        // 显示加载状态（可选，可以根据需要自定义 UI）
        when (val currentState = loadState) {
            is SVGALoadState.Loading -> {
                // 可以显示加载指示器
                // CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            is SVGALoadState.Error -> {
                // 可以显示错误提示
                // Text(
                //     text = "加载失败: ${currentState.exception.message}",
                //     color = MaterialTheme.colorScheme.error
                // )
            }
            else -> {}
        }
    }
}