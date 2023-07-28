package com.demo.wxqrcode

import android.app.Application

class App : Application() {

    init {
        System.loadLibrary("opencv_java4")
    }

    override fun onCreate() {
        super.onCreate()
        WeChatQRCodeDetector.init(this)
    }
}