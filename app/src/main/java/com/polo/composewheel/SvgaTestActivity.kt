package com.polo.composewheel

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity


import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

class SvgaTestActivity : AppCompatActivity() {
    private var svgaImageView: SVGAImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_svga_test)

        svgaImageView = findViewById(R.id.svgaImageView)
        val btnPlay: Button = findViewById(R.id.btnPlay)

        btnPlay.setOnClickListener {
            // Use the provided URL as demo svga URL
            val demoUrl = "https://res.lukeelive.com/FhTlxVQWhIByJoxVADeNGtMpDGFQ?imageslim"
            playFromUrl(demoUrl)
        }
    }

    // New: load SVGA from network URL (expects java.net.URL)
    private fun playFromUrl(url: String) {
        val parser = SVGAParser(this)
        try {
            val urlObj = URL(url)
            parser.decodeFromURL(urlObj, object : SVGAParser.ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    runOnUiThread {
                        svgaImageView?.setVideoItem(videoItem)
                        svgaImageView?.startAnimation()
                    }
                }

                override fun onError() {
                    runOnUiThread { Toast.makeText(this@SvgaTestActivity, "SVGA parse error (URL)", Toast.LENGTH_SHORT).show() }
                }
            })
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            runOnUiThread { Toast.makeText(this@SvgaTestActivity, "Invalid URL: ${e.message}", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { Toast.makeText(this@SvgaTestActivity, "Failed to load SVGA from URL: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // Keep original asset-loading method (optional)
    private fun playFromAssets(name: String) {
        val parser = SVGAParser(this)
        try {
            parser.decodeFromAssets(name, object : SVGAParser.ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    svgaImageView?.setVideoItem(videoItem)
                    svgaImageView?.startAnimation()
                }

                override fun onError() {
                    runOnUiThread { Toast.makeText(this@SvgaTestActivity, "SVGA parse error", Toast.LENGTH_SHORT).show() }
                }
            })
        } catch (e: IOException) {
            e.printStackTrace()
            runOnUiThread { Toast.makeText(this@SvgaTestActivity, "Failed to load SVGA: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        svgaImageView?.stopAnimation()
    }
}
