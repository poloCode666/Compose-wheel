package com.polo.composewheel

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.opensource.svgaplayer.SVGAConfig
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import com.polo.composewheel.util.SvgaDownloader
import kotlinx.coroutines.launch
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
            val demoUrl = "https://res.lukeelive.com/FhTlxVQWhIByJoxVADeNGtMpDGFQ"
            // 或者去掉 imageslim 参数的 URL
            // val demoUrl = "https://res.lukeelive.com/FhTlxVQWhIByJoxVADeNGtMpDGFQ"

            // 方法1: 使用 SVGAParser 自带的 URL 解析
            // playFromUrlDirect(demoUrl)

            // 方法2: 使用自定义下载器（控制下载流程）
            playFromUrlWithDownloader(demoUrl)
        }
    }

    fun playFromUrlWithDownloader(demoUrl: String) {
        lifecycleScope.launch {
            try {
                val svgaFile = SvgaDownloader.downloadSvgaFile(this@SvgaTestActivity, demoUrl)
                Log.d("SvgaTestActivity", "Downloaded SVGA file path: ${svgaFile?.absolutePath}")
                if (svgaFile != null) {
                    // 使用 SVGAParser 的回调方式解析
                    parser.decodeFromInputStream(
                        inputStream = FileInputStream(svgaFile),
                        cacheKey = svgaFile.absolutePath,
                        config = SVGAConfig(),
                        callback = object : SVGAParser.ParseCompletion {
                            override fun onComplete(videoItem: SVGAVideoEntity) {
                                svgaImageView?.setVideoItem(videoItem)
                                svgaImageView?.startAnimation()
                            }

                            override fun onError() {
                                Toast.makeText(this@SvgaTestActivity, "SVGA 解析失败", Toast.LENGTH_SHORT).show()
                            }
                        },
                        closeInputStream = true,
                        playCallback = null,
                        memoryCacheKey = null,
                        alias = demoUrl
                    )
                } else {
                    Toast.makeText(this@SvgaTestActivity, "下载 SVGA 文件失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@SvgaTestActivity, "播放 SVGA 动画出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        svgaImageView?.stopAnimation()
    }
}
