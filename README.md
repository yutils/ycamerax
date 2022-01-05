# YCameraX

CameraX 使用，支持安卓12，一键拍照，一键视频，逐帧回调bitmap分析，支持双指放大，双击放大，单击手动对焦

[![platform](https://img.shields.io/badge/platform-Android-lightgrey.svg)](https://developer.android.google.cn/studio/index.html)
![Gradle](https://img.shields.io/badge/Gradle-7.1-brightgreen.svg)
[![last commit](https://img.shields.io/github/last-commit/yutils/ycamerax.svg)](https://github.com/yutils/ycamerax/commits/master)
![repo size](https://img.shields.io/github/repo-size/yutils/ycamerax.svg)
![android studio](https://img.shields.io/badge/android%20studio-2020.3.1-green.svg)
[![maven](https://img.shields.io/badge/maven-address-green.svg)](https://search.maven.org/artifact/com.kotlinx/ycamerax)

**[releases里面有APK文件。点击前往](https://github.com/yutils/ycamerax/releases)**

**[releases里面有AAR包。点击前往](https://github.com/yutils/ycamerax/releases)**


**如果拉取整个项目，请用AS2020.3.1以上打开**

# `不建议直接拉取项目编译，请仔细看完 ` #

## 引用

1. 在根build.gradle中添加

```
allprojects {
    repositories {
        mavenCentral()
    }
}
```

2. [子module添加依赖，当前最新版：————> 1.0.1　　　　![最新版](https://img.shields.io/badge/%E6%9C%80%E6%96%B0%E7%89%88-1.0.1-green.svg)](https://search.maven.org/artifact/com.kotlinx/ycamerax)

```
dependencies {
    //更新地址  https://github.com/yutils/ycamerax 建议过几天访问看下有没有新版本
    implementation 'com.kotlinx:ycamerax:1.0.1'
}
```

# 使用方法

可以参考TakeActivity.kt

**kotlin**

```kotlin
package com.kotlinx.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.renderscript.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.databinding.DataBindingUtil
import com.kotlinx.camerax.databinding.ActivityTakeBinding
import com.kotlinx.camerax.listener.AnalysisListener
import com.kotlinx.camerax.listener.TakeListener
import com.kotlinx.camerax.listener.VideoListener
import java.io.File
import java.util.*

/**
 * 调用拍照录像，示例
 */
class TakeActivity : AppCompatActivity() {
    lateinit var yCameraX: YCameraX
    lateinit var binding: ActivityTakeBinding

    @SuppressLint("ClickableViewAccessibility", "RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_take)
        //申请权限
        registerPermissions.launch(permissions)
        yCameraX = YCameraX(this, binding.viewFinder, binding.focusView)
        //当前相机,后置摄像头
        yCameraX.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        //是否启用图像分析，启用图像分析就不能启用录像，启用录像就不能图像分析
        yCameraX.useImageAnalysis = true
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

```
