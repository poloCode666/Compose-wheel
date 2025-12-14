package com.opensource.svgaplayer.download

import android.net.http.HttpResponseCache
import com.opensource.svgaplayer.cache.SVGAFileCache
import com.opensource.svgaplayer.coroutine.SvgaCoroutineManager
import com.opensource.svgaplayer.utils.log.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

open class FileDownloader {

    companion object {
        private const val TAG = "SVGAFileDownloader"
        private const val SIZE = 4096
        private const val TIMEOUT = 30_000

        private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        private val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        private val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(TIMEOUT.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * 下载文件
     * @param url URL
     * @param complete 完成回调
     * @param failure 失败回调
     * @return 协程 job ，可取消下载任务
     */
    open fun resume(
        url: URL, complete: (inputStream: InputStream) -> Unit, failure: (e: Exception) -> Unit = {}
    ): Job {
        return SvgaCoroutineManager.launchIo {
            //下载到缓存地址
            val cacheKey = SVGAFileCache.buildCacheKey(url)
            val cacheFile = SVGAFileCache.buildCacheFile(cacheKey)
            var success = false
            try {
                LogUtils.info(
                    TAG, "================ file download start ================ " +
                            "\r\n url = $url"
                )
                if (HttpResponseCache.getInstalled() == null) {
                    LogUtils.error(
                        TAG,
                        "在配置 HttpResponseCache 前 SVGAParser 无法缓存."
                                + " 查看 https://github.com/yyued/SVGAPlayer-Android#cache "
                    )
                }

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }

                    val body = response.body ?: throw IOException("Empty response body")

                    if (!cacheFile.exists()) {
                        cacheFile.createNewFile()
                    }

                    body.byteStream().use { inputStream ->
                        FileOutputStream(cacheFile).use { output ->
                            val buffer = ByteArray(SIZE)
                            while (isActive) {
                                val bytesRead = inputStream.read(buffer)
                                if (bytesRead == -1) {
                                    break
                                }
                                output.write(buffer, 0, bytesRead)
                            }
                            output.flush()
                        }
                    }
                }

                if (isActive) {
                    success = true
                    LogUtils.info(TAG, "================ file download success ================")
                    complete(cacheFile.inputStream())
                } else {
                    LogUtils.info(TAG, "================ file download cancel ================")
                    cacheFile.delete()
                    failure(CancellationException("download cancel"))
                }
            } catch (e: Exception) {
                cacheFile.delete()
                LogUtils.error(TAG, "================ file download fail ================")
                LogUtils.error(TAG, "error: ${e.message}")
                e.printStackTrace()
                failure(e)
            } finally {
                if (!success && cacheFile.exists() && !isActive) {
                    cacheFile.delete()
                }
            }
        }
    }
}