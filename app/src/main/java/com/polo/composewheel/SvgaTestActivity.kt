package com.polo.composewheel

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.polo.composewheel.util.SvgaDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileInputStream

class SvgaTestActivity : AppCompatActivity() {
    private var svgaImageView: SVGAImageView? = null
    private lateinit var parser: SVGAParser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_svga_test)

        // 初始化 SVGAParser
        parser = SVGAParser(this)

        svgaImageView = findViewById(R.id.svgaImageView)
        val btnPlay: Button = findViewById(R.id.btnPlay)

        btnPlay.setOnClickListener {
            // 使用 GitHub 上的官方 SVGA 测试文件
            val demoUrl = "https://res.lukeelive.com/FhTlxVQWhIByJoxVADeNGtMpDGFQ?imageslim"

            // 使用挂起函数版本
            playFromUrlWithSuspend(demoUrl)
        }
    }

    /**
     * 使用挂起函数版本播放 SVGA
     * 更简洁，不需要回调
     */
    private fun playFromUrlWithSuspend(demoUrl: String) {
        lifecycleScope.launch {
            try {
                // 1. 下载文件
                val svgaFile = SvgaDownloader.downloadSvgaFile(this@SvgaTestActivity, demoUrl)
                Log.d("SvgaTestActivity", "Downloaded SVGA file path: ${svgaFile?.absolutePath}")

                if (svgaFile == null) {
                    Toast.makeText(this@SvgaTestActivity, "下载 SVGA 文件失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 2. 使用挂起函数解析 - 直接返回 SVGAVideoEntity，不需要回调
                val videoItem = parser.decodeFromInputStreamSuspend(
                    inputStream = BufferedInputStream(FileInputStream(svgaFile)),
                    cacheKey = svgaFile.absolutePath,
                    closeInputStream = true,
                    alias = demoUrl
                )

                // 3. 在主线程设置并播放
                withContext(Dispatchers.Main) {
                    svgaImageView?.setVideoItem(videoItem)
                    svgaImageView?.startAnimation()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SvgaTestActivity, "播放 SVGA 动画出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        svgaImageView?.stopAnimation()
    }
}
