package com.razamtech.smartbrakealert.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

internal fun ImageProxy.toBitmap(): Bitmap {
    if (format != ImageFormat.YUV_420_888 || planes.size < 3) {
        throw IllegalArgumentException("Unsupported image format: $format")
    }

    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    yBuffer.rewind()
    uBuffer.rewind()
    vBuffer.rewind()

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val outputStream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, outputStream)
    val jpegBytes = outputStream.toByteArray()
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}
