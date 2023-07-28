package com.demo.wxqrcode

import android.os.Bundle
import android.widget.Toast
import com.demo.wxqrcode.databinding.ActivityMainBinding
import org.opencv.android.CameraActivity
import org.opencv.android.CameraBridgeViewBase

class MainActivity : CameraActivity() {

    private lateinit var binding: ActivityMainBinding

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
        const val TAG = "PreviewSize"
    }
}