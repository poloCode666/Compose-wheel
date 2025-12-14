package com.opensource.svgaplayer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.opensource.svgaplayer.cache.SVGAFileCache
import com.opensource.svgaplayer.cache.SVGAMemoryCache
import com.opensource.svgaplayer.cache.SVGAMemoryLoadingQueue
import com.opensource.svgaplayer.coroutine.SvgaCoroutineManager
import com.opensource.svgaplayer.download.FileDownloader
import com.opensource.svgaplayer.proto.MovieEntity
import com.opensource.svgaplayer.utils.log.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ThreadPoolExecutor
import java.util.zip.InflaterInputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Created by PonyCui 16/6/18.
 */
private var fileLock: Any = Any()
private var isUnzipping = false

class SVGAParser constructor(context: Context) {
    private var mContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    interface ParseCompletion {
        fun onComplete(videoItem: SVGAVideoEntity)
        fun onError()
    }

    interface PlayCallback {
        fun onPlay(file: List<File>)
    }

    private var fileDownloader = FileDownloader()

    companion object {
        const val TAG = "SVGAParser"

        @SuppressLint("StaticFieldLeak")
        private var mShareParser: SVGAParser? = null

        @JvmStatic
        fun setThreadPoolExecutor(executor: ThreadPoolExecutor) {
            SvgaCoroutineManager.setThreadPoolExecutor(executor)
        }

        @JvmStatic
        fun shareParser(): SVGAParser? {
            return mShareParser
        }

        fun init(context: Context) {
            if (mShareParser == null) {
                mShareParser = SVGAParser(context)
            }
        }
    }

    fun decodeFromFile(
        path: String,
        config: SVGAConfig,
        callback: ParseCompletion?,
        playCallback: PlayCallback? = null
    ): Job? {
        if (mContext == null) {
            LogUtils.error(TAG, "在配置 SVGAParser context 前, 无法解析 SVGA 文件。")
            return null
        }
        LogUtils.info(TAG, "================ decode $path from file ================")
        //加载内存缓存数据
        val memoryCacheKey: String? =
            if (config.isCacheToMemory) SVGAMemoryCache.createKey(path, config) else null
        if (decodeFromMemoryCacheKey(memoryCacheKey, config, callback, playCallback, path)) {
            return null
        }
        //加载文件数据
        return SvgaCoroutineManager.launchIo {
            try {
                val cacheKey = SVGAFileCache.buildCacheKey(path)
                var inputStream: InputStream? = null
                val file = kotlin.runCatching { File(path) }.getOrNull()
                if (file?.exists() == true) {
                    if (file.isFile) {
                        inputStream = FileInputStream(file)
                    }
                } else {
                    val uri = kotlin.runCatching { Uri.parse(path) }.getOrNull()
                    val scheme = uri?.scheme?.lowercase()
                    when (scheme) {
                        "http", "https", "file" -> {
                            inputStream = URL(path).openStream()
                        }

                        "content" -> {
                            inputStream = mContext.contentResolver.openInputStream(uri)
                        }
                    }
                }
                inputStream?.let {
                    decodeFromInputStream(
                        it,
                        cacheKey,
                        config,
                        callback,
                        true,
                        playCallback,
                        memoryCacheKey,
                        alias = path
                    )
                } ?: run {
                    memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                    invokeErrorCallback(Exception("file inputStream is null"), callback, path)
                }
            } catch (e: Exception) {
                memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                invokeErrorCallback(e, callback, path)
            }
        }
    }

    fun decodeFromAssets(
        name: String,
        callback: ParseCompletion?,
        playCallback: PlayCallback? = null
    ): Job? {
        return decodeFromAssets(name, config = SVGAConfig(), callback, playCallback)
    }

    fun decodeFromAssets(
        name: String,
        config: SVGAConfig,
        callback: ParseCompletion?,
        playCallback: PlayCallback? = null
    ): Job? {
        if (mContext == null) {
            LogUtils.error(TAG, "在配置 SVGAParser context 前, 无法解析 SVGA 文件。")
            return null
        }
        //加载内存缓存数据
        val memoryCacheKey: String? =
            if (config.isCacheToMemory) SVGAMemoryCache.createKey(name, config) else null
        LogUtils.info(
            TAG,
            "================ decode $name from assets memoryCacheKey = $memoryCacheKey ================"
        )
        if (decodeFromMemoryCacheKey(memoryCacheKey, config, callback, playCallback, name)) {
            return null
        }
        //加载Assets数据
        return SvgaCoroutineManager.launchIo {
            try {
                mContext?.assets?.open(name)?.let {
                    decodeFromInputStream(
                        it,
                        SVGAFileCache.buildCacheKey("file:///assets/$name"),
                        config,
                        callback,
                        true,
                        playCallback,
                        memoryCacheKey,
                        alias = name
                    )
                } ?: run {
                    memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                    invokeErrorCallback(Exception("assets inputStream is null"), callback, name)
                }
            } catch (e: Exception) {
                memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                invokeErrorCallback(e, callback, name)
            }
        }
    }

    fun decodeFromURL(
        url: URL,
        callback: ParseCompletion?,
        playCallback: PlayCallback? = null
    ): Job? {
        return decodeFromURL(url, config = SVGAConfig(), callback, playCallback)
    }

    fun decodeFromURL(
        url: URL,
        config: SVGAConfig,
        callback: ParseCompletion?,
        playCallback: PlayCallback? = null
    ): Job? {
        if (mContext == null) {
            LogUtils.error(TAG, "在配置 SVGAParser context 前, 无法解析 SVGA 文件。")
            return null
        }
        val urlPath = url.toString()
        LogUtils.info(TAG, "================ decode from url: $urlPath ================")
        //加载内存缓存数据
        val memoryCacheKey: String? =
            if (config.isCacheToMemory) SVGAMemoryCache.createKey(urlPath, config) else null
        if (decodeFromMemoryCacheKey(memoryCacheKey, config, callback, playCallback, urlPath)) {
            return null
        }
        val cacheKey = SVGAFileCache.buildCacheKey(url)
        val cachedType = SVGAFileCache.getCachedType(cacheKey)
        return if (cachedType != null) { //加载本地缓存数据
            LogUtils.info(TAG, "this url has disk cached")
            SvgaCoroutineManager.launchIo {
                if (cachedType == SVGAFileCache.Type.ZIP) {
                    decodeFromUnzipDirCacheKey(
                        cacheKey,
                        config,
                        callback,
                        memoryCacheKey,
                        alias = urlPath
                    )
                } else {
                    decodeFromSVGAFileCacheKey(
                        cacheKey,
                        config,
                        callback,
                        playCallback,
                        memoryCacheKey,
                        alias = urlPath
                    )
                }
            }
            return null
        } else { //加载网络数据（下载资源）
            LogUtils.info(TAG, "no cached, prepare to download")
            fileDownloader.resume(url, {
                this.decodeFromInputStream(
                    it,
                    cacheKey,
                    config,
                    callback,
                    false,
                    playCallback,
                    memoryCacheKey,
                    alias = urlPath
                )
            }, {
                memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                this.invokeErrorCallback(it, callback, alias = urlPath)
            })
        }.apply {
            invokeOnCompletion { exception ->
                if (exception is CancellationException) {
                    LogUtils.info(
                        TAG, "================ decode from url canceled: $urlPath ================"
                    )
                    memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                }
            }
        }
    }

    /**
     * 读取解析本地缓存的 svga 文件.
     */
    private fun decodeFromSVGAFileCacheKey(
        cacheKey: String,
        config: SVGAConfig,
        callback: ParseCompletion?,
        playCallback: PlayCallback?,
        memoryCacheKey: String?,
        alias: String? = null
    ) {
        SvgaCoroutineManager.launchIo {
            val svgaFile = SVGAFileCache.buildCacheFile(cacheKey)
            try {
                LogUtils.info(
                    TAG,
                    "================ decode $alias from svga cache file to entity ================ \n" +
                            "svga cache File = $svgaFile"
                )
                FileInputStream(svgaFile).use { inputStream ->
                    //检查是否是zip文件
                    val magicCode = ByteArray(4)
                    if (inputStream.markSupported()) {
                        inputStream.mark(4)
                        inputStream.read(magicCode)
                        inputStream.reset()
                    }
                    if (isZipFile(magicCode)) {
                        decodeFromUnzipDirCacheKey(
                            cacheKey,
                            config,
                            callback,
                            memoryCacheKey,
                            alias
                        )
                    } else {
                        LogUtils.info(TAG, "inflate start")
                        InflaterInputStream(inputStream).use { inflaterInputStream ->
                            val entity = MovieEntity.ADAPTER.decode(inflaterInputStream)
                            val videoItem = SVGAVideoEntity(
                                entity,
                                File(cacheKey),
                                config.frameWidth,
                                config.frameHeight,
                                memoryCacheKey
                            )
                            LogUtils.info(
                                TAG,
                                "inflate complete : width = ${config.frameWidth}, height = ${config.frameHeight}, size = ${videoItem.getMemorySize()}"
                            )
                            LogUtils.info(TAG, "SVGAVideoEntity prepare start")
                            videoItem.prepare({
                                LogUtils.info(TAG, "SVGAVideoEntity prepare success")
                                invokeCompleteCallback(videoItem, callback, alias)
                            }, playCallback)
                        }
                    }
                }
            } catch (e: Exception) {
                memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                svgaFile.delete() //解码失败删除文件，否则一直失败
                invokeErrorCallback(e, callback, alias)
            } finally {
                LogUtils.info(
                    TAG,
                    "================ decode $alias from svga cachel file to entity end ================"
                )
            }
        }
    }

    fun decodeFromInputStream(
        inputStream: InputStream,
        cacheKey: String,
        config: SVGAConfig,
        callback: ParseCompletion?,
        closeInputStream: Boolean = false,
        playCallback: PlayCallback? = null,
        memoryCacheKey: String?,
        alias: String? = null
    ): Job? {
        if (mContext == null) {
            LogUtils.error(TAG, "在配置 SVGAParser context 前, 无法解析 SVGA 文件。")
            return null
        }
        LogUtils.info(TAG, "================ decode $alias from input stream ================")
        return SvgaCoroutineManager.launchIo {
            try {
                //检查是否是zip文件
                val magicCode = ByteArray(4)
                if (inputStream.markSupported()) {
                    inputStream.mark(4)
                    inputStream.read(magicCode)
                    inputStream.reset()
                }
                if (isZipFile(magicCode)) {
                    LogUtils.info(TAG, "decode from zip file")
                    if (!SVGAFileCache.buildCacheDir(cacheKey).exists() || isUnzipping) {
                        synchronized(fileLock) {
                            if (!SVGAFileCache.buildCacheDir(cacheKey).exists()) {
                                isUnzipping = true
                                LogUtils.info(TAG, "no cached, prepare to unzip")
                                unzip(inputStream, cacheKey)
                                isUnzipping = false
                                LogUtils.info(TAG, "unzip success")
                            }
                        }
                    }
                    decodeFromUnzipDirCacheKey(
                        cacheKey,
                        config,
                        callback,
                        memoryCacheKey,
                        alias
                    )
                } else {
                    InflaterInputStream(inputStream).use { inflaterInputStream ->
                        val entity = MovieEntity.ADAPTER.decode(inflaterInputStream)
                        val videoItem = SVGAVideoEntity(
                            entity,
                            File(cacheKey),
                            config.frameWidth,
                            config.frameHeight,
                            memoryCacheKey
                        )
                        LogUtils.info(TAG, "SVGAVideoEntity prepare start")
                        videoItem.prepare({
                            LogUtils.info(TAG, "SVGAVideoEntity prepare success")
                            invokeCompleteCallback(videoItem, callback, alias)
                        }, playCallback)
                    }
                }
            } catch (e: java.lang.Exception) {
                memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                invokeErrorCallback(e, callback, alias)
            } finally {
                if (closeInputStream) {
                    inputStream.close()
                }
                LogUtils.info(
                    TAG,
                    "================ decode $alias from input stream end ================"
                )
            }
        }
    }

    /**
     * @deprecated from 2.4.0
     */
    @Deprecated(
        "This method has been deprecated from 2.4.0.",
        ReplaceWith("this.decodeFromAssets(assetsName, callback)")
    )
    fun parse(assetsName: String, config: SVGAConfig, callback: ParseCompletion?) {
        this.decodeFromAssets(assetsName, config, callback, null)
    }

    /**
     * @deprecated from 2.4.0
     */
    @Deprecated(
        "This method has been deprecated from 2.4.0.",
        ReplaceWith("this.decodeFromURL(url, callback)")
    )
    fun parse(url: URL, config: SVGAConfig, callback: ParseCompletion?) {
        this.decodeFromURL(url, config, callback, null)
    }

    private fun invokeCompleteCallback(
        videoItem: SVGAVideoEntity,
        callback: ParseCompletion?,
        alias: String?
    ) {
        LogUtils.info(TAG, "================ $alias parser complete ================")
        val cacheKey = videoItem.getMemoryCacheKey()
        if (cacheKey.isNullOrEmpty()) {
            handler.post {
                callback?.onComplete(videoItem)
            }
        } else {
            //存入内存缓存
            SVGAMemoryCache.INSTANCE.putData(cacheKey, videoItem)
            val inQueue = SVGAMemoryLoadingQueue.inQueue(cacheKey)
            if (inQueue) {
                //通知等待队列
                handler.post {
                    val itemList = SVGAMemoryLoadingQueue.removeItem(cacheKey)
                    itemList?.forEach {
                        it.callback?.onComplete(videoItem)
                    }
                }
            } else {
                handler.post {
                    callback?.onComplete(videoItem)
                }
            }
        }
    }

    private fun invokeErrorCallback(
        e: Exception,
        callback: ParseCompletion?,
        alias: String?
    ) {
        e.printStackTrace()
        LogUtils.error(TAG, "================ $alias parser error ================")
        //LogUtils.error(TAG, "$alias parse error", e)
        handler.post {
            callback?.onError()
        }
    }

    /**
     * 从解压缓存中加载
     */
    private fun decodeFromUnzipDirCacheKey(
        cacheKey: String,
        config: SVGAConfig,
        callback: ParseCompletion?,
        memoryCacheKey: String?,
        alias: String?
    ): Job? {
        LogUtils.info(TAG, "================ decode $alias from cache ================")
        LogUtils.debug(TAG, "decodeFromCacheKey called with cacheKey : $cacheKey")
        if (mContext == null) {
            LogUtils.error(TAG, "在配置 SVGAParser context 前, 无法解析 SVGA 文件。")
            return null
        }
        return SvgaCoroutineManager.launchIo {
            try {
                val cacheDir = SVGAFileCache.buildCacheDir(cacheKey)
                File(cacheDir, "movie.binary").takeIf { it.isFile }?.let { binaryFile ->
                    try {
                        LogUtils.info(TAG, "binary change to entity")
                        FileInputStream(binaryFile).use {
                            LogUtils.info(TAG, "binary change to entity success")
                            invokeCompleteCallback(
                                SVGAVideoEntity(
                                    MovieEntity.ADAPTER.decode(it),
                                    cacheDir,
                                    config.frameWidth,
                                    config.frameHeight,
                                    memoryCacheKey
                                ),
                                callback,
                                alias
                            )
                        }

                    } catch (e: Exception) {
                        LogUtils.error(TAG, "binary change to entity fail", e)
                        cacheDir.delete()
                        binaryFile.delete()
                        throw e
                    }
                }
                File(cacheDir, "movie.spec").takeIf { it.isFile }?.let { jsonFile ->
                    try {
                        LogUtils.info(TAG, "spec change to entity")
                        FileInputStream(jsonFile).use { fileInputStream ->
                            ByteArrayOutputStream().use { byteArrayOutputStream ->
                                val buffer = ByteArray(2048)
                                while (isActive) {
                                    val size = fileInputStream.read(buffer, 0, buffer.size)
                                    if (size == -1) {
                                        break
                                    }
                                    byteArrayOutputStream.write(buffer, 0, size)
                                }
                                byteArrayOutputStream.toString().let {
                                    JSONObject(it).let { json ->
                                        LogUtils.info(TAG, "spec change to entity success")
                                        invokeCompleteCallback(
                                            SVGAVideoEntity(
                                                json,
                                                cacheDir,
                                                config.frameWidth,
                                                config.frameHeight,
                                                memoryCacheKey
                                            ),
                                            callback,
                                            alias
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        LogUtils.error(TAG, "$alias movie.spec change to entity fail", e)
                        cacheDir.delete()
                        jsonFile.delete()
                        throw e
                    }
                }
            } catch (e: Exception) {
                memoryCacheKey?.let { SVGAMemoryLoadingQueue.removeItem(memoryCacheKey) }
                invokeErrorCallback(e, callback, alias)
            }
        }
    }

    /**
     * 加载内存缓存
     * @return true内部已将数据通过接口返回，不用再加载
     */
    private fun decodeFromMemoryCacheKey(
        memoryCacheKey: String?,
        config: SVGAConfig,
        callback: ParseCompletion?,
        playCallback: PlayCallback?,
        alias: String?
    ): Boolean {
        return if (config.isCacheToMemory && !memoryCacheKey.isNullOrEmpty()) { //加载内存缓存
            //获取内存缓存
            val entity = SVGAMemoryCache.INSTANCE.getData(memoryCacheKey)
            //查询等待队列
            val inQueue = SVGAMemoryLoadingQueue.inQueue(memoryCacheKey)
            //加入等待队列
            SVGAMemoryLoadingQueue.addItem(
                memoryCacheKey,
                SVGAMemoryLoadingQueue.SVGAMemoryLoadingItem(callback)
            )
            LogUtils.info(
                TAG,
                "decodeFromMemoryCacheKey addItem $memoryCacheKey, inQueue = $inQueue"
            )
            if (!inQueue && entity == null) { //无缓存，无队列，原路径加载
                return false
            }
            if (!inQueue && entity != null) { //第一个进入队列的，且有缓存的情况。回调并可清空队列
                entity.prepare({
                    LogUtils.info(TAG, "decodeFromMemoryCacheKey prepare success")
                    this.invokeCompleteCallback(entity, callback, alias = alias)
                }, playCallback)
            }
            true
        } else {
            false
        }
    }

    // 是否是 zip 文件
    private fun isZipFile(bytes: ByteArray): Boolean {
        return bytes.size >= 4
                && bytes[0].toInt() == 80
                && bytes[1].toInt() == 75
                && bytes[2].toInt() == 3
                && bytes[3].toInt() == 4
    }

    // 解压
    private fun unzip(inputStream: InputStream, cacheKey: String) {
        LogUtils.info(TAG, "================ unzip prepare ================")
        val cacheDir = SVGAFileCache.buildCacheDir(cacheKey)
        cacheDir.mkdirs()
        try {
            BufferedInputStream(inputStream).use {
                ZipInputStream(it).use { zipInputStream ->
                    while (true) {
                        val zipItem = zipInputStream.nextEntry ?: break
                        if (zipItem.name.contains("../")) {
                            // 解压路径存在路径穿越问题，直接过滤
                            continue
                        }
                        if (zipItem.name.contains("/")) {
                            continue
                        }
                        val file = File(cacheDir, zipItem.name)
                        ensureUnzipSafety(file, cacheDir.absolutePath)
                        FileOutputStream(file).use { fileOutputStream ->
                            val buff = ByteArray(2048)
                            while (true) {
                                val readBytes = zipInputStream.read(buff)
                                if (readBytes <= 0) {
                                    break
                                }
                                fileOutputStream.write(buff, 0, readBytes)
                            }
                        }
                        LogUtils.error(TAG, "================ unzip complete ================")
                        zipInputStream.closeEntry()
                    }
                }
            }
            //解压完成，删除下载缓存
            val downloadCacheFile = SVGAFileCache.buildCacheFile(cacheKey)
            if (downloadCacheFile.exists()) {
                downloadCacheFile.delete()
            }
        } catch (e: Exception) {
            LogUtils.error(TAG, "================ unzip error ================")
            LogUtils.error(TAG, "error", e)
            SVGAFileCache.clearDir(cacheDir.absolutePath)
            cacheDir.delete()
            throw e
        }
    }

    // 检查 zip 路径穿透
    private fun ensureUnzipSafety(outputFile: File, dstDirPath: String) {
        val dstDirCanonicalPath = File(dstDirPath).canonicalPath
        val outputFileCanonicalPath = outputFile.canonicalPath
        if (!outputFileCanonicalPath.startsWith(dstDirCanonicalPath)) {
            throw IOException("Found Zip Path Traversal Vulnerability with $dstDirCanonicalPath")
        }
    }

    // ==================== 挂起函数版本 ====================

    /**
     * 挂起函数版本：从 InputStream 解码 SVGA 文件
     * 直接返回 SVGAVideoEntity，不需要回调
     *
     * @param inputStream 输入流
     * @param cacheKey 缓存键
     * @param config SVGA 配置
     * @param closeInputStream 是否在解析完成后关闭输入流
     * @param alias 别名，用于日志
     * @return SVGAVideoEntity 解析成功返回实体
     * @throws IllegalStateException 解析失败时抛出异常
     */
    suspend fun decodeFromInputStreamSuspend(
        inputStream: InputStream,
        cacheKey: String,
        config: SVGAConfig = SVGAConfig(),
        closeInputStream: Boolean = true,
        alias: String? = null
    ): SVGAVideoEntity = withContext(Dispatchers.IO) {
        if (mContext == null) {
            throw IllegalStateException("在配置 SVGAParser context 前, 无法解析 SVGA 文件。")
        }
        LogUtils.info(TAG, "================ decode $alias from input stream (suspend) ================")

        try {
            // 检查是否是 zip 文件
            val magicCode = ByteArray(4)
            if (inputStream.markSupported()) {
                inputStream.mark(4)
                inputStream.read(magicCode)
                inputStream.reset()
            }

            if (isZipFile(magicCode)) {
                LogUtils.info(TAG, "decode from zip file")
                if (!SVGAFileCache.buildCacheDir(cacheKey).exists() || isUnzipping) {
                    synchronized(fileLock) {
                        if (!SVGAFileCache.buildCacheDir(cacheKey).exists()) {
                            isUnzipping = true
                            LogUtils.info(TAG, "no cached, prepare to unzip")
                            unzip(inputStream, cacheKey)
                            isUnzipping = false
                            LogUtils.info(TAG, "unzip success")
                        }
                    }
                }
                decodeFromUnzipDirCacheKeySuspend(cacheKey, config, alias)
            } else {
                // 在解析前检查文件大小（如果是文件输入流）
                val fileSize = if (inputStream is java.io.FileInputStream) {
                    try {
                        val file = File(cacheKey)
                        if (file.exists()) file.length() else -1L
                    } catch (e: Exception) {
                        -1L
                    }
                } else {
                    -1L
                }
                if (fileSize > 0) {
                    LogUtils.info(TAG, "准备解压 SVGA 文件，文件大小: $fileSize bytes")
                }
                
                try {
                    InflaterInputStream(inputStream).use { inflaterInputStream ->
                        val entity = MovieEntity.ADAPTER.decode(inflaterInputStream)
                        val videoItem = SVGAVideoEntity(
                            entity,
                            File(cacheKey),
                            config.frameWidth,
                            config.frameHeight,
                            null
                        )
                        LogUtils.info(TAG, "SVGAVideoEntity prepare start")
                        videoItem.prepareSuspend()
                        LogUtils.info(TAG, "SVGAVideoEntity prepare success")
                        videoItem
                    }
                } catch (e: java.util.zip.ZipException) {
                    val errorMsg = "ZLIB解压失败（ZipException），可能是文件损坏或不完整。文件大小: $fileSize bytes"
                    LogUtils.error(TAG, errorMsg, e)
                    // 删除可能损坏的缓存文件
                    val cacheFile = File(cacheKey)
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                        LogUtils.info(TAG, "已删除可能损坏的缓存文件: ${cacheFile.absolutePath}")
                    }
                    throw IOException(errorMsg, e)
                } catch (e: java.io.EOFException) {
                    val errorMsg = "文件提前结束（EOFException），可能是下载不完整。文件大小: $fileSize bytes"
                    LogUtils.error(TAG, errorMsg, e)
                    // 删除可能损坏的缓存文件
                    val cacheFile = File(cacheKey)
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                        LogUtils.info(TAG, "已删除可能损坏的缓存文件: ${cacheFile.absolutePath}")
                    }
                    throw IOException(errorMsg, e)
                } catch (e: java.io.IOException) {
                    // 检查是否是 ZLIB 相关的错误
                    if (e.message?.contains("ZLIB", ignoreCase = true) == true || 
                        e.message?.contains("unexpected end", ignoreCase = true) == true) {
                        val errorMsg = "ZLIB解压失败（IOException），可能是文件损坏或不完整。文件大小: $fileSize bytes, 错误: ${e.message}"
                        LogUtils.error(TAG, errorMsg, e)
                        // 删除可能损坏的缓存文件
                        val cacheFile = File(cacheKey)
                        if (cacheFile.exists()) {
                            cacheFile.delete()
                            LogUtils.info(TAG, "已删除可能损坏的缓存文件: ${cacheFile.absolutePath}")
                        }
                        throw IOException(errorMsg, e)
                    }
                    throw e
                }
            }
        } finally {
            if (closeInputStream) {
                try {
                    inputStream.close()
                } catch (_: Exception) {
                    // ignore
                }
            }
            LogUtils.info(TAG, "================ decode $alias from input stream end ================")
        }
    }

    /**
     * 挂起函数版本：从缓存目录解码
     */
    private suspend fun decodeFromUnzipDirCacheKeySuspend(
        cacheKey: String,
        config: SVGAConfig,
        alias: String?
    ): SVGAVideoEntity = withContext(Dispatchers.IO) {
        LogUtils.info(TAG, "================ decode $alias from cache (suspend) ================")
        val cacheDir = SVGAFileCache.buildCacheDir(cacheKey)

        // 尝试读取 binary 格式
        val binaryFile = File(cacheDir, "movie.binary")
        if (binaryFile.isFile) {
            FileInputStream(binaryFile).use { fis ->
                val entity = MovieEntity.ADAPTER.decode(fis)
                val videoItem = SVGAVideoEntity(
                    entity,
                    cacheDir,
                    config.frameWidth,
                    config.frameHeight,
                    null
                )
                videoItem.prepareSuspend()
                return@withContext videoItem
            }
        }

        // 尝试读取 spec (JSON) 格式
        val specFile = File(cacheDir, "movie.spec")
        if (specFile.isFile) {
            FileInputStream(specFile).use { fis ->
                ByteArrayOutputStream().use { baos ->
                    val buffer = ByteArray(2048)
                    var len: Int
                    while (fis.read(buffer).also { len = it } != -1) {
                        baos.write(buffer, 0, len)
                    }
                    val jsonObj = JSONObject(baos.toString())
                    val videoItem = SVGAVideoEntity(
                        jsonObj,
                        cacheDir,
                        config.frameWidth,
                        config.frameHeight,
                        null
                    )
                    videoItem.prepareSuspend()
                    return@withContext videoItem
                }
            }
        }

        throw IllegalStateException("缓存目录中找不到有效的 SVGA 文件: $cacheKey")
    }

    /**
     * 挂起函数版本：从缓存文件解码（SVGA 格式）
     */
    private suspend fun decodeFromSVGAFileCacheKeySuspend(
        cacheKey: String,
        config: SVGAConfig,
        alias: String?
    ): SVGAVideoEntity = withContext(Dispatchers.IO) {
        val svgaFile = SVGAFileCache.buildCacheFile(cacheKey)
        LogUtils.info(
            TAG,
            "================ decode $alias from svga cache file (suspend) ================ \n" +
                    "svga cache File = $svgaFile"
        )
        FileInputStream(svgaFile).use { inputStream ->
            //检查是否是zip文件
            val magicCode = ByteArray(4)
            if (inputStream.markSupported()) {
                inputStream.mark(4)
                inputStream.read(magicCode)
                inputStream.reset()
            }
            if (isZipFile(magicCode)) {
                decodeFromUnzipDirCacheKeySuspend(cacheKey, config, alias)
            } else {
                LogUtils.info(TAG, "inflate start")
                val svgaFile = SVGAFileCache.buildCacheFile(cacheKey)
                val fileSize = if (svgaFile.exists()) svgaFile.length() else -1L
                if (fileSize > 0) {
                    LogUtils.info(TAG, "准备解压 SVGA 文件，文件大小: $fileSize bytes")
                }
                
                try {
                    InflaterInputStream(inputStream).use { inflaterInputStream ->
                        val entity = MovieEntity.ADAPTER.decode(inflaterInputStream)
                        val videoItem = SVGAVideoEntity(
                            entity,
                            File(cacheKey),
                            config.frameWidth,
                            config.frameHeight,
                            null
                        )
                        LogUtils.info(
                            TAG,
                            "inflate complete : width = ${config.frameWidth}, height = ${config.frameHeight}, size = ${videoItem.getMemorySize()}"
                        )
                        LogUtils.info(TAG, "SVGAVideoEntity prepare start")
                        videoItem.prepareSuspend()
                        LogUtils.info(TAG, "SVGAVideoEntity prepare success")
                        videoItem
                    }
                } catch (e: java.util.zip.ZipException) {
                    val errorMsg = "ZLIB解压失败（ZipException），可能是文件损坏或不完整。文件大小: $fileSize bytes"
                    LogUtils.error(TAG, errorMsg, e)
                    // 删除可能损坏的缓存文件
                    if (svgaFile.exists()) {
                        svgaFile.delete()
                        LogUtils.info(TAG, "已删除可能损坏的缓存文件: ${svgaFile.absolutePath}")
                    }
                    throw IOException(errorMsg, e)
                } catch (e: java.io.EOFException) {
                    val errorMsg = "文件提前结束（EOFException），可能是下载不完整。文件大小: $fileSize bytes"
                    LogUtils.error(TAG, errorMsg, e)
                    // 删除可能损坏的缓存文件
                    if (svgaFile.exists()) {
                        svgaFile.delete()
                        LogUtils.info(TAG, "已删除可能损坏的缓存文件: ${svgaFile.absolutePath}")
                    }
                    throw IOException(errorMsg, e)
                } catch (e: java.io.IOException) {
                    // 检查是否是 ZLIB 相关的错误
                    if (e.message?.contains("ZLIB", ignoreCase = true) == true || 
                        e.message?.contains("unexpected end", ignoreCase = true) == true) {
                        val errorMsg = "ZLIB解压失败（IOException），可能是文件损坏或不完整。文件大小: $fileSize bytes, 错误: ${e.message}"
                        LogUtils.error(TAG, errorMsg, e)
                        // 删除可能损坏的缓存文件
                        if (svgaFile.exists()) {
                            svgaFile.delete()
                            LogUtils.info(TAG, "已删除可能损坏的缓存文件: ${svgaFile.absolutePath}")
                        }
                        throw IOException(errorMsg, e)
                    }
                    throw e
                }
            }
        }
    }

    /**
     * 挂起函数版本：从 URL 解码 SVGA 文件
     * 直接返回 SVGAVideoEntity，不需要回调
     *
     * @param url URL
     * @param config SVGA 配置
     * @param alias 别名，用于日志
     * @return SVGAVideoEntity 解析成功返回实体
     * @throws IllegalStateException 解析失败时抛出异常
     */
    suspend fun decodeFromURLSuspend(
        url: URL,
        config: SVGAConfig = SVGAConfig(),
        alias: String? = null
    ): SVGAVideoEntity = withContext(Dispatchers.IO) {
        if (mContext == null) {
            throw IllegalStateException("在配置 SVGAParser context 前, 无法解析 SVGA 文件。")
        }
        val urlPath = alias ?: url.toString()
        LogUtils.info(TAG, "================ decode from url (suspend): $urlPath ================")

        //加载内存缓存数据
        val memoryCacheKey: String? =
            if (config.isCacheToMemory) SVGAMemoryCache.createKey(urlPath, config) else null
        
        if (memoryCacheKey != null && config.isCacheToMemory) {
            val entity = SVGAMemoryCache.INSTANCE.getData(memoryCacheKey)
            if (entity != null) {
                LogUtils.info(TAG, "Using memory cache for: $urlPath")
                // 内存缓存中的实体需要重新 prepare
                entity.prepareSuspend()
                return@withContext entity
            }
        }

        val cacheKey = SVGAFileCache.buildCacheKey(url)
        val cachedType = SVGAFileCache.getCachedType(cacheKey)
        
        if (cachedType != null) { //加载本地缓存数据
            LogUtils.info(TAG, "this url has disk cached: $urlPath")
            if (cachedType == SVGAFileCache.Type.ZIP) {
                decodeFromUnzipDirCacheKeySuspend(cacheKey, config, urlPath)
            } else {
                decodeFromSVGAFileCacheKeySuspend(cacheKey, config, urlPath)
            }
        } else { //加载网络数据（下载资源），带重试机制
            LogUtils.info(TAG, "no cached, prepare to download: $urlPath")
            
            // 重试机制：最多重试2次（总共尝试3次）
            val maxRetries = 2
            var lastException: Exception? = null
            
            for (attempt in 0..maxRetries) {
                try {
                    if (attempt > 0) {
                        LogUtils.info(TAG, "================ 开始第 ${attempt + 1} 次重试下载: $urlPath ================")
                        // 重试前等待一小段时间，避免立即重试
                        kotlinx.coroutines.delay(500L * attempt) // 递增延迟：500ms, 1000ms
                    }
                    
                    // 直接使用 suspendCancellableCoroutine 获取 inputStream
                    val inputStream = suspendCancellableCoroutine<InputStream> { continuation ->
                        fileDownloader.resume(url, { inputStream ->
                            continuation.resume(inputStream)
                        }, { e ->
                            continuation.resumeWithException(e)
                        })
                        
                        continuation.invokeOnCancellation {
                            LogUtils.info(TAG, "================ decode from url canceled (suspend): $urlPath ================")
                        }
                    }
                    
                    // 验证输入流
                    val availableBytes = try {
                        inputStream.available().toLong()
                    } catch (e: Exception) {
                        -1L
                    }
                    LogUtils.info(TAG, "准备解析，输入流可用字节数: $availableBytes")
                    
                    // 现在在 suspend 函数作用域中，可以直接调用 suspend 函数
                    val videoItem = try {
                        decodeFromInputStreamSuspend(
                            inputStream,
                            cacheKey,
                            config,
                            closeInputStream = true,
                            alias = urlPath
                        )
                    } catch (e: java.util.zip.ZipException) {
                        // ZLIB 相关错误，可能是文件损坏或不完整
                        val cacheFile = SVGAFileCache.buildCacheFile(cacheKey)
                        val fileSize = if (cacheFile.exists()) cacheFile.length() else -1L
                        val errorMsg = "ZLIB解压失败，可能是文件损坏或不完整。文件大小: $fileSize bytes, 可用字节: $availableBytes"
                        LogUtils.error(TAG, errorMsg, e)
                        // 删除可能损坏的缓存文件
                        if (cacheFile.exists()) {
                            cacheFile.delete()
                            LogUtils.info(TAG, "已删除可能损坏的缓存文件: ${cacheFile.absolutePath}")
                        }
                        throw IOException(errorMsg, e)
                    } catch (e: java.io.EOFException) {
                        // 文件提前结束
                        val cacheFile = SVGAFileCache.buildCacheFile(cacheKey)
                        val fileSize = if (cacheFile.exists()) cacheFile.length() else -1L
                        val errorMsg = "文件提前结束（EOF），可能是下载不完整。文件大小: $fileSize bytes, 可用字节: $availableBytes"
                        LogUtils.error(TAG, errorMsg, e)
                        // 删除可能损坏的缓存文件
                        if (cacheFile.exists()) {
                            cacheFile.delete()
                            LogUtils.info(TAG, "已删除可能损坏的缓存文件: ${cacheFile.absolutePath}")
                        }
                        throw IOException(errorMsg, e)
                    }
                    
                    // 保存到内存缓存
                    memoryCacheKey?.let { 
                        SVGAMemoryCache.INSTANCE.putData(it, videoItem)
                    }
                    
                    if (attempt > 0) {
                        LogUtils.info(TAG, "================ 第 ${attempt + 1} 次重试成功: $urlPath ================")
                    }
                    
                    return@withContext videoItem
                    
                } catch (e: Exception) {
                    lastException = e
                    val isRetryable = when {
                        // 网络相关错误，可以重试
                        e is java.net.SocketTimeoutException -> true
                        e is java.net.UnknownHostException -> true
                        e is java.net.ConnectException -> true
                        e is java.io.IOException -> {
                            // 检查是否是下载或解析相关的错误
                            val msg = e.message ?: ""
                            msg.contains("下载", ignoreCase = true) ||
                            msg.contains("不完整", ignoreCase = true) ||
                            msg.contains("ZLIB", ignoreCase = true) ||
                            msg.contains("unexpected end", ignoreCase = true) ||
                            msg.contains("EOF", ignoreCase = true) ||
                            msg.contains("HTTP error", ignoreCase = true) ||
                            msg.contains("Empty response", ignoreCase = true)
                        }
                        // 其他不可重试的错误
                        else -> false
                    }
                    
                    if (isRetryable && attempt < maxRetries) {
                        LogUtils.warn(TAG, "第 ${attempt + 1} 次尝试失败，将重试: ${e.message}")
                        // 继续重试
                    } else {
                        // 不可重试或已达到最大重试次数
                        if (attempt >= maxRetries) {
                            LogUtils.error(TAG, "================ 所有重试均失败（共 ${attempt + 1} 次尝试）: $urlPath ================")
                            LogUtils.error(TAG, "最后一次错误: ${e.message}", e)
                        } else {
                            LogUtils.error(TAG, "错误不可重试: ${e.message}", e)
                        }
                        throw e
                    }
                }
            }
            
            // 理论上不会到达这里，但为了编译安全
            throw lastException ?: IllegalStateException("未知错误")
        }
    }
}
