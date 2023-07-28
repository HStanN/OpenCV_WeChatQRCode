package com.demo.wxqrcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Im
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.children
import org.opencv.android.*
import org.opencv.android.CameraBridgeViewBase.CAMERA_ID_BACK
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

class QRCodeDetectorLayout : FrameLayout, CvCameraViewListener2 {

    val camera2View: JavaCamera2View = JavaCamera2View(context, CAMERA_ID_BACK)

    private val resultImageView: ImageView

    private var dstRgb: Mat? = null
    private var dstGray: Mat? = null
    private val center: Point = Point()
    private var m: Mat? = null

    private var previewWidth = 0
    private var previewHeight = 0

    private var isDetected = false

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
        resultImageView = ImageView(context)
        resultImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        resultImageView.visibility = INVISIBLE
        addView(resultImageView)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }

    private var startTime = 0L

    override fun onCameraViewStarted(width: Int, height: Int) {
        Log.d(TAG, "onCameraViewStarted: $width * $height")
        previewWidth = width
        previewHeight = height
        startTime = System.currentTimeMillis()
    }

    override fun onCameraViewStopped() {
        Log.d(TAG, "onCameraViewStopped")
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        dstRgb?.release()
//        dstGray?.release()
        val rgba = inputFrame?.rgba()
//        val gray = inputFrame?.gray()
        rgba?.let {
            center.x = (rgba.cols() shr 1).toDouble()
            center.y = (rgba.rows() shr 1).toDouble()
            if (null == dstRgb) {
                m = Imgproc.getRotationMatrix2D(center, 270.0, 1.0)
                dstRgb = Mat(rgba.cols(), rgba.rows(), rgba.type())
//                dstGray = Mat(gray!!.cols(), gray.rows(), gray.type())
            }
            Imgproc.warpAffine(rgba, dstRgb, m, rgba.size())
            dstRgb?.let {
                detectMat(it)
            }
        }
        return dstRgb
    }

    private fun detectMat(source: Mat) {
        if (isDetected) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - startTime < 2000){
            return
        }
        val points = mutableListOf<Mat>()
        val res = WeChatQRCodeDetector.detectAndDecode(source, points)
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
                val qrCodeResult = QRCodeResult(content, point1, point2, point3,point4)
                results.add(qrCodeResult)
            }
            val isMultiQRCode = results.size > 1
            for (qrcodeResult in results) {
                drawQRCodeCornerPoint(source, qrcodeResult.point1)
                drawQRCodeCornerPoint(source, qrcodeResult.point2)
                drawQRCodeCornerPoint(source, qrcodeResult.point3)
                drawQRCodeCornerPoint(source, qrcodeResult.point4)
                drawQRCodeArrow(source,qrcodeResult)
            }
            if (!isMultiQRCode) {
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

    private fun drawQRCodeArrow(source: Mat,qrCodeResult: QRCodeResult) {
        val centerX = (qrCodeResult.point2.x + qrCodeResult.point4.x) / 2
        val centerY = (qrCodeResult.point2.y + qrCodeResult.point4.y) / 2
        val centerPoint = Point(centerX,centerY)
        centerPoint?.let {
            Imgproc.circle(
                source,
                centerPoint,
                10,
                Scalar(0.0, 0.0, 0.0, 0.0),
                Imgproc.FILLED,
                Imgproc.LINE_AA
            )
        }
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
        params.leftMargin = (x - halfSize).toInt()
        val extraMargin = (measuredHeight - previewHeight) / 2.0
        params.topMargin = (extraMargin + y - halfSize).toInt()
        arrow.setImageResource(R.drawable.ic_open_qrcode)
        arrow.alpha = 0.3f
        arrow.setOnClickListener {
            forwardListener?.forward(content)
        }
        addView(arrow, params)
        arrow.postDelayed({
            Log.d("ArrowPosition","extra = $extraMargin")
            Log.d("ArrowPosition","x = $x , y = $y")
            Log.d("ArrowPosition","pivotX = ${arrow.pivotX} , pivotY = ${arrow.pivotY}")
            Log.d("ArrowPosition","vX = ${arrow.x} , vY = ${arrow.y}")
        },1000)
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
        for (view in arrowList){
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