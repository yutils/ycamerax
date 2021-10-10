package com.kotlinx.camerax.listener

import java.io.File

interface VideoListener{
    fun value(file: File)
}