package com.airfloat.app.pose

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

fun ImageProxy.toBitmap(): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer
    buffer.rewind()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)

    val crop = cropRect
    val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())

    return cropped
}
