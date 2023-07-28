package com.demo.wxqrcode

fun interface QRCodeDetectResultListener {
    fun forward(content: String)
}