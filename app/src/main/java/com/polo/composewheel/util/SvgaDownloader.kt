package com.polo.composewheel.util

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * SVGA 文件下载管理器
 * - 使用 OkHttp 进行网络请求
 * - 支持 SSL 证书信任
 * - 基于 URL 的 MD5 作为缓存 key
 * - 支持回调方式和挂起函数两种调用方式
 * - 支持进度回调
 */
object SvgaDownloader {

    private const val TAG = "SvgaDownloader"
    private const val CACHE_DIR_NAME = "svga_cache"
    private const val CONNECT_TIMEOUT = 15L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L

    // SSL 信任所有证书（仅用于开发测试，生产环境请使用正确的证书验证）
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    })

    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
    }

    // OkHttp 客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    /**
     * 下载结果回调接口
     */
    abstract class DownloadCallback {
        abstract fun onSuccess(file: File)
        abstract fun onError(e: Exception)
        open fun onProgress(progress: Int) {}
    }

    /**
     * 获取缓存目录
     * 优先使用外部存储，不可用时使用应用内部缓存目录
     */
    private fun getCacheDir(context: Context): File {
        val cacheDir = try {
            // 尝试使用外部存储
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (externalDir != null) {
                File(externalDir, CACHE_DIR_NAME)
            } else {
                // 降级到内部缓存
                File(context.cacheDir, CACHE_DIR_NAME)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get external storage, using internal cache", e)
            File(context.cacheDir, CACHE_DIR_NAME)
        }

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * 根据 URL 生成缓存文件名（使用 MD5）
     */
    private fun urlToFileName(url: String): String {
        val md5 = MessageDigest.getInstance("MD5")
        val digest = md5.digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".svga"
    }

    /**
     * 获取缓存文件
     */
    private fun getCacheFile(context: Context, url: String): File {
        return File(getCacheDir(context), urlToFileName(url))
    }

    /**
     * 检查缓存是否存在
     */
    fun isCached(context: Context, url: String): Boolean {
        return getCacheFile(context, url).exists()
    }

    /**
     * 获取缓存文件（如果存在）
     */
    fun getCachedFile(context: Context, url: String): File? {
        val file = getCacheFile(context, url)
        return if (file.exists()) file else null
    }

    /**
     * 清除所有缓存
     */
    fun clearCache(context: Context) {
        getCacheDir(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * 清除指定 URL 的缓存
     */
    fun clearCache(context: Context, url: String) {
        getCacheFile(context, url).delete()
    }

    /**
     * 获取缓存大小（字节）
     */
    fun getCacheSize(context: Context): Long {
        return getCacheDir(context).listFiles()?.sumOf { it.length() } ?: 0L
    }

    // ==================== 同步下载方式 ====================

    /**
     * 下载或获取缓存（同步方式，需在非主线程调用）
     */
    @Throws(IOException::class)
    fun downloadSync(
        context: Context,
        url: String,
        forceDownload: Boolean = false,
        onProgress: ((progress: Int) -> Unit)? = null
    ): File {
        val cacheFile = getCacheFile(context, url)

        // 检查缓存
        if (!forceDownload && cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "使用缓存文件: ${cacheFile.name}, 大小: ${cacheFile.length()}")
            onProgress?.invoke(100)
            return cacheFile
        }

        // 下载文件
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        var isDownloadSuccess = false

        try {
            Log.d(TAG, "开始下载: $url")
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error code: ${response.code}")
                }

                val body = response.body ?: throw IOException("Response body is null")
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(4096)
                    var total: Long = 0
                    var count: Int

                    while (inputStream.read(buffer).also { count = it } != -1) {
                        total += count
                        output.write(buffer, 0, count)

                        // 更新进度
                        if (contentLength > 0) {
                            val progress = (total * 100 / contentLength).toInt()
                            onProgress?.invoke(progress)
                        }
                    }

                    output.flush()
                    isDownloadSuccess = true
                    Log.d(TAG, "下载完成: ${cacheFile.name}, 大小: $total 字节")
                }
            }

            // 下载完成，重命名为正式文件
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            if (!tempFile.renameTo(cacheFile)) {
                throw IOException("Failed to rename temp file to cache file")
            }

            return cacheFile

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "非法参数异常: ${e.message}", e)
            throw IOException("Invalid URL or parameters: ${e.message}", e)
        } catch (e: IOException) {
            Log.e(TAG, "下载失败: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "未知错误: ${e.message}", e)
            throw IOException("Download failed: ${e.message}", e)
        } finally {
            // 清理失败的临时文件
            if (!isDownloadSuccess && tempFile.exists()) {
                Log.d(TAG, "清理临时文件: ${tempFile.name}")
                tempFile.delete()
            }
        }
    }

    // ==================== 回调方式 ====================

    /**
     * 下载或获取缓存（回调方式）
     * @param context 上下文
     * @param url SVGA 文件的 URL
     * @param forceDownload 是否强制重新下载（忽略缓存）
     * @param callback 下载结果回调
     */
    fun download(
        context: Context,
        url: String,
        forceDownload: Boolean = false,
        callback: DownloadCallback
    ) {
        Thread {
            try {
                val file = downloadSync(context, url, forceDownload) { progress ->
                    callback.onProgress(progress)
                }
                callback.onSuccess(file)
            } catch (e: Exception) {
                callback.onError(e)
            }
        }.start()
    }

    // ==================== 挂起函数方式 ====================

    /**
     * 下载或获取缓存（挂起函数方式）
     * @param context 上下文
     * @param url SVGA 文件的 URL
     * @param forceDownload 是否强制重新下载（忽略缓存）
     * @return 下载完成的文件
     */
    suspend fun downloadSuspend(
        context: Context,
        url: String,
        forceDownload: Boolean = false,
        onProgress: ((progress: Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        downloadSync(context, url, forceDownload) { progress ->
            // 在主线程回调进度
            MainScope().launch {
                onProgress?.invoke(progress)
            }
        }
    }

    /**
     * 下载或获取缓存（挂起函数方式，带进度回调）
     * @param context 上下文
     * @param url SVGA 文件的 URL
     * @param forceDownload 是否强制重新下载（忽略缓存）
     * @param onProgress 进度回调 (downloadedBytes, totalBytes)，totalBytes 可能为 -1（未知大小）
     * @return 下载完成的文件
     */
    suspend fun downloadWithProgress(
        context: Context,
        url: String,
        forceDownload: Boolean = false,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile(context, url)

        Log.d(TAG, "下载URL: $url")

        // 检查缓存
        if (!forceDownload && cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "使用缓存文件: ${cacheFile.absolutePath}")
            val fileSize = cacheFile.length()
            withContext(Dispatchers.Main) {
                onProgress?.invoke(fileSize, fileSize)
            }
            return@withContext cacheFile
        }

        // 下载文件
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        var isDownloadSuccess = false

        try {
            Log.d(TAG, "开始下载: $url")
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error code: ${response.code}")
                }

                val body = response.body ?: throw IOException("Response body is null")
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(4096)
                    var total: Long = 0
                    var count: Int

                    while (inputStream.read(buffer).also { count = it } != -1) {
                        total += count
                        output.write(buffer, 0, count)

                        // 在主线程回调进度
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(total, contentLength)
                        }
                    }

                    output.flush()
                    isDownloadSuccess = true
                    Log.d(TAG, "下载完成: ${cacheFile.name}, 大小: $total 字节")
                }
            }

            // 下载完成，重命名为正式文件
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            if (!tempFile.renameTo(cacheFile)) {
                throw IOException("Failed to rename temp file to cache file")
            }

            cacheFile

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "非法参数异常: ${e.message}", e)
            throw IOException("Invalid URL or parameters: ${e.message}", e)
        } catch (e: IOException) {
            Log.e(TAG, "下载失败: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "未知错误: ${e.message}", e)
            throw IOException("Download failed: ${e.message}", e)
        } finally {
            // 清理失败的临时文件
            if (!isDownloadSuccess && tempFile.exists()) {
                Log.d(TAG, "清理临时文件: ${tempFile.name}")
                tempFile.delete()
            }
        }
    }

    // ==================== 便捷扩展 ====================

    /**
     * 下载 SVGA 文件的便捷挂起函数
     * 优先从缓存获取，没有缓存则下载
     *
     * @param context 上下文
     * @param url SVGA 文件的 URL
     * @return 下载成功返回文件，失败返回 null
     */
    suspend fun downloadSvgaFile(
        context: Context,
        url: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            // 先检查缓存
            val cachedFile = getCachedFile(context, url)
            if (cachedFile != null && cachedFile.length() > 0) {
                Log.d(TAG, "从缓存获取 SVGA 文件: ${cachedFile.absolutePath}")
                return@withContext cachedFile
            }

            // 没有缓存，开始下载
            Log.d(TAG, "缓存不存在，开始下载: $url")
            downloadSync(context, url, forceDownload = false)
        } catch (e: Exception) {
            Log.e(TAG, "下载 SVGA 文件失败: ${e.message}", e)
            null
        }
    }

    /**
     * 使用 suspendCancellableCoroutine 包装回调方式（可取消）
     */
    suspend fun downloadCancellable(
        context: Context,
        url: String,
        forceDownload: Boolean = false
    ): File = suspendCancellableCoroutine { continuation ->
        val thread = Thread {
            try {
                val file = downloadSync(context, url, forceDownload)
                if (continuation.isActive) {
                    continuation.resume(file)
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }

        thread.start()

        continuation.invokeOnCancellation {
            thread.interrupt()
        }
    }
}

