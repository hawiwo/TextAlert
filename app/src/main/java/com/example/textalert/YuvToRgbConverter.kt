package com.example.textalert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.renderscript.RenderScript // deprecated but still present; we won't use it
import androidx.camera.core.ImageProxy
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory

class YuvToRgbConverter(private val context: Context) {

    fun toBitmap(image: ImageProxy): Bitmap {
        // ImageProxy ist YUV_420_888 -> in NV21 kodieren, dann als JPEG decodieren
        val nv21 = yuv420888ToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)

        val chromaRowStride = image.planes[1].rowStride
        val chromaPixelStride = image.planes[1].pixelStride

        // U und V abwechselnd in NV21 schreiben (VU-VU-…)
        var offset = ySize
        val width = image.width
        val height = image.height
        val rows = height / 2
        val cols = width / 2

        val uRow = ByteArray(chromaRowStride)
        val vRow = ByteArray(chromaRowStride)

        var row = 0
        while (row < rows) {
            uBuffer.get(uRow, 0, chromaRowStride)
            vBuffer.get(vRow, 0, chromaRowStride)
            var col = 0
            while (col < cols) {
                val vuIndex = col * chromaPixelStride
                nv21[offset++] = vRow[vuIndex]
                nv21[offset++] = uRow[vuIndex]
                col++
            }
            row++
        }
        return nv21
    }
}

