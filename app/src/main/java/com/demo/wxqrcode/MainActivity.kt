package com.demo.wxqrcode

import android.os.Bundle
import android.widget.Toast
import com.gyf.immersionbar.ImmersionBar
import com.demo.wxqrcode.databinding.ActivityMainBinding
import org.opencv.android.CameraActivity
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size

class MainActivity : CameraActivity() {

    private lateinit var binding: ActivityMainBinding

    private var dstRgb: Mat? = null
    private val center: Point = Point()
    private var m: Mat? = null
    private var size: Size? = null

    private fun setFullscreen() {
        ImmersionBar.with(this)
            .reset()
            .transparentStatusBar()
            .statusBarColor(R.color.transparent_0)
            .statusBarDarkFont(true)
            .fullScreen(true)
            .init()
    }


    override fun getCameraViewList(): MutableList<out CameraBridgeViewBase> {
        return mutableListOf(binding.qrcodeLayout.camera2View)
    }

    override fun onResume() {
        super.onResume()
        binding.qrcodeLayout.onResume()
    }

    override fun onBackPressed() {
        if (binding.qrcodeLayout.isResultShowing()) {
            binding.qrcodeLayout.hideResult()
            binding.qrcodeLayout.enableView()
            return
        }
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFullscreen()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.qrcodeLayout.forwardListener = QRCodeDetectResultListener { content ->
            Toast.makeText(applicationContext, content, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.qrcodeLayout.disableView()
    }

    companion object {
        init {
            System.loadLibrary("wxqrcode")
        }

        const val TAG = "PreviewSize"
    }
}