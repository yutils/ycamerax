package com.kotlinx.camerax.listener

import java.io.File

interface TakeListener{
    fun value(file: File)
}