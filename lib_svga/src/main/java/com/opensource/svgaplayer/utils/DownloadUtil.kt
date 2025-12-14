package com.opensource.svgaplayer.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object DownloadUtil {

    private const val CACHE_DIR = "anim_cache"
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    // Simple private logger that delegates to android.util.Log.d
    private fun log(tag: String, message: String) {
        Log.d(tag, message)
    }

    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, SecureRandom())
    }
    private val client = OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    abstract class DownloadCallback {
        abstract fun onSuccess(file: File)
        abstract fun onFailure(e: IOException)
        open fun onProgress(progress: Int){}

    }



    fun getFileFromResource(context: Context, resId: Int): File {
        val cacheDir = File(context.cacheDir, "resource_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val file = File(cacheDir, "$resId.png")
        if (!file.exists()) {
            context.resources.openRawResource(resId).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        }
        return file
    }





    private fun ensureCacheDir(context: Context): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return cacheDir
    }

    private fun buildTargetFile(cacheDir: File, url: String, name: String?): File {
        val fileName = name?.takeIf { it.isNotBlank() } ?: url.hashCode().toString()
        val target = File(cacheDir, fileName)
        target.parentFile?.mkdirs()
        return target
    }

    fun isFileCached(context: Context, url: String, name: String? = null): File? {
        val cacheDir = ensureCacheDir(context)
        val file = buildTargetFile(cacheDir, url, name)
        return if (file.exists()) file else null
    }

    /**
     * 下载 [url] 到应用外部下载目录并返回已下载的 [File]（若已缓存则直接返回）。
     *
     * 行为说明：
     * - 这是一个 suspending 函数，成功时返回下载好的 [File]。
     * - 失败时会抛出异常（通常为 IOException）。在异常发生时，函数会删除部分下载的临时文件以避免残留。
     *
     * 上游异常处理说明（更准确的用法）：
     * - 如果你需要在调用点对下载错误做局部、结构化的处理（例如重试、提示用户、更新 UI），应在协程内部使用 try/catch：
     *
     *    // 推荐：结构化错误处理
     *    lifecycleScope.launch {
     *        try {
     *            val file = DownloadUtil.downloadFile(context, url)
     *            // 使用 file
     *        } catch (e: IOException) {
     *            // 针对下载过程的结构化异常处理（重试、提示等）
     *        }
     *    }
     *
     * - 如果你想要一个全局的、用于记录或上报未被捕获异常的后备处理器，可在启动协程时传入 CoroutineExceptionHandler。
     *   注意：CoroutineExceptionHandler 只会处理“未被结构化捕获”的异常，若你在协程内部已经用 try/catch 捕获了异常，则 handler 不会被触发。
     *
     *    // 备份型全局处理（handler 仅处理未被捕获的异常）
     *    val handler = CoroutineExceptionHandler { _, throwable ->
     *        // 全局/未捕获异常处理（上报、日志、全局提示等）
     *    }
     *    lifecycleScope.launch(handler) {
     *        // 如果这里不加 try/catch，抛出的异常会被 handler 捕获并处理（此处无需再写 try/catch）
     *        val file = DownloadUtil.downloadFile(context, url)
     *        // 使用 file
     *    }
     *
     * - 综上：
     *   * 不需要同时在同一个协程里既传 handler 又对同一调用写 try/catch（那样 handler 不会被调用）。
     *   * 推荐在能做局部恢复或反馈的地方使用结构化 try/catch；在应用级或作用域级使用 CoroutineExceptionHandler 作为兜底/上报手段。
     *
     * 额外提示：
     * - 对于 async/Deferred，异常只有在调用 await() 时才会重新抛出并可被捕获（CoroutineExceptionHandler 不会在 await 之前触发）。
     * - CoroutineExceptionHandler 不会捕获 CancellationException（协程被取消时通常不视为错误）。
     */
    suspend fun downloadFile(context: Context, url: String, name: String? = null): File {
        return withContext(Dispatchers.IO) {
            val cacheDir = ensureCacheDir(context)
            val file = buildTargetFile(cacheDir, url, name)

            if (file.exists()) {
                log("DownloadFile", "file already cached: ${file.absolutePath}")
                return@withContext file
            }

            var success = false
            val startTime = System.currentTimeMillis()
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val body = response.body ?: throw IOException("Empty response body")
                    body.contentLength()
                    val inputStream = body.byteStream()

                    inputStream.use { input ->
                        FileOutputStream(file).use { output ->
                            val data = ByteArray(4096)
                            var total: Long = 0
                            var count: Int

                            while (input.read(data).also { count = it } != -1) {
                                total += count
                                output.write(data, 0, count)
//                                if (contentLength > 0) {
//                                    val progress = (total * 100 / contentLength).toInt()
//                                    log("DownloadFile", "Progress: $progress%")
//                                }
                            }

                            output.flush()
                        }
                    }
                }

                success = true

                val cost = System.currentTimeMillis() - startTime
                log("DownloadFile", "downloaded file to: ${file.absolutePath}, cost=${cost}ms")
                return@withContext file
            } finally {
                if (!success) {
                    log("DownloadFile", "download failed, deleting partial file")
                    file.delete()
                }
            }
        }
    }
}
