package com.kotlinx.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.renderscript.*
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.kotlinx.camerax.listener.AnalysisListener
import com.kotlinx.camerax.listener.CustomTouchListener
import com.kotlinx.camerax.listener.TakeListener
import com.kotlinx.camerax.listener.VideoListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CamaraX 使用
 * 注意：imageAnalysis 和 videoCapture 不能同时使用
 * @author yujing 2021年10月10日14:45:13
 */
/*
用法：
class TakeActivity : AppCompatActivity() {
    lateinit var yCameraX: YCameraX
    lateinit var binding: ActivityTakeBinding

    @SuppressLint("ClickableViewAccessibility", "RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_take)
        yCameraX = YCameraX(this, binding.viewFinder, binding.focusView)
        //当前相机,后置摄像头
        yCameraX.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        //显示旋转方向
        yCameraX.displayOrientation=0
        //是否启用图像分析，启用图像分析就不能启用录像，启用录像就不能图像分析
        yCameraX.useImageAnalysis = false
        //申请权限
        registerPermissions.launch(permissions)

        //拍照完成监听
        yCameraX.takeListener = object : TakeListener {
            override fun value(file: File) {
                Toast.makeText(this@TakeActivity, "拍照完成：${file.path}", Toast.LENGTH_SHORT).show()
            }
        }
        //录像完成监听
        yCameraX.videoListener = object : VideoListener {
            override fun value(file: File) {
                Toast.makeText(this@TakeActivity, "录像完成：${file.path}", Toast.LENGTH_SHORT).show()
            }
        }
        //图像分析，逐帧回调
        yCameraX.analysisListener = object : AnalysisListener {
            override fun value(bitmap: Bitmap?) {
                binding.imageView.setImageBitmap(bitmap)
            }
        }

        //拍照按钮
        binding.cameraCaptureButton.setOnClickListener { yCameraX.takePhoto() }

        //录像按钮
        binding.btnStartVideo.visibility = if (yCameraX.useImageAnalysis) View.GONE else View.VISIBLE
        binding.btnStartVideo.setOnClickListener {
            if (binding.btnStartVideo.text == "录像") {
                binding.btnStartVideo.text = "停止"
                binding.btnStartVideo.strokeColor = ColorStateList.valueOf(Color.RED)
                yCameraX.takeVideo()//开始录像
            } else {
                yCameraX.stopVideo()//停止录制
                binding.btnStartVideo.text = "录像"
                binding.btnStartVideo.strokeColor = ColorStateList.valueOf(Color.parseColor("#66CCFF"))
            }
        }

        //切换摄像头
        binding.btnSwitch.setOnClickListener {
            //yCameraX.switch()
            yCameraX.cameraSelector = if (yCameraX.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            registerPermissions.launch(permissions)
        }
    }

    // 多个权限获取
    private var permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)
    private val registerPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        for (item in permissions) {
            if (it[item]!!) {// 同意
                Log.d("TakeActivity", "获取权限：${item}")
            } else {
                Toast.makeText(this, "用户未授予的权限。", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
        }
        //获取所有权限后，启动摄像头
        yCameraX.startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        yCameraX.onDestroy()
    }
}
 */
class YCameraX(val activity: AppCompatActivity, val viewFinder: PreviewView, val focusView: FocusImageView) {
    companion object {
        private const val TAG = "CameraX"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA//当前相机
    var useImageAnalysis = true  //是否启用图像分析，启用图像分析就不能启用录像，启用录像就不能图像分析
    var analysisListener: AnalysisListener? = null //图像分析回调
    var takeListener: TakeListener? = null //拍照回调
    var videoListener: VideoListener? = null //录像回调
    var displayOrientation = 90//显示旋转方向,硬件方向
    var analysisInterval = 1//处理间隔,1每一帧，2每两帧

    private var analysisCount = 0L
    private var rotation = 0  //旋转角度
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null//相机信息
    private var preview: Preview? = null//预览对象
    private var camera: Camera? = null//相机对象
    private var imageCapture: ImageCapture? = null//拍照用例
    private var videoCapture: VideoCapture? = null//录像用例
    private var imageAnalysis: ImageAnalysis? = null//图像分析用例

    /**
     * 启动摄像头
     */
    @SuppressLint("RestrictedApi")
    fun startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            //旋转角度
            rotation = viewFinder.display.rotation ?: 0
            //获取相机信息
            cameraProvider = cameraProviderFuture.get()
            //预览配置
            preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            //拍照用例配置
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)//设置高宽比
                .setTargetRotation(rotation)//设置旋转角度
                .build()

            //每帧图像分析
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)//设置高宽比
                .setTargetRotation(rotation)//设置旋转角度
                .build()

            //录像用例配置
            videoCapture = VideoCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3) //设置高宽比
                .setTargetRotation(rotation)//设置旋转角度
                .build()

            try {
                cameraProvider?.unbindAll()//先解绑所有用例
                //imageAnalysis 和 videoCapture 不能同时使用
                camera = cameraProvider?.bindToLifecycle(activity, cameraSelector, preview, imageCapture, if (useImageAnalysis) imageAnalysis else videoCapture)//绑定用例

            } catch (exc: Exception) {
                Log.e(TAG, "用例绑定失败", exc)
            }
            startImageAnalysis()
            initCameraListener()
        }, ContextCompat.getMainExecutor(activity))
    }


    /**
     * 切换摄像头
     */
    fun switch() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    /**
     * 初始化手势动作
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initCameraListener() {
        if (camera == null) return
        val zoomState = camera!!.cameraInfo.zoomState

        //监听手势
        val touchListener = TouchListener(activity)
        viewFinder.setOnTouchListener { v, event ->
            touchListener.onTouch(event)
            return@setOnTouchListener true
        }
        //手势回调
        touchListener.customTouchListener = object : CustomTouchListener {
            override fun zoom(delta: Float) {
                //双指缩放
                zoomState.value?.let {
                    val currentZoomRatio = it.zoomRatio
                    camera!!.cameraControl.setZoomRatio(currentZoomRatio * delta)
                }
            }

            override fun click(x: Float, y: Float) {
                //点击对焦
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    //val factory =  binding.viewFinder.createMeteringPointFactory(cameraSelector)
                    val factory = viewFinder.meteringPointFactory
                    val point = factory.createPoint(x, y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    focusView.startFocus(Point(x.toInt(), y.toInt()))
                    val future: ListenableFuture<*> = camera!!.cameraControl.startFocusAndMetering(action)
                    future.addListener({
                        try {
                            val result = future.get() as FocusMeteringResult
                            if (result.isFocusSuccessful) {
                                focusView.onFocusSuccess()
                            } else {
                                focusView.onFocusFailed()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "对焦错误", e)
                        }
                    }, cameraExecutor)
                }
            }

            override fun doubleClick(x: Float, y: Float) {
                //双击放大缩小
                zoomState.value?.let {
                    val currentZoomRatio = it.zoomRatio
                    if (currentZoomRatio > it.minZoomRatio) {
                        camera!!.cameraControl.setLinearZoom(0f)
                    } else {
                        camera!!.cameraControl.setLinearZoom(0.5f)
                    }
                }
            }

            override fun longClick(x: Float, y: Float) {
                //长按
            }
        }
    }

    /**
     * 拍照
     */
    fun takePhoto() {
        val imageCapture = imageCapture ?: return
        //图片保存路径
        val fileName = "CameraX_" + SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA).format(System.currentTimeMillis()) + ".jpg"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/Camera/" + fileName)
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    //val savedUri = Uri.fromFile(file)
                    val msg = "拍照完成: ${file.absolutePath}"
                    Log.i(TAG, msg)
                    activity.runOnUiThread { takeListener?.value(file) }
                    // 通知图库更新
                    val paths = arrayOf(file.absolutePath)
                    MediaScannerConnection.scanFile(activity, paths, null, null)
                }

                override fun onError(e: ImageCaptureException) {
                    Log.e(TAG, "拍照失败: ${e.message}", e)
                    activity.runOnUiThread { Toast.makeText(activity, "拍照失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            })
    }

    /**
     * 录像
     */
    @SuppressLint("RestrictedApi", "ClickableViewAccessibility")
    fun takeVideo() {
        if (useImageAnalysis) return
        //视频保存路径
        val fileName = "CameraX_" + SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA).format(System.currentTimeMillis()) + ".mp4"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/Camera/" + fileName)
        if (!file.parentFile.exists()) file.parentFile.mkdirs()

        var ofo = VideoCapture.OutputFileOptions.Builder(file).build()
        //开始录像
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        videoCapture?.startRecording(ofo, Executors.newSingleThreadExecutor(), object : VideoCapture.OnVideoSavedCallback {
            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                val msg = "录像完成: ${file.absolutePath}"
                Log.i(TAG, msg)
                activity.runOnUiThread { videoListener?.value(file) }
                // 通知图库更新
                val paths = arrayOf(file.absolutePath)
                MediaScannerConnection.scanFile(activity, paths, null, null)
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                //保存失败的回调，可能在开始或结束录制时被调用
                Log.e(TAG, "录像失败: $message")
                activity.runOnUiThread { Toast.makeText(activity, "录像失败: $message", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    /**
     * 停止录像
     */
    @SuppressLint("RestrictedApi")
    fun stopVideo() {
        if (useImageAnalysis) return
        try {
            videoCapture?.stopRecording()//停止录制
        } catch (e: Exception) {
            Log.e(TAG, "录像失败: ${e.message}", e)
            activity.runOnUiThread { Toast.makeText(activity, "录像失败: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    /**
     * 开始图像分析
     */
    private fun startImageAnalysis() {
        if (!useImageAnalysis) return
        imageAnalysis?.setAnalyzer(cameraExecutor!!, object : ImageAnalysis.Analyzer {
            //每帧回调
            override fun analyze(image: ImageProxy) {
                if (analysisCount++ % analysisInterval == 0L) {
                    analysisListener?.let {
                        val bitmap = image.toBitmap()
                        activity.runOnUiThread { it.value(bitmap) }
                    }
                }
                image.close()
            }

            /**
             * image 转 bitmap
             */
            fun ImageProxy.toBitmap(): Bitmap? {
                val yBuffer = planes[0].buffer // Y
                val uBuffer = planes[1].buffer // U
                val vBuffer = planes[2].buffer // V

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)

                //U and V are swapped
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                //yuv转bitmap
                var bitmap: Bitmap? = nv21ToBitmapFast(nv21, this.width, this.height, activity) ?: return null

                //旋转
                val matrix = Matrix()
                val ori = getCameraOri(rotation)
                matrix.setRotate(if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) ori.toFloat() else -ori.toFloat())
                //前置摄像头y轴翻转
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                    matrix.postScale(-1f, 1f)
                // 围绕原地进行旋转
                val newBM = Bitmap.createBitmap(bitmap!!, 0, 0, width, height, matrix, false)
                if (newBM == bitmap) return newBM
                bitmap.recycle()
                return newBM
            }

            /**
             * 旋转
             */
            private fun getCameraOri(rotation: Int): Int {
                var degrees = rotation * 90
                when (rotation) {
                    0 -> degrees = 0
                    1 -> degrees = 90
                    2 -> degrees = 180
                    3 -> degrees = 270
                }
                //var displayOrientation = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) 90 else 270
                var displayOrientation = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) displayOrientation else 360 - displayOrientation

                var result: Int
                if (CameraSelector.DEFAULT_FRONT_CAMERA == cameraSelector) {
                    result = (displayOrientation + degrees) % 360
                    result = (360 - result) % 360
                } else {
                    result = (displayOrientation - degrees + 360) % 360
                }
                return result
            }

            /**
             * nv21 转 bitmap
             */
            fun nv21ToBitmapFast(nv21: ByteArray, width: Int, height: Int, context: Context?): Bitmap? {
                val rs = RenderScript.create(context)
                val toRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
                val yuvType = Type.Builder(rs, Element.U8(rs)).setX(nv21.size)
                val `in` = Allocation.createTyped(rs, yuvType.create(), 1)
                val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height)
                val out = Allocation.createTyped(rs, rgbaType.create(), 1)
                `in`.copyFrom(nv21)
                toRgb.setInput(`in`)
                toRgb.forEach(out)
                val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                out.copyTo(newBitmap)
                out.destroy()
                `in`.destroy()
                toRgb.destroy()
                rs.destroy()
                return newBitmap
            }
        })
    }

    /**
     * 退出释放
     */
    fun onDestroy() {
        cameraExecutor?.shutdown()
    }
}