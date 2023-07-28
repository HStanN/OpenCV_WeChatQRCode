package com.demo.wxqrcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.children
import org.opencv.android.*
import org.opencv.android.CameraBridgeViewBase.CAMERA_ID_BACK
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.min

class QRCodeDetectorLayout : FrameLayout, CvCameraViewListener2 {

    val camera2View: JavaCamera2View = JavaCamera2View(context, CAMERA_ID_BACK)

    private val resultImageView: ImageView

    private var dstRgb: Mat? = null

    private var previewWidth = 0
    private var previewHeight = 0
    private var hScale = 1.0f
    private var vScale = 1.0f
    private var isRatioSmall = false

    //如果检测到二维码就主动停止继续采集
    private var isDetected = false

    //设置检测间隔，可以提升帧率
    private var lastDetectTime = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    var forwardListener: QRCodeDetectResultListener? = null

    private val mLoaderCallback = object : BaseLoaderCallback(context.applicationContext) {
        override fun onManagerConnected(status: Int) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                enableView()
                return
            }
            super.onManagerConnected(status)
        }
    }

    companion object {
        const val TAG = "QRCodeDetectorLayout"
        private const val ARROW_SIZE = 100
        //检测间隔时长：1s
        private const val DETECT_INTERVAL = 1000
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        addView(camera2View)
        camera2View.setCvCameraViewListener(this)
        camera2View.enableFpsMeter()
        //设置最大采集尺寸
        camera2View.setMaxFrameSize(720, -1)
        resultImageView = ImageView(context)
        resultImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        resultImageView.visibility = INVISIBLE
        addView(resultImageView)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        previewWidth = height
        previewHeight = width
        vScale = previewHeight * 1.00f / camera2View.measuredHeight
        hScale = previewWidth * 1.00f / camera2View.measuredWidth
        isRatioSmall =
            (previewHeight * 1.00f / previewWidth) < (camera2View.measuredHeight * 1.00f / camera2View.measuredWidth)
        Log.d(
            TAG,
            "onCameraViewStarted: $width * $height   scale = $vScale , $hScale  isSmall = $isRatioSmall"
        )
    }

    override fun onCameraViewStopped() {
        Log.d(TAG, "onCameraViewStopped")
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        dstRgb = inputFrame?.rgba()
        inputFrame?.let {
            dstRgb?.let {
                detectMat(it, inputFrame.gray())
            }
        }
        return dstRgb
    }

    private fun detectMat(source: Mat, gray: Mat) {
        if (isDetected) return
        val timeInterval = System.currentTimeMillis() - lastDetectTime
        if (timeInterval < DETECT_INTERVAL){
            //设置一个检测间隔时间，可以提升画面帧数
            return
        }
        lastDetectTime = System.currentTimeMillis()
        val points = mutableListOf<Mat>()
        val res = WeChatQRCodeDetector.detectAndDecode(gray, points)
        val results = mutableListOf<QRCodeResult>()
        if (res != null && res.size > 0) {
            isDetected = true
            for ((i, content) in res.withIndex()) {
                val pointArr = FloatArray(8)
                points[i].get(0, 0, pointArr)
                val point1 = Point(pointArr[0].toDouble(), pointArr[1].toDouble())
                val point2 =
                    Point(pointArr[2].toDouble(), pointArr[3].toDouble())
                val point3 =
                    Point(pointArr[4].toDouble(), pointArr[5].toDouble())
                val point4 =
                    Point(pointArr[6].toDouble(), pointArr[7].toDouble())
                val qrCodeResult = QRCodeResult(content, point1, point2, point3, point4)
                results.add(qrCodeResult)
            }
            val isMultiQRCode = results.size > 1
            for (qrcodeResult in results) {
                //画出二维码的4个顶点
                drawQRCodeCornerPoint(source, qrcodeResult.point1)
                drawQRCodeCornerPoint(source, qrcodeResult.point2)
                drawQRCodeCornerPoint(source, qrcodeResult.point3)
                drawQRCodeCornerPoint(source, qrcodeResult.point4)
                //添加二维码中心箭头
                drawQRCodeArrow(qrcodeResult)
            }
            if (!isMultiQRCode) {
                //只有一个二维码时直接返回结果
                forwardListener?.forward(results[0].content)
            }
            val tmp = source.clone()
            runOnUIThread {
                disableView()
                showResult(tmp)
            }
        }
    }

    private fun drawQRCodeCornerPoint(source: Mat, point: Point?) {
        point?.let {
            Imgproc.circle(
                source,
                it,
                10,
                Scalar(0.0, 255.0, 0.0, 100.0),
                Imgproc.FILLED,
                Imgproc.LINE_AA
            )
        }
    }

    private fun drawQRCodeArrow(qrCodeResult: QRCodeResult) {
        val centerX = (qrCodeResult.point2.x + qrCodeResult.point4.x) / 2
        val centerY = (qrCodeResult.point2.y + qrCodeResult.point4.y) / 2
        runOnUIThread {
            addArrowImage(centerX, centerY, qrCodeResult.content)
        }
    }

    private fun addArrowImage(x: Double, y: Double, content: String) {
        val arrow = ImageView(context)
        arrow.tag = "arrow_$content"
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        params.width = ARROW_SIZE
        params.height = ARROW_SIZE
        val halfSize = ARROW_SIZE / 2
        val point = calculateRealPosition(x, y)
        params.leftMargin = (point.x - halfSize).toInt()
        params.topMargin = (point.y - halfSize).toInt()
        arrow.setImageResource(R.drawable.ic_open_qrcode)
        arrow.setOnClickListener {
            forwardListener?.forward(content)
        }
        addView(arrow, params)
    }

    //根据放大的实际图像和预览布局的比例，计算二维码中心点在布局中的实际坐标点
    private fun calculateRealPosition(x: Double, y: Double): Point {
        val point = Point()
        if (isRatioSmall) {
            val scale = min(vScale, hScale)
            val realX = x / scale
            val realY = y / scale
            val diffX = ((previewWidth * 1.00f / scale) - camera2View.measuredWidth) / 2
            point.x = realX - diffX
            point.y = realY
        } else {
            val scale = min(vScale, hScale)
            val realX = x / scale
            val realY = y / scale
            val diffY = ((previewHeight * 1.00f / scale) - camera2View.measuredHeight) / 2
            point.x = realX
            point.y = realY - diffY
        }
        return point
    }

    private fun showResult(source: Mat) {
        val bitmap = Bitmap.createBitmap(source.width(), source.height(), Bitmap.Config.RGB_565)
        Utils.matToBitmap(source, bitmap)
        resultImageView.setImageBitmap(bitmap)
        resultImageView.visibility = VISIBLE
        source.release()
    }

    private fun runOnUIThread(runnable: Runnable) {
        mainHandler.post(runnable)
    }

    fun isResultShowing(): Boolean {
        return resultImageView.visibility == VISIBLE
    }

    fun hideResult() {
        isDetected = false
        val iterator = children.iterator()
        val arrowList = mutableListOf<View>()
        while (iterator.hasNext()) {
            val child = iterator.next()
            if (child.tag != null && child.tag is String && (child.tag as String).startsWith("arrow_")) {
                arrowList.add(child)
            }
        }
        for (view in arrowList) {
            removeView(view)
        }
        resultImageView.setImageBitmap(null)
        resultImageView.visibility = INVISIBLE
    }

    fun onResume() {
        if (!OpenCVLoader.initDebug(true)) {
            OpenCVLoader.initAsync(
                OpenCVLoader.OPENCV_VERSION_3_4_0,
                context.applicationContext,
                mLoaderCallback
            )
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    fun enableView() {
        camera2View.enableView()
    }

    fun disableView() {
        camera2View.disableView()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        camera2View.disableView()
    }
}