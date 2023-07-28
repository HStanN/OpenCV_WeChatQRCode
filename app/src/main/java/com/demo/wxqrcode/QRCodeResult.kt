package com.demo.wxqrcode

import org.opencv.core.Point

data class QRCodeResult(
    val content: String,
    val point1: Point,
    val point2: Point,
    val point3: Point,
    val point4: Point
)
