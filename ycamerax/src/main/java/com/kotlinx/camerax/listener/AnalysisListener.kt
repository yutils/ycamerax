package com.kotlinx.camerax.listener

import android.graphics.Bitmap

interface AnalysisListener{
    fun value(bitmap: Bitmap?)
}