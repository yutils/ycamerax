package com.kotlinx.camerax.listener

/**
 * 缩放监听
 */
interface CustomTouchListener {
    /**
     * 缩放
     */
    fun zoom(delta: Float)

    /**
     * 点击
     */
    fun click(x: Float, y: Float)

    /**
     * 双击
     */
    fun doubleClick(x: Float, y: Float)

    /**
     * 长按
     */
    fun longClick(x: Float, y: Float)
}