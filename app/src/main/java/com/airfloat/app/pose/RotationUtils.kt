package com.airfloat.app.pose

import android.graphics.Bitmap
import android.graphics.Matrix

fun Bitmap.rotate(deg:Int):Bitmap{

    if(deg==0) return this

    val matrix = Matrix()

    matrix.postRotate(
        deg.toFloat()
    )

    return Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        matrix,
        true
    )
}