package com.kotlinx.camerax

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.kotlinx.camerax.listener.CustomTouchListener

/**
 * 手势监听
 * 缩放，点击，双击，长按
 * @author 余静 2021年10月9日15:38:16
 */

/*用法：

//初始化
val touchListener=TouchListener(applicationContext)
//设置监听
binding.view.setOnTouchListener { v, event ->
    touchListener.onTouch(event)
    return@setOnTouchListener true
}
//回调
touchListener.customTouchListener = object : CustomTouchListener {
    override fun zoom(delta: Float) {
        //双指缩放
    }

    override fun click(x: Float, y: Float) {
        //单击
    }

    override fun doubleClick(x: Float, y: Float) {
        //双击
    }

    override fun longClick(x: Float, y: Float) {
        //长按
    }
}
 */
class TouchListener(var context: Context) {
    /**
     * 缩放相关
     */
    private var currentDistance = 0f
    private var lastDistance = 0f
    private var mScaleGestureDetector: ScaleGestureDetector? = null
    private var mGestureDetector: GestureDetector? = null

    //用户监听这个回调
    var customTouchListener: CustomTouchListener? = null

    init {
        /**
         * 缩放监听
         */
        var onScaleGestureListener: ScaleGestureDetector.OnScaleGestureListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val delta = detector.scaleFactor
                customTouchListener?.zoom(delta)
                return true
            }
        }

        /**
         * 手势监听，单击，双击，长按
         */
        var onGestureListener: GestureDetector.SimpleOnGestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                customTouchListener?.longClick(e.x, e.y)
            }

            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                currentDistance = 0f
                lastDistance = 0f
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                customTouchListener?.click(e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                customTouchListener?.doubleClick(e.x, e.y)
                return true
            }
        }

        mGestureDetector = GestureDetector(context, onGestureListener)
        mScaleGestureDetector = ScaleGestureDetector(context, onScaleGestureListener)
    }


    fun onTouch(event: MotionEvent) {
        mScaleGestureDetector!!.onTouchEvent(event)
        if (!mScaleGestureDetector!!.isInProgress) {
            mGestureDetector!!.onTouchEvent(event)
        }
    }
}