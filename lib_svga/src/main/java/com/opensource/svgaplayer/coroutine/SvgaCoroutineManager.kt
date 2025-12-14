package com.opensource.svgaplayer.coroutine

import com.opensource.svgaplayer.utils.log.LogUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor

/**
 * @Author     :Leo
 * Date        :2024/6/25
 * Description : 协程管理器
 */

object SvgaCoroutineManager {
    /**
     * 协程异常处理
     */
    private val coroutineExceptionHandler =
        CoroutineExceptionHandler { coroutineContext, throwable ->
            LogUtils.debug(
                "SvgaCoroutineManager",
                "coroutineContext $coroutineContext, error msg : ${throwable.message}"
            )
            throwable.printStackTrace()
        }

    private var dispatcher: ExecutorCoroutineDispatcher? = null

    private val job = SupervisorJob() // 使用 SupervisorJob，子协程失败不会影响其他协程

    /**
     * 协程作用域
     */
    private val scope = CoroutineScope(job)

    /**
     * 跟踪活跃的 Job，用于按 tag 取消
     */
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /**
     * 设置自定义线程池
     */
    @JvmStatic
    internal fun setThreadPoolExecutor(threadPoolExecutor: ThreadPoolExecutor) {
        dispatcher = threadPoolExecutor.asCoroutineDispatcher()
    }

    /**
     * 启动协程，优先采用自定义线程池，没有则使用共享IO线程池
     * @param tag 可选标签，用于后续取消
     */
    fun launchIo(
        handler: CoroutineExceptionHandler = coroutineExceptionHandler,
        childJob: Job = SupervisorJob(this.job),
        tag: String? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val newJob = scope.launch(
            (dispatcher ?: Dispatchers.IO) + handler + childJob
        ) { block.invoke(this) }

        // 如果有 tag，跟踪这个 job
        tag?.let {
            activeJobs[it] = newJob
            newJob.invokeOnCompletion { activeJobs.remove(tag) }
        }

        return newJob
    }

    /**
     * 在主线程启动协程
     */
    fun launchMain(
        handler: CoroutineExceptionHandler = coroutineExceptionHandler,
        childJob: Job = SupervisorJob(this.job),
        tag: String? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val newJob = scope.launch(Dispatchers.Main + handler + childJob) { block.invoke(this) }

        tag?.let {
            activeJobs[it] = newJob
            newJob.invokeOnCompletion { activeJobs.remove(tag) }
        }

        return newJob
    }

    /**
     * 根据 tag 取消协程
     */
    fun cancelByTag(tag: String) {
        activeJobs[tag]?.cancel()
        activeJobs.remove(tag)
    }

    /**
     * 取消所有协程
     */
    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    /**
     * 创建弱引用包装的回调，防止内存泄漏
     */
    fun <T> weakCallback(callback: T): WeakReference<T> {
        return WeakReference(callback)
    }
}